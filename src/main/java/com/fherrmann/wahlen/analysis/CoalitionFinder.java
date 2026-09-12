package com.fherrmann.wahlen.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Findet alle Parteikombinationen, die rechnerisch eine Mehrheit haetten.
 *
 * <p>Gerechnet wird wertfrei — mit einer festen Ausnahme und zwei Markierungen,
 * die Felix am 2026-09-12 so entschieden hat, damit die Liste das zeigt, was es
 * zu sehen gibt, statt in Arithmetik zu ertrinken ("man sieht mehr, indem man
 * weniger sieht"):
 * <ul>
 *   <li><b>Fest:</b> SPD, Gruene und Linke koalieren nie mit der AfD. Solche
 *       Kombinationen erscheinen gar nicht erst — auch nicht auf Wunsch.</li>
 *   <li><b>Markierungen fuer die Schalter im UI:</b> jedes Buendnis traegt
 *       {@code afd} (AfD beteiligt, "Brandmauer") und {@code unionLinke}
 *       (Union und Linke zusammen, "Unvereinbarkeitsbeschluss"). Die Seite
 *       blendet damit ein und aus; die Rechnung selbst bleibt vollstaendig,
 *       damit man den Schalter auch umlegen kann.</li>
 * </ul>
 */
@Component
public class CoalitionFinder {

    /** Mehr Parteien im Parlament als hier werden nicht kombinatorisch durchgerechnet. */
    private static final int MAX_PARTIES = 12;

    /**
     * @param partyIds   Beteiligte
     * @param seats      Sitze zusammen
     * @param name       gaengiger Name, sonst aus den Parteifarben zusammengesetzt
     * @param minimal    true, wenn keine Partei weggelassen werden kann, ohne die
     *                   Mehrheit zu verlieren
     * @param afd        AfD beteiligt (nie bei einer Alleinregierung — eine
     *                   Brandmauer verhindert keine absolute Mehrheit)
     * @param unionLinke Union und Linke sitzen zusammen drin
     */
    public record Coalition(List<Integer> partyIds, int seats, String name, boolean minimal,
                            boolean afd, boolean unionLinke) {
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
            Set<String> lager = CoalitionNames.canonical(members, shortcutsByPartyId);
            if (CoalitionNames.neverTogether(lager)) {
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
                    CoalitionNames.nameFor(members, shortcutsByPartyId), minimal,
                    members.size() > 1 && lager.contains(CoalitionNames.AFD),
                    lager.contains(CoalitionNames.UNION) && lager.contains(CoalitionNames.LINKE)));
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
     * <p>Passiert, seit SPD und Linke beide als "Rot" gelten: Union+SPD+Freie
     * Waehler und Union+Linke+Freie Waehler ergeben beide "Schwarz-Rot-Orange".
     * Die etablierten Namen (Rot-Rot, Rot-Rot-Gruen) sind es wert; fuer die frei
     * zusammengesetzten Faelle gilt dann lieber die Parteiliste als ein
     * doppelter Farbname.
     */
    private static List<Coalition> disambiguate(List<Coalition> coalitions,
                                                Map<Integer, String> shortcuts) {
        Map<String, Long> occurrences = coalitions.stream()
                .collect(Collectors.groupingBy(Coalition::name, Collectors.counting()));

        List<Coalition> out = new ArrayList<>(coalitions.size());
        for (Coalition c : coalitions) {
            if (occurrences.getOrDefault(c.name(), 0L) > 1) {
                String joined = c.partyIds().stream()
                        .map(id -> shortcuts.getOrDefault(id, String.valueOf(id)))
                        .collect(Collectors.joining(" + "));
                out.add(new Coalition(c.partyIds(), c.seats(), joined, c.minimal(),
                        c.afd(), c.unionLinke()));
            } else {
                out.add(c);
            }
        }
        return out;
    }

    /** Namensgebung und Lagerlogik fuer Koalitionen. */
    static final class CoalitionNames {

        static final String UNION = "Union";
        static final String AFD = "AfD";
        static final String LINKE = "Linke";

        /** Diese drei sitzen nie mit der AfD in einer Regierung — feste Regel, kein Schalter. */
        private static final Set<String> NEVER_WITH_AFD = Set.of("SPD", "Grüne", LINKE);

        private CoalitionNames() {
        }

        /**
         * CDU, CSU und die Bundes-Sammelposition "CDU/CSU" sind fuer Namen und
         * Lager dasselbe: Kenia heisst in Sachsen-Anhalt genauso wie im Bund.
         */
        static String canonical(String shortcut) {
            return switch (shortcut) {
                case "CDU/CSU", "CDU", "CSU" -> UNION;
                default -> shortcut;
            };
        }

        static Set<String> canonical(List<Integer> partyIds, Map<Integer, String> shortcuts) {
            Set<String> out = new LinkedHashSet<>();
            for (Integer id : partyIds) {
                String shortcut = shortcuts.get(id);
                if (shortcut != null) {
                    out.add(canonical(shortcut));
                }
            }
            return out;
        }

        static boolean neverTogether(Set<String> lager) {
            return lager.contains(AFD) && lager.stream().anyMatch(NEVER_WITH_AFD::contains);
        }

        /**
         * Etablierte Namen, geprueft als exakte Mengengleichheit der Lager. Nur
         * die, die keine Farbfolge sind: Schwarz-Gruen, Rot-Gruen oder
         * Schwarz-Blau entstehen unten aus den Farbwoertern — in der Reihenfolge
         * der Groesse, so wie man in Baden-Wuerttemberg auch "Gruen-Schwarz" sagt.
         */
        private static final List<Map.Entry<Set<String>, String>> KNOWN = List.of(
                Map.entry(Set.of(UNION, "SPD"), "Große Koalition"),
                Map.entry(Set.of("SPD", LINKE), "Rot-Rot"),
                Map.entry(Set.of("SPD", LINKE, "Grüne"), "Rot-Rot-Grün"),
                Map.entry(Set.of("SPD", "Grüne", "FDP"), "Ampel"),
                Map.entry(Set.of(UNION, "Grüne", "FDP"), "Jamaika"),
                Map.entry(Set.of(UNION, "SPD", "Grüne"), "Kenia"),
                Map.entry(Set.of(UNION, "SPD", "FDP"), "Deutschland-Koalition"),
                Map.entry(Set.of(UNION, "FDP", AFD), "Bahamas"),
                Map.entry(Set.of(UNION, "SPD", "Grüne", "FDP"), "Simbabwe"),
                Map.entry(Set.of(UNION, "SPD", "BSW"), "Brombeer"),
                Map.entry(Set.of("SPD", "Grüne", "SSW"), "Küstenkoalition"));

        /** Umgangssprachliche Farbe je Lager, fuer zusammengesetzte Namen. */
        private static final Map<String, String> COLOR_WORDS = Map.ofEntries(
                Map.entry(UNION, "Schwarz"),
                Map.entry("SPD", "Rot"),
                Map.entry("Grüne", "Grün"),
                Map.entry("FDP", "Gelb"),
                Map.entry(AFD, "Blau"),
                // Auch die Linke ist "Rot" — Rot-Rot und Rot-Rot-Gruen sind die
                // gaengigen Bezeichnungen, alles andere klingt konstruiert. Wo
                // dadurch zwei Buendnisse denselben Farbnamen bekaemen, steht die
                // Parteiliste unmittelbar daneben und macht es eindeutig.
                Map.entry(LINKE, "Rot"),
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

            Set<String> lager = canonical(partyIds, shortcuts);
            for (Map.Entry<Set<String>, String> entry : KNOWN) {
                if (entry.getKey().equals(lager)) {
                    return entry.getValue();
                }
            }

            // Kein etablierter Name: aus den Farbwoertern zusammensetzen, in der
            // Reihenfolge absteigender Groesse ("Schwarz-Rot-Grün").
            List<String> words = new ArrayList<>();
            for (String one : lager) {
                String word = COLOR_WORDS.get(one);
                if (word == null) {
                    return String.join(" + ", members);
                }
                words.add(word);
            }
            return String.join("-", words);
        }
    }
}
