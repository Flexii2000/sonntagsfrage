package com.fherrmann.wahlen.dawum;

/** Ergebnis eines Importlaufs — wird geloggt und ueber /api/meta ausgegeben. */
public record ImportOutcome(
        Status status,
        int surveysInserted,
        int surveysUpdated,
        int surveysDeleted,
        int surveysUnchanged,
        String dawumLastUpdate,
        String message) {

    public enum Status {
        /** DAWUM hatte nichts Neues. */
        UNCHANGED,
        /** Daten wurden uebernommen. */
        IMPORTED,
        /** Abruf oder Import ist fehlgeschlagen; der alte Stand bleibt gueltig. */
        FAILED
    }

    public static ImportOutcome unchanged(String lastUpdate) {
        return new ImportOutcome(Status.UNCHANGED, 0, 0, 0, 0, lastUpdate, "keine Aenderung");
    }

    public static ImportOutcome failed(String message) {
        return new ImportOutcome(Status.FAILED, 0, 0, 0, 0, null, message);
    }

    public boolean changedAnything() {
        return status == Status.IMPORTED
                && (surveysInserted > 0 || surveysUpdated > 0 || surveysDeleted > 0);
    }
}
