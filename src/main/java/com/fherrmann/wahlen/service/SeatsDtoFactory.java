package com.fherrmann.wahlen.service;

import com.fherrmann.wahlen.analysis.CoalitionFinder;
import com.fherrmann.wahlen.analysis.SeatCalculator;
import com.fherrmann.wahlen.api.Dtos;
import com.fherrmann.wahlen.domain.Parliament;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Baut Sitzverteilung und Koalitionsliste — einmal fuer die Sonntagsfrage,
 * einmal fuer den Wahlabend, mit derselben Rechnung und derselben Beschriftung.
 */
@Component
public class SeatsDtoFactory {

    /** Die Sammelposition "Sonstige" ist keine Partei — nie in Sitze/Koalitionen. */
    public static final int PARTY_SONSTIGE = 0;

    private final SeatCalculator seatCalculator;
    private final CoalitionFinder coalitionFinder;

    public SeatsDtoFactory(SeatCalculator seatCalculator, CoalitionFinder coalitionFinder) {
        this.seatCalculator = seatCalculator;
        this.coalitionFinder = coalitionFinder;
    }

    /**
     * Projektion nach Sainte-Lague auf die regulaere Groesse des Parlaments.
     *
     * @param basisPrefix z.B. "Projektion aus der Hochrechnung: " — oder leer
     */
    public Dtos.SeatsDto projected(Parliament parliament, Map<Integer, Double> percentByParty,
                                   String basisPrefix) {
        Integer total = parliament.getSeatsTotal();
        if (total == null || total <= 0 || percentByParty.isEmpty()) {
            return null;
        }
        SeatCalculator.SeatDistribution distribution = seatCalculator.distribute(
                percentByParty,
                parliament.getThresholdPercent().doubleValue(),
                parliament.thresholdExemptPartyIds(),
                Set.of(PARTY_SONSTIGE),
                total);

        List<Dtos.SeatEntryDto> entries = distribution.inParliament().stream()
                .map(id -> new Dtos.SeatEntryDto(id,
                        distribution.seats().getOrDefault(id, 0),
                        percentByParty.getOrDefault(id, 0.0)))
                .toList();

        return new Dtos.SeatsDto(
                distribution.totalSeats(), distribution.majority(), entries,
                round(distribution.failed(), 1),
                parliament.getThresholdPercent().doubleValue(),
                basisPrefix + describeBasis(total, parliament.getThresholdPercent()));
    }

    /**
     * Sitze, wie die Quelle sie meldet (Landeswahlleitung, ARD/ZDF). Parteien mit
     * Stimmen, aber ohne Sitz gelten als an der Huerde gescheitert.
     */
    public Dtos.SeatsDto official(Parliament parliament, Map<Integer, Double> percentByParty,
                                  Map<Integer, Integer> seatsByParty, String basis) {
        List<Dtos.SeatEntryDto> entries = new ArrayList<>();
        Map<Integer, Double> failed = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<Integer, Integer> e : seatsByParty.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                entries.add(new Dtos.SeatEntryDto(e.getKey(), e.getValue(),
                        percentByParty.getOrDefault(e.getKey(), 0.0)));
                total += e.getValue();
            }
        }
        percentByParty.forEach((id, percent) -> {
            if (id != PARTY_SONSTIGE && percent != null && percent > 0
                    && seatsByParty.getOrDefault(id, 0) <= 0) {
                failed.put(id, Math.round(percent * 10.0) / 10.0);
            }
        });
        if (total == 0) {
            return null;
        }
        entries.sort(Comparator.comparingInt(Dtos.SeatEntryDto::seats).reversed());
        return new Dtos.SeatsDto(total, total / 2 + 1, entries, failed,
                parliament.getThresholdPercent().doubleValue(), basis);
    }

    public List<Dtos.CoalitionDto> coalitions(Dtos.SeatsDto seats, Map<Integer, String> shortcuts) {
        if (seats == null) {
            return List.of();
        }
        Map<Integer, Integer> seatsByParty = new LinkedHashMap<>();
        seats.entries().forEach(e -> seatsByParty.put(e.partyId(), e.seats()));
        return coalitionFinder.find(seatsByParty, seats.majority(), shortcuts).stream()
                .filter(CoalitionFinder.Coalition::minimal)
                .map(c -> new Dtos.CoalitionDto(c.partyIds(), c.seats(), c.name(), c.minimal(),
                        c.seats() - seats.majority()))
                .toList();
    }

    /** Beim Europaparlament gibt es in Deutschland keine Huerde — das muss dastehen. */
    static String describeBasis(int totalSeats, BigDecimal threshold) {
        if (threshold == null || threshold.signum() <= 0) {
            return "Sainte-Laguë auf %d Sitze, ohne Prozenthürde".formatted(totalSeats);
        }
        return "Sainte-Laguë auf %d Sitze, %s-%%-Hürde".formatted(
                totalSeats, threshold.stripTrailingZeros().toPlainString());
    }

    static Map<Integer, Double> round(Map<Integer, Double> values, int decimals) {
        double factor = Math.pow(10, decimals);
        Map<Integer, Double> out = new LinkedHashMap<>();
        values.forEach((k, v) -> out.put(k, Math.round(v * factor) / factor));
        return out;
    }
}
