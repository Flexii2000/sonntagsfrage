package com.fherrmann.wahlen.wahlabend;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Die Zeitlogik des Wahlabends, ohne Datenbank und deshalb direkt testbar.
 *
 * <p>Der Abend beginnt mit der Schliessung der Wahllokale um 18:00 Uhr und
 * endet, wenn das amtliche Endergebnis vorliegt — das koennen zwei Wochen
 * sein. "Heiss" ist nur die Zeit, in der sich tatsaechlich etwas bewegt: der
 * Abend selbst und der Folgetag mit dem vorlaeufigen Ergebnis.
 */
public final class WahlabendClock {

    public static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    public static final LocalTime POLLS_CLOSE = LocalTime.of(18, 0);

    /** Zustand des Wahlabends einer Wahl. */
    public enum Phase {
        /** Wahltag vor 18 Uhr — noch nichts da, aber gleich. */
        AUSSTEHEND,
        /** Ab 18 Uhr bis zum amtlichen Endergebnis. */
        LIVE,
        /** Amtliches Endergebnis liegt vor. */
        ABGESCHLOSSEN
    }

    private WahlabendClock() {
    }

    public static ZonedDateTime pollsClose(LocalDate electionDate) {
        return electionDate.atTime(POLLS_CLOSE).atZone(BERLIN);
    }

    public static Phase phase(LocalDate electionDate, boolean hasOfficialResult, ZonedDateTime now) {
        if (hasOfficialResult) {
            return Phase.ABGESCHLOSSEN;
        }
        return now.isBefore(pollsClose(electionDate)) ? Phase.AUSSTEHEND : Phase.LIVE;
    }

    /** 18:00 Uhr am Wahltag bis {@code hotHours} spaeter. */
    public static boolean isHot(LocalDate electionDate, ZonedDateTime now, int hotHours) {
        ZonedDateTime start = pollsClose(electionDate);
        return !now.isBefore(start) && now.isBefore(start.plus(Duration.ofHours(hotHours)));
    }
}
