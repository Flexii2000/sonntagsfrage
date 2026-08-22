package com.fherrmann.wahlen.analysis;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Glaettet die Rohumfragen zu einer Verlaufskurve je Partei.
 *
 * <p>Verfahren: <b>Gauss-gewichteter gleitender Durchschnitt</b>. Fuer jeden
 * Stuetztag geht jede Umfrage mit
 * {@code exp(-0.5 * ((t - t_umfrage) / sigma)^2)} ein.
 *
 * <p>Bewusst kein LOESS: der Gauss-Kernel laesst sich in einem Satz erklaeren,
 * hat keine Randartefakte durch lokale Regression und ist nachrechenbar — bei
 * Wahlumfragen ist Nachvollziehbarkeit wichtiger als der letzte Prozentpunkt
 * Anpassungsguete. Die Methode steht so auch auf der Seite /wahlen/daten.
 */
@Component
public class TrendCalculator {

    /** Unterhalb dieses Gesamtgewichts gilt ein Stuetztag als unbelegt. */
    private static final double MIN_TOTAL_WEIGHT = 0.35;

    /** Obergrenze der automatischen Glaettungsbreite — darueber wird es Brei. */
    private static final double MAX_SIGMA_DAYS = 45;

    /**
     * @param dates      Stuetztage der Kurve
     * @param byParty    Partei-ID -> Werte, {@code NaN} wo keine Daten vorliegen
     * @param sigmaDays  verwendete Glaettungsbreite
     */
    public record Trend(List<LocalDate> dates, Map<Integer, double[]> byParty, double sigmaDays) {

        public boolean isEmpty() {
            return dates.isEmpty();
        }
    }

    /**
     * Passt die Glaettungsbreite an die Umfragedichte an.
     *
     * <p>Der Bundestag wird fast taeglich befragt, Sachsen-Anhalt zwischen zwei
     * Wahlkaempfen alle paar Wochen. Mit einem festen sigma von 10 Tagen zerfaellt
     * die Kurve dort in Fragmente, weil zwischen zwei Umfragen schlicht nichts
     * liegt. Deshalb wird sigma am Median des Abstands zwischen aufeinander
     * folgenden Umfragen ausgerichtet — dicht befragte Parlamente behalten die
     * feine Aufloesung, duenn befragte bekommen eine durchgehende Linie.
     *
     * <p>Der verwendete Wert wird im UI ausgewiesen, damit die Kurve nachrechenbar
     * bleibt.
     */
    public double adaptiveSigma(List<PollPoint> points, double baseSigmaDays) {
        if (points.size() < 3) {
            return MAX_SIGMA_DAYS;
        }
        long[] days = points.stream()
                .map(PollPoint::effectiveDate)
                .mapToLong(LocalDate::toEpochDay)
                .sorted()
                .toArray();
        long[] gaps = new long[days.length - 1];
        for (int i = 1; i < days.length; i++) {
            gaps[i - 1] = days[i] - days[i - 1];
        }
        java.util.Arrays.sort(gaps);
        double median = gaps[gaps.length / 2];
        return Math.min(MAX_SIGMA_DAYS, Math.max(baseSigmaDays, median * 1.5));
    }

    public Trend compute(List<PollPoint> points,
                         Collection<Integer> partyIds,
                         LocalDate from,
                         LocalDate to,
                         double sigmaDays,
                         double cutoffSigmas,
                         int maxPoints) {
        if (points.isEmpty() || partyIds.isEmpty() || from == null || to == null || to.isBefore(from)) {
            return new Trend(List.of(), Map.of(), sigmaDays);
        }

        List<LocalDate> grid = buildGrid(from, to, maxPoints);
        double cutoffDays = sigmaDays * cutoffSigmas;

        // Umfragen einmal vorsortieren, damit pro Stuetztag nur das Fenster
        // durchlaufen wird statt aller Umfragen.
        List<PollPoint> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> a.effectiveDate().compareTo(b.effectiveDate()));
        long[] dayOf = new long[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            dayOf[i] = sorted.get(i).effectiveDate().toEpochDay();
        }

        Map<Integer, double[]> byParty = new LinkedHashMap<>();
        for (Integer partyId : partyIds) {
            byParty.put(partyId, new double[grid.size()]);
        }

        int windowStart = 0;
        for (int g = 0; g < grid.size(); g++) {
            long day = grid.get(g).toEpochDay();
            while (windowStart < dayOf.length && dayOf[windowStart] < day - cutoffDays) {
                windowStart++;
            }

            Map<Integer, double[]> acc = new LinkedHashMap<>();
            for (Integer partyId : partyIds) {
                acc.put(partyId, new double[2]);
            }

            for (int i = windowStart; i < sorted.size() && dayOf[i] <= day + cutoffDays; i++) {
                double dt = dayOf[i] - day;
                double weight = Math.exp(-0.5 * (dt / sigmaDays) * (dt / sigmaDays));
                if (weight <= 0) {
                    continue;
                }
                Map<Integer, Double> results = sorted.get(i).results();
                for (Map.Entry<Integer, double[]> entry : acc.entrySet()) {
                    Double value = results.get(entry.getKey());
                    if (value != null) {
                        entry.getValue()[0] += weight * value;
                        entry.getValue()[1] += weight;
                    }
                }
            }

            for (Map.Entry<Integer, double[]> entry : acc.entrySet()) {
                double[] sums = entry.getValue();
                byParty.get(entry.getKey())[g] =
                        sums[1] >= MIN_TOTAL_WEIGHT ? sums[0] / sums[1] : Double.NaN;
            }
        }

        return new Trend(grid, byParty, sigmaDays);
    }

    /**
     * Geglaetteter Wert genau an einem Tag — Grundlage des Institutsvergleichs.
     *
     * @param exclude Umfragen, die nicht eingehen duerfen (Leave-one-out)
     */
    public Double valueAt(List<PollPoint> points,
                          int partyId,
                          LocalDate date,
                          double sigmaDays,
                          double cutoffSigmas,
                          java.util.function.Predicate<PollPoint> exclude) {
        long day = date.toEpochDay();
        double cutoffDays = sigmaDays * cutoffSigmas;
        double weighted = 0;
        double total = 0;
        for (PollPoint point : points) {
            if (exclude != null && exclude.test(point)) {
                continue;
            }
            Double value = point.percentFor(partyId);
            if (value == null) {
                continue;
            }
            double dt = point.effectiveDate().toEpochDay() - day;
            if (Math.abs(dt) > cutoffDays) {
                continue;
            }
            double weight = Math.exp(-0.5 * (dt / sigmaDays) * (dt / sigmaDays));
            weighted += weight * value;
            total += weight;
        }
        return total >= MIN_TOTAL_WEIGHT ? weighted / total : null;
    }

    private static List<LocalDate> buildGrid(LocalDate from, LocalDate to, int maxPoints) {
        long span = ChronoUnit.DAYS.between(from, to);
        int step = (int) Math.max(1, Math.ceil((span + 1.0) / Math.max(2, maxPoints)));
        List<LocalDate> grid = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(step)) {
            grid.add(d);
        }
        if (grid.isEmpty() || !grid.get(grid.size() - 1).equals(to)) {
            grid.add(to);
        }
        return grid;
    }
}
