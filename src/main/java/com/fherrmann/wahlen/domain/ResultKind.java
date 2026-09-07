package com.fherrmann.wahlen.domain;

/**
 * Reifegrad eines Wahlergebnisses. Die Reihenfolge der Konstanten ist die
 * Rangfolge: ein spaeterer Wert loest einen frueheren im UI ab.
 */
public enum ResultKind {
    /** 18:00 Uhr, ARD/ZDF auf Basis der Nachwahlbefragung. */
    PROGNOSE("Prognose"),
    /** Laufende Hochrechnung im Verlauf des Abends. */
    HOCHRECHNUNG("Hochrechnung"),
    /** Zwischenstand der amtlichen Auszaehlung (Landeswahlleitung), noch unvollstaendig. */
    AUSZAEHLUNG("Auszählungsstand"),
    /** Vorlaeufiges amtliches Endergebnis (nachts). */
    VORLAEUFIG("Vorläufiges Ergebnis"),
    /** Amtliches Endergebnis. */
    AMTLICH("Amtliches Endergebnis");

    private final String label;

    ResultKind(String label) {
        this.label = label;
    }

    /** Bezeichnung fuer die Oberflaeche. */
    public String label() {
        return label;
    }

    /** Alles, was am Wahlabend hereinkommt — also alles ausser dem amtlichen Endergebnis. */
    public boolean isElectionNight() {
        return this != AMTLICH;
    }
}
