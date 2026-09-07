package com.fherrmann.wahlen.reference;

import java.util.List;
import java.util.Map;

/** Abbild der YAML-Dateien unter {@code src/main/resources/reference/}. */
public final class ReferenceData {

    private ReferenceData() {
    }

    public record ParliamentsFile(List<ParliamentRef> parliaments) {
    }

    public record ParliamentRef(
            Integer id,
            String slug,
            String level,
            Double threshold,
            Integer seats,
            List<String> thresholdExempt,
            Integer order) {
    }

    public record PartiesFile(List<PartyRef> parties) {
    }

    public record PartyRef(String shortcut, String light, String dark,
                           Integer order, Integer spectrum) {
    }

    public record ElectionsFile(List<ElectionRef> elections) {
    }

    public record ElectionRef(
            String parliament,
            String date,
            String status,
            Boolean dateConfirmed,
            Double turnout,
            Integer seats,
            String source,
            Map<String, Double> results,
            /** Automatische Wahlabend-Quellen (siehe {@link LiveSourceRef}); optional. */
            List<LiveSourceRef> live) {
    }

    /**
     * Eine automatische Quelle fuer Wahlabend-Staende — heute immer eine CSV
     * der Landeswahlleitung. Alle Spaltennamen beziehen sich auf die Kopfzeile
     * der Datei; die Kopfzeile wird anhand von {@code validVotes} gefunden, so
     * dass vorangestellte Titelzeilen (MV) nicht stoeren.
     */
    public record LiveSourceRef(
            /** Nur {@code csv}. */
            String type,
            /** Quellenangabe im UI, z.B. "Landeswahlleiterin Sachsen-Anhalt". */
            String label,
            String url,
            /** Zeichensatz der Datei, Standard UTF-8 (MV: ISO-8859-1). */
            String charset,
            /** Spalte -> Wert, um die Landeszeile zu finden. Leerer Wert = leere Zelle. */
            Map<String, String> row,
            /** Spalte mit den gueltigen (Zweit-)Stimmen — Nenner der Prozentwerte. */
            String validVotes,
            String voters,
            String eligible,
            /** Fertige Wahlbeteiligung in Prozent, falls die Datei sie liefert. */
            String turnout,
            /** Regex ueber Spaltennamen; Gruppe 1 ist der Parteiname (SA: {@code ^F\d+\.(.+)$}). */
            String partyPattern,
            /** Alternativ: alle Spalten hinter dieser sind Parteien (MV: "Gültige Stimmen"). */
            String partiesAfter,
            /** Spalten, die trotz Position keine Partei sind (z.B. "Einzelbewerber"). */
            List<String> ignore,
            /** Quellname -> DAWUM-Kuerzel, zusaetzlich zu den eingebauten Zuordnungen. */
            Map<String, String> aliases,
            String districtsTotal,
            String districtsCounted,
            /** Spalte mit dem Berechnungszeitpunkt plus Format (java.time-Muster). */
            String timestamp,
            String timestampFormat,
            /** Fester Reifegrad (z.B. VORLAEUFIG), wenn er sich nicht aus der Datei ableiten laesst. */
            String kind,
            /** Spalte -> Wert: trifft es zu, ist die Auszaehlung abgeschlossen (VORLAEUFIG). */
            Map<String, String> completeWhen,
            /** Sitzverteilung aus einer zweiten Datei; optional. */
            SeatsSourceRef seats) {
    }

    /**
     * Sitze je Partei — entweder "breit" (eine Zeile, Parteien als Spalten,
     * wie MV) oder "lang" (eine Zeile je Partei, wie Sachsen-Anhalt).
     */
    public record SeatsSourceRef(
            String url,
            String charset,
            /** Breit: Spalte -> Wert der Landeszeile. */
            Map<String, String> row,
            /** Breit: alle Spalten hinter dieser sind Parteien. */
            String partiesAfter,
            List<String> ignore,
            /** Lang: Spalte mit dem Parteinamen ... */
            String partyColumn,
            /** ... und Spalte mit der Sitzzahl. */
            String seatsColumn) {
    }
}
