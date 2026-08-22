package com.fherrmann.wahlen.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Eine einzelne Umfrage. ID stammt aus der DAWUM-API. */
@Entity
public class Survey {

    @Id
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parliament_id", nullable = false)
    private Parliament parliament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institute_id")
    private Institute institute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tasker_id")
    private Tasker tasker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "method_id")
    private Method method;

    /** Veroeffentlichungsdatum. */
    @Column(name = "published_on", nullable = false)
    private LocalDate publishedOn;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "surveyed_persons")
    private Integer surveyedPersons;

    /** Fingerabdruck aller fachlichen Felder; siehe DawumImportService. */
    @Column(name = "content_hash")
    private String contentHash;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SurveyResult> results = new ArrayList<>();

    protected Survey() {
    }

    public Survey(Integer id, Parliament parliament, LocalDate publishedOn) {
        this.id = id;
        this.parliament = parliament;
        this.publishedOn = publishedOn;
    }

    public Integer getId() {
        return id;
    }

    public Parliament getParliament() {
        return parliament;
    }

    public void setParliament(Parliament parliament) {
        this.parliament = parliament;
    }

    public Institute getInstitute() {
        return institute;
    }

    public void setInstitute(Institute institute) {
        this.institute = institute;
    }

    public Tasker getTasker() {
        return tasker;
    }

    public void setTasker(Tasker tasker) {
        this.tasker = tasker;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }

    public void setPublishedOn(LocalDate publishedOn) {
        this.publishedOn = publishedOn;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Integer getSurveyedPersons() {
        return surveyedPersons;
    }

    public void setSurveyedPersons(Integer surveyedPersons) {
        this.surveyedPersons = surveyedPersons;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public List<SurveyResult> getResults() {
        return results;
    }
}
