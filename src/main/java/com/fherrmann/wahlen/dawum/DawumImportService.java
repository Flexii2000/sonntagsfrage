package com.fherrmann.wahlen.dawum;

import com.fherrmann.wahlen.domain.ImportState;
import com.fherrmann.wahlen.domain.Institute;
import com.fherrmann.wahlen.domain.Method;
import com.fherrmann.wahlen.domain.Parliament;
import com.fherrmann.wahlen.domain.ParliamentLevel;
import com.fherrmann.wahlen.domain.Party;
import com.fherrmann.wahlen.domain.Survey;
import com.fherrmann.wahlen.domain.SurveyResult;
import com.fherrmann.wahlen.domain.Tasker;
import com.fherrmann.wahlen.reference.Slugs;
import com.fherrmann.wahlen.repository.ImportStateRepository;
import com.fherrmann.wahlen.repository.InstituteRepository;
import com.fherrmann.wahlen.repository.MethodRepository;
import com.fherrmann.wahlen.repository.ParliamentRepository;
import com.fherrmann.wahlen.repository.PartyRepository;
import com.fherrmann.wahlen.repository.SurveyHash;
import com.fherrmann.wahlen.repository.SurveyRepository;
import com.fherrmann.wahlen.repository.TaskerRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uebernimmt den DAWUM-Datenbestand in die eigene Datenbank.
 *
 * <p>Der Import ist idempotent und laeuft in drei Stufen, damit weder die
 * Quelle noch die Datenbank unnoetig belastet werden:
 *
 * <ol>
 *   <li>{@code last_update.txt} vergleichen — 39 Bytes, der Normalfall endet hier</li>
 *   <li>Vollabruf mit {@code If-None-Match}</li>
 *   <li>pro Umfrage einen Fingerabdruck vergleichen; nur Geaendertes anfassen</li>
 * </ol>
 *
 * <p>Schlaegt irgendetwas davon fehl, bleibt der bisherige Datenbestand
 * unveraendert gueltig — die Seite altert dann sichtbar, statt auszufallen.
 */
@Service
public class DawumImportService {

    private static final Logger log = LoggerFactory.getLogger(DawumImportService.class);

    private final DawumClient client;
    private final ParliamentRepository parliaments;
    private final PartyRepository parties;
    private final InstituteRepository institutes;
    private final TaskerRepository taskers;
    private final MethodRepository methods;
    private final SurveyRepository surveys;
    private final ImportStateRepository importState;

    public DawumImportService(DawumClient client,
                              ParliamentRepository parliaments,
                              PartyRepository parties,
                              InstituteRepository institutes,
                              TaskerRepository taskers,
                              MethodRepository methods,
                              SurveyRepository surveys,
                              ImportStateRepository importState) {
        this.client = client;
        this.parliaments = parliaments;
        this.parties = parties;
        this.institutes = institutes;
        this.taskers = taskers;
        this.methods = methods;
        this.surveys = surveys;
        this.importState = importState;
    }

