package com.fherrmann.wahlen.wahlabend;

import com.fherrmann.wahlen.domain.Election;
import com.fherrmann.wahlen.reference.ReferenceData;
import com.fherrmann.wahlen.reference.ReferenceData.LiveSourceRef;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Welche automatischen Quellen es je Wahl gibt — direkt aus
 * {@code elections.yaml}, nicht aus der Datenbank: die Konfiguration gehoert
 * zum Repo, genau wie Termine und Ergebnisse.
 */
@Component
public class LiveSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(LiveSourceRegistry.class);

    private final Map<String, List<LiveSourceRef>> bySlugAndDate = new HashMap<>();

    public LiveSourceRegistry() {
        try (InputStream in = new ClassPathResource("reference/elections.yaml").getInputStream()) {
            ReferenceData.ElectionsFile file = new YAMLMapper().readValue(in, ReferenceData.ElectionsFile.class);
            for (ReferenceData.ElectionRef ref : file.elections()) {
                if (ref.live() != null && !ref.live().isEmpty()) {
                    bySlugAndDate.put(key(ref.parliament(), ref.date()), List.copyOf(ref.live()));
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("elections.yaml nicht lesbar", e);
        }
        log.info("Wahlabend-Quellen konfiguriert für {} Wahl(en)", bySlugAndDate.size());
    }

    public List<LiveSourceRef> sourcesFor(Election election) {
        return bySlugAndDate.getOrDefault(
                key(election.getParliament().getSlug(), election.getElectionDate().toString()), List.of());
    }

    private static String key(String slug, String date) {
        return slug + "|" + date;
    }
}
