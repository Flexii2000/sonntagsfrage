package com.fherrmann.wahlen.wahlabend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WahlabendClockTest {

    private static final LocalDate ELECTION = LocalDate.of(2026, 9, 6);

    private static ZonedDateTime at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, WahlabendClock.BERLIN);
    }

    @Test
    @DisplayName("vor 18 Uhr am Wahltag ist der Abend ausstehend")
    void beforePollsClose() {
        assertThat(WahlabendClock.phase(ELECTION, false, at(6, 17, 59)))
                .isEqualTo(WahlabendClock.Phase.AUSSTEHEND);
    }

    @Test
    @DisplayName("ab 18 Uhr laeuft der Wahlabend")
    void fromPollsClose() {
        assertThat(WahlabendClock.phase(ELECTION, false, at(6, 18, 0)))
                .isEqualTo(WahlabendClock.Phase.LIVE);
    }

    @Test
    @DisplayName("ohne amtliches Ergebnis bleibt er auch Wochen spaeter live")
    void staysLiveUntilOfficial() {
        assertThat(WahlabendClock.phase(ELECTION, false, at(27, 12, 0)))
                .isEqualTo(WahlabendClock.Phase.LIVE);
    }

    @Test
    @DisplayName("mit amtlichem Ergebnis ist er abgeschlossen")
    void closedWithOfficialResult() {
        assertThat(WahlabendClock.phase(ELECTION, true, at(6, 20, 0)))
                .isEqualTo(WahlabendClock.Phase.ABGESCHLOSSEN);
    }

    @Test
    @DisplayName("heiss ist der Abend selbst und der Folgetag")
    void hotWindow() {
        assertThat(WahlabendClock.isHot(ELECTION, at(6, 17, 0), 36)).isFalse();
        assertThat(WahlabendClock.isHot(ELECTION, at(6, 18, 0), 36)).isTrue();
        assertThat(WahlabendClock.isHot(ELECTION, at(7, 12, 0), 36)).isTrue();
        assertThat(WahlabendClock.isHot(ELECTION, at(8, 5, 59), 36)).isTrue();
        assertThat(WahlabendClock.isHot(ELECTION, at(8, 6, 0), 36)).isFalse();
    }
}
