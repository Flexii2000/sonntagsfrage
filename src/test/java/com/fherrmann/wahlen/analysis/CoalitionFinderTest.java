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
            1, "CDU/CSU", 2, "SPD", 3, "FDP", 4, "Grüne", 5, "Linke", 7, "AfD",
            8, "BSW", 9, "Freie Wähler");

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
        assertThat(names(finder.find(seats(1, 30, 2, 25, 4, 20, 7, 25), 51, SHORTCUTS)))
                .contains("Kenia");
        assertThat(names(finder.find(seats(1, 30, 2, 25, 3, 20, 7, 25), 51, SHORTCUTS)))
                .contains("Deutschland-Koalition");
    }

    @Test
    @DisplayName("kennt auch Bahamas, Simbabwe und Brombeer")
    void knowsTheNewerNames() {
        assertThat(names(finder.find(seats(1, 30, 7, 25, 3, 10, 2, 35), 51, SHORTCUTS)))
                .contains("Bahamas");
        assertThat(names(finder.find(seats(1, 20, 2, 20, 4, 15, 3, 10, 7, 35), 51, SHORTCUTS)))
                .contains("Simbabwe");
        assertThat(names(finder.find(seats(1, 30, 2, 15, 8, 15, 7, 40), 51, SHORTCUTS)))
                .contains("Brombeer");
    }

    @Test
    @DisplayName("CDU, CSU und CDU/CSU sind fuer die Namen dasselbe Lager")
    void treatsUnionVariantsAlike() {
        Map<Integer, String> land = Map.of(1, "CDU", 2, "SPD", 4, "Grüne", 3, "FDP");
        assertThat(names(finder.find(seats(1, 45, 2, 40, 4, 15), 51, land)))
                .contains("Große Koalition", "Kenia");
        Map<Integer, String> bayern = Map.of(1, "CSU", 4, "Grüne", 3, "FDP", 2, "SPD");
        assertThat(names(finder.find(seats(1, 40, 4, 25, 3, 20, 2, 15), 51, bayern)))
                .contains("Jamaika");
    }

    @Test
    @DisplayName("Zweierbuendnisse heissen nach ihren Farben, die groessere zuerst")
    void colourNamesFollowSize() {
        assertThat(names(finder.find(seats(4, 45, 1, 40, 2, 15), 51, SHORTCUTS)))
                .contains("Grün-Schwarz");
        assertThat(names(finder.find(seats(1, 45, 4, 40, 2, 15), 51, SHORTCUTS)))
                .contains("Schwarz-Grün");
    }

    @Test
    @DisplayName("nennt SPD plus Linke schlicht Rot-Rot")
    void redRed() {
        assertThat(names(finder.find(seats(2, 30, 5, 25, 1, 45), 51, SHORTCUTS)))
                .contains("Rot-Rot");
    }

    @Test
    @DisplayName("nennt SPD, Linke und Gruene Rot-Rot-Gruen")
    void redRedGreen() {
        assertThat(names(finder.find(seats(2, 25, 5, 20, 4, 20, 1, 35), 51, SHORTCUTS)))
                .contains("Rot-Rot-Grün");
    }

    @Test
    @DisplayName("SPD, Gruene und Linke sitzen nie mit der AfD in einem Buendnis")
    void neverPairsAfdWithSpdGreensOrLeft() {
        List<CoalitionFinder.Coalition> found =
                finder.find(seats(7, 30, 2, 25, 1, 21, 4, 15, 5, 10), 51, SHORTCUTS);

        assertThat(found).isNotEmpty();
        assertThat(found).noneSatisfy(c -> {
            assertThat(c.partyIds()).contains(7);
            assertThat(c.partyIds()).containsAnyOf(2, 4, 5);
        });
        // Union + AfD bleibt drin: das ist Sache des Schalters, nicht der Rechnung.
        assertThat(found).anySatisfy(c -> assertThat(c.partyIds()).containsExactlyInAnyOrder(1, 7));
    }

    @Test
    @DisplayName("markiert AfD-Beteiligung und Union+Linke fuer die Schalter")
    void flagsFirewallCases() {
        List<CoalitionFinder.Coalition> found =
                finder.find(seats(1, 35, 7, 25, 5, 20, 2, 20), 51, SHORTCUTS);

        CoalitionFinder.Coalition schwarzBlau = byParties(found, 1, 7);
        assertThat(schwarzBlau.afd()).isTrue();
        assertThat(schwarzBlau.unionLinke()).isFalse();

        CoalitionFinder.Coalition unionLinke = byParties(found, 1, 5);
        assertThat(unionLinke.afd()).isFalse();
        assertThat(unionLinke.unionLinke()).isTrue();

        CoalitionFinder.Coalition groko = byParties(found, 1, 2);
        assertThat(groko.afd()).isFalse();
        assertThat(groko.unionLinke()).isFalse();
    }

    @Test
    @DisplayName("eine absolute Mehrheit der AfD ist keine Brandmauer-Frage")
    void singlePartyAfdIsNotFlagged() {
        List<CoalitionFinder.Coalition> found = finder.find(seats(7, 60, 1, 40), 51, SHORTCUTS);

        assertThat(found).anySatisfy(c -> {
            assertThat(c.partyIds()).containsExactly(7);
            assertThat(c.afd()).isFalse();
        });
    }

    @Test
    @DisplayName("vergibt keinen Namen zweimal in derselben Liste")
    void namesStayUnique() {
        // Union+SPD+Freie Waehler und Union+Linke+Freie Waehler ergaeben beide
        // "Schwarz-Rot-Orange".
        List<String> found = names(finder.find(seats(1, 30, 2, 20, 5, 20, 9, 15, 4, 15), 51, SHORTCUTS));

        assertThat(found).doesNotHaveDuplicates();
        assertThat(found).anySatisfy(n -> assertThat(n).contains("Linke"));
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

    private static CoalitionFinder.Coalition byParties(List<CoalitionFinder.Coalition> coalitions,
                                                       Integer... ids) {
        return coalitions.stream()
                .filter(c -> c.partyIds().size() == ids.length && c.partyIds().containsAll(List.of(ids)))
                .findFirst().orElseThrow();
    }
}
