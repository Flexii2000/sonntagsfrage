package com.fherrmann.wahlen.domain;

/**
 * Reifegrad eines Wahlergebnisses. Die Reihenfolge der Konstanten ist die
 * Rangfolge: ein spaeterer Wert loest einen frueheren im UI ab.
 */
public enum ResultKind {
    /** 18:00 Uhr, ARD/ZDF auf Basis der Nachwahlbefragung. */
    PROGNOSE,
    /** Laufende Hochrechnung im Verlauf des Abends. */
    HOCHRECHNUNG,
    /** Vorlaeufiges amtliches Endergebnis (nachts). */
    VORLAEUFIG,
    /** Amtliches Endergebnis. */
    AMTLICH
}
