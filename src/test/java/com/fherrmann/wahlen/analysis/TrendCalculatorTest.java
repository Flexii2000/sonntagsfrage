package com.fherrmann.wahlen.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrendCalculatorTest {

    private final TrendCalculator calculator = new TrendCalculator();

    private static PollPoint at(LocalDate date, int instituteId, double value) {
        return new PollPoint(date.hashCode(), instituteId, 1, 1, date, date, date, 1000, Map.of(1, value));
    }

    @Test
    @DisplayName("gibt bei konstanten Umfragen genau diesen Wert zurueck")
    void constantInputStaysConstant() {
        LocalDate day = LocalDate.of(2026, 6, 1);
        List<PollPoint> points = List.of(
                at(day, 1, 20.0), at(day.plusDays(5), 2, 20.0), at(day.plusDays(10), 3, 20.0));

        TrendCalculator.Trend trend = calculator.compute(
                points, List.of(1), day, day.plusDays(10), 10, 3, 20);

        for (double value : trend.byParty().get(1)) {
            assertThat(value).isEqualTo(20.0, within(1e-9));
        }
    }

    @Test
    @DisplayName("gewichtet nahe Umfragen staerker als weit entfernte")
    void weightsNearbySurveysHigher() {
        LocalDate target = LocalDate.of(2026, 6, 20);
        List<PollPoint> points = List.of(
                at(target, 1, 30.0),
                at(target.minusDays(20), 2, 10.0));

        Double value = calculator.valueAt(points, 1, target, 10, 3, null);

        // Gewichte: 1.0 und exp(-2) = 0.135 -> deutlich naeher an 30 als an 20
        assertThat(value).isNotNull();
        assertThat(value).isGreaterThan(27.0).isLessThan(30.0);
    }

    @Test
    @DisplayName("liefert NaN, wo im Fenster keine Umfragen liegen")
    void producesGapWhereNoData() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        List<PollPoint> points = List.of(at(start, 1, 25.0));

        TrendCalculator.Trend trend = calculator.compute(
                points, List.of(1), start, start.plusDays(120), 10, 3, 130);

        double[] values = trend.byParty().get(1);
        assertThat(values[0]).isEqualTo(25.0, within(1e-9));
        assertThat(values[values.length - 1]).isNaN();
    }

    @Test
    @DisplayName("blendet beim Leave-one-out die eigenen Umfragen aus")
    void leaveOneOutIgnoresOwnSurveys() {
        LocalDate day = LocalDate.of(2026, 6, 1);
        List<PollPoint> points = List.of(
                at(day, 1, 40.0),
                at(day, 2, 20.0),
                at(day, 3, 20.0));

        Double withoutInstitute1 = calculator.valueAt(
                points, 1, day, 10, 3, p -> p.instituteId() == 1);

        assertThat(withoutInstitute1).isEqualTo(20.0, within(1e-9));
    }

    @Test
    @DisplayName("kommt mit leerer Eingabe klar")
    void emptyInput() {
        TrendCalculator.Trend trend = calculator.compute(
                List.of(), List.of(1), LocalDate.now(), LocalDate.now(), 10, 3, 10);
        assertThat(trend.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("respektiert das Abschneiden bei cutoffSigmas")
    void respectsCutoff() {
        LocalDate day = LocalDate.of(2026, 6, 1);
        List<PollPoint> points = List.of(at(day, 1, 30.0));

        assertThat(calculator.valueAt(points, 1, day.plusDays(100), 10, 3, null)).isNull();
    }
}
