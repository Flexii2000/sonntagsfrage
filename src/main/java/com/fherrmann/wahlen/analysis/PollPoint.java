package com.fherrmann.wahlen.analysis;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Eine Umfrage in der Form, in der die Auswertung sie braucht — ohne JPA,
 * ohne Lazy Loading, damit die Rechnungen reine Funktionen bleiben und sich
 * ohne Datenbank testen lassen.
 *
 * @param results Partei-ID -> Prozentwert
 */
public record PollPoint(
        int surveyId,
        Integer instituteId,
        Integer taskerId,
        Integer methodId,
        LocalDate publishedOn,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer surveyedPersons,
        Map<Integer, Double> results) {

    /**
     * Das Datum, auf das die Umfrage in der Zeitreihe gelegt wird: die Mitte des
     * Erhebungszeitraums.
     *
     * <p>Das ist der methodisch saubere Bezugspunkt — eine Umfrage, die vom 1.
     * bis 14. erhoben und am 21. veroeffentlicht wird, beschreibt die Stimmung
     * um den 7., nicht die am 21. Viele Aufbereitungen nehmen stattdessen das
     * Veroeffentlichungsdatum und verschieben damit den gesamten Verlauf um bis
     * zu zwei Wochen nach hinten.
     */
    public LocalDate effectiveDate() {
        if (periodStart != null && periodEnd != null && !periodEnd.isBefore(periodStart)) {
            long half = ChronoUnit.DAYS.between(periodStart, periodEnd) / 2;
            return periodStart.plusDays(half);
        }
        if (periodEnd != null) {
            return periodEnd;
        }
        if (periodStart != null) {
            return periodStart;
        }
        return publishedOn;
    }

    public Double percentFor(int partyId) {
        return results.get(partyId);
    }
}
