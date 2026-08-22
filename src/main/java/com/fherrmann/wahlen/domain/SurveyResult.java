package com.fherrmann.wahlen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Ergebnis einer Partei in einer Umfrage, in Prozent. */
@Entity
@Table(name = "survey_result")
public class SurveyResult {

    @EmbeddedId
    private SurveyResultId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("surveyId")
    @JoinColumn(name = "survey_id")
    private Survey survey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("partyId")
    @JoinColumn(name = "party_id")
    private Party party;

    @Column(nullable = false)
    private BigDecimal percent;

    protected SurveyResult() {
    }

    public SurveyResult(Survey survey, Party party, BigDecimal percent) {
        this.id = new SurveyResultId(survey.getId(), party.getId());
        this.survey = survey;
        this.party = party;
        this.percent = percent;
    }

    public SurveyResultId getId() {
        return id;
    }

    public Survey getSurvey() {
        return survey;
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
}
