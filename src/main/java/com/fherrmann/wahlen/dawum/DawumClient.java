package com.fherrmann.wahlen.dawum;

import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Zugriff auf die DAWUM-API.
 *
 * <p>Der Abruf ist zweistufig, damit die Quelle nicht unnoetig belastet wird:
 * zuerst {@code last_update.txt} (39 Bytes), erst bei einer Aenderung der
 * eigentliche Datenbestand (~1 MB) — und der zusaetzlich noch mit
 * {@code If-None-Match}, falls der Zeitstempel mal ohne Inhaltsaenderung
 * springt.
 */
@Component
public class DawumClient {

    private static final Logger log = LoggerFactory.getLogger(DawumClient.class);

    private final RestClient restClient;

    public DawumClient(@Qualifier("dawumRestClient") RestClient dawumRestClient) {
        this.restClient = dawumRestClient;
    }

    /** Zeitstempel der letzten Aenderung, roh wie von DAWUM geliefert. */
    public Optional<String> fetchLastUpdate() {
        String body = restClient.get()
                .uri("/last_update.txt")
                .retrieve()
                .body(String.class);
        return Optional.ofNullable(body).map(String::trim).filter(s -> !s.isEmpty());
    }

    /**
     * Laedt den kompletten Datenbestand.
     *
     * @param knownEtag ETag des letzten erfolgreichen Abrufs, oder {@code null}
     * @return leer, wenn DAWUM mit {@code 304 Not Modified} geantwortet hat
     */
    public Optional<DawumFetch> fetch(String knownEtag) {
        return restClient.get()
                .uri("/")
                .headers(headers -> {
                    if (knownEtag != null && !knownEtag.isBlank()) {
                        headers.setIfNoneMatch(knownEtag);
                    }
                })
                .exchange((request, response) -> {
                    HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
                    if (status == HttpStatus.NOT_MODIFIED) {
                        log.debug("DAWUM meldet 304 Not Modified");
                        return Optional.empty();
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IOException("DAWUM antwortete mit HTTP " + response.getStatusCode());
                    }
                    DawumPayload payload = response.bodyTo(DawumPayload.class);
                    if (payload == null || payload.surveys() == null || payload.surveys().isEmpty()) {
                        throw new IOException("DAWUM lieferte einen leeren Datenbestand");
                    }
                    String etag = response.getHeaders().getETag();
                    return Optional.of(new DawumFetch(payload, etag));
                });
    }
}
