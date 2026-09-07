package com.fherrmann.wahlen.wahlabend;

import static org.assertj.core.api.Assertions.assertThat;

import com.fherrmann.wahlen.domain.ElectionReport;
import com.fherrmann.wahlen.domain.ResultKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WahlabendServiceTest {

    private static ElectionReport report(ResultKind kind, String at) {
        return new ElectionReport(null, kind, Instant.parse(at), "Test");
    }

    @Test
    @DisplayName("der juengste Stand gilt")
    void latestByTime() {
        ElectionReport early = report(ResultKind.PROGNOSE, "2026-09-06T16:00:00Z");
        ElectionReport late = report(ResultKind.HOCHRECHNUNG, "2026-09-06T19:30:00Z");
        assertThat(WahlabendService.pickLatest(List.of(late, early))).contains(late);
    }

    @Test
    @DisplayName("ein vorlaeufiges Ergebnis schlaegt jede spaetere Hochrechnung")
    void officialBeatsProjection() {
        ElectionReport official = report(ResultKind.VORLAEUFIG, "2026-09-07T00:56:00Z");
        ElectionReport later = report(ResultKind.HOCHRECHNUNG, "2026-09-07T01:30:00Z");
        assertThat(WahlabendService.pickLatest(List.of(official, later))).contains(official);
    }

    @Test
    @DisplayName("leerer Verlauf, kein Stand")
    void emptyHistory() {
        assertThat(WahlabendService.pickLatest(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Zeitangabe: nur Uhrzeit am Wahltag, Datum an anderen Tagen")
    void timeLabel() {
        LocalDate election = LocalDate.of(2026, 9, 6);
        assertThat(WahlabendService.timeLabel(Instant.parse("2026-09-06T16:00:00Z"), election))
                .isEqualTo("18:00");
        assertThat(WahlabendService.timeLabel(Instant.parse("2026-09-07T00:56:00Z"), election))
                .isEqualTo("7.9., 02:56");
    }
}
