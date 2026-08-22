package com.fherrmann.wahlen.service;

import com.fherrmann.wahlen.analysis.CoalitionFinder;
import com.fherrmann.wahlen.analysis.HouseEffectCalculator;
import com.fherrmann.wahlen.analysis.PollPoint;
import com.fherrmann.wahlen.analysis.SeatCalculator;
import com.fherrmann.wahlen.analysis.TrendCalculator;
import com.fherrmann.wahlen.api.Dtos;
import com.fherrmann.wahlen.config.WahlenProperties;
import com.fherrmann.wahlen.domain.Election;
import com.fherrmann.wahlen.domain.ElectionResult;
import com.fherrmann.wahlen.domain.Parliament;
import com.fherrmann.wahlen.domain.Party;
import com.fherrmann.wahlen.domain.ResultKind;
import com.fherrmann.wahlen.repository.InstituteRepository;
import com.fherrmann.wahlen.repository.MethodRepository;
import com.fherrmann.wahlen.repository.ParliamentRepository;
import com.fherrmann.wahlen.repository.PartyRepository;
import com.fherrmann.wahlen.repository.SurveyRepository;
import com.fherrmann.wahlen.repository.TaskerRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Setzt aus Rohdaten und Rechnungen die fertigen Ansichten zusammen. */
@Service
public class ParliamentViewService {

    /** Die Sammelposition "Sonstige" ist keine Partei — nie in Sitze/Koalitionen. */
    private static final int PARTY_SONSTIGE = 0;
    /** Ohne vorherige Wahl im Datenbestand: so weit zurueckblicken. */
    private static final int FALLBACK_MONTHS = 24;
    /** Institutseffekte ueber diesen Zeitraum. */
    private static final int HOUSE_EFFECT_MONTHS = 24;
    /** Parteien unterhalb dieses Werts landen nicht in der Standardauswahl. */
    private static final double MIN_RELEVANT_PERCENT = 1.0;
    /**
     * Mindestens dieser Anteil der Umfragen im Zeitraum muss die Partei
     * ueberhaupt ausweisen.
     *
     * <p>Nicht jedes Institut fragt jede Kleinpartei ab. Eine Serie, die nur in
     * jeder fuenften Umfrage vorkommt, wird zu einer Kette von Strichen statt zu
     * einer Linie — das sieht nach Fehler aus, obwohl es korrekt "keine Daten"
     * bedeutet. Solche Parteien bleiben in den Tabellen, aber aus dem Chart raus.
     */
    private static final double MIN_COVERAGE = 0.3;
    /**
     * Der "aktuelle Stand" wird nur ueber dieses Fenster geglaettet.
     *
     * <p>Die Kurve einer Fuenfjahresansicht darf ruhig breit glaetten — die
     * Kopfzahl daneben darf es nicht, sonst verschluckt sie genau die Bewegung
     * der letzten Wochen, wegen der jemand die Seite aufruft.
     */
    private static final int CURRENT_WINDOW_DAYS = 120;

    private final ParliamentRepository parliaments;
    private final PartyRepository parties;
    private final InstituteRepository institutes;
    private final TaskerRepository taskers;
    private final MethodRepository methods;
    private final SurveyRepository surveys;
    private final PollDataService pollData;
    private final ElectionCalendarService calendar;
    private final TrendCalculator trendCalculator;
    private final SeatCalculator seatCalculator;
    private final CoalitionFinder coalitionFinder;
    private final HouseEffectCalculator houseEffects;
    private final WahlenProperties properties;

