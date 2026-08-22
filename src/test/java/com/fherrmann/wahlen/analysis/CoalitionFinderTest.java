package com.fherrmann.wahlen.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoalitionFinderTest {

    private final CoalitionFinder finder = new CoalitionFinder();

    private static final Map<Integer, String> SHORTCUTS = Map.of(
            1, "CDU/CSU", 2, "SPD", 3, "FDP", 4, "Grüne", 5, "Linke", 7, "AfD");

    private static Map<Integer, Integer> seats(Object... pairs) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Integer) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("findet nur Kombinationen mit Mehrheit")
    void onlyReturnsMajorities() {
        List<CoalitionFinder.Coalition> found =
                finder.find(seats(1, 40, 2, 35, 4, 25), 51, SHORTCUTS);

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(c -> assertThat(c.seats()).isGreaterThanOrEqualTo(51));
    }

    @Test
    @DisplayName("markiert ueberfluessige Partner als nicht minimal")
    void marksRedundantPartners() {
        // 1 und 2 haben zusammen schon die Mehrheit -> das Dreierbuendnis ist nicht minimal.
        List<CoalitionFinder.Coalition> found =
                finder.find(seats(1, 40, 2, 35, 4, 25), 51, SHORTCUTS);

        CoalitionFinder.Coalition all = found.stream()
                .filter(c -> c.partyIds().size() == 3).findFirst().orElseThrow();
        assertThat(all.minimal()).isFalse();

        CoalitionFinder.Coalition pair = found.stream()
                .filter(c -> c.partyIds().size() == 2).findFirst().orElseThrow();
        assertThat(pair.minimal()).isTrue();
    }

    @Test
    @DisplayName("kennt die etablierten Koalitionsnamen")
    void knowsEstablishedNames() {
        assertThat(names(finder.find(seats(1, 45, 2, 40, 3, 15), 51, SHORTCUTS)))
                .contains("Große Koalition");
        assertThat(names(finder.find(seats(2, 30, 4, 25, 3, 25, 1, 20), 51, SHORTCUTS)))
                .contains("Ampel");
        assertThat(names(finder.find(seats(1, 30, 4, 25, 3, 25, 2, 20), 51, SHORTCUTS)))
                .contains("Jamaika");
    }

    @Test
    @DisplayName("vergibt fuer SPD und Linke unterscheidbare Farbwoerter")
    void distinguishesRedFromDarkRed() {
        List<String> withSpd = names(finder.find(seats(7, 45, 2, 30, 5, 25), 51, SHORTCUTS));

        // AfD+SPD und AfD+Linke duerfen nicht beide "Blau-Rot" heissen.
        assertThat(withSpd).contains("Blau-Rot", "Blau-Dunkelrot");
        assertThat(withSpd).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("faellt bei unbekannten Parteien auf die Kuerzel zurueck")
    void fallsBackToShortcuts() {
        Map<Integer, String> exotic = Map.of(1, "CDU/CSU", 99, "Regionalpartei");
        List<String> found = names(finder.find(seats(1, 30, 99, 30), 51, exotic));

        assertThat(found).anySatisfy(n -> assertThat(n).contains("Regionalpartei"));
    }

    @Test
    @DisplayName("erkennt eine Alleinregierung")
    void singlePartyMajority() {
        List<CoalitionFinder.Coalition> found = finder.find(seats(1, 60, 2, 40), 51, SHORTCUTS);

        assertThat(found).anySatisfy(c -> {
            assertThat(c.partyIds()).containsExactly(1);
            assertThat(c.name()).isEqualTo("Alleinregierung CDU/CSU");
        });
    }

    private static List<String> names(List<CoalitionFinder.Coalition> coalitions) {
        return coalitions.stream().map(CoalitionFinder.Coalition::name).toList();
    }
}
