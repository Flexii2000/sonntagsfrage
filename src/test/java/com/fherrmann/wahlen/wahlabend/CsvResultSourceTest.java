package com.fherrmann.wahlen.wahlabend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fherrmann.wahlen.reference.ReferenceData.LiveSourceRef;
import com.fherrmann.wahlen.reference.ReferenceData.SeatsSourceRef;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsvResultSourceTest {

    /** Gekuerzte Kopie der echten Landesdatei von Sachsen-Anhalt (LT 2026). */
    private static final String SACHSEN_ANHALT = """
            "Ergebnisart";"Datum";"Satzart";"Schlüsselnummer";"Name";"Wahllokal";"A.Wahlberechtigte";"B.Wähler";"E.Ungültige.Zweitstimmen";"F.Gültige.Zweitstimmen";"F01.CDU";"F02.AfD";"F03.Die Linke";"F04.SPD";"F05.FDP";"F06.GRÜNE";"F07.FREIE WÄHLER";"F08.dieBasis";"F15.BSW";"C.Ungültige.Erststimmen";"D.Gültige.Erststimmen";"D01.CDU";"D02.AfD"
            "V";"07.09.2026";"LAN";"15";"Sachsen-Anhalt";"U";1706851;964460;10932;953528;138714;488221;72633;73339;21960;70937;10523;1434;47710;12304;952156;195009;494902
            "V";"07.09.2026";"LAN";"15";"Sachsen-Anhalt";"B";;363751;1964;361787;87908;87816;39908;48964;12009;46561;4907;465;21581;3173;360578;119526;87043
            "V";"07.09.2026";"LAN";"15";"Sachsen-Anhalt";"";1706851;1328211;12896;1315315;226622;576037;112541;122303;33969;117498;15430;1899;69291;15477;1312734;314535;581945
            "V";"06.09.2026";"KRS";"15001";"Dessau-Roßlau, Stadt";"";61152;47083;473;46610;8551;18134;3953;4709;1452;4937;455;52;2963;669;46414;13407;19125
            """;

    /** Aufbau der MV-Datei: Titelzeilen, dann Kopf; Land ist Wahlkreis 99, Ausgabe A = absolut. */
    private static final String MECKLENBURG = """
            Wahl zum Landtag von Mecklenburg-Vorpommern am 20. September 2026
            Zwischenergebnis der Wahlkreise am 20.09.2026 um 19:00:00 Uhr - Stimmenanzahl der Parteien
            (c) Der Landeswahlleiter Mecklenburg-Vorpommern

            Berechnungsdatum;Ausgabe;Wahlkreis;Wahlkreisname/Land;Wahlbezirke insg.;Erf. Wahlbezirke;Wahlberechtigte;Wähler;Wahlbeteiligung;Erst-/Zweitstimme;Ungültige Stimmen;Gültige Stimmen;SPD;AfD;CDU;Die Linke;GRÜNE;FDP;BSW;Einzelbewerber
            20.09.2026 19:45:00;A;1;Greifswald;60;30;45611;20000;43,8;2;300;19700;6000;5000;4000;2000;1500;700;500;x
            20.09.2026 19:45:00;A;99;Mecklenburg-Vorpommern;1974;987;1312471;600000;45,7;1;9000;591000;200000;150000;120000;60000;30000;20000;11000;x
            20.09.2026 19:45:00;P;99;Mecklenburg-Vorpommern;1974;987;1312471;600000;45,7;2;1,5;98,5;33,3;25,0;20,0;10,0;5,0;3,3;1,7;x
            20.09.2026 19:45:00;A;99;Mecklenburg-Vorpommern;1974;987;1312471;600000;45,7;2;9000;591000;200000;150000;120000;60000;30000;20000;11000;x
            """;

    private static LiveSourceRef sachsenAnhalt() {
        return new LiveSourceRef("csv", "Landeswahlleiterin Sachsen-Anhalt", "https://example/land.csv", null,
                Map.of("Satzart", "LAN", "Wahllokal", ""),
                "F.Gültige.Zweitstimmen", "B.Wähler", "A.Wahlberechtigte", null,
                "^F\\d+\\.(.+)$", null, null, null,
                null, null, null, null, null, null, null);
    }

    private static LiveSourceRef mecklenburg() {
        return new LiveSourceRef("csv", "Landeswahlleiter MV", "https://example/l_wahlkreise.csv", "ISO-8859-1",
                Map.of("Wahlkreis", "99", "Ausgabe", "A", "Erst-/Zweitstimme", "2"),
                "Gültige Stimmen", "Wähler", "Wahlberechtigte", "Wahlbeteiligung",
                null, "Gültige Stimmen", List.of("Einzelbewerber"), null,
                "Wahlbezirke insg.", "Erf. Wahlbezirke", "Berechnungsdatum", "dd.MM.yyyy HH:mm:ss",
                null, null, null);
    }

    @Test
    @DisplayName("Sachsen-Anhalt: Landeszeile, Prozent aus Zweitstimmen, Beteiligung aus Waehlern")
    void parsesSachsenAnhalt() {
        Instant lastModified = Instant.parse("2026-09-07T01:26:14Z");
        CsvResultSource.Parsed parsed = CsvResultSource.parseResults(SACHSEN_ANHALT, sachsenAnhalt(), lastModified);

        assertThat(parsed.percent().get("AfD")).isCloseTo(43.79, within(0.01));
        assertThat(parsed.percent().get("CDU")).isCloseTo(17.23, within(0.01));
        assertThat(parsed.percent().get("Die Linke")).isCloseTo(8.56, within(0.01));
        assertThat(parsed.percent().get("BSW")).isCloseTo(5.27, within(0.01));
        // Erststimmen-Spalten (D..) duerfen nicht als Parteien durchgehen.
        assertThat(parsed.percent()).doesNotContainKey("D01.CDU");
        assertThat(parsed.turnout()).isCloseTo(77.8, within(0.05));
        assertThat(parsed.timestamp()).isEqualTo(lastModified);
        assertThat(parsed.complete()).isFalse();
    }

    @Test
    @DisplayName("MV: Titelzeilen ueberspringen, Landeszeile 99/A/2, Fortschritt und Zeitstempel lesen")
    void parsesMecklenburg() {
        CsvResultSource.Parsed parsed = CsvResultSource.parseResults(MECKLENBURG, mecklenburg(), null);

        assertThat(parsed.percent().get("SPD")).isCloseTo(33.84, within(0.01));
        assertThat(parsed.percent().get("Die Linke")).isCloseTo(10.15, within(0.01));
        assertThat(parsed.percent()).doesNotContainKey("Einzelbewerber");
        assertThat(parsed.turnout()).isCloseTo(45.7, within(0.01));
        assertThat(parsed.districtsCounted()).isEqualTo(987);
        assertThat(parsed.districtsTotal()).isEqualTo(1974);
        assertThat(parsed.complete()).isFalse();
        assertThat(parsed.timestamp()).isEqualTo(
                ZonedDateTime.of(2026, 9, 20, 19, 45, 0, 0, WahlabendClock.BERLIN).toInstant());
    }

    @Test
    @DisplayName("MV: alle Wahlbezirke erfasst = vollstaendig")
    void detectsCompleteCount() {
        String complete = MECKLENBURG.replace(";1974;987;", ";1974;1974;");
        assertThat(CsvResultSource.parseResults(complete, mecklenburg(), null).complete()).isTrue();
    }

    @Test
    @DisplayName("ohne gueltige Stimmen gibt es keinen Stand — vor 18 Uhr ist die Datei leer")
    void emptyBeforeCounting() {
        String empty = MECKLENBURG.replace(";9000;591000;200000;150000;120000;60000;30000;20000;11000;x",
                ";0;0;0;0;0;0;0;0;0;x");
        assertThat(CsvResultSource.parseResults(empty, mecklenburg(), null)).isNull();
    }

    @Test
    @DisplayName("Sitze lang (eine Zeile je Partei) und breit (Parteien als Spalten)")
    void parsesSeatsBothLayouts() {
        String longFormat = """
                "Nummer.Landeswahlvorschlag";"Partei";"Sitze.insgesamt";"Kreiswahlvorschlaege";"Landeswahlvorschlaege"
                "1";"CDU";15;0;15
                "2";"AfD";39;38;1
                "7";"FDP";0;0;0
                ;"Insgesamt";83;41;42
                """;
        Map<String, Integer> seats = CsvResultSource.parseSeats(longFormat,
                new SeatsSourceRef("u", null, null, null, null, "Partei", "Sitze.insgesamt"));
        assertThat(seats).containsEntry("CDU", 15).containsEntry("AfD", 39).containsEntry("FDP", 0)
                .doesNotContainKey("Insgesamt");

        String wideFormat = """
                Titel
                Berechnungsdatum;Sort;Wahlkreis;Wahlkreisname/Land;Mandatstyp;SPD;AfD;CDU;Einzelbewerber
                20.09.2026 23:00:00;101;99;Mecklenburg-Vorpommern;Mandate nach Landesliste;0;13;11;0
                20.09.2026 23:00:00;103;99;Mecklenburg-Vorpommern;Insgesamt;34;14;12;0
                """;
        Map<String, Integer> wide = CsvResultSource.parseSeats(wideFormat,
                new SeatsSourceRef("u", null, Map.of("Wahlkreis", "99", "Mandatstyp", "Insgesamt"),
                        "Mandatstyp", List.of("Einzelbewerber"), null, null));
        assertThat(wide).containsEntry("SPD", 34).containsEntry("AfD", 14).containsEntry("CDU", 12)
                .doesNotContainKey("Einzelbewerber");
    }

    @Test
    @DisplayName("Zahlen: deutsche Dezimaltrennung, Tausenderpunkte, x fuer nicht angetreten")
    void parsesNumbers() {
        assertThat(CsvResultSource.number("72,1")).isEqualTo(72.1);
        assertThat(CsvResultSource.number("1.234")).isEqualTo(1234.0);
        assertThat(CsvResultSource.number("1.234,5")).isEqualTo(1234.5);
        assertThat(CsvResultSource.number("43.79")).isEqualTo(43.79);
        assertThat(CsvResultSource.number("x")).isNull();
        assertThat(CsvResultSource.number("")).isNull();
        assertThat(CsvResultSource.number(null)).isNull();
    }
}
