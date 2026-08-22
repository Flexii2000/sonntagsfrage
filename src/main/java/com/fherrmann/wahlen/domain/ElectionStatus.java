package com.fherrmann.wahlen.domain;

public enum ElectionStatus {
    /** Termin steht (oder ist grob bekannt), Wahl hat noch nicht stattgefunden. */
    SCHEDULED,
    /** Wahl ist gelaufen. */
    HELD
}
