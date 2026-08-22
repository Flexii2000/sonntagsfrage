package com.fherrmann.wahlen.reference;

import tools.jackson.dataformat.yaml.YAMLMapper;
import com.fherrmann.wahlen.domain.Election;
import com.fherrmann.wahlen.domain.ElectionResult;
import com.fherrmann.wahlen.domain.ElectionStatus;
import com.fherrmann.wahlen.domain.Parliament;
import com.fherrmann.wahlen.domain.ParliamentLevel;
import com.fherrmann.wahlen.domain.Party;
import com.fherrmann.wahlen.domain.ResultKind;
import com.fherrmann.wahlen.repository.ElectionRepository;
import com.fherrmann.wahlen.repository.ParliamentRepository;
import com.fherrmann.wahlen.repository.PartyRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traegt die kuratierten Referenzdaten in die Datenbank ein: Slugs, Ebenen,
 * Sperrklauseln und Sitzzahlen der Parlamente, Parteifarben sowie den kompletten
 * Wahlkalender inklusive amtlicher Ergebnisse.
 *
 * <p>Laeuft bei jedem Start und ist idempotent — die YAML-Dateien im Repo sind
 * die Wahrheit, die Datenbank nur ihre Projektion. Eine Korrektur besteht also
 * aus "YAML aendern, deployen", nicht aus einem SQL-Update.
 *
 * <p>Parlamente und Parteien, die noch nicht existieren, werden hier NICHT
 * angelegt — die kommen aus DAWUM. Deshalb laeuft der Loader nach dem Import.
 */
@Service
public class ReferenceDataLoader {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataLoader.class);

    private final ParliamentRepository parliaments;
    private final PartyRepository parties;
    private final ElectionRepository elections;
    private final YAMLMapper yaml = new YAMLMapper();

    public ReferenceDataLoader(ParliamentRepository parliaments,
                               PartyRepository parties,
                               ElectionRepository elections) {
        this.parliaments = parliaments;
        this.parties = parties;
        this.elections = elections;
    }

    @Transactional
    public void load() {
        applyParliaments();
        applyParties();
        applyElections();
    }

    private void applyParliaments() {
        ReferenceData.ParliamentsFile file =
                read("reference/parliaments.yaml", ReferenceData.ParliamentsFile.class);
        Map<String, Integer> partyIdsByShortcut = partyIdsByShortcut();

        for (ReferenceData.ParliamentRef ref : file.parliaments()) {
            parliaments.findById(ref.id()).ifPresentOrElse(parliament -> {
                parliament.setSlug(ref.slug());
                parliament.setLevel(ParliamentLevel.valueOf(ref.level()));
                if (ref.threshold() != null) {
                    parliament.setThresholdPercent(BigDecimal.valueOf(ref.threshold()));
                }
                parliament.setSeatsTotal(ref.seats());
                parliament.setSortOrder(ref.order() != null ? ref.order() : 100);
                parliament.setThresholdExempt(resolveExempt(ref.thresholdExempt(), partyIdsByShortcut));
                parliaments.save(parliament);
            }, () -> log.warn("Parlament {} ({}) steht in parliaments.yaml, aber nicht in der Datenbank "
                    + "— laeuft der DAWUM-Import?", ref.id(), ref.slug()));
        }
    }

    private String resolveExempt(List<String> shortcuts, Map<String, Integer> partyIds) {
        if (shortcuts == null || shortcuts.isEmpty()) {
            return null;
        }
        String joined = shortcuts.stream()
                .map(partyIds::get)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private void applyParties() {
        ReferenceData.PartiesFile file =
                read("reference/parties.yaml", ReferenceData.PartiesFile.class);
        Map<String, Party> byShortcut = new HashMap<>();
        parties.findAll().forEach(p -> byShortcut.put(p.getShortcut(), p));

        for (ReferenceData.PartyRef ref : file.parties()) {
            Party party = byShortcut.get(ref.shortcut());
            if (party == null) {
                log.debug("Partei '{}' aus parties.yaml kommt (noch) in keiner Umfrage vor", ref.shortcut());
                continue;
            }
            party.setColorLight(ref.light());
            party.setColorDark(ref.dark());
            party.setSortOrder(ref.order() != null ? ref.order() : 100);
            party.setSpectrum(ref.spectrum() != null ? ref.spectrum() : 99);
            parties.save(party);
        }

        // Parteien ohne kuratierte Farbe bekommen eine stabile, aus dem Namen
        // abgeleitete — besser als gar keine, und ueber Reimports konstant.
        parties.findAll().stream()
                .filter(p -> p.getColorLight() == null)
                .forEach(p -> {
                    p.setColorLight(FallbackColors.light(p.getShortcut()));
                    p.setColorDark(FallbackColors.dark(p.getShortcut()));
                    parties.save(p);
                    log.debug("Partei '{}' ohne kuratierte Farbe — Fallback vergeben", p.getShortcut());
                });
    }

    private void applyElections() {
        ReferenceData.ElectionsFile file =
                read("reference/elections.yaml", ReferenceData.ElectionsFile.class);
        Map<String, Party> partiesByShortcut = new HashMap<>();
        parties.findAll().forEach(p -> partiesByShortcut.put(p.getShortcut(), p));

        int written = 0;
        for (ReferenceData.ElectionRef ref : file.elections()) {
            Parliament parliament = parliaments.findBySlug(ref.parliament()).orElse(null);
            if (parliament == null) {
                log.warn("Wahl {} verweist auf unbekanntes Parlament '{}'", ref.date(), ref.parliament());
                continue;
            }
            LocalDate date = LocalDate.parse(ref.date());
            Election election = elections
                    .findByParliamentIdAndElectionDate(parliament.getId(), date)
                    .orElseGet(() -> new Election(parliament, date,
                            ElectionStatus.valueOf(ref.status())));

            election.setStatus(ElectionStatus.valueOf(ref.status()));
            election.setDateConfirmed(ref.dateConfirmed() == null || ref.dateConfirmed());
            election.setTurnoutPercent(ref.turnout() != null ? BigDecimal.valueOf(ref.turnout()) : null);
            election.setSeatsTotal(ref.seats());
            election.setSourceUrl(ref.source());

            // Erst loeschen und flushen, dann neu anlegen. Ohne den Flush
            // dazwischen ordnet Hibernate die INSERTs vor die DELETEs und
            // laeuft beim zweiten Start in den Unique-Index uq_election_result_final.
            if (election.getResults().removeIf(r -> r.getKind() == ResultKind.AMTLICH)) {
                elections.saveAndFlush(election);
            }
            if (ref.results() != null) {
                ref.results().forEach((shortcut, percent) -> {
                    Party party = partiesByShortcut.get(shortcut);
                    if (party == null) {
                        log.warn("Wahl {} {}: Partei '{}' unbekannt — Ergebnis ignoriert",
                                ref.parliament(), ref.date(), shortcut);
                        return;
                    }
                    ElectionResult result = new ElectionResult(
                            election, party, BigDecimal.valueOf(percent), ResultKind.AMTLICH);
                    result.setSource(ref.source());
                    election.getResults().add(result);
                });
            }
            elections.save(election);
            written++;
        }
        log.info("Referenzdaten geladen: {} Wahltermine", written);
    }

    private Map<String, Integer> partyIdsByShortcut() {
        Map<String, Integer> map = new HashMap<>();
        parties.findAll().forEach(p -> map.put(p.getShortcut(), p.getId()));
        return map;
    }

    private <T> T read(String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return yaml.readValue(in, type);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Referenzdatei " + path + " nicht lesbar", e);
        }
    }
}
