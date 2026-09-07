package com.fherrmann.wahlen.wahlabend;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Was der automatische Abruf zuletzt je Wahl erlebt hat — nur im Speicher,
 * fuer die Anzeige "Quelle zuletzt geprueft / Fehler". Ein Neustart vergisst
 * das, und das ist in Ordnung: der naechste Abruf ist eine Minute entfernt.
 */
@Component
public class LiveSourceStatus {

    public record Status(Instant lastCheck, Instant lastChange, String lastError) {
    }

    private final Map<Long, Status> byElection = new ConcurrentHashMap<>();

    public void checked(Long electionId, boolean changed) {
        Status old = byElection.get(electionId);
        Instant now = Instant.now();
        byElection.put(electionId, new Status(
                now, changed ? now : (old != null ? old.lastChange() : null), null));
    }

    public void failed(Long electionId, String error) {
        Status old = byElection.get(electionId);
        byElection.put(electionId, new Status(
                Instant.now(), old != null ? old.lastChange() : null, error));
    }

    public Optional<Status> get(Long electionId) {
        return Optional.ofNullable(byElection.get(electionId));
    }
}