    public ParliamentViewService(ParliamentRepository parliaments,
                                 PartyRepository parties,
                                 InstituteRepository institutes,
                                 TaskerRepository taskers,
                                 MethodRepository methods,
                                 SurveyRepository surveys,
                                 PollDataService pollData,
                                 ElectionCalendarService calendar,
                                 TrendCalculator trendCalculator,
                                 SeatCalculator seatCalculator,
                                 CoalitionFinder coalitionFinder,
                                 HouseEffectCalculator houseEffects,
                                 WahlenProperties properties) {
        this.parliaments = parliaments;
        this.parties = parties;
        this.institutes = institutes;
        this.taskers = taskers;
        this.methods = methods;
        this.surveys = surveys;
        this.pollData = pollData;
        this.calendar = calendar;
        this.trendCalculator = trendCalculator;
        this.seatCalculator = seatCalculator;
        this.coalitionFinder = coalitionFinder;
        this.houseEffects = houseEffects;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Optional<Dtos.ParliamentDetailDto> detail(String slug, LocalDate from, LocalDate to) {
        return parliaments.findBySlug(slug).map(p -> buildDetail(p, from, to));
    }

    /**
     * Was auf der Startseite oben steht — inklusive der Begruendung, damit die
     * Auswahl fuer den Leser nachvollziehbar ist und nicht willkuerlich wirkt.
     */
    @Transactional(readOnly = true)
    public Dtos.FeaturedDto featured() {
        ElectionCalendarService.Featured featured = calendar.featured();
        Parliament parliament = parliaments.findById(featured.parliamentId())
                .or(() -> parliaments.findBySlug("bundestag"))
                .orElse(null);
        if (parliament == null) {
            return new Dtos.FeaturedDto(null, featured.reason().name(),
                    "Noch keine Daten geladen", null);
        }

        LocalDate today = calendar.today();
        Election election = featured.election();
        String headline = switch (featured.reason()) {
            case UPCOMING_ELECTION -> {
                long days = ChronoUnit.DAYS.between(today, election.getElectionDate());
                yield days == 0
                        ? "Heute: " + parliament.getElectionName()
                        : "%s in %d %s".formatted(
                                parliament.getElectionName(), days, days == 1 ? "Tag" : "Tagen");
            }
            case RECENT_ELECTION -> "%s — gerade gewählt".formatted(parliament.getElectionName());
            case DEFAULT -> "Sonntagsfrage zur " + parliament.getElectionName();
        };

        return new Dtos.FeaturedDto(
                parliament.getSlug(),
                featured.reason().name(),
                headline,
                election != null ? electionDto(election, today) : null);
    }

    @Transactional(readOnly = true)
    public List<Dtos.ParliamentSummaryDto> overview() {
        List<Election> allElections = calendar.all();
        return parliaments.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(p -> buildSummary(p, allElections))
                .toList();
    }

    private Dtos.ParliamentSummaryDto buildSummary(Parliament parliament, List<Election> allElections) {
        List<PollPoint> points = pollData.pointsFor(parliament.getId());
        LocalDate today = calendar.today();
        LocalDate latest = points.isEmpty() ? null : points.get(0).effectiveDate();

        Optional<Election> last = calendar.lastElection(parliament.getId(), allElections);
        Optional<Election> next = calendar.nextElection(parliament.getId(), allElections);

        // Sparkline: die letzten 12 Monate, grob abgetastet.
        LocalDate sparkFrom = (latest != null ? latest : today).minusMonths(12);
        LocalDate sparkTo = latest != null ? maxDate(latest, today) : today;
        List<Party> relevant = relevantParties(points, sparkFrom, sparkTo, 6);
        List<PollPoint> sparkPoints = inRange(points, sparkFrom, sparkTo);
        double sparkSigma = trendCalculator.adaptiveSigma(sparkPoints, properties.trend().sigmaDays());
        TrendCalculator.Trend trend = trendCalculator.compute(
                sparkPoints, ids(relevant), sparkFrom, sparkTo,
                sparkSigma, properties.trend().cutoffSigmas(), 40);

        return new Dtos.ParliamentSummaryDto(
                parliamentDto(parliament, points, latest),
                relevant.stream().map(this::partyDto).toList(),
                currentDto(points, relevant, latest != null ? latest : today,
                        currentSigma(points, latest != null ? latest : today)),
                trendDto(trend),
                last.map(e -> electionDto(e, today)).orElse(null),
                next.map(e -> electionDto(e, today)).orElse(null));
    }

    private Dtos.ParliamentDetailDto buildDetail(Parliament parliament, LocalDate requestedFrom,
                                                 LocalDate requestedTo) {
        List<PollPoint> points = pollData.pointsFor(parliament.getId());
        List<Election> allElections = calendar.all();
        LocalDate today = calendar.today();
        LocalDate latest = points.isEmpty() ? null : points.get(0).effectiveDate();

        Optional<Election> last = calendar.lastElection(parliament.getId(), allElections);
        Optional<Election> next = calendar.nextElection(parliament.getId(), allElections);

        LocalDate to = requestedTo != null ? requestedTo
                : (latest != null ? maxDate(latest, today) : today);
        LocalDate from = requestedFrom != null ? requestedFrom : defaultFrom(last, points, to);
        if (from.isAfter(to)) {
            from = to.minusMonths(1);
        }
        String rangeLabel = requestedFrom == null && last.isPresent()
                && last.get().getElectionDate().equals(from)
                ? "seit der " + parliament.getElectionName() + " "
                  + last.get().getElectionDate().getYear()
                : "%s bis %s".formatted(from, to);

        List<PollPoint> windowed = inRange(points, from, to);
        List<Party> relevant = relevantParties(points, from, to, 12);

        double sigma = trendCalculator.adaptiveSigma(windowed, properties.trend().sigmaDays());
        TrendCalculator.Trend trend = trendCalculator.compute(
                points, ids(relevant), from, to,
                sigma, properties.trend().cutoffSigmas(), properties.trend().maxPoints());

        // Bezugspunkt ist die juengste Erhebung, nicht "heute": in Laendern, die
        // nur alle paar Wochen befragt werden, laege heute sonst ausserhalb des
        // Kernel-Fensters und es gaebe gar keinen aktuellen Wert. Ausserdem ist
        // "Stand 7. August" ehrlicher als eine Hochrechnung auf den heutigen Tag.
        LocalDate currentAsOf = latest != null && latest.isBefore(to) ? latest : to;
        Dtos.CurrentDto current = currentDto(points, relevant, currentAsOf, currentSigma(points, currentAsOf));
        Dtos.SeatsDto seats = seatsDto(parliament, current, relevant);
        List<Dtos.CoalitionDto> coalitions = coalitionsDto(seats, relevant);

        Map<Integer, String> instituteNames = instituteNames();
        List<Dtos.HouseEffectDto> effects = houseEffects
                .compute(points, ids(relevant).stream().toList(),
                        to.minusMonths(HOUSE_EFFECT_MONTHS),
                        sigma, properties.trend().cutoffSigmas())
                .stream()
                .map(e -> new Dtos.HouseEffectDto(
                        e.instituteId(),
                        instituteNames.getOrDefault(e.instituteId(), "unbekannt"),
                        e.surveyCount(), e.latestSurvey(), round(e.deviationByParty(), 2)))
                .toList();

        // Die Parteiliste muss auch die abdecken, die nur noch im alten
        // Wahlergebnis vorkommen (z.B. die FDP nach einem Rauswurf) — sonst kann
        // das Frontend deren Namen und Farbe nicht aufloesen.
        List<Party> described = withElectionParties(relevant, last.orElse(null));

        return new Dtos.ParliamentDetailDto(
                parliamentDto(parliament, points, latest),
                described.stream().map(this::partyDto).toList(),
                new Dtos.RangeDto(from, to, rangeLabel),
                current,
                trendDto(trend),
                pollDtos(windowed),
                last.map(e -> electionDto(e, today)).orElse(null),
                next.map(e -> electionDto(e, today)).orElse(null),
                seats,
                coalitions,
                effects);
    }

    /**
     * Standard-Zeitraum: seit der letzten Wahl. Das ist der Zeitraum, in dem ein
     * Vergleich ueberhaupt sinnvoll ist — ueber eine Wahl hinweg hat sich das
     * Parteiensystem oft veraendert. Gibt es dazu keine Daten, die letzten zwei Jahre.
     */
    private LocalDate defaultFrom(Optional<Election> last, List<PollPoint> points, LocalDate to) {
        // Der Rueckfallzeitraum haengt an der juengsten Umfrage, nicht an heute.
        // Sonst ist das Fenster leer, wenn laenger als FALLBACK_MONTHS gar nicht
        // befragt wurde — beim Europaparlament ist genau das der Fall: die letzte
        // Umfrage stammt von vor der Europawahl 2024.
        LocalDate anchor = points.isEmpty() ? to : points.get(0).effectiveDate();
        LocalDate fallback = (anchor.isBefore(to) ? anchor : to).minusMonths(FALLBACK_MONTHS);
        if (last.isEmpty()) {
            return fallback;
        }
        LocalDate electionDate = last.get().getElectionDate();
        long after = points.stream().filter(p -> !p.effectiveDate().isBefore(electionDate)).count();
        return after >= 5 ? electionDate : fallback;
    }

    private Dtos.ParliamentDto parliamentDto(Parliament p, List<PollPoint> points, LocalDate latest) {
        LocalDate latestPublished = points.stream()
                .map(PollPoint::publishedOn)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new Dtos.ParliamentDto(
                p.getId(), p.getSlug(), p.getName(), p.getShortcut(), p.getElectionName(),
                p.getLevel().name(), p.getThresholdPercent().doubleValue(), p.getSeatsTotal(),
                points.size(), latest, latestPublished);
    }

    private Dtos.PartyDto partyDto(Party p) {
        return new Dtos.PartyDto(p.getId(), p.getShortcut(), p.getName(),
                p.getColorLight(), p.getColorDark(), p.getSortOrder(), p.getSpectrum());
    }

    /**
     * Die drei Namenstabellen werden einmal je Aufruf geladen, nicht je Umfrage —
     * sonst waeren es bei 2.600 Bundestagsumfragen 7.800 Abfragen.
     */
    private List<Dtos.PollDto> pollDtos(List<PollPoint> points) {
        Map<Integer, String> instituteNames = instituteNames();
        Map<Integer, String> taskerNames = taskerNames();
        Map<Integer, String> methodNames = methodNames();
        return points.stream()
                .map(point -> new Dtos.PollDto(
                        point.surveyId(), point.publishedOn(), point.effectiveDate(),
                        point.periodStart(), point.periodEnd(),
                        point.instituteId(),
                        lookup(instituteNames, point.instituteId()),
                        lookup(taskerNames, point.taskerId()),
                        lookup(methodNames, point.methodId()),
                        point.surveyedPersons(), point.results()))
                .toList();
    }

    private Dtos.TrendDto trendDto(TrendCalculator.Trend trend) {
        List<Dtos.SeriesDto> series = new ArrayList<>();
        trend.byParty().forEach((partyId, values) -> {
            List<Double> list = new ArrayList<>(values.length);
            for (double v : values) {
                list.add(Double.isNaN(v) ? null : Math.round(v * 100.0) / 100.0);
            }
            series.add(new Dtos.SeriesDto(partyId, list));
        });
        return new Dtos.TrendDto(trend.dates(), series, trend.sigmaDays());
    }

    /** Glaettungsbreite fuer die Kopfzahlen — nur aus den juengsten Umfragen. */
    private double currentSigma(List<PollPoint> points, LocalDate asOf) {
        List<PollPoint> recent = inRange(points, asOf.minusDays(CURRENT_WINDOW_DAYS), asOf);
        return trendCalculator.adaptiveSigma(recent, properties.trend().sigmaDays());
    }

    private Dtos.CurrentDto currentDto(List<PollPoint> points, List<Party> relevant,
                                       LocalDate asOf, double sigma) {
        Map<Integer, Double> value = new LinkedHashMap<>();
        Map<Integer, Double> change = new LinkedHashMap<>();
        double cutoff = properties.trend().cutoffSigmas();
        for (Party party : relevant) {
            Double now = trendCalculator.valueAt(points, party.getId(), asOf, sigma, cutoff, null);
            if (now == null) {
                continue;
            }
            value.put(party.getId(), round(now));
            Double before = trendCalculator.valueAt(
                    points, party.getId(), asOf.minusDays(30), sigma, cutoff, null);
            if (before != null) {
                change.put(party.getId(), round(now - before));
            }
        }
        return new Dtos.CurrentDto(value, change, asOf);
    }

    private Dtos.SeatsDto seatsDto(Parliament parliament, Dtos.CurrentDto current, List<Party> relevant) {
        Integer total = parliament.getSeatsTotal();
        if (total == null || total <= 0 || current.value().isEmpty()) {
            return null;
        }
        SeatCalculator.SeatDistribution distribution = seatCalculator.distribute(
                current.value(),
                parliament.getThresholdPercent().doubleValue(),
                parliament.thresholdExemptPartyIds(),
                Set.of(PARTY_SONSTIGE),
                total);

        List<Dtos.SeatEntryDto> entries = distribution.inParliament().stream()
                .map(id -> new Dtos.SeatEntryDto(id,
                        distribution.seats().getOrDefault(id, 0),
                        current.value().getOrDefault(id, 0.0)))
                .toList();

        return new Dtos.SeatsDto(
                distribution.totalSeats(), distribution.majority(), entries,
                round(distribution.failed(), 1),
                parliament.getThresholdPercent().doubleValue(),
                describeBasis(total, parliament.getThresholdPercent()));
    }

    /** Beim Europaparlament gibt es in Deutschland keine Huerde — das muss dastehen. */
    private static String describeBasis(int totalSeats, java.math.BigDecimal threshold) {
        if (threshold == null || threshold.signum() <= 0) {
            return "Sainte-Laguë auf %d Sitze, ohne Prozenthürde".formatted(totalSeats);
        }
        return "Sainte-Laguë auf %d Sitze, %s-%%-Hürde".formatted(
                totalSeats, threshold.stripTrailingZeros().toPlainString());
    }

    private List<Dtos.CoalitionDto> coalitionsDto(Dtos.SeatsDto seats, List<Party> relevant) {
        if (seats == null) {
            return List.of();
        }
        Map<Integer, Integer> seatsByParty = new LinkedHashMap<>();
        seats.entries().forEach(e -> seatsByParty.put(e.partyId(), e.seats()));
        Map<Integer, String> shortcuts = new HashMap<>();
        relevant.forEach(p -> shortcuts.put(p.getId(), p.getShortcut()));

        return coalitionFinder.find(seatsByParty, seats.majority(), shortcuts).stream()
                .filter(CoalitionFinder.Coalition::minimal)
                .map(c -> new Dtos.CoalitionDto(c.partyIds(), c.seats(), c.name(), c.minimal(),
                        c.seats() - seats.majority()))
                .toList();
    }

    private Dtos.ElectionDto electionDto(Election election, LocalDate today) {
        Map<Integer, Double> results = new LinkedHashMap<>();
        election.getResults().stream()
                .filter(r -> r.getKind() == ResultKind.AMTLICH)
                .sorted(Comparator.comparing(ElectionResult::getPercent).reversed())
                .forEach(r -> results.put(r.getParty().getId(), r.getPercent().doubleValue()));

        return new Dtos.ElectionDto(
                election.getElectionDate(),
                election.isDateConfirmed(),
                election.getStatus().name(),
                election.getTurnoutPercent() != null ? election.getTurnoutPercent().doubleValue() : null,
                election.getSeatsTotal(),
                election.getSourceUrl(),
                results,
                ChronoUnit.DAYS.between(today, election.getElectionDate()));
    }

    /**
     * Parteien, die im Zeitraum tatsaechlich vorkommen — sortiert nach ihrem
     * juengsten Wert, damit die Legende der Reihenfolge im Chart folgt.
     * "Sonstige" haengt immer hinten.
     */
    private List<Party> relevantParties(List<PollPoint> points, LocalDate from, LocalDate to, int limit) {
        List<PollPoint> window = inRange(points, from, to);
        Map<Integer, Double> latestValue = new LinkedHashMap<>();
        Map<Integer, Integer> occurrences = new LinkedHashMap<>();
        for (PollPoint point : window) {
            point.results().forEach((partyId, percent) -> {
                latestValue.putIfAbsent(partyId, percent);
                occurrences.merge(partyId, 1, Integer::sum);
            });
        }
        int total = Math.max(window.size(), 1);

        Set<Integer> keep = new LinkedHashSet<>();
        latestValue.entrySet().stream()
                .filter(e -> e.getKey() == PARTY_SONSTIGE
                        || (e.getValue() >= MIN_RELEVANT_PERCENT
                            && occurrences.getOrDefault(e.getKey(), 0) >= total * MIN_COVERAGE))
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(limit)
                .forEach(e -> keep.add(e.getKey()));

        Map<Integer, Party> byId = new HashMap<>();
        parties.findAllById(keep).forEach(p -> byId.put(p.getId(), p));

        List<Party> result = new ArrayList<>();
        keep.stream().filter(id -> id != PARTY_SONSTIGE)
                .map(byId::get).filter(java.util.Objects::nonNull).forEach(result::add);
        Party sonstige = byId.get(PARTY_SONSTIGE);
        if (sonstige != null) {
            result.add(sonstige);
        }
        return result;
    }

    /** Chart-Parteien plus die, die nur im letzten Wahlergebnis auftauchen. */
    private List<Party> withElectionParties(List<Party> relevant, Election lastElection) {
        if (lastElection == null || lastElection.getResults().isEmpty()) {
            return relevant;
        }
        Map<Integer, Party> byId = new LinkedHashMap<>();
        relevant.forEach(p -> byId.put(p.getId(), p));
        lastElection.getResults().stream()
                .map(ElectionResult::getParty)
                .forEach(p -> byId.putIfAbsent(p.getId(), p));
        return List.copyOf(byId.values());
    }

    private static List<PollPoint> inRange(List<PollPoint> points, LocalDate from, LocalDate to) {
        return points.stream()
                .filter(p -> !p.effectiveDate().isBefore(from) && !p.effectiveDate().isAfter(to))
                .toList();
    }

    private static Set<Integer> ids(List<Party> parties) {
        Set<Integer> ids = new LinkedHashSet<>();
        parties.forEach(p -> ids.add(p.getId()));
        return ids;
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static String lookup(Map<Integer, String> names, Integer id) {
        return id == null ? null : names.get(id);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static Map<Integer, Double> round(Map<Integer, Double> values, int decimals) {
        double factor = Math.pow(10, decimals);
        Map<Integer, Double> out = new LinkedHashMap<>();
        values.forEach((k, v) -> out.put(k, Math.round(v * factor) / factor));
        return out;
    }

    private Map<Integer, String> instituteNames() {
        Map<Integer, String> map = new HashMap<>();
        institutes.findAll().forEach(i -> map.put(i.getId(), i.getName()));
        return map;
    }

    private Map<Integer, String> taskerNames() {
        Map<Integer, String> map = new HashMap<>();
        taskers.findAll().forEach(t -> map.put(t.getId(), t.getName()));
        return map;
    }

    private Map<Integer, String> methodNames() {
        Map<Integer, String> map = new HashMap<>();
        methods.findAll().forEach(m -> map.put(m.getId(), m.getName()));
        return map;
    }
}
