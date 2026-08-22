package com.fherrmann.wahlen.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Sitzprojektion nach <b>Sainte-Lague/Schepers</b> — dem Verfahren, das
 * Bundestag und die meisten Landtage tatsaechlich verwenden.
 *
 * <p><b>Bewusste Vereinfachung:</b> Ueberhang- und Ausgleichsmandate,
 * Direktmandate und die Grundmandatsklausel bleiben aussen vor. Sie lassen sich
 * aus einem landesweiten Prozentwert nicht ableiten. Die Projektion verteilt
 * deshalb die regulaere Sitzzahl des Parlaments auf die Parteien ueber der
 * Sperrklausel. Das UI weist das aus.
 */
@Component
public class SeatCalculator {

    /**
     * @param seats        Partei-ID -> Sitze
     * @param totalSeats   verteilte Sitze insgesamt
     * @param majority     Sitze, die fuer eine Mehrheit noetig sind
     * @param inParliament Parteien ueber der Sperrklausel
     * @param failed       Parteien unter der Sperrklausel (mit ihrem Prozentwert)
     */
    public record SeatDistribution(
            Map<Integer, Integer> seats,
            int totalSeats,
            int majority,
            List<Integer> inParliament,
            Map<Integer, Double> failed) {
    }

    /**
     * @param percentages       Partei-ID -> Prozent
     * @param thresholdPercent  Sperrklausel
     * @param exemptPartyIds    von der Sperrklausel befreit (z.B. SSW in SH)
     * @param ignoredPartyIds   nie beruecksichtigt (z.B. die Sammelposition "Sonstige")
     * @param totalSeats        zu verteilende Sitze
     */
    public SeatDistribution distribute(Map<Integer, Double> percentages,
                                       double thresholdPercent,
                                       Set<Integer> exemptPartyIds,
                                       Set<Integer> ignoredPartyIds,
                                       int totalSeats) {
        Map<Integer, Double> eligible = new LinkedHashMap<>();
        Map<Integer, Double> failed = new LinkedHashMap<>();

        percentages.forEach((partyId, percent) -> {
            if (percent == null || ignoredPartyIds.contains(partyId)) {
                return;
            }
            boolean passes = percent >= thresholdPercent || exemptPartyIds.contains(partyId);
            if (passes && percent > 0) {
                eligible.put(partyId, percent);
            } else if (percent > 0) {
                failed.put(partyId, percent);
            }
        });

        Map<Integer, Integer> seats = sainteLague(eligible, totalSeats);
        List<Integer> inParliament = new ArrayList<>(eligible.keySet());
        inParliament.sort(Comparator.comparingDouble((Integer id) -> -eligible.get(id)));

        return new SeatDistribution(seats, totalSeats, totalSeats / 2 + 1, inParliament, failed);
    }

    /**
     * Hoechstzahlverfahren mit den Divisoren 0,5 / 1,5 / 2,5 / ...
     *
     * <p>Bei gleichen Hoechstzahlen entscheidet im echten Wahlrecht das Los;
     * hier gewinnt deterministisch die Partei mit dem hoeheren Stimmenanteil,
     * damit dieselbe Umfrage immer dieselbe Projektion ergibt.
     */
    private Map<Integer, Integer> sainteLague(Map<Integer, Double> votes, int totalSeats) {
        Map<Integer, Integer> seats = new LinkedHashMap<>();
        votes.keySet().forEach(id -> seats.put(id, 0));
        if (votes.isEmpty() || totalSeats <= 0) {
            return seats;
        }

        for (int seat = 0; seat < totalSeats; seat++) {
            Integer best = null;
            double bestQuotient = -1;
            for (Map.Entry<Integer, Double> entry : votes.entrySet()) {
                double quotient = entry.getValue() / (seats.get(entry.getKey()) + 0.5);
                if (quotient > bestQuotient
                        || (quotient == bestQuotient && best != null
                            && entry.getValue() > votes.get(best))) {
                    bestQuotient = quotient;
                    best = entry.getKey();
                }
            }
            seats.merge(best, 1, Integer::sum);
        }
        return seats;
    }
}
