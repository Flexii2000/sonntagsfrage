package com.fherrmann.wahlen.dawum;

/** Erfolgreich geladene DAWUM-Antwort samt ETag fuer den naechsten Abruf. */
public record DawumFetch(DawumPayload payload, String etag) {
}