    @Transactional
    public ImportOutcome importIfChanged(boolean force) {
        try {
            // Zuerst festhalten, DASS nachgefragt wurde. Ohne das sieht ein Dienst,
            // bei dem die Quelle laenger nichts Neues liefert, von aussen aus wie
            // ein stehengebliebener Dienst.
            writeState(ImportState.LAST_CHECK, Instant.now().toString());

            String knownUpdate = readState(ImportState.DAWUM_LAST_UPDATE).orElse(null);
            Optional<String> remoteUpdate = client.fetchLastUpdate();

            if (!force && remoteUpdate.isPresent() && remoteUpdate.get().equals(knownUpdate)
                    && surveys.count() > 0) {
                log.debug("DAWUM unveraendert seit {}", knownUpdate);
                return ImportOutcome.unchanged(knownUpdate);
            }

            String knownEtag = force ? null : readState(ImportState.DAWUM_ETAG).orElse(null);
            Optional<DawumFetch> fetched = client.fetch(knownEtag);
            if (fetched.isEmpty()) {
                remoteUpdate.ifPresent(v -> writeState(ImportState.DAWUM_LAST_UPDATE, v));
                return ImportOutcome.unchanged(remoteUpdate.orElse(knownUpdate));
            }

            ImportOutcome outcome = apply(fetched.get().payload());
            if (fetched.get().etag() != null) {
                writeState(ImportState.DAWUM_ETAG, fetched.get().etag());
            }
            writeState(ImportState.DAWUM_LAST_UPDATE, outcome.dawumLastUpdate());
            writeState(ImportState.LAST_SUCCESSFUL_RUN, Instant.now().toString());
            writeState(ImportState.LAST_ERROR, null);
            log.info("DAWUM-Import: {} neu, {} aktualisiert, {} entfernt, {} unveraendert (Stand {})",
                    outcome.surveysInserted(), outcome.surveysUpdated(),
                    outcome.surveysDeleted(), outcome.surveysUnchanged(), outcome.dawumLastUpdate());
            return outcome;
        } catch (Exception e) {
            log.error("DAWUM-Import fehlgeschlagen — bisheriger Datenbestand bleibt gueltig", e);
            writeState(ImportState.LAST_ERROR, Instant.now() + ": " + e.getMessage());
            return ImportOutcome.failed(e.getMessage());
        }
    }

    private ImportOutcome apply(DawumPayload payload) {
        Map<Integer, Parliament> parliamentsById = upsertParliaments(payload.parliaments());
        Map<Integer, Party> partiesById = upsertParties(payload.parties());
        Map<Integer, Institute> institutesById = upsertInstitutes(payload.institutes());
        Map<Integer, Tasker> taskersById = upsertTaskers(payload.taskers());
        Map<Integer, Method> methodsById = upsertMethods(payload.methods());

        Map<Integer, String> existingHashes = new HashMap<>();
        for (SurveyHash hash : surveys.findAllHashes()) {
            existingHashes.put(hash.id(), hash.contentHash());
        }

        Set<Integer> seen = new HashSet<>();
        List<Integer> changedIds = new ArrayList<>();
        Map<Integer, DawumPayload.Survey> toWrite = new HashMap<>();
        int unchanged = 0;

        for (Map.Entry<String, DawumPayload.Survey> entry : payload.surveys().entrySet()) {
            Integer id = parseInt(entry.getKey());
            DawumPayload.Survey dto = entry.getValue();
            if (id == null || dto == null || dto.date() == null) {
                continue;
            }
            seen.add(id);
            String hash = fingerprint(dto);
            String known = existingHashes.get(id);
            if (known != null && known.equals(hash)) {
                unchanged++;
                continue;
            }
            if (known != null) {
                changedIds.add(id);
            }
            toWrite.put(id, dto);
        }

        // Verschwundene Umfragen entfernen (DAWUM korrigiert gelegentlich).
        List<Integer> obsolete = existingHashes.keySet().stream().filter(id -> !seen.contains(id)).toList();
        if (!obsolete.isEmpty()) {
            surveys.deleteResultsBySurveyIds(obsolete);
            surveys.deleteBySurveyIds(obsolete);
        }

        // Ergebniszeilen geaenderter Umfragen vorab loeschen, damit die
        // Neuanlage nicht gegen den Primaerschluessel laeuft.
        if (!changedIds.isEmpty()) {
            surveys.deleteResultsBySurveyIds(changedIds);
        }
        if (!obsolete.isEmpty() || !changedIds.isEmpty()) {
            surveys.flush();
        }

        int inserted = 0;
        int updated = 0;
        for (Map.Entry<Integer, DawumPayload.Survey> entry : toWrite.entrySet()) {
            Integer id = entry.getKey();
            DawumPayload.Survey dto = entry.getValue();
            Parliament parliament = parliamentsById.get(parseInt(dto.parliamentId()));
            if (parliament == null) {
                log.warn("Umfrage {} verweist auf unbekanntes Parlament {} — uebersprungen",
                        id, dto.parliamentId());
                continue;
            }

            boolean isNew = !existingHashes.containsKey(id);
            Survey survey = isNew
                    ? new Survey(id, parliament, parseDate(dto.date()))
                    : surveys.findById(id).orElseGet(() -> new Survey(id, parliament, parseDate(dto.date())));

            survey.setParliament(parliament);
            survey.setPublishedOn(parseDate(dto.date()));
            survey.setInstitute(institutesById.get(parseInt(dto.instituteId())));
            survey.setTasker(taskersById.get(parseInt(dto.taskerId())));
            survey.setMethod(methodsById.get(parseInt(dto.methodId())));
            if (dto.surveyPeriod() != null) {
                survey.setPeriodStart(parseDate(dto.surveyPeriod().dateStart()));
                survey.setPeriodEnd(parseDate(dto.surveyPeriod().dateEnd()));
            } else {
                survey.setPeriodStart(null);
                survey.setPeriodEnd(null);
            }
            survey.setSurveyedPersons(parseInt(dto.surveyedPersons()));
            survey.setContentHash(fingerprint(dto));

            survey.getResults().clear();
            if (dto.results() != null) {
                dto.results().forEach((partyKey, percent) -> {
                    Party party = partiesById.get(parseInt(partyKey));
                    if (party != null && percent != null) {
                        survey.getResults().add(new SurveyResult(survey, party, BigDecimal.valueOf(percent)));
                    }
                });
            }

            surveys.save(survey);
            if (isNew) {
                inserted++;
            } else {
                updated++;
            }
        }

        String lastUpdate = payload.database() != null ? payload.database().lastUpdate() : null;
        return new ImportOutcome(ImportOutcome.Status.IMPORTED,
                inserted, updated, obsolete.size(), unchanged, lastUpdate, "ok");
    }

