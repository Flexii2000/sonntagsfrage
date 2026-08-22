package com.fherrmann.wahlen.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeatCalculatorTest {

    private final SeatCalculator calculator = new SeatCalculator();

    private static Map<Integer, Double> votes(Object... pairs) {
        Map<Integer, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Integer) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return map;
    }

    @Test
    @DisplayName("verteilt alle Sitze und keinen mehr")
    void distributesExactlyAllSeats() {
        SeatCalculator.SeatDistribution d = calculator.distribute(
                votes(1, 30.0, 2, 25.0, 3, 20.0, 4, 15.0, 5, 10.0),
                5.0, Set.of(), Set.of(), 100);

        assertThat(d.seats().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
    }

    @Test
    @DisplayName("laesst Parteien unter der Sperrklausel draussen")
    void appliesThreshold() {
        SeatCalculator.SeatDistribution d = calculator.distribute(
                votes(1, 40.0, 2, 35.0, 3, 4.9),
                5.0, Set.of(), Set.of(), 100);

        assertThat(d.seats()).doesNotContainKey(3);
        assertThat(d.failed()).containsKey(3);
        assertThat(d.inParliament()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("laesst befreite Parteien trotz Unterschreitung einziehen (SSW-Fall)")
    void honoursThresholdExemption() {
        SeatCalculator.SeatDistribution d = calculator.distribute(
                votes(1, 40.0, 2, 35.0, 10, 4.0),
                5.0, Set.of(10), Set.of(), 100);

        assertThat(d.seats()).containsKey(10);
        assertThat(d.seats().get(10)).isPositive();
        assertThat(d.failed()).doesNotContainKey(10);
    }

    @Test
    @DisplayName("ignoriert die Sammelposition Sonstige")
    void ignoresOtherParties() {
        SeatCalculator.SeatDistribution d = calculator.distribute(
                votes(0, 12.0, 1, 50.0, 2, 38.0),
                5.0, Set.of(), Set.of(0), 100);

        assertThat(d.seats()).doesNotContainKey(0);
        assertThat(d.failed()).doesNotContainKey(0);
    }

    @Test
    @DisplayName("rechnet nach Sainte-Lague, nicht nach d'Hondt")
    void usesSainteLague() {
        // 53 / 24 / 23 auf 7 Sitze trennt die beiden Verfahren sauber:
        //   Sainte-Lague (Divisoren 0,5 / 1,5 / 2,5 ...) -> 3 / 2 / 2
        //   d'Hondt      (Divisoren 1 / 2 / 3 ...)       -> 4 / 2 / 1
        // Der Test faellt also auf, falls jemand das Verfahren austauscht.
        SeatCalculator.SeatDistribution d = calculator.distribute(
                votes(1, 53.0, 2, 24.0, 3, 23.0),
                0.0, Set.of(), Set.of(), 7);

        assertThat(d.seats().get(1)).isEqualTo(3);
        assertThat(d.seats().get(2)).isEqualTo(2);
        assertThat(d.seats().get(3)).isEqualTo(2);
    }

    @Test
    @DisplayName("berechnet die Mehrheitsschwelle")
    void computesMajority() {
        assertThat(calculator.distribute(votes(1, 100.0), 0.0, Set.of(), Set.of(), 83).majority())
                .isEqualTo(42);
        assertThat(calculator.distribute(votes(1, 100.0), 0.0, Set.of(), Set.of(), 630).majority())
                .isEqualTo(316);
    }

    @Test
    @DisplayName("kommt mit leerer Eingabe klar")
    void emptyInput() {
        SeatCalculator.SeatDistribution d =
                calculator.distribute(Map.of(), 5.0, Set.of(), Set.of(), 100);
        assertThat(d.seats()).isEmpty();
        assertThat(d.inParliament()).isEmpty();
    }
}
