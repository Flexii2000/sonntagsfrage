package com.fherrmann.wahlen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Eine Partei. ID stammt aus der DAWUM-API. */
@Entity
public class Party {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String shortcut;

    @Column(nullable = false)
    private String name;

    /** Parteifarbe fuer den hellen Modus (#rrggbb). */
    @Column(name = "color_light")
    private String colorLight;

    /** Parteifarbe fuer den dunklen Modus — Schwarz und Gelb brauchen hier
     *  zwingend eigene Werte, sonst verschwinden sie im Hintergrund. */
    @Column(name = "color_dark")
    private String colorDark;

    /** Position von links nach rechts im Sitzbogen — Konvention, keine Wertung. */
    @Column(nullable = false)
    private int spectrum = 99;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    protected Party() {
    }

    public Party(Integer id, String shortcut, String name) {
        this.id = id;
        this.shortcut = shortcut;
        this.name = name;
    }

    public Integer getId() {
        return id;
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

    public String getColorLight() {
        return colorLight;
    }

    public void setColorLight(String colorLight) {
        this.colorLight = colorLight;
    }

    public String getColorDark() {
        return colorDark;
    }

    public void setColorDark(String colorDark) {
        this.colorDark = colorDark;
    }

    public int getSpectrum() {
        return spectrum;
    }

    public void setSpectrum(int spectrum) {
        this.spectrum = spectrum;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
