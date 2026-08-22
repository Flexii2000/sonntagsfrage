package com.fherrmann.wahlen.service;

import com.fherrmann.wahlen.config.WahlenProperties;
import com.fherrmann.wahlen.domain.Election;
import com.fherrmann.wahlen.domain.ElectionStatus;
import com.fherrmann.wahlen.repository.ElectionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Wahlkalender und die Frage, welches Parlament gerade das interessanteste ist.
 */
@Service
public class ElectionCalendarService {

    private final ElectionRepository elections;
    private final WahlenProperties properties;
    private final Clock clock;

    public ElectionCalendarService(ElectionRepository elections,
                                   WahlenProperties properties,
                                   Clock clock) {
        this.elections = elections;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Warum ein Parlament auf der Startseite steht — wird im UI als Begruendung
     * ausgegeben, damit die Auswahl nicht willkuerlich wirkt.
     */
    public enum FeatureReason {
        /** Wahl steht unmittelbar bevor. */
        UPCOMING_ELECTION,
        /** Wahl war gerade. */
        RECENT_ELECTION,
        /** Nichts Besonderes los — Bundestag. */
        DEFAULT
    }

    public record Featured(Integer parliamentId, FeatureReason reason, Election election) {
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Transactional(readOnly = true)
    public List<Election> all() {
        return elections.findAllWithParliament();
    }

    /** Die letzte stattgefundene Wahl eines Parlaments. */
    public Optional<Election> lastElection(Integer parliamentId, List<Election> all) {
        LocalDate today = today();
        return all.stream()
                .filter(e -> e.getParliament().getId().equals(parliamentId))
                .filter(e -> e.getStatus() == ElectionStatus.HELD || !e.getElectionDate().isAfter(today))
                .max(Comparator.comparing(Election::getElectionDate));
    }

    /** Die naechste anstehende Wahl eines Parlaments. */
    public Optional<Election> nextElection(Integer parliamentId, List<Election> all) {
        LocalDate today = today();
        return all.stream()
                .filter(e -> e.getParliament().getId().equals(parliamentId))
                .filter(e -> e.getElectionDate().isAfter(today))
                .min(Comparator.comparing(Election::getElectionDate));
    }

    /**
     * Welches Parlament gehoert auf die Startseite?
     *
     * <ol>
     *   <li>Wahl innerhalb der naechsten {@code preElectionDays} Tage — die naechstliegende</li>
     *   <li>Wahl innerhalb der letzten {@code postElectionDays} Tage — die juengste</li>
     *   <li>sonst: Bundestag</li>
     * </ol>
     *
     * <p>Termine, die noch nicht amtlich bekanntgegeben sind
     * ({@code dateConfirmed = false}), loesen bewusst kein Featuring aus — sonst
     * wuerde eine grobe Schaetzung wie "Herbst 2029" die Startseite kapern.
     *
     * <p>Fallen zwei Wahlen auf denselben Tag (2026 etwa Berlin und
     * Mecklenburg-Vorpommern am 20.09.), gewinnt das groessere Parlament; die
     * andere Wahl bleibt ueber den Wahlkalender direkt daneben erreichbar.
     */
    @Transactional(readOnly = true)
    public Featured featured() {
        List<Election> all = all();
        LocalDate today = today();

        Optional<Election> upcoming = all.stream()
                .filter(Election::isDateConfirmed)
                .filter(e -> !e.getElectionDate().isBefore(today))
                .filter(e -> ChronoUnit.DAYS.between(today, e.getElectionDate())
                        <= properties.featured().preElectionDays())
                .min(Comparator.comparing(Election::getElectionDate)
                        .thenComparing(this::parliamentSize, Comparator.reverseOrder()));
        if (upcoming.isPresent()) {
            return new Featured(upcoming.get().getParliament().getId(),
                    FeatureReason.UPCOMING_ELECTION, upcoming.get());
        }

        Optional<Election> recent = all.stream()
                .filter(e -> e.getElectionDate().isBefore(today))
                .filter(e -> ChronoUnit.DAYS.between(e.getElectionDate(), today)
                        <= properties.featured().postElectionDays())
                .max(Comparator.comparing(Election::getElectionDate)
                        .thenComparing(this::parliamentSize));
        if (recent.isPresent()) {
            return new Featured(recent.get().getParliament().getId(),
                    FeatureReason.RECENT_ELECTION, recent.get());
        }

        return new Featured(0, FeatureReason.DEFAULT, null);
    }

    private int parliamentSize(Election election) {
        Integer seats = election.getParliament().getSeatsTotal();
        return seats != null ? seats : 0;
    }
}
