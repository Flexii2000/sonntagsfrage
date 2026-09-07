package com.fherrmann.wahlen.wahlabend;

import com.fherrmann.wahlen.analysis.PollPoint;
import com.fherrmann.wahlen.analysis.TrendCalculator;
import com.fherrmann.wahlen.api.Dtos;
import com.fherrmann.wahlen.config.WahlenProperties;
import com.fherrmann.wahlen.domain.Election;
import com.fherrmann.wahlen.domain.ElectionReport;
import com.fherrmann.wahlen.domain.ElectionResult;
import com.fherrmann.wahlen.domain.Parliament;
import com.fherrmann.wahlen.domain.Party;
import com.fherrmann.wahlen.domain.ResultKind;
import com.fherrmann.wahlen.repository.ElectionReportRepository;
import com.fherrmann.wahlen.service.ElectionCalendarService;
import com.fherrmann.wahlen.service.SeatsDtoFactory;
import com.fherrmann.wahlen.wahlabend.WahlabendClock.Phase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setzt den Wahlabend-Block zusammen: der massgebliche Stand, der Verlauf des
 * Abends, Sitze und Mehrheiten daraus, und der Vergleich mit der vorigen Wahl
 * und mit dem, was die Umfragen zum Wahltag sagten.
 */
@Service
public class WahlabendService {

    /** Glaettungsfenster fuer "was sagten die Umfragen zum Wahltag" — wie bei den Kopfzahlen. */
    private static final int POLLS_WINDOW_DAYS = 120;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d.M., HH:mm", Locale.GERMAN);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN);

    private final ElectionCalendarService calendar;
    private final ElectionReportRepository reports;
    private final SeatsDtoFactory seats;
    private final TrendCalculator trendCalculator;
    private final LiveSourceStatus sourceStatus;
    private final WahlenProperties properties;

    public WahlabendService(ElectionCalendarService calendar,
                            ElectionReportRepository reports,
                            SeatsDtoFactory seats,
                            TrendCalculator trendCalculator,
                            LiveSourceStatus sourceStatus,
                            WahlenProperties properties) {
        this.calendar = calendar;
        this.reports = reports;
        this.seats = seats;
        this.trendCalculator = trendCalculator;
        this.sourceStatus = sourceStatus;
        this.properties = properties;
    }

    /** Alle Wahlen, deren Wahlabend gerade laeuft (fuer den automatischen Abruf). */
    @Transactional(readOnly = true)
    public List<Election> liveElections() {
        List<Election> all = calendar.all();
        ZonedDateTime now = calendar.now();
        return all.stream()
                .filter(e -> !e.getElectionDate().isAfter(now.toLocalDate()))
                .filter(e -> WahlabendClock.phase(e.getElectionDate(),
                        ElectionCalendarService.hasOfficialResult(e), now) == Phase.LIVE)
                .toList();
    }

    public boolean isHot(Election election) {
        return WahlabendClock.isHot(election.getElectionDate(), calendar.now(),
                properties.wahlabend().hotHours());
    }

    /** Der massgebliche Stand: ein vorlaeufiges Ergebnis schlaegt jede Hochrechnung, sonst der juengste. */
    public static Optional<ElectionReport> pickLatest(List<ElectionReport> history) {
        Optional<ElectionReport> official = history.stream()
                .filter(r -> r.getKind() == ResultKind.VORLAEUFIG)
                .max(Comparator.comparing(ElectionReport::getReportedAt).thenComparing(ElectionReport::getId));
        if (official.isPresent()) {
            return official;
        }
        return history.stream()
                .max(Comparator.comparing(ElectionReport::getReportedAt).thenComparing(ElectionReport::getId));
    }

    /** Juengster Stand einer Wahl — Ersatz fuer das amtliche Ergebnis, solange das fehlt. */
    @Transactional(readOnly = true)
    public Optional<ElectionReport> latestReport(Election election) {
        return pickLatest(reports.findByElectionWithResults(election.getId()));
    }

    /** Kurzfassung fuer die Karten der Startseite. */
    @Transactional(readOnly = true)
    public Optional<Dtos.WahlabendSummaryDto> summary(Parliament parliament, List<Election> all) {
        Optional<Election> last = calendar.lastElection(parliament.getId(), all);
        if (last.isEmpty()) {
            return Optional.empty();
        }
        Election election = last.get();
        Phase phase = WahlabendClock.phase(election.getElectionDate(),
                ElectionCalendarService.hasOfficialResult(election), calendar.now());
        if (phase == Phase.ABGESCHLOSSEN) {
            return Optional.empty();
        }
        Optional<ElectionReport> latest = latestReport(election);
        return Optional.of(new Dtos.WahlabendSummaryDto(
                phase.name(), phase == Phase.LIVE,
                latest.map(r -> r.getKind().label()).orElse(null),
                latest.map(r -> timeLabel(r.getReportedAt(), election.getElectionDate())).orElse(null)));
    }

