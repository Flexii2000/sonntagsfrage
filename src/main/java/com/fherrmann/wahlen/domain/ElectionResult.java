package com.fherrmann.wahlen.domain;

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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ergebnis einer Partei bei einer Wahl.
 *
 * <p>{@code reportedAt} ist nur fuer Wahlabend-Staende (Prognose/Hochrechnung)
 * gesetzt und macht sie historisierbar; Endergebnisse lassen das Feld leer und
 * sind dadurch pro (Wahl, Partei, Art) eindeutig.
 */
@Entity
@Table(name = "election_result")
public class ElectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(nullable = false)
    private BigDecimal percent;

    private Integer seats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultKind kind = ResultKind.AMTLICH;

    @Column(name = "reported_at")
    private Instant reportedAt;

    private String source;

    protected ElectionResult() {
    }

    public ElectionResult(Election election, Party party, BigDecimal percent, ResultKind kind) {
        this.election = election;
        this.party = party;
        this.percent = percent;
        this.kind = kind;
    }

    public Long getId() {
        return id;
    }

    public Election getElection() {
        return election;
    }

    public Party getParty() {
        return party;
    }

    public BigDecimal getPercent() {
        return percent;
    }

    public void setPercent(BigDecimal percent) {
        this.percent = percent;
    }

    public Integer getSeats() {
        return seats;
    }

    public void setSeats(Integer seats) {
        this.seats = seats;
    }

    public ResultKind getKind() {
        return kind;
    }

    public void setKind(ResultKind kind) {
        this.kind = kind;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(Instant reportedAt) {
        this.reportedAt = reportedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
