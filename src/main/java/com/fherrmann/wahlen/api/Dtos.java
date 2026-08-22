package com.fherrmann.wahlen.api;

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

    public record ElectionDto(
            LocalDate date, boolean confirmed, String status,
            Double turnout, Integer seats, String source,
            Map<Integer, Double> results, Long daysAway) {
    }

    public record SeatEntryDto(int partyId, int seats, double percent) {
    }

    public record SeatsDto(
            int totalSeats, int majority, List<SeatEntryDto> entries,
            Map<Integer, Double> belowThreshold, double threshold, String basis) {
    }

    public record CoalitionDto(
            List<Integer> partyIds, int seats, String name, boolean minimal, int over) {
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
            List<HouseEffectDto> instituteEffects) {
    }

    public record ParliamentSummaryDto(
            ParliamentDto parliament,
            List<PartyDto> parties,
            CurrentDto current,
            TrendDto spark,
            ElectionDto lastElection,
            ElectionDto nextElection) {
    }

    public record FeaturedDto(String slug, String reason, String headline, ElectionDto election) {
    }

    public record MetaDto(
            String dawumLastUpdate, String lastCheck, String lastImport, String lastError,
            long surveyCount, String license, String licenseUrl,
            String sourceName, String sourceUrl, double sigmaDays) {
    }
}
