package com.fherrmann.wahlen.wahlabend;

import com.fherrmann.wahlen.config.CacheConfig;
import com.fherrmann.wahlen.domain.Election;
import com.fherrmann.wahlen.domain.ElectionReport;
import com.fherrmann.wahlen.domain.ElectionResult;
import com.fherrmann.wahlen.domain.Parliament;
import com.fherrmann.wahlen.domain.Party;
import com.fherrmann.wahlen.domain.ResultKind;
import com.fherrmann.wahlen.repository.ElectionReportRepository;
import com.fherrmann.wahlen.repository.ElectionRepository;
import com.fherrmann.wahlen.repository.ParliamentRepository;
import com.fherrmann.wahlen.repository.PartyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traegt Wahlabend-Staende ein — von Hand ueber die API oder automatisch aus
 * einer Landeswahlleitungs-CSV — und prueft sie vorher auf Plausibilitaet.
 *
 * <p>Ein Stand wird nie veraendert, nur angelegt oder geloescht. Der Verlauf
 * des Abends soll spaeter genau so nachlesbar sein, wie er passiert ist.
 */
@Service
public class ElectionReportService {

    private static final Logger log = LoggerFactory.getLogger(ElectionReportService.class);

    /** Die Summe der Prozentwerte darf etwas von 100 abweichen (Rundung, fehlende Sonstige). */
    private static final double MIN_SUM = 95.0;
    private static final double MAX_SUM = 101.0;
    private static final String SONSTIGE = "Sonstige";

    private final ParliamentRepository parliaments;
    private final ElectionRepository elections;
    private final ElectionReportRepository reports;
    private final PartyRepository parties;
    private final CacheManager cacheManager;

    public ElectionReportService(ParliamentRepository parliaments,
                                 ElectionRepository elections,
                                 ElectionReportRepository reports,
                                 PartyRepository parties,
                                 CacheManager cacheManager) {
        this.parliaments = parliaments;
        this.elections = elections;
        this.reports = reports;
        this.parties = parties;
        this.cacheManager = cacheManager;
    }

    /** Handeingabe: Wahl ueber Slug und Datum, Parteinamen muessen passen. */
    @Transactional
    public ElectionReport add(String slug, LocalDate electionDate, ReportInput input) {
        Parliament parliament = parliaments.findBySlug(slug)
                .orElseThrow(() -> ReportException.notFound("Unbekanntes Parlament: " + slug));
        Election election = elections.findByParliamentIdAndElectionDate(parliament.getId(), electionDate)
                .orElseThrow(() -> ReportException.notFound(
                        "Keine Wahl am " + electionDate + " für " + parliament.getName()));
        return add(election, input);
    }

    @Transactional
    public ElectionReport add(Election detachedElection, ReportInput input) {
        validate(input);
        // Der Poller bringt die Wahl aus einer frueheren Transaktion mit.
        Election election = elections.findById(detachedElection.getId())
                .orElseThrow(() -> ReportException.notFound("Wahl nicht mehr vorhanden"));
        Map<String, Party> byKey = partiesByKey();
        Party sonstige = byKey.get(PartyAliases.normalize(SONSTIGE));

        Map<Party, Double> percents = new LinkedHashMap<>();
        double sum = 0;
        for (Map.Entry<String, Double> e : input.results().entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            Party party = resolve(e.getKey(), input, byKey, sonstige);
            percents.merge(party, e.getValue(), Double::sum);
            sum += e.getValue();
        }
        if (sum < MIN_SUM || sum > MAX_SUM) {
            throw ReportException.badRequest(
                    "Die Prozentwerte summieren sich auf %.1f, erwartet werden rund 100".formatted(sum));
        }
        // Fehlt "Sonstige", ist der Rest bis 100 gemeint.
        if (sonstige != null && !percents.containsKey(sonstige) && sum < 100.0 - 0.05) {
            percents.put(sonstige, Math.round((100.0 - sum) * 10.0) / 10.0);
        }

        Map<Party, Integer> seats = new LinkedHashMap<>();
        if (input.seats() != null) {
            for (Map.Entry<String, Integer> e : input.seats().entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                Party party = resolve(e.getKey(), input, byKey, sonstige);
                if (sonstige != null && party.getId().equals(sonstige.getId())) {
                    continue;
                }
                seats.merge(party, e.getValue(), Integer::sum);
            }
        }

        Instant reportedAt = input.reportedAt() != null ? input.reportedAt() : Instant.now();
        String source = input.source().trim();
        if (reports.existsByElectionIdAndSourceAndKindAndReportedAt(
                election.getId(), source, input.kind(), reportedAt)) {
            throw new ReportException(HttpStatus.CONFLICT,
                    "Für %s gibt es um diese Zeit schon eine %s — erst löschen, dann neu eintragen"
                            .formatted(source, input.kind().label()));
        }
        ElectionReport report = new ElectionReport(election, input.kind(), reportedAt, source);
        report.setSourceUrl(blankToNull(input.sourceUrl()));
        report.setNote(blankToNull(input.note()));
        report.setTurnoutPercent(input.turnout() != null ? decimal(input.turnout()) : null);
        report.setSeatsOfficial(input.seatsOfficial() && !seats.isEmpty());
        report.setFingerprint(input.fingerprint());

        percents.forEach((party, percent) -> {
            ElectionResult row = new ElectionResult(election, party, decimal(percent), input.kind());
            row.setReportedAt(reportedAt);
            row.setSource(report.getSource());
            row.setSeats(seats.get(party));
            row.setReport(report);
            report.getResults().add(row);
        });

        ElectionReport saved = reports.save(report);
        clearCaches();
        log.info("Wahlabend {} {}: {} von {} eingetragen ({} Parteien)",
                election.getParliament().getSlug(), election.getElectionDate(),
                input.kind(), report.getSource(), percents.size());
        return saved;
    }

