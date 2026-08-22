package com.fherrmann.wahlen.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CacheConfig {

    /** Rohumfragen eines Parlaments, aus der Datenbank zusammengesetzt. */
    public static final String POLL_POINTS = "pollPoints";
    /** Fertig berechnete Detailansichten. */
    public static final String PARLIAMENT_VIEW = "parliamentView";
    /** Uebersicht aller Parlamente fuer die Startseite. */
    public static final String OVERVIEW = "overview";

    /**
     * Alle Caches werden nach einem erfolgreichen DAWUM-Import geleert; die
     * Zeitschranke ist nur ein Sicherheitsnetz, falls das mal ausbleibt.
     */
    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(POLL_POINTS, PARLIAMENT_VIEW, OVERVIEW);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofHours(6)));
        return manager;
    }
}
