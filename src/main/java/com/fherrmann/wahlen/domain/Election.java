package com.fherrmann.wahlen.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ein Wahltermin — vergangen oder anstehend. Quelle sind die kuratierten
 * Referenzdaten, nicht DAWUM.
 */
@Entity
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parliament_id", nullable = false)
    private Parliament parliament;

    @Column(name = "election_date", nullable = false)
    private LocalDate electionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ElectionStatus status = ElectionStatus.SCHEDULED;

    /**
     * false = der Termin ist noch nicht amtlich bekanntgegeben, das Datum ist
     * nur die beste Schaetzung (z.B. "Herbst 2029"). Solche Termine loesen
     * bewusst kein Featuring aus.
     */
    @Column(name = "date_confirmed", nullable = false)
    private boolean dateConfirmed = true;

    @Column(name = "turnout_percent")
    private BigDecimal turnoutPercent;

    @Column(name = "seats_total")
    private Integer seatsTotal;

    @Column(name = "source_url")
    private String sourceUrl;

    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ElectionResult> results = new ArrayList<>();

    protected Election() {
    }

    public Election(Parliament parliament, LocalDate electionDate, ElectionStatus status) {
        this.parliament = parliament;
        this.electionDate = electionDate;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Parliament getParliament() {
        return parliament;
    }

    public LocalDate getElectionDate() {
        return electionDate;
    }

    public void setElectionDate(LocalDate electionDate) {
        this.electionDate = electionDate;
    }

    public ElectionStatus getStatus() {
        return status;
    }

    public void setStatus(ElectionStatus status) {
        this.status = status;
    }

    public boolean isDateConfirmed() {
        return dateConfirmed;
    }

    public void setDateConfirmed(boolean dateConfirmed) {
        this.dateConfirmed = dateConfirmed;
    }

    public BigDecimal getTurnoutPercent() {
        return turnoutPercent;
    }

    public void setTurnoutPercent(BigDecimal turnoutPercent) {
        this.turnoutPercent = turnoutPercent;
    }

    public Integer getSeatsTotal() {
        return seatsTotal;
    }

    public void setSeatsTotal(Integer seatsTotal) {
        this.seatsTotal = seatsTotal;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public List<ElectionResult> getResults() {
        return results;
    }
}
