package com.fherrmann.wahlen.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fherrmann.wahlen.wahlabend.ReportException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WahlabendControllerTest {

    private static final LocalDate ELECTION = LocalDate.of(2026, 9, 6);

    @Test
    @DisplayName("HH:mm ab 18 Uhr liegt am Wahltag")
    void eveningTimeOnElectionDay() {
        assertThat(WahlabendController.parseReportedAt("18:00", ELECTION))
                .isEqualTo(Instant.parse("2026-09-06T16:00:00Z"));
        assertThat(WahlabendController.parseReportedAt("23:22", ELECTION))
                .isEqualTo(Instant.parse("2026-09-06T21:22:00Z"));
    }

    @Test
    @DisplayName("HH:mm vor 18 Uhr meint die Nacht danach")
    void nightTimeOnNextDay() {
        assertThat(WahlabendController.parseReportedAt("02:56", ELECTION))
                .isEqualTo(Instant.parse("2026-09-07T00:56:00Z"));
    }

    @Test
    @DisplayName("voller Zeitstempel wird uebernommen")
    void isoTimestamp() {
        assertThat(WahlabendController.parseReportedAt("2026-09-06T18:28:00+02:00", ELECTION))
                .isEqualTo(Instant.parse("2026-09-06T16:28:00Z"));
    }

    @Test
    @DisplayName("Unsinn ist ein Fehler, keine stille Annahme")
    void rejectsGarbage() {
        assertThatThrownBy(() -> WahlabendController.parseReportedAt("gestern", ELECTION))
                .isInstanceOf(ReportException.class);
    }
}