    /**
     * Der komplette Block fuer die Detailseite.
     *
     * @return leer, wenn es fuer dieses Parlament keinen Wahlabend zu zeigen gibt —
     *         also immer dann, wenn das amtliche Ergebnis da ist und kein Stand
     *         vom Abend gespeichert wurde
     */
    @Transactional(readOnly = true)
    public Optional<Dtos.WahlabendDto> forParliament(Parliament parliament, List<Election> all,
                                                     List<PollPoint> points) {
        Optional<Election> last = calendar.lastElection(parliament.getId(), all);
        if (last.isEmpty()) {
            return Optional.empty();
        }
        Election election = last.get();
        ZonedDateTime now = calendar.now();
        Phase phase = WahlabendClock.phase(election.getElectionDate(),
                ElectionCalendarService.hasOfficialResult(election), now);
        List<ElectionReport> history = reports.findByElectionWithResults(election.getId());
        if (phase == Phase.ABGESCHLOSSEN && history.isEmpty()) {
            return Optional.empty();
        }

        LocalDate electionDate = election.getElectionDate();
        Optional<ElectionReport> latest = pickLatest(history);

        // Vorige Wahl desselben Parlaments mit amtlichem Ergebnis.
        Optional<Election> previous = all.stream()
                .filter(e -> e.getParliament().getId().equals(parliament.getId()))
                .filter(e -> e.getElectionDate().isBefore(electionDate))
                .filter(ElectionCalendarService::hasOfficialResult)
                .max(Comparator.comparing(Election::getElectionDate));
        Map<Integer, Double> previousResults = new LinkedHashMap<>();
        previous.ifPresent(p -> p.getResults().stream()
                .filter(r -> r.getKind() == ResultKind.AMTLICH)
                .sorted(Comparator.comparing(ElectionResult::getPercent).reversed())
                .forEach(r -> previousResults.put(r.getParty().getId(), r.getPercent().doubleValue())));

        // Alle Parteien, die irgendwo vorkommen — sortiert nach dem juengsten Stand.
        Map<Integer, Party> partyById = new LinkedHashMap<>();
        for (ElectionReport r : history) {
            r.getResults().forEach(res -> partyById.putIfAbsent(res.getParty().getId(), res.getParty()));
        }
        previous.ifPresent(p -> p.getResults().forEach(
                res -> partyById.putIfAbsent(res.getParty().getId(), res.getParty())));

        Map<Integer, Double> latestResults = latest.map(WahlabendService::results).orElse(Map.of());
        List<Party> ordered = new ArrayList<>(partyById.values());
        ordered.sort(Comparator
                .comparing((Party p) -> p.getId() == SeatsDtoFactory.PARTY_SONSTIGE ? 1 : 0)
                .thenComparing(p -> -latestResults.getOrDefault(p.getId(),
                        previousResults.getOrDefault(p.getId(), 0.0))));

        Map<Integer, Double> pollsBefore = pollsAt(points, ordered, electionDate);

        Dtos.SeatsDto seatsDto = null;
        List<Dtos.CoalitionDto> coalitions = List.of();
        if (latest.isPresent()) {
            ElectionReport r = latest.get();
            Map<Integer, Integer> seatMap = seatsOf(r);
            if (r.isSeatsOfficial() && !seatMap.isEmpty()) {
                seatsDto = seats.official(parliament, latestResults, seatMap, "Sitze laut " + r.getSource());
            } else {
                seatsDto = seats.projected(parliament, latestResults,
                        "Projektion aus " + dative(r.getKind()) + ": ");
            }
            Map<Integer, String> shortcuts = new HashMap<>();
            ordered.forEach(p -> shortcuts.put(p.getId(), p.getShortcut()));
            coalitions = seats.coalitions(seatsDto, shortcuts);
        }

        boolean hot = WahlabendClock.isHot(electionDate, now, properties.wahlabend().hotHours());
        int refresh = switch (phase) {
            case LIVE -> hot ? properties.wahlabend().clientRefreshSeconds()
                             : properties.wahlabend().clientCoolRefreshSeconds();
            case AUSSTEHEND -> properties.wahlabend().clientRefreshSeconds();
            case ABGESCHLOSSEN -> 0;
        };

        String title = parliament.getElectionName() + " " + electionDate.getYear();
        String headline = switch (phase) {
            case AUSSTEHEND -> "Heute wird gewählt — ab 18 Uhr Prognose und Hochrechnungen";
            case LIVE -> latest.map(r -> r.getKind().label() + " von " + timeLabel(r.getReportedAt(), electionDate) + " Uhr")
                    .orElse("Wahllokale geschlossen — die erste Prognose kommt gleich");
            case ABGESCHLOSSEN -> "Wahlabend vom " + LONG_DATE.format(electionDate);
        };

        Optional<LiveSourceStatus.Status> status = sourceStatus.get(election.getId());

        return Optional.of(new Dtos.WahlabendDto(
                phase.name(), phase == Phase.LIVE, hot, refresh,
                title, headline,
                electionDate, parliament.getElectionName(),
                latest.map(r -> reportDto(r, electionDate)).orElse(null),
                history.stream().map(r -> reportDto(r, electionDate)).toList(),
                ordered.stream().map(Dtos.PartyDto::from).toList(),
                previous.map(Election::getElectionDate).orElse(null), previousResults,
                pollsBefore, pollsBefore.isEmpty() ? null : electionDate,
                seatsDto, coalitions,
                status.map(s -> DAY_TIME.withZone(WahlabendClock.BERLIN).format(s.lastCheck())).orElse(null),
                status.map(LiveSourceStatus.Status::lastError).orElse(null),
                Instant.now()));
    }

