package com.fherrmann.wahlen.reference;

import java.util.List;
import java.util.Map;

/** Abbild der YAML-Dateien unter {@code src/main/resources/reference/}. */
public final class ReferenceData {

    private ReferenceData() {
    }

    public record ParliamentsFile(List<ParliamentRef> parliaments) {
    }

    public record ParliamentRef(
            Integer id,
            String slug,
            String level,
            Double threshold,
            Integer seats,
            List<String> thresholdExempt,
            Integer order) {
    }

    public record PartiesFile(List<PartyRef> parties) {
    }

    public record PartyRef(String shortcut, String light, String dark,
                           Integer order, Integer spectrum) {
    }

    public record ElectionsFile(List<ElectionRef> elections) {
    }

    public record ElectionRef(
            String parliament,
            String date,
            String status,
            Boolean dateConfirmed,
            Double turnout,
            Integer seats,
            String source,
            Map<String, Double> results) {
    }
}
