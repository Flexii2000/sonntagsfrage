package com.fherrmann.wahlen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Alle fachlichen Stellschrauben an einer Stelle, gebunden an {@code wahlen.*}. */
@ConfigurationProperties(prefix = "wahlen")
public record WahlenProperties(
        @DefaultValue Dawum dawum,
        @DefaultValue Trend trend,
        @DefaultValue Featured featured) {

    public record Dawum(
            @DefaultValue("https://api.dawum.de") String baseUrl,
            @DefaultValue("wahlen.fherrmann.com/1.0 (+https://fherrmann.com/wahlen)") String userAgent,
            @DefaultValue("true") boolean importOnStartup) {
    }

    public record Trend(
            /** Standardabweichung des Gauss-Kernels in Tagen. */
            @DefaultValue("10") double sigmaDays,
            /** Ausserhalb dieses Vielfachen von sigma werden Umfragen ignoriert. */
            @DefaultValue("3") double cutoffSigmas,
            /** Maximale Anzahl Stuetzstellen einer Trendkurve. */
            @DefaultValue("400") int maxPoints) {
    }

    public record Featured(
            /** So viele Tage vor einer Wahl wird sie auf der Startseite gefeatured. */
            @DefaultValue("21") int preElectionDays,
            /** So viele Tage nach einer Wahl bleibt sie gefeatured. */
            @DefaultValue("7") int postElectionDays) {
    }
}
