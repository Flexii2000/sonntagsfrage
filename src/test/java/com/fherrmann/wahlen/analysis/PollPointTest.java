package com.fherrmann.wahlen.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PollPointTest {

    private static PollPoint point(LocalDate published, LocalDate start, LocalDate end) {
        return new PollPoint(1, 1, 1, 1, published, start, end, 1000, Map.of(1, 30.0));
    }

    @Test
    @DisplayName("datiert auf die Mitte des Erhebungszeitraums, nicht auf die Veroeffentlichung")
    void usesMiddleOfSurveyPeriod() {
        PollPoint p = point(LocalDate.of(2026, 8, 21),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15));

        assertThat(p.effectiveDate()).isEqualTo(LocalDate.of(2026, 8, 8));
    }

    @Test
    @DisplayName("faellt auf das Veroeffentlichungsdatum zurueck, wenn kein Zeitraum bekannt ist")
    void fallsBackToPublicationDate() {
        assertThat(point(LocalDate.of(2026, 8, 21), null, null).effectiveDate())
                .isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    @DisplayName("nutzt das Ende, wenn nur eines der beiden Zeitraumsdaten da ist")
    void handlesHalfOpenPeriod() {
        assertThat(point(LocalDate.of(2026, 8, 21), null, LocalDate.of(2026, 8, 10)).effectiveDate())
                .isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    @DisplayName("kommt mit einem eintaegigen Zeitraum klar")
    void singleDayPeriod() {
        LocalDate day = LocalDate.of(2026, 8, 10);
        assertThat(point(LocalDate.of(2026, 8, 12), day, day).effectiveDate()).isEqualTo(day);
    }
}
