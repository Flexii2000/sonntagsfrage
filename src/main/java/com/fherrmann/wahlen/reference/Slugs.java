package com.fherrmann.wahlen.reference;

import java.text.Normalizer;
import java.util.Locale;

/** URL-Slugs aus deutschen Parlamentsnamen. */
public final class Slugs {

    private Slugs() {
    }

    /**
     * {@code "Nordrhein-Westfalen (NRW)"} → {@code "nordrhein-westfalen"},
     * {@code "Europäisches Parlament"} → {@code "europaeisches-parlament"}.
     *
     * <p>Wird nur als Fallback gebraucht: fuer die bekannten Parlamente stehen
     * die Slugs in {@code reference/parliaments.yaml}.
     */
    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "unbekannt";
        }
        String s = input.replaceAll("\\(.*?\\)", " ")
                .toLowerCase(Locale.GERMAN)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isBlank() ? "unbekannt" : s;
    }
}
