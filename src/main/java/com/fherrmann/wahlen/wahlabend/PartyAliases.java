package com.fherrmann.wahlen.wahlabend;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Uebersetzt Parteinamen, wie Landeswahlleitungen und Fernsehsender sie
 * schreiben, in die DAWUM-Kuerzel dieser Datenbank.
 *
 * <p>Die Statistischen Landesaemter schreiben "Die Linke", "GRÜNE" oder
 * "FREIE WÄHLER"; DAWUM fuehrt dieselben Parteien als "Linke", "Grüne" und
 * "Freie Wähler". Der Vergleich laeuft ueber eine normalisierte Form (klein,
 * ohne Umlaute, ohne Leerzeichen), damit Schreibvarianten nicht auffallen.
 */
public final class PartyAliases {

    private static final Map<String, String> BUILT_IN = new HashMap<>();

    static {
        alias("Linke", "Die Linke", "DIE LINKE", "LINKE", "Linke.", "DIE LINKE.");
        alias("Grüne", "GRÜNE", "Gruene", "B90/GRÜNE", "GRÜNE/B 90", "Bündnis 90/Die Grünen",
                "BÜNDNIS 90/DIE GRÜNEN", "B90/Grüne", "Grüne/B90");
        alias("Freie Wähler", "FREIE WÄHLER", "FW", "Freie Waehler", "FREIE  WÄHLER");
        alias("CDU/CSU", "Union");
        alias("Piraten", "PIRATEN", "Piratenpartei");
        alias("Die PARTEI", "PARTEI");
        alias("Tierschutzpartei", "Tierschutz", "Partei Mensch Umwelt Tierschutz");
        alias("ÖDP", "OEDP", "ÖDP / Familie");
        alias("Familie", "Familien-Partei", "FAMILIE");
        alias("BVB/FW", "BVB / FREIE WÄHLER", "BVB/FREIE WÄHLER");
        alias("Sonstige", "Andere", "Sonst.", "Son", "Übrige", "Sonstige Parteien");
    }

    private PartyAliases() {
    }

    private static void alias(String canonical, String... variants) {
        BUILT_IN.put(normalize(canonical), canonical);
        for (String v : variants) {
            BUILT_IN.put(normalize(v), canonical);
        }
    }

    /**
     * Vergleichsform: Kleinbuchstaben, Umlaute aufgeloest, ohne Leer- und
     * Sonderzeichen. "FREIE WÄHLER" und "Freie Wähler" werden so identisch.
     */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim().toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[^a-z0-9]", "");
    }

    /**
     * Kanonisches Kuerzel fuer einen Quellnamen. Zuerst die Zuordnungen des
     * Aufrufers, dann die eingebauten; sonst der Name selbst (getrimmt).
     */
    public static String canonical(String sourceName, Map<String, String> extra) {
        if (sourceName == null) {
            return null;
        }
        String key = normalize(sourceName);
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                if (normalize(e.getKey()).equals(key)) {
                    return e.getValue();
                }
            }
        }
        return BUILT_IN.getOrDefault(key, sourceName.trim());
    }

    public static String canonical(String sourceName) {
        return canonical(sourceName, null);
    }
}
