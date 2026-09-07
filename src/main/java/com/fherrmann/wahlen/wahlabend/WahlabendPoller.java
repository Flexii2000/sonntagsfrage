package com.fherrmann.wahlen.wahlabend;

import com.fherrmann.wahlen.config.WahlenProperties;
import com.fherrmann.wahlen.domain.Election;
import com.fherrmann.wahlen.reference.ReferenceData.LiveSourceRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Holt am Wahlabend die Landeswahlleitungs-Daten — im Minutentakt, solange der
 * Abend "heiss" ist, danach seltener, bis das amtliche Ergebnis in
 * {@code elections.yaml} steht und der Wahlabend damit vorbei ist.
 *
 * <p>Ein Fehler beim Abruf bleibt ein Logeintrag plus ein Hinweis im UI; der
 * letzte gute Stand steht weiter auf der Seite. Auch hier gilt: sichtbar
 * altern statt ausfallen.
 */
@Component
public class WahlabendPoller {

    private static final Logger log = LoggerFactory.getLogger(WahlabendPoller.class);

    private final WahlabendService wahlabend;
    private final LiveSourceRegistry registry;
    private final CsvResultSource csv;
    private final ElectionReportService reports;
    private final LiveSourceStatus status;
    private final WahlenProperties properties;
    private final Map<Long, Instant> lastRun = new ConcurrentHashMap<>();

    public WahlabendPoller(WahlabendService wahlabend,
                           LiveSourceRegistry registry,
                           CsvResultSource csv,
                           ElectionReportService reports,
                           LiveSourceStatus status,
                           WahlenProperties properties) {
        this.wahlabend = wahlabend;
        this.registry = registry;
        this.csv = csv;
        this.reports = reports;
        this.status = status;
        this.properties = properties;
    }

    /** Jede Minute nachsehen; ob tatsaechlich abgerufen wird, entscheidet der Rhythmus je Wahl. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void poll() {
        List<Election> live;
        try {
            live = wahlabend.liveElections();
        } catch (RuntimeException e) {
            log.warn("Wahlabend-Abruf: Wahlen nicht ermittelbar: {}", e.getMessage());
            return;
        }
        Instant now = Instant.now();
        for (Election election : live) {
            List<LiveSourceRef> sources = registry.sourcesFor(election);
            if (sources.isEmpty()) {
                continue;
            }
            int interval = wahlabend.isHot(election)
                    ? properties.wahlabend().hotIntervalSeconds()
                    : properties.wahlabend().coolIntervalSeconds();
            Instant last = lastRun.get(election.getId());
            if (last != null && last.plusSeconds(Math.max(interval - 5, 0)).isAfter(now)) {
                continue;
            }
            lastRun.put(election.getId(), now);
            for (LiveSourceRef source : sources) {
                fetchOne(election, source);
            }
        }
    }

    /** Einen Abruf sofort ausfuehren — fuer den Start und fuer Tests von Hand. */
    public void fetchOne(Election election, LiveSourceRef source) {
        String where = election.getParliament().getSlug() + " " + election.getElectionDate();
        try {
            Optional<ReportInput> input = csv.fetch(source, election);
            boolean changed = input.isPresent() && reports.addIfChanged(election, input.get()).isPresent();
            status.checked(election.getId(), changed);
            if (changed) {
                log.info("Wahlabend {}: neuer Stand von {} ({})", where, source.label(), input.get().kind());
            } else {
                log.debug("Wahlabend {}: {} unverändert", where, source.label());
            }
        } catch (RuntimeException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            status.failed(election.getId(), message);
            log.warn("Wahlabend {}: Abruf von {} fehlgeschlagen: {}", where, source.url(), message);
        }
    }
}
