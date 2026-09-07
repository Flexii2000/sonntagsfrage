package com.fherrmann.wahlen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Alle fachlichen Stellschrauben an einer Stelle, gebunden an {@code wahlen.*}. */
@ConfigurationProperties(prefix = "wahlen")
public record WahlenProperties(
        @DefaultValue Dawum dawum,
        @DefaultValue Trend trend,
        @DefaultValue Featured featured,
        @DefaultValue Cors cors,
        @DefaultValue Wahlabend wahlabend) {

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

    public record Cors(
            /** Herkuenfte, die die JSON-API im Browser lesen duerfen. */
            @DefaultValue("https://status.fherrmann.com") java.util.List<String> allowedOrigins) {
    }

    public record Wahlabend(
            /**
             * Geheimnis fuer das Eintragen von Wahlabend-Staenden (Prognose,
             * Hochrechnung) ueber die API und das Formular. Leer = Eingabe gesperrt.
             */
            @DefaultValue("") String token,
            /** Abrufrhythmus automatischer Quellen am Wahlabend selbst (Sekunden). */
            @DefaultValue("60") int hotIntervalSeconds,
            /** Abrufrhythmus in den Tagen danach, bis das amtliche Ergebnis da ist. */
            @DefaultValue("900") int coolIntervalSeconds,
            /** Wie oft der Browser am Wahlabend nachfragt (Sekunden). */
            @DefaultValue("60") int clientRefreshSeconds,
            /** Wie oft der Browser in den Tagen danach nachfragt. */
            @DefaultValue("600") int clientCoolRefreshSeconds,
            /** So lange nach Schliessung der Wahllokale gilt der Abend als "heiss" (Stunden). */
            @DefaultValue("36") int hotHours) {
    }

    public record Featured(
            /** So viele Tage vor einer Wahl wird sie auf der Startseite gefeatured. */
            @DefaultValue("21") int preElectionDays,
            /** So viele Tage nach einer Wahl bleibt sie gefeatured. */
            @DefaultValue("7") int postElectionDays) {
    }
}
