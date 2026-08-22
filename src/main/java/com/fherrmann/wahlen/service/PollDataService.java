package com.fherrmann.wahlen.service;

import com.fherrmann.wahlen.analysis.PollPoint;
import com.fherrmann.wahlen.config.CacheConfig;
import com.fherrmann.wahlen.repository.SurveyRepository;
import com.fherrmann.wahlen.repository.SurveyRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Laedt die Rohumfragen eines Parlaments und formt sie zu {@link PollPoint}s.
 *
 * <p>Zwischen Datenbank und Auswertung liegt bewusst diese Schicht: die
 * Rechnungen arbeiten dadurch auf schlichten Records statt auf JPA-Entities
 * und sind ohne laufende Datenbank testbar. Das Ergebnis wird gecacht und erst
 * nach einem erfolgreichen Import verworfen — Umfragen aendern sich nur dann.
 */
@Service
public class PollDataService {

    private final SurveyRepository surveys;

    public PollDataService(SurveyRepository surveys) {
        this.surveys = surveys;
    }

    @Cacheable(CacheConfig.POLL_POINTS)
    @Transactional(readOnly = true)
    public List<PollPoint> pointsFor(Integer parliamentId) {
        Map<Integer, PollPointBuilder> builders = new LinkedHashMap<>();
        for (SurveyRow row : surveys.findRowsByParliament(parliamentId)) {
            builders.computeIfAbsent(row.surveyId(), id -> new PollPointBuilder(row))
                    .add(row.partyId(), row.percent().doubleValue());
        }
        List<PollPoint> points = new ArrayList<>(builders.size());
        builders.values().forEach(b -> points.add(b.build()));
        points.sort(Comparator.comparing(PollPoint::effectiveDate).reversed()
                .thenComparing(Comparator.comparingInt(PollPoint::surveyId).reversed()));
        return List.copyOf(points);
    }

    private static final class PollPointBuilder {

        private final SurveyRow first;
        private final Map<Integer, Double> results = new LinkedHashMap<>();

        private PollPointBuilder(SurveyRow first) {
            this.first = first;
        }

        private void add(Integer partyId, double percent) {
            results.put(partyId, percent);
        }

        private PollPoint build() {
            return new PollPoint(
                    first.surveyId(), first.instituteId(), first.taskerId(), first.methodId(),
                    first.publishedOn(), first.periodStart(), first.periodEnd(),
                    first.surveyedPersons(), Map.copyOf(results));
        }
    }
}
