package com.fherrmann.wahlen.dawum;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Abbild der DAWUM-API-Antwort ({@code https://api.dawum.de/}).
 *
 * <p>Die API liefert alle Entitaeten als Objekte, deren Schluessel die IDs
 * sind — deshalb ueberall {@code Map<String, ...>} statt Listen.
 */
public record DawumPayload(
        @JsonProperty("Database") Database database,
        @JsonProperty("Parliaments") Map<String, Parliament> parliaments,
        @JsonProperty("Institutes") Map<String, Named> institutes,
        @JsonProperty("Taskers") Map<String, Named> taskers,
        @JsonProperty("Methods") Map<String, Named> methods,
        @JsonProperty("Parties") Map<String, Party> parties,
        @JsonProperty("Surveys") Map<String, Survey> surveys) {

    public record Database(
            @JsonProperty("License") License license,
            @JsonProperty("Publisher") String publisher,
            @JsonProperty("Author") String author,
            @JsonProperty("Last_Update") String lastUpdate) {
    }

    public record License(
            @JsonProperty("Name") String name,
            @JsonProperty("Shortcut") String shortcut,
            @JsonProperty("Link") String link) {
    }

    public record Parliament(
            @JsonProperty("Shortcut") String shortcut,
            @JsonProperty("Name") String name,
            @JsonProperty("Election") String election) {
    }

    public record Party(
            @JsonProperty("Shortcut") String shortcut,
            @JsonProperty("Name") String name) {
    }

    public record Named(@JsonProperty("Name") String name) {
    }

    public record Survey(
            @JsonProperty("Date") String date,
            @JsonProperty("Survey_Period") Period surveyPeriod,
            @JsonProperty("Surveyed_Persons") String surveyedPersons,
            @JsonProperty("Parliament_ID") String parliamentId,
            @JsonProperty("Institute_ID") String instituteId,
            @JsonProperty("Tasker_ID") String taskerId,
            @JsonProperty("Method_ID") String methodId,
            @JsonProperty("Results") Map<String, Double> results) {
    }

    public record Period(
            @JsonProperty("Date_Start") String dateStart,
            @JsonProperty("Date_End") String dateEnd) {
    }
}
