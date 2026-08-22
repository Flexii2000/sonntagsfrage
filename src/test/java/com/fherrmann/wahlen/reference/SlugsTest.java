package com.fherrmann.wahlen.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    @DisplayName("wirft Klammerzusaetze weg")
    void dropsParentheses() {
        assertThat(Slugs.slugify("Nordrhein-Westfalen (NRW)")).isEqualTo("nordrhein-westfalen");
    }

    @Test
    @DisplayName("schreibt Umlaute aus")
    void transliteratesUmlauts() {
        assertThat(Slugs.slugify("Europäisches Parlament")).isEqualTo("europaeisches-parlament");
        assertThat(Slugs.slugify("Thüringen")).isEqualTo("thueringen");
        assertThat(Slugs.slugify("Baden-Württemberg")).isEqualTo("baden-wuerttemberg");
    }

    @Test
    @DisplayName("faellt bei leerer Eingabe nicht um")
    void handlesBlankInput() {
        assertThat(Slugs.slugify(null)).isEqualTo("unbekannt");
        assertThat(Slugs.slugify("   ")).isEqualTo("unbekannt");
        assertThat(Slugs.slugify("(...)")).isEqualTo("unbekannt");
    }
}
