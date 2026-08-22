package com.fherrmann.wahlen.dawum;

import com.fherrmann.wahlen.config.WahlenProperties;
import com.fherrmann.wahlen.reference.ReferenceDataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Haelt den Datenbestand aktuell.
 *
 * <p>Die Reihenfolge ist wichtig: erst DAWUM (legt Parlamente und Parteien an),
 * dann die Referenzdaten (haengen Slugs, Farben, Sperrklauseln und den
 * Wahlkalender daran), dann Caches leeren.
 */
@Component
public class DawumImportJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DawumImportJob.class);

    private final DawumImportService importService;
    private final ReferenceDataLoader referenceData;
    private final CacheManager cacheManager;
    private final WahlenProperties properties;

    public DawumImportJob(DawumImportService importService,
                          ReferenceDataLoader referenceData,
                          CacheManager cacheManager,
                          WahlenProperties properties) {
        this.importService = importService;
        this.referenceData = referenceData;
        this.cacheManager = cacheManager;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.dawum().importOnStartup()) {
            importService.importIfChanged(false);
        } else {
            log.info("Import beim Start ist abgeschaltet");
        }
        // Die Referenzdaten kommen aus dem Repo, nicht aus DAWUM — sie muessen
        // deshalb bei JEDEM Start neu eingelesen werden, auch wenn DAWUM nichts
        // Neues hatte. Sonst wirkt sich eine korrigierte elections.yaml erst beim
        // naechsten zufaelligen Datenupdate aus.
        refreshReferenceData();
        clearCaches();
    }

    /**
     * Zweimal pro Stunde. DAWUM aktualisiert mehrfach taeglich; der Lauf kostet
     * im Normalfall einen 39-Byte-Abruf, weil {@code last_update.txt} zuerst
     * geprueft wird.
     */
    @Scheduled(cron = "${wahlen.dawum.import-cron:0 7,37 * * * *}", zone = "Europe/Berlin")
    public void scheduled() {
        runOnce(false);
    }

    /** Erzwingt einen Vollimport, ignoriert Zeitstempel und ETag. */
    public ImportOutcome runOnce(boolean force) {
        ImportOutcome outcome = importService.importIfChanged(force);
        if (outcome.status() == ImportOutcome.Status.IMPORTED) {
            refreshReferenceData();
            clearCaches();
        }
        return outcome;
    }

    private void refreshReferenceData() {
        try {
            referenceData.load();
        } catch (RuntimeException e) {
            log.error("Referenzdaten konnten nicht geladen werden", e);
        }
    }

    private void clearCaches() {
        cacheManager.getCacheNames()
                .forEach(name -> cacheManager.getCache(name).clear());
        log.debug("Caches geleert");
    }
}