    /** Geglaetteter Umfragestand zum Wahltag — derselbe Kernel wie bei den Kopfzahlen. */
    private Map<Integer, Double> pollsAt(List<PollPoint> points, List<Party> parties, LocalDate day) {
        Map<Integer, Double> out = new LinkedHashMap<>();
        if (points == null || points.isEmpty()) {
            return out;
        }
        List<PollPoint> recent = points.stream()
                .filter(p -> !p.effectiveDate().isBefore(day.minusDays(POLLS_WINDOW_DAYS))
                        && !p.effectiveDate().isAfter(day))
                .toList();
        if (recent.isEmpty()) {
            return out;
        }
        double sigma = trendCalculator.adaptiveSigma(recent, properties.trend().sigmaDays());
        for (Party party : parties) {
            Double v = trendCalculator.valueAt(points, party.getId(), day, sigma,
                    properties.trend().cutoffSigmas(), null);
            if (v != null) {
                out.put(party.getId(), Math.round(v * 10.0) / 10.0);
            }
        }
        return out;
    }

    public static Map<Integer, Double> results(ElectionReport report) {
        Map<Integer, Double> map = new LinkedHashMap<>();
        report.getResults().stream()
                .sorted(Comparator.comparing(ElectionResult::getPercent).reversed())
                .forEach(r -> map.put(r.getParty().getId(), r.getPercent().doubleValue()));
        return map;
    }

    private static Map<Integer, Integer> seatsOf(ElectionReport report) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        report.getResults().stream()
                .filter(r -> r.getSeats() != null)
                .sorted(Comparator.comparing(ElectionResult::getSeats).reversed())
                .forEach(r -> map.put(r.getParty().getId(), r.getSeats()));
        return map;
    }

    public static Dtos.ReportDto reportDto(ElectionReport r, LocalDate electionDate) {
        return new Dtos.ReportDto(
                r.getId(), r.getKind().name(), r.getKind().label(), r.getReportedAt(),
                timeLabel(r.getReportedAt(), electionDate),
                r.getSource(), r.getSourceUrl(),
                r.getTurnoutPercent() != null ? r.getTurnoutPercent().doubleValue() : null,
                r.getNote(), r.isSeatsOfficial(),
                results(r), seatsOf(r));
    }

    /** "18:27" am Wahltag, "7.9., 03:26" an jedem anderen Tag. */
    static String timeLabel(Instant at, LocalDate electionDate) {
        ZonedDateTime local = at.atZone(WahlabendClock.BERLIN);
        return local.toLocalDate().equals(electionDate) ? TIME.format(local) : DAY_TIME.format(local);
    }

    private static String dative(ResultKind kind) {
        return switch (kind) {
            case PROGNOSE -> "der Prognose";
            case HOCHRECHNUNG -> "der Hochrechnung";
            case AUSZAEHLUNG -> "dem Auszählungsstand";
            case VORLAEUFIG -> "dem vorläufigen Ergebnis";
            case AMTLICH -> "dem amtlichen Ergebnis";
        };
    }
}