    private Map<Integer, Parliament> upsertParliaments(Map<String, DawumPayload.Parliament> source) {
        Map<Integer, Parliament> byId = new HashMap<>();
        parliaments.findAll().forEach(p -> byId.put(p.getId(), p));
        Set<String> usedSlugs = new HashSet<>();
        byId.values().forEach(p -> usedSlugs.add(p.getSlug()));

        if (source == null) {
            return byId;
        }
        source.forEach((key, dto) -> {
            Integer id = parseInt(key);
            if (id == null || dto == null) {
                return;
            }
            Parliament parliament = byId.get(id);
            if (parliament == null) {
                String slug = uniqueSlug(Slugs.slugify(dto.shortcut()), usedSlugs);
                parliament = new Parliament(id, slug, dto.shortcut(), dto.name(), dto.election());
                parliament.setLevel(guessLevel(dto));
                parliament.setSortOrder(id == 0 ? 0 : 100 + id);
                usedSlugs.add(slug);
                byId.put(id, parliament);
            } else {
                // Slug, Ebene, Sperrklausel und Sitzzahl gehoeren den
                // Referenzdaten — DAWUM liefert nur die Bezeichnungen.
                parliament.setShortcut(dto.shortcut());
                parliament.setName(dto.name());
                parliament.setElectionName(dto.election());
            }
            parliaments.save(parliament);
        });
        return byId;
    }

    private Map<Integer, Party> upsertParties(Map<String, DawumPayload.Party> source) {
        Map<Integer, Party> byId = new HashMap<>();
        parties.findAll().forEach(p -> byId.put(p.getId(), p));
        if (source == null) {
            return byId;
        }
        source.forEach((key, dto) -> {
            Integer id = parseInt(key);
            if (id == null || dto == null) {
                return;
            }
            Party party = byId.get(id);
            if (party == null) {
                party = new Party(id, dto.shortcut(), dto.name());
                byId.put(id, party);
            } else {
                party.setShortcut(dto.shortcut());
                party.setName(dto.name());
            }
            parties.save(party);
        });
        return byId;
    }

