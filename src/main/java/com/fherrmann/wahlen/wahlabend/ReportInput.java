package com.fherrmann.wahlen.wahlabend;

import com.fherrmann.wahlen.domain.ResultKind;
import java.time.Instant;
import java.util.Map;

/**
 * Ein einzutragender Stand, noch mit Parteinamen statt IDs — so, wie er von
 * einem Menschen oder einer CSV kommt.
 *
 * @param results       Parteiname (DAWUM-Kuerzel oder gaengige Schreibweise) -> Prozent
 * @param seats         Parteiname -> Sitze; leer, wenn die Quelle keine liefert
 * @param seatsOfficial true, wenn {@code seats} von der Quelle stammen (nicht projiziert)
 * @param aliases       zusaetzliche Namenszuordnungen der Quelle
 * @param strict        true: unbekannte Parteinamen sind ein Fehler; false: sie
 *                      wandern in "Sonstige" (fuer automatische Quellen)
 * @param fingerprint   Inhaltshash automatischer Quellen; null bei Handeingabe
 */
public record ReportInput(
        ResultKind kind,
        Instant reportedAt,
        String source,
        String sourceUrl,
        Double turnout,
        String note,
        Map<String, Double> results,
        Map<String, Integer> seats,
        boolean seatsOfficial,
        Map<String, String> aliases,
        boolean strict,
        String fingerprint) {
}
