package com.fherrmann.wahlen.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Findet alle Parteikombinationen, die rechnerisch eine Mehrheit haetten.
 *
 * <p><b>Bewusst wertfrei:</b> es wird nicht gefiltert, wer mit wem koalieren
 * will oder ausschliesst. Die Frage, die diese Rechnung beantwortet, ist
 * ausschliesslich "was ginge arithmetisch" — alles andere waere eine politische
 * Einschaetzung, die eine Umfrageseite nicht treffen sollte.
 */
@Component
public class CoalitionFinder {

    /** Mehr Parteien im Parlament als hier werden nicht kombinatorisch durchgerechnet. */
    private static final int MAX_PARTIES = 12;

    /**
     * @param partyIds Beteiligte
     * @param seats    Sitze zusammen
     * @param name     gaengiger Name, sonst aus den Parteifarben zusammengesetzt
     * @param minimal  true, wenn keine Partei weggelassen werden kann, ohne die
     *                 Mehrheit zu verlieren
     */
    public record Coalition(List<Integer> partyIds, int seats, String name, boolean minimal) {
    }

    public List<Coalition> find(Map<Integer, Integer> seatsByParty,
                                int majority,
                                Map<Integer, String> shortcutsByPartyId) {
        List<Integer> parties = seatsByParty.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(MAX_PARTIES)
                .toList();

        List<Coalition> result = new ArrayList<>();
        int n = parties.size();
        for (int mask = 1; mask < (1 << n); mask++) {
            int seats = 0;
            List<Integer> members = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    members.add(parties.get(i));
                    seats += seatsByParty.get(parties.get(i));
                }
            }
            if (seats < majority) {
                continue;
            }
            boolean minimal = true;
            for (Integer member : members) {
                if (seats - seatsByParty.get(member) >= majority) {
                    minimal = false;
                    break;
                }
            }
            result.add(new Coalition(members, seats,
                    CoalitionNames.nameFor(members, shortcutsByPartyId), minimal));
        }

        result.sort(Comparator
                .comparing(Coalition::minimal).reversed()
                .thenComparingInt((Coalition c) -> c.partyIds().size())
                .thenComparing(Comparator.comparingInt(Coalition::seats).reversed()));
        return disambiguate(result, shortcutsByPartyId);
    }

    /**
     * Zwei Buendnisse mit demselben Namen in einer Liste sehen aus wie ein Fehler.
     *
     * <p>Passiert, seit SPD und Linke beide als "Rot" gelten: AfD+Gruene+SPD und
     * AfD+Gruene+Linke ergeben beide "Blau-Gruen-Rot". Die etablierten Namen
     * (Rot-Rot, Rot-Rot-Gruen) sind es wert; fuer die frei zusammengesetzten
     * Faelle gilt dann lieber die Parteiliste als ein doppelter Farbname.
     */
    private static List<Coalition> disambiguate(List<Coalition> coalitions,
                                                Map<Integer, String> shortcuts) {
        Map<String, Long> occurrences = coalitions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Coalition::name, java.util.stream.Collectors.counting()));

        List<Coalition> out = new ArrayList<>(coalitions.size());
        for (Coalition c : coalitions) {
            if (occurrences.getOrDefault(c.name(), 0L) > 1) {
                String joined = c.partyIds().stream()
                        .map(id -> shortcuts.getOrDefault(id, String.valueOf(id)))
                        .collect(java.util.stream.Collectors.joining(" + "));
                out.add(new Coalition(c.partyIds(), c.seats(), joined, c.minimal()));
            } else {
                out.add(c);
            }
        }
        return out;
    }

    /** Namensgebung fuer Koalitionen. */
    static final class CoalitionNames {

        private CoalitionNames() {
        }

        /** Etablierte Namen, geprueft als exakte Mengengleichheit der Kuerzel. */
        private static final List<Map.Entry<Set<String>, String>> KNOWN = List.of(
                Map.entry(Set.of("CDU/CSU", "SPD"), "Große Koalition"),
                Map.entry(Set.of("SPD", "Grüne", "FDP"), "Ampel"),
                Map.entry(Set.of("CDU/CSU", "Grüne", "FDP"), "Jamaika"),
                Map.entry(Set.of("CDU", "Grüne", "FDP"), "Jamaika"),
                Map.entry(Set.of("CSU", "Grüne", "FDP"), "Jamaika"),
                Map.entry(Set.of("CDU/CSU", "SPD", "Grüne"), "Kenia"),
                Map.entry(Set.of("CDU", "SPD", "Grüne"), "Kenia"),
                Map.entry(Set.of("CDU/CSU", "SPD", "FDP"), "Deutschland-Koalition"),
                Map.entry(Set.of("CDU", "SPD", "FDP"), "Deutschland-Koalition"),
                Map.entry(Set.of("SPD", "Linke", "Grüne"), "Rot-Rot-Grün"),
                Map.entry(Set.of("SPD", "Linke"), "Rot-Rot"),
                Map.entry(Set.of("CDU", "Grüne", "SPD", "FDP"), "Ampel plus Union"));

        /** Umgangssprachliche Farbe je Partei, fuer zusammengesetzte Namen. */
        private static final Map<String, String> COLOR_WORDS = Map.ofEntries(
                Map.entry("CDU/CSU", "Schwarz"),
                Map.entry("CDU", "Schwarz"),
                Map.entry("CSU", "Schwarz"),
                Map.entry("SPD", "Rot"),
                Map.entry("Grüne", "Grün"),
                Map.entry("FDP", "Gelb"),
                Map.entry("AfD", "Blau"),
                // Auch die Linke ist "Rot" — Rot-Rot und Rot-Rot-Gruen sind die
                // gaengigen Bezeichnungen, alles andere klingt konstruiert. Wo
                // dadurch zwei Buendnisse denselben Farbnamen bekaemen, steht die
                // Parteiliste unmittelbar daneben und macht es eindeutig.
                Map.entry("Linke", "Rot"),
                Map.entry("BSW", "Lila"),
                Map.entry("Freie Wähler", "Orange"),
                Map.entry("SSW", "Blau"),
                Map.entry("Volt", "Violett"));

        static String nameFor(List<Integer> partyIds, Map<Integer, String> shortcuts) {
            Set<String> members = new LinkedHashSet<>();
            for (Integer id : partyIds) {
                String shortcut = shortcuts.get(id);
                if (shortcut != null) {
                    members.add(shortcut);
                }
            }
            if (members.isEmpty()) {
                return "Koalition";
            }
            if (members.size() == 1) {
                return "Alleinregierung " + members.iterator().next();
            }

            Set<String> comparable = new TreeSet<>(members);
            for (Map.Entry<Set<String>, String> entry : KNOWN) {
                if (new TreeSet<>(entry.getKey()).equals(comparable)) {
                    return entry.getValue();
                }
            }

            // Kein etablierter Name: aus den Farbwoertern zusammensetzen, in der
            // Reihenfolge absteigender Groesse ("Schwarz-Rot-Grün").
            List<String> words = new ArrayList<>();
            boolean allKnown = true;
            for (String shortcut : members) {
                String word = COLOR_WORDS.get(shortcut);
                if (word == null) {
                    allKnown = false;
                    break;
                }
                words.add(word);
            }
            if (allKnown) {
                return String.join("-", words);
            }
            return String.join(" + ", members);
        }
    }
}