    private Map<Integer, Institute> upsertInstitutes(Map<String, DawumPayload.Named> source) {
        Map<Integer, Institute> byId = new HashMap<>();
        institutes.findAll().forEach(i -> byId.put(i.getId(), i));
        if (source == null) {
            return byId;
        }
        source.forEach((key, dto) -> {
            Integer id = parseInt(key);
            if (id == null || dto == null) {
                return;
            }
            Institute institute = byId.get(id);
            if (institute == null) {
                institute = new Institute(id, dto.name());
                byId.put(id, institute);
            } else {
                institute.setName(dto.name());
            }
            institutes.save(institute);
        });
        return byId;
    }

    private Map<Integer, Tasker> upsertTaskers(Map<String, DawumPayload.Named> source) {
        Map<Integer, Tasker> byId = new HashMap<>();
        taskers.findAll().forEach(t -> byId.put(t.getId(), t));
        if (source == null) {
            return byId;
        }
        source.forEach((key, dto) -> {
            Integer id = parseInt(key);
            if (id == null || dto == null) {
                return;
            }
            Tasker tasker = byId.get(id);
            if (tasker == null) {
                tasker = new Tasker(id, dto.name());
                byId.put(id, tasker);
            } else {
                tasker.setName(dto.name());
            }
            taskers.save(tasker);
        });
        return byId;
    }

    private Map<Integer, Method> upsertMethods(Map<String, DawumPayload.Named> source) {
        Map<Integer, Method> byId = new HashMap<>();
        methods.findAll().forEach(m -> byId.put(m.getId(), m));
        if (source == null) {
            return byId;
        }
        source.forEach((key, dto) -> {
            Integer id = parseInt(key);
            if (id == null || dto == null) {
                return;
            }
            Method method = byId.get(id);
            if (method == null) {
                method = new Method(id, dto.name());
                byId.put(id, method);
            } else {
                method.setName(dto.name());
            }
            methods.save(method);
        });
        return byId;
    }

    /**
     * Fingerabdruck ueber alles, was sich fachlich aendern kann. Die Ergebnisse
     * gehen sortiert ein, damit die Reihenfolge im JSON keine Rolle spielt.
     */
    static String fingerprint(DawumPayload.Survey dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(dto.date()).append('|')
                .append(dto.parliamentId()).append('|')
                .append(dto.instituteId()).append('|')
                .append(dto.taskerId()).append('|')
                .append(dto.methodId()).append('|')
                .append(dto.surveyedPersons()).append('|');
        if (dto.surveyPeriod() != null) {
            sb.append(dto.surveyPeriod().dateStart()).append('|')
                    .append(dto.surveyPeriod().dateEnd());
        }
        sb.append('|');
        if (dto.results() != null) {
            new TreeMap<>(dto.results()).forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfuegbar", e);
        }
    }

    private static ParliamentLevel guessLevel(DawumPayload.Parliament dto) {
        String name = Objects.toString(dto.name(), "");
        if (name.toLowerCase().contains("europ")) {
            return ParliamentLevel.EU;
        }
        if ("Bundestag".equalsIgnoreCase(dto.shortcut())) {
            return ParliamentLevel.BUND;
        }
        return ParliamentLevel.LAND;
    }

    private static String uniqueSlug(String base, Set<String> used) {
        String candidate = base;
        int suffix = 2;
        while (used.contains(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Optional<String> readState(String key) {
        return importState.findById(key).map(ImportState::getValue);
    }

    private void writeState(String key, String value) {
        ImportState state = importState.findById(key).orElseGet(() -> new ImportState(key, null));
        state.setValue(value);
        importState.save(state);
    }

    /** Liest den Importzustand fuer die Meta-Ausgabe. */
    @Transactional(readOnly = true)
    public Map<String, String> currentState() {
        Map<String, String> map = new HashMap<>();
        importState.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }
}
