package com.fherrmann.wahlen.api;

import com.fherrmann.wahlen.domain.Party;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Die JSON-Formate der oeffentlichen API, gebuendelt an einer Stelle. */
public final class Dtos {

    private Dtos() {
    }

    public record PartyDto(
            int id, String shortcut, String name,
            String colorLight, String colorDark, int order, int spectrum) {

        public static PartyDto from(Party p) {
            return new PartyDto(p.getId(), p.getShortcut(), p.getName(),
                    p.getColorLight(), p.getColorDark(), p.getSortOrder(), p.getSpectrum());
        }
    }

    public record ParliamentDto(
            int id, String slug, String name, String shortcut, String electionName,
            String level, double threshold, Integer seats,
            long surveyCount, LocalDate latestSurvey, LocalDate latestPublished) {
    }

    public record PollDto(
            int id, LocalDate published, LocalDate effective,
            LocalDate periodStart, LocalDate periodEnd,
            Integer instituteId, String institute, String tasker, String method,
            Integer surveyed, Map<Integer, Double> results) {
    }

    /** {@code values} enthaelt {@code null} an Tagen ohne ausreichende Datenlage. */
    public record SeriesDto(int partyId, List<Double> values) {
    }

    public record TrendDto(List<LocalDate> dates, List<SeriesDto> series, double sigmaDays) {
    }

    /**
     * {@code resultKind} sagt, was in {@code results} steht: AMTLICH aus den
     * Referenzdaten oder — solange das fehlt — der juengste Wahlabend-Stand.
     */
    public record ElectionDto(
            LocalDate date, boolean confirmed, String status,
            Double turnout, Integer seats, String source,
            Map<Integer, Double> results, String resultKind, Long daysAway) {
    }

    /** Ein Stand am Wahlabend. {@code timeLabel} ist fuer Menschen, {@code reportedAt} fuer Maschinen. */
    public record ReportDto(
            long id, String kind, String kindLabel, Instant reportedAt, String timeLabel,
            String source, String sourceUrl, Double turnout, String note, boolean seatsOfficial,
            Map<Integer, Double> results, Map<Integer, Integer> seats) {
    }

    /**
     * Alles fuer den Wahlabend-Block einer Wahl.
     *
     * @param phase          AUSSTEHEND | LIVE | ABGESCHLOSSEN
     * @param refreshSeconds wie oft der Browser nachfragen soll; 0 = gar nicht
     * @param latest         der massgebliche Stand (vorlaeufiges Ergebnis vor allem anderen, sonst der juengste)
     * @param history        alle Staende, aelteste zuerst
     * @param previousResults amtliches Ergebnis der vorigen Wahl desselben Parlaments
     * @param pollsBefore    geglaetteter Umfragestand zum Wahltag — wie gut lagen die Umfragen?
     */
    public record WahlabendDto(
            String phase, boolean live, boolean hot, int refreshSeconds,
            String title, String headline,
            LocalDate electionDate, String electionName,
            ReportDto latest, List<ReportDto> history,
            List<PartyDto> parties,
            LocalDate previousDate, Map<Integer, Double> previousResults,
            Map<Integer, Double> pollsBefore, LocalDate pollsAsOf,
            SeatsDto seats, List<CoalitionDto> coalitions,
            String sourceLastCheck, String sourceError,
            Instant generatedAt) {
    }

    /** Kurzfassung fuer die Karten der Startseite. */
    public record WahlabendSummaryDto(String phase, boolean live, String kindLabel, String timeLabel) {
    }

    public record SeatEntryDto(int partyId, int seats, double percent) {
    }

    public record SeatsDto(
            int totalSeats, int majority, List<SeatEntryDto> entries,
            Map<Integer, Double> belowThreshold, double threshold, String basis) {
    }

    /**
     * @param afd        AfD beteiligt — die Seite blendet solche Buendnisse hinter
     *                   dem Schalter "Brandmauer" aus
     * @param unionLinke Union und Linke zusammen — Schalter "Unvereinbarkeitsbeschluss"
     */
    public record CoalitionDto(
            List<Integer> partyIds, int seats, String name, boolean minimal, int over,
            boolean afd, boolean unionLinke) {
    }

    public record HouseEffectDto(
            int instituteId, String institute, int surveyCount,
            LocalDate latestSurvey, Map<Integer, Double> deviation) {
    }

    public record RangeDto(LocalDate from, LocalDate to, String label) {
    }

    /** Aktueller geglaetteter Stand plus Veraenderung gegenueber vor 30 Tagen. */
    public record CurrentDto(
            Map<Integer, Double> value, Map<Integer, Double> change30d, LocalDate asOf) {
    }

    public record ParliamentDetailDto(
            ParliamentDto parliament,
            List<PartyDto> parties,
            RangeDto range,
            CurrentDto current,
            TrendDto trend,
            List<PollDto> polls,
            ElectionDto lastElection,
            ElectionDto nextElection,
            SeatsDto seats,
            List<CoalitionDto> coalitions,
            List<HouseEffectDto> instituteEffects,
            WahlabendDto wahlabend) {
    }

    public record ParliamentSummaryDto(
            ParliamentDto parliament,
            List<PartyDto> parties,
            CurrentDto current,
            TrendDto spark,
            ElectionDto lastElection,
            ElectionDto nextElection,
            WahlabendSummaryDto wahlabend) {
    }

    public record FeaturedDto(String slug, String reason, String headline, ElectionDto election) {
    }

    public record MetaDto(
            String dawumLastUpdate, String lastCheck, String lastImport, String lastError,
            long surveyCount, String license, String licenseUrl,
            String sourceName, String sourceUrl, double sigmaDays) {
    }
}
