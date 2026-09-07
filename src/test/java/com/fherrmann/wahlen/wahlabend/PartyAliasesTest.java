package com.fherrmann.wahlen.wahlabend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PartyAliasesTest {

    @Test
    @DisplayName("Schreibweisen der Landeswahlleitungen landen beim DAWUM-Kuerzel")
    void mapsOfficialSpellings() {
        assertThat(PartyAliases.canonical("Die Linke")).isEqualTo("Linke");
        assertThat(PartyAliases.canonical("DIE LINKE")).isEqualTo("Linke");
        assertThat(PartyAliases.canonical("GRÜNE")).isEqualTo("Grüne");
        assertThat(PartyAliases.canonical("FREIE WÄHLER")).isEqualTo("Freie Wähler");
        assertThat(PartyAliases.canonical("FREIE  WÄHLER")).isEqualTo("Freie Wähler");
        assertThat(PartyAliases.canonical("PIRATEN")).isEqualTo("Piraten");
    }

    @Test
    @DisplayName("bekannte Kuerzel bleiben, wie sie sind")
    void keepsCanonicalNames() {
        assertThat(PartyAliases.canonical("AfD")).isEqualTo("AfD");
        assertThat(PartyAliases.canonical("CDU")).isEqualTo("CDU");
        assertThat(PartyAliases.canonical("BSW")).isEqualTo("BSW");
        assertThat(PartyAliases.canonical(" SPD ")).isEqualTo("SPD");
    }

    @Test
    @DisplayName("Zuordnungen der Quelle gehen vor")
    void sourceAliasesWin() {
        assertThat(PartyAliases.canonical("CSU", Map.of("CSU", "CDU/CSU"))).isEqualTo("CDU/CSU");
    }

    @Test
    @DisplayName("Vergleichsform ignoriert Gross-/Kleinschreibung, Umlaute und Leerzeichen")
    void normalizes() {
        assertThat(PartyAliases.normalize("Freie Wähler")).isEqualTo(PartyAliases.normalize("FREIE WAEHLER"));
        assertThat(PartyAliases.normalize("Grüne")).isEqualTo("gruene");
        assertThat(PartyAliases.normalize("Die PARTEI")).isEqualTo("diepartei");
    }
}
