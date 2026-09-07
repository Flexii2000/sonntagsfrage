package com.fherrmann.wahlen.wahlabend;

import org.springframework.http.HttpStatus;

/** Fachlicher Fehler beim Eintragen eines Wahlabend-Stands — wird als HTTP-Status ausgegeben. */
public class ReportException extends RuntimeException {

    private final HttpStatus status;

    public ReportException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ReportException badRequest(String message) {
        return new ReportException(HttpStatus.BAD_REQUEST, message);
    }

    public static ReportException notFound(String message) {
        return new ReportException(HttpStatus.NOT_FOUND, message);
    }

    public HttpStatus status() {
        return status;
    }
}
