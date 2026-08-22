package com.fherrmann.wahlen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SurveyResultId implements Serializable {

    @Column(name = "survey_id")
    private Integer surveyId;

    @Column(name = "party_id")
    private Integer partyId;

    protected SurveyResultId() {
    }

    public SurveyResultId(Integer surveyId, Integer partyId) {
        this.surveyId = surveyId;
        this.partyId = partyId;
    }

    public Integer getSurveyId() {
        return surveyId;
    }

    public Integer getPartyId() {
        return partyId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SurveyResultId other
                && Objects.equals(surveyId, other.surveyId)
                && Objects.equals(partyId, other.partyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(surveyId, partyId);
    }
}
