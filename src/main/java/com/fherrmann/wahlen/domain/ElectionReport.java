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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Ein Stand am Wahlabend: die 18-Uhr-Prognose, eine Hochrechnung, ein
 * Zwischenstand der Auszaehlung oder das vorlaeufige Ergebnis — mit seinen
 * Parteizeilen. Jeder Stand bleibt erhalten; so wird der Verlauf des Abends
 * spaeter nachlesbar.
 */
@Entity
@Table(name = "election_report")
public class ElectionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultKind kind;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "turnout_percent")
    private BigDecimal turnoutPercent;

    private String note;

    /** true: Sitze stammen aus der Quelle; false: sie werden projiziert. */
    @Column(name = "seats_official", nullable = false)
    private boolean seatsOfficial;

    /** Inhaltshash, damit ein unveraenderter Abruf keinen neuen Stand erzeugt. */
    private String fingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ElectionResult> results = new ArrayList<>();

    protected ElectionReport() {
    }

    public ElectionReport(Election election, ResultKind kind, Instant reportedAt, String source) {
        this.election = election;
        this.kind = kind;
        this.reportedAt = reportedAt;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Election getElection() {
        return election;
    }

    public ResultKind getKind() {
        return kind;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public String getSource() {
        return source;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public BigDecimal getTurnoutPercent() {
        return turnoutPercent;
    }

    public void setTurnoutPercent(BigDecimal turnoutPercent) {
        this.turnoutPercent = turnoutPercent;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isSeatsOfficial() {
        return seatsOfficial;
    }

    public void setSeatsOfficial(boolean seatsOfficial) {
        this.seatsOfficial = seatsOfficial;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ElectionResult> getResults() {
        return results;
    }
}
