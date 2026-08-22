package com.fherrmann.wahlen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;

/**
 * Ein Parlament (Bundestag, Landtag, Europaparlament).
 * Die ID stammt aus der DAWUM-API und wird uebernommen.
 */
@Entity
public class Parliament {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String shortcut;

    @Column(nullable = false)
    private String name;

    @Column(name = "election_name", nullable = false)
    private String electionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParliamentLevel level = ParliamentLevel.LAND;

    /** Sperrklausel in Prozent. */
    @Column(name = "threshold_percent", nullable = false)
    private BigDecimal thresholdPercent = new BigDecimal("5.00");

    /** Regulaere Groesse des Parlaments, Basis der Sitzprojektion. */
    @Column(name = "seats_total")
    private Integer seatsTotal;

    /** Komma-separierte Party-IDs, die von der Sperrklausel befreit sind. */
    @Column(name = "threshold_exempt")
    private String thresholdExempt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    protected Parliament() {
    }

    public Parliament(Integer id, String slug, String shortcut, String name, String electionName) {
        this.id = id;
        this.slug = slug;
        this.shortcut = shortcut;
        this.name = name;
        this.electionName = electionName;
    }

    public Integer getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getShortcut() {
        return shortcut;
    }

    public void setShortcut(String shortcut) {
        this.shortcut = shortcut;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getElectionName() {
        return electionName;
    }

    public void setElectionName(String electionName) {
        this.electionName = electionName;
    }

    public ParliamentLevel getLevel() {
        return level;
    }

    public void setLevel(ParliamentLevel level) {
        this.level = level;
    }

    public BigDecimal getThresholdPercent() {
        return thresholdPercent;
    }

    public void setThresholdPercent(BigDecimal thresholdPercent) {
        this.thresholdPercent = thresholdPercent;
    }

    public Integer getSeatsTotal() {
        return seatsTotal;
    }

    public void setSeatsTotal(Integer seatsTotal) {
        this.seatsTotal = seatsTotal;
    }

    public String getThresholdExempt() {
        return thresholdExempt;
    }

    public void setThresholdExempt(String thresholdExempt) {
        this.thresholdExempt = thresholdExempt;
    }

    /** Party-IDs, die trotz Ergebnis unter der Sperrklausel einziehen. */
    public java.util.Set<Integer> thresholdExemptPartyIds() {
        if (thresholdExempt == null || thresholdExempt.isBlank()) {
            return java.util.Set.of();
        }
        return java.util.Arrays.stream(thresholdExempt.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
