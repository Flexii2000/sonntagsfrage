package com.fherrmann.wahlen.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * Alles Datumsbezogene laeuft ueber diese Uhr — dadurch sind Wahlkalender
     * und Featured-Logik testbar, ohne das Systemdatum zu manipulieren.
     */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Berlin"));
    }
}