    /**
     * Fuer automatische Quellen: nur eintragen, wenn sich gegenueber dem letzten
     * Stand derselben Quelle etwas geaendert hat.
     *
     * @return der neue Stand, oder leer, wenn nichts Neues drin war
     */
    @Transactional
    public Optional<ElectionReport> addIfChanged(Election election, ReportInput input) {
        Optional<ElectionReport> latest = reports
                .findFirstByElectionIdAndSourceOrderByReportedAtDescIdDesc(election.getId(), input.source().trim());
        if (latest.isPresent() && input.fingerprint() != null
                && input.fingerprint().equals(latest.get().getFingerprint())) {
            return Optional.empty();
        }
        if (latest.isPresent() && input.reportedAt() != null
                && !input.reportedAt().isAfter(latest.get().getReportedAt())) {
            // Gleicher oder aelterer Zeitstempel bei anderem Inhalt: die Quelle hat
            // nachtraeglich korrigiert. Der Unique-Index verlangt einen neuen
            // Zeitpunkt — der Abrufzeitpunkt ist dann der ehrlichere.
            input = new ReportInput(input.kind(), Instant.now(), input.source(), input.sourceUrl(),
                    input.turnout(), input.note(), input.results(), input.seats(), input.seatsOfficial(),
                    input.aliases(), input.strict(), input.fingerprint());
        }
        return Optional.of(add(election, input));
    }

    @Transactional
    public void delete(long id) {
        ElectionReport report = reports.findById(id)
                .orElseThrow(() -> ReportException.notFound("Kein Stand mit ID " + id));
        reports.delete(report);
        clearCaches();
        log.info("Wahlabend-Stand {} ({} {}) gelöscht", id, report.getKind(), report.getSource());
    }

    @Transactional(readOnly = true)
    public List<ElectionReport> history(Long electionId) {
        return reports.findByElectionWithResults(electionId);
    }

    /** Parteikuerzel, die die Eingabe kennt — fuer Fehlermeldungen und das Formular. */
    @Transactional(readOnly = true)
    public List<String> knownShortcuts() {
        return parties.findAllByOrderBySortOrderAscShortcutAsc().stream().map(Party::getShortcut).toList();
    }

    private static void validate(ReportInput input) {
        if (input.kind() == null || !input.kind().isElectionNight()) {
            throw ReportException.badRequest(
                    "Art muss PROGNOSE, HOCHRECHNUNG, AUSZAEHLUNG oder VORLAEUFIG sein — "
                    + "das amtliche Endergebnis gehört in elections.yaml");
        }
        if (input.source() == null || input.source().isBlank()) {
            throw ReportException.badRequest("Quelle fehlt (z.B. \"ARD / infratest dimap\")");
        }
        if (input.results() == null || input.results().isEmpty()) {
            throw ReportException.badRequest("Keine Ergebnisse angegeben");
        }
        for (Map.Entry<String, Double> e : input.results().entrySet()) {
            Double v = e.getValue();
            if (v != null && (v < 0 || v > 100)) {
                throw ReportException.badRequest("Unplausibler Wert für " + e.getKey() + ": " + v);
            }
        }
        if (input.turnout() != null && (input.turnout() < 0 || input.turnout() > 100)) {
            throw ReportException.badRequest("Unplausible Wahlbeteiligung: " + input.turnout());
        }
    }

    private Party resolve(String name, ReportInput input, Map<String, Party> byKey, Party sonstige) {
        String canonical = PartyAliases.canonical(name, input.aliases());
        Party party = byKey.get(PartyAliases.normalize(canonical));
        if (party != null) {
            return party;
        }
        if (input.strict() || sonstige == null) {
            throw ReportException.badRequest(
                    "Unbekannte Partei \"%s\". Bekannte Kürzel: %s".formatted(name, String.join(", ", knownShortcuts())));
        }
        log.debug("Partei '{}' unbekannt — zaehlt zu Sonstige", name);
        return sonstige;
    }

    private Map<String, Party> partiesByKey() {
        Map<String, Party> map = new HashMap<>();
        parties.findAll().forEach(p -> map.put(PartyAliases.normalize(p.getShortcut()), p));
        return map;
    }

    private void clearCaches() {
        var overview = cacheManager.getCache(CacheConfig.OVERVIEW);
        if (overview != null) {
            overview.clear();
        }
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
