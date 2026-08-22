package com.fherrmann.wahlen.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Flache Lesezeile: eine Partei-Angabe einer Umfrage, inklusive der
 * Umfrage-Metadaten. Wird im Service zu {@code PollPoint}s gruppiert.
 *
 * <p>Bewusst kein Entity-Graph: die Auswertung braucht alle Umfragen eines
 * Parlaments auf einmal (Bundestag: ~2.600 Umfragen, ~21.000 Zeilen), und
 * das ist als flache Projektion in einem Query deutlich guenstiger als ein
 * Baum aus Entities.
 */
public record SurveyRow(
        Integer surveyId,
        Integer instituteId,
        Integer taskerId,
        Integer methodId,
        LocalDate publishedOn,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer surveyedPersons,
        Integer partyId,
        BigDecimal percent) {
}
