package com.fherrmann.wahlen.analysis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Institutseffekte ("house effects"): sieht ein Institut eine Partei
 * systematisch staerker oder schwaecher als der Rest?
 *
 * <p>Rechnung je Institut und Partei: fuer jede eigene Umfrage wird der
 * geglaettete Konsens am selben Tag <b>aus den Umfragen aller anderen
 * Institute</b> gebildet (Leave-one-out — sonst wuerde ein Institut, das oft
 * misst, seinen eigenen Vergleichsmassstab setzen) und die Abweichung
 * gemittelt.
 *
 * <p>Das ist die Kernaussage hinter "nicht jede neue Umfrage ist eine neue
 * Wahrheit", nur als Zahl statt als Bauchgefuehl.
 */
@Component
public class HouseEffectCalculator {

    /** Weniger eigene Umfragen als das ergeben keinen belastbaren Wert. */
    private static final int MIN_SURVEYS = 3;

    private final TrendCalculator trendCalculator;

    public HouseEffectCalculator(TrendCalculator trendCalculator) {
        this.trendCalculator = trendCalculator;
    }

    /**
     * @param deviationByParty Partei-ID -> mittlere Abweichung in Prozentpunkten
     */
    public record InstituteEffect(
            Integer instituteId,
            int surveyCount,
            LocalDate latestSurvey,
            Map<Integer, Double> deviationByParty) {
    }

    public List<InstituteEffect> compute(List<PollPoint> points,
                                         List<Integer> partyIds,
                                         LocalDate since,
                                         double sigmaDays,
                                         double cutoffSigmas) {
        List<PollPoint> window = points.stream()
                .filter(p -> p.instituteId() != null)
                .filter(p -> since == null || !p.effectiveDate().isBefore(since))
                .toList();
        if (window.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<PollPoint>> byInstitute = new LinkedHashMap<>();
        for (PollPoint point : window) {
            byInstitute.computeIfAbsent(point.instituteId(), k -> new ArrayList<>()).add(point);
        }

        List<InstituteEffect> effects = new ArrayList<>();
        byInstitute.forEach((instituteId, ownPoints) -> {
            if (ownPoints.size() < MIN_SURVEYS) {
                return;
            }
            Map<Integer, double[]> sums = new HashMap<>();
            for (Integer partyId : partyIds) {
                sums.put(partyId, new double[2]);
            }

            for (PollPoint own : ownPoints) {
                for (Integer partyId : partyIds) {
                    Double mine = own.percentFor(partyId);
                    if (mine == null) {
                        continue;
                    }
                    Double consensus = trendCalculator.valueAt(
                            window, partyId, own.effectiveDate(), sigmaDays, cutoffSigmas,
                            other -> Objects.equals(other.instituteId(), instituteId));
                    if (consensus == null) {
                        continue;
                    }
                    double[] acc = sums.get(partyId);
                    acc[0] += mine - consensus;
                    acc[1] += 1;
                }
            }

            Map<Integer, Double> deviations = new LinkedHashMap<>();
            for (Integer partyId : partyIds) {
                double[] acc = sums.get(partyId);
                if (acc[1] >= MIN_SURVEYS) {
                    deviations.put(partyId, acc[0] / acc[1]);
                }
            }
            if (deviations.isEmpty()) {
                return;
            }
            LocalDate latest = ownPoints.stream()
                    .map(PollPoint::effectiveDate)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            effects.add(new InstituteEffect(instituteId, ownPoints.size(), latest, deviations));
        });

        effects.sort(Comparator.comparingInt(InstituteEffect::surveyCount).reversed());
        return effects;
    }
}
