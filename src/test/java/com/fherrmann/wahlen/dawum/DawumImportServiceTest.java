package com.fherrmann.wahlen.dawum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DawumImportServiceTest {

    private static DawumPayload.Survey survey(Map<String, Double> results) {
        return new DawumPayload.Survey(
                "2026-08-21",
                new DawumPayload.Period("2026-08-12", "2026-08-17"),
                "1276", "0", "22", "67", "3", results);
    }

    @Test
    @DisplayName("Fingerabdruck ist unabhaengig von der Reihenfolge der Ergebnisse")
    void fingerprintIgnoresResultOrder() {
        Map<String, Double> a = new LinkedHashMap<>();
        a.put("7", 28.0);
        a.put("1", 20.0);
        a.put("2", 15.0);

        Map<String, Double> b = new LinkedHashMap<>();
        b.put("2", 15.0);
        b.put("7", 28.0);
        b.put("1", 20.0);

        assertThat(DawumImportService.fingerprint(survey(a)))
                .isEqualTo(DawumImportService.fingerprint(survey(b)));
    }

    @Test
    @DisplayName("Fingerabdruck aendert sich, wenn sich ein Wert aendert")
    void fingerprintReactsToChangedValue() {
        String before = DawumImportService.fingerprint(survey(Map.of("7", 28.0)));
        String after = DawumImportService.fingerprint(survey(Map.of("7", 28.5)));

        assertThat(before).isNotEqualTo(after);
    }

    @Test
    @DisplayName("Fingerabdruck aendert sich, wenn eine Partei dazukommt")
    void fingerprintReactsToNewParty() {
        Map<String, Double> more = new LinkedHashMap<>();
        more.put("7", 28.0);
        more.put("1", 20.0);

        assertThat(DawumImportService.fingerprint(survey(Map.of("7", 28.0))))
                .isNotEqualTo(DawumImportService.fingerprint(survey(more)));
    }

    @Test
    @DisplayName("Fingerabdruck kommt mit fehlendem Erhebungszeitraum klar")
    void fingerprintHandlesMissingPeriod() {
        DawumPayload.Survey withoutPeriod = new DawumPayload.Survey(
                "2026-08-21", null, "1276", "0", "22", "67", "3", Map.of("7", 28.0));

        assertThat(DawumImportService.fingerprint(withoutPeriod)).isNotBlank();
    }
}
