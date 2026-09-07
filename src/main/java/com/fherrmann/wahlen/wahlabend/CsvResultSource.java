package com.fherrmann.wahlen.wahlabend;

import com.fherrmann.wahlen.domain.Election;
import com.fherrmann.wahlen.domain.ResultKind;
import com.fherrmann.wahlen.reference.ReferenceData.LiveSourceRef;
import com.fherrmann.wahlen.reference.ReferenceData.SeatsSourceRef;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Liest den Landesstand aus einer Ergebnis-CSV einer Landeswahlleitung.
 *
 * <p>Die Dateien unterscheiden sich je Land (Sachsen-Anhalt: eine Spalte je
 * Partei mit Praefix, Landeszeile ueber "Satzart"; Mecklenburg-Vorpommern:
 * Titelzeilen vor dem Kopf, ISO-8859-1, Landeszeile ueber "Wahlkreis 99"),
 * folgen aber demselben Muster: eine Zeile fuer das Land, eine Spalte je
 * Partei, eine Spalte mit den gueltigen Stimmen. Genau das beschreibt ein
 * {@link LiveSourceRef} in {@code elections.yaml} — neue Laender sind damit
 * Konfiguration, kein Code.
 *
 * <p>Der Abruf ist hoeflich: ETag und Last-Modified werden mitgeschickt, ein
 * 304 kostet die Quelle nichts.
 */
@Component
public class CsvResultSource {

    private static final Logger log = LoggerFactory.getLogger(CsvResultSource.class);

    private record Validator(String etag, String lastModified) {
    }

    /** Was ein Abruf ergab — leer bei 304 oder wenn noch nichts ausgezaehlt ist. */
    public record Parsed(Map<String, Double> percent, Map<String, Integer> seats, Double turnout,
                         Integer districtsCounted, Integer districtsTotal, Instant timestamp,
                         boolean complete) {
    }

    private final RestClient client;
    private final Map<String, Validator> validators = new ConcurrentHashMap<>();

    public CsvResultSource(@Qualifier("wahlabendRestClient") RestClient client) {
        this.client = client;
    }

    /**
     * @return der neue Stand als Eingabe fuer {@link ElectionReportService}, oder
     *         leer, wenn die Quelle unveraendert ist oder noch keine Stimmen zaehlt
     */
    public Optional<ReportInput> fetch(LiveSourceRef ref, Election election) {
        if (!"csv".equalsIgnoreCase(ref.type())) {
            throw new IllegalArgumentException("Unbekannter Quellentyp: " + ref.type());
        }
        Optional<Download> download = download(ref.url());
        if (download.isEmpty()) {
            return Optional.empty();
        }
        String text = decode(download.get().body(), ref.charset());
        Parsed parsed = parseResults(text, ref, download.get().lastModified());
        if (parsed == null) {
            return Optional.empty();
        }

        ResultKind kind = kindFor(ref, parsed);
        Map<String, Integer> seats = Map.of();
        if (kind == ResultKind.VORLAEUFIG && ref.seats() != null && ref.seats().url() != null) {
            try {
                seats = fetchSeats(ref.seats());
            } catch (RuntimeException e) {
                log.warn("Sitzverteilung von {} nicht lesbar: {}", ref.seats().url(), e.getMessage());
            }
        }

        String note = parsed.districtsTotal() != null && parsed.districtsCounted() != null
                ? "Auszählungsstand: %s von %s Wahlbezirken".formatted(
                        group(parsed.districtsCounted()), group(parsed.districtsTotal()))
                : null;
        String label = ref.label() != null && !ref.label().isBlank() ? ref.label() : "Landeswahlleitung";

        return Optional.of(new ReportInput(
                kind, parsed.timestamp(), label, ref.url(), parsed.turnout(), note,
                parsed.percent(), seats, !seats.isEmpty(),
                ref.aliases(), false,
                fingerprint(kind, parsed, seats)));
    }

    private ResultKind kindFor(LiveSourceRef ref, Parsed parsed) {
        if (ref.kind() != null && !ref.kind().isBlank()) {
            return ResultKind.valueOf(ref.kind().trim().toUpperCase(Locale.ROOT));
        }
        return parsed.complete() ? ResultKind.VORLAEUFIG : ResultKind.AUSZAEHLUNG;
    }

    // ------------------------------------------------------------ Parsen

    /** Paketsichtbar fuer Tests: der Kern ohne HTTP. */
    static Parsed parseResults(String text, LiveSourceRef ref, Instant lastModified) {
        Table table = Table.parse(text, ref.validVotes());
        Map<String, String> row = table.find(ref.row());
        if (row == null) {
            throw new IllegalStateException("Landeszeile nicht gefunden (" + ref.row() + ")");
        }
        Double valid = number(row.get(ref.validVotes()));
        if (valid == null || valid <= 0) {
            return null;
        }

        Map<String, Double> percent = new LinkedHashMap<>();
        for (String column : partyColumns(table.headers(), ref.partyPattern(), ref.partiesAfter(), ref.ignore())) {
            Double votes = number(row.get(column));
            if (votes == null) {
                continue;
            }
            String name = partyName(column, ref.partyPattern());
            percent.merge(name, Math.round(votes / valid * 10000.0) / 100.0, Double::sum);
        }
        if (percent.isEmpty()) {
            throw new IllegalStateException("Keine Parteispalten erkannt");
        }

        Double turnout = null;
        if (ref.turnout() != null) {
            turnout = number(row.get(ref.turnout()));
        } else if (ref.voters() != null && ref.eligible() != null) {
            Double voters = number(row.get(ref.voters()));
            Double eligible = number(row.get(ref.eligible()));
            if (voters != null && eligible != null && eligible > 0) {
                turnout = Math.round(voters / eligible * 1000.0) / 10.0;
            }
        }

        Integer counted = null;
        Integer total = null;
        boolean complete = false;
        if (ref.districtsCounted() != null && ref.districtsTotal() != null) {
            Double c = number(row.get(ref.districtsCounted()));
            Double t = number(row.get(ref.districtsTotal()));
            if (c != null && t != null) {
                counted = c.intValue();
                total = t.intValue();
                complete = total > 0 && counted >= total;
            }
        } else if (ref.completeWhen() != null && !ref.completeWhen().isEmpty()) {
            complete = ref.completeWhen().entrySet().stream()
                    .allMatch(e -> e.getValue().equals(String.valueOf(row.getOrDefault(e.getKey(), "")).trim()));
        }

        Instant timestamp = null;
        if (ref.timestamp() != null && row.get(ref.timestamp()) != null && !row.get(ref.timestamp()).isBlank()) {
            String pattern = ref.timestampFormat() != null ? ref.timestampFormat() : "dd.MM.yyyy HH:mm:ss";
            try {
                timestamp = LocalDateTime.parse(row.get(ref.timestamp()).trim(), DateTimeFormatter.ofPattern(pattern))
                        .atZone(WahlabendClock.BERLIN).toInstant();
            } catch (DateTimeParseException e) {
                log.debug("Zeitstempel '{}' passt nicht zu '{}'", row.get(ref.timestamp()), pattern);
            }
        }
        if (timestamp == null) {
            timestamp = lastModified != null ? lastModified : Instant.now();
        }
        return new Parsed(percent, Map.of(), turnout, counted, total, timestamp, complete);
    }

    private Map<String, Integer> fetchSeats(SeatsSourceRef ref) {
        Optional<Download> download = download(ref.url());
        // Bei 304 den Cache-Eintrag ignorieren: Sitze brauchen wir jedes Mal.
        if (download.isEmpty()) {
            validators.remove(ref.url());
            download = download(ref.url());
        }
        if (download.isEmpty()) {
            return Map.of();
        }
        return parseSeats(decode(download.get().body(), ref.charset()), ref);
    }

    /** Paketsichtbar fuer Tests. */
    static Map<String, Integer> parseSeats(String text, SeatsSourceRef ref) {
        Map<String, Integer> seats = new LinkedHashMap<>();
        if (ref.partyColumn() != null && ref.seatsColumn() != null) {
            // Lang: eine Zeile je Partei.
            Table table = Table.parse(text, ref.seatsColumn());
            for (Map<String, String> row : table.rows()) {
                String name = row.get(ref.partyColumn());
                Double value = number(row.get(ref.seatsColumn()));
                if (name == null || name.isBlank() || value == null) {
                    continue;
                }
                if (PartyAliases.normalize(name).startsWith("insgesamt") || PartyAliases.normalize(name).equals("gesamt")) {
                    continue;
                }
                seats.merge(name.trim(), value.intValue(), Integer::sum);
            }
        } else if (ref.partiesAfter() != null) {
            // Breit: eine Zeile, Parteien als Spalten.
            Table table = Table.parse(text, ref.partiesAfter());
            Map<String, String> row = table.find(ref.row());
            if (row == null) {
                throw new IllegalStateException("Sitzzeile nicht gefunden (" + ref.row() + ")");
            }
            for (String column : partyColumns(table.headers(), null, ref.partiesAfter(), ref.ignore())) {
                Double value = number(row.get(column));
                if (value != null) {
                    seats.merge(column.trim(), value.intValue(), Integer::sum);
                }
            }
        }
        int total = seats.values().stream().mapToInt(Integer::intValue).sum();
        return total > 0 ? seats : Map.of();
    }

    static List<String> partyColumns(List<String> headers, String pattern, String after, List<String> ignore) {
        List<String> out = new ArrayList<>();
        if (pattern != null && !pattern.isBlank()) {
            Pattern p = Pattern.compile(pattern);
            for (String h : headers) {
                if (p.matcher(h).matches() && !ignored(h, ignore)) {
                    out.add(h);
                }
            }
        } else if (after != null) {
            int idx = headers.indexOf(after);
            if (idx < 0) {
                throw new IllegalStateException("Spalte '" + after + "' nicht im Kopf");
            }
            for (String h : headers.subList(idx + 1, headers.size())) {
                if (!h.isBlank() && !ignored(h, ignore)) {
                    out.add(h);
                }
            }
        } else {
            throw new IllegalStateException("Weder partyPattern noch partiesAfter konfiguriert");
        }
        return out;
    }

    private static boolean ignored(String header, List<String> ignore) {
        if (ignore == null) {
            return false;
        }
        String key = PartyAliases.normalize(header);
        return ignore.stream().anyMatch(i -> PartyAliases.normalize(i).equals(key));
    }

    private static String partyName(String column, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return column.trim();
        }
        Matcher m = Pattern.compile(pattern).matcher(column);
        return m.matches() && m.groupCount() >= 1 ? m.group(1).trim() : column.trim();
    }

    /** "1.234" und "72,1" sind deutsch, "x" und "-" heissen "nicht angetreten". */
    static Double number(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace(" ", "").replace(" ", "");
        if (s.isEmpty() || s.equalsIgnoreCase("x") || s.equals("-") || s.equals("–")) {
            return null;
        }
        if (s.contains(",")) {
            s = s.replace(".", "").replace(',', '.');
        } else if (s.matches("\\d{1,3}(\\.\\d{3})+")) {
            s = s.replace(".", "");
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String group(int n) {
        return String.format(Locale.GERMAN, "%,d", n);
    }

    private static String fingerprint(ResultKind kind, Parsed parsed, Map<String, Integer> seats) {
        StringBuilder sb = new StringBuilder(kind.name()).append('|');
        new TreeMap<>(parsed.percent()).forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        sb.append('|');
        new TreeMap<>(seats).forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        sb.append('|').append(parsed.turnout()).append('|').append(parsed.districtsCounted());
        try {
            byte[] out = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------ HTTP

    private record Download(byte[] body, Instant lastModified) {
    }

    private Optional<Download> download(String url) {
        Validator known = validators.get(url);
        return client.get()
                .uri(url)
                .headers(h -> {
                    if (known != null && known.etag() != null) {
                        h.setIfNoneMatch(known.etag());
                    }
                    if (known != null && known.lastModified() != null) {
                        h.set("If-Modified-Since", known.lastModified());
                    }
                })
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == HttpStatus.NOT_MODIFIED.value()) {
                        return Optional.<Download>empty();
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IOException("HTTP " + response.getStatusCode().value() + " von " + url);
                    }
                    byte[] body = response.getBody().readAllBytes();
                    String etag = response.getHeaders().getETag();
                    String lastModifiedRaw = response.getHeaders().getFirst("Last-Modified");
                    if (etag != null || lastModifiedRaw != null) {
                        validators.put(url, new Validator(etag, lastModifiedRaw));
                    }
                    long lm = response.getHeaders().getLastModified();
                    return Optional.of(new Download(body, lm > 0 ? Instant.ofEpochMilli(lm) : null));
                });
    }

    private static String decode(byte[] body, String charset) {
        Charset cs = charset == null || charset.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(charset);
        String text = new String(body, cs);
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    // ------------------------------------------------------------ CSV

    /** Eine geparste Datei: Kopf plus Zeilen als Spaltenname -> Wert. */
    record Table(List<String> headers, List<Map<String, String>> rows) {

        /** Der Kopf ist die erste Zeile, die {@code anchorColumn} enthaelt — Titelzeilen davor stoeren nicht. */
        static Table parse(String text, String anchorColumn) {
            List<String> lines = text.lines().toList();
            char delimiter = ';';
            List<String> headers = null;
            List<Map<String, String>> rows = new ArrayList<>();
            for (String line : lines) {
                if (headers == null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    delimiter = line.chars().filter(c -> c == ';').count() >= line.chars().filter(c -> c == ',').count()
                            ? ';' : ',';
                    List<String> cells = split(line, delimiter);
                    if (anchorColumn == null || cells.stream().anyMatch(c -> c.trim().equals(anchorColumn))) {
                        headers = cells.stream().map(String::trim).toList();
                    }
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = split(line, delimiter);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < cells.size() ? cells.get(i) : "");
                }
                rows.add(row);
            }
            if (headers == null) {
                throw new IllegalStateException("Kopfzeile mit Spalte '" + anchorColumn + "' nicht gefunden");
            }
            return new Table(headers, rows);
        }

        Map<String, String> find(Map<String, String> conditions) {
            if (conditions == null || conditions.isEmpty()) {
                return rows.isEmpty() ? null : rows.get(0);
            }
            for (Map<String, String> row : rows) {
                boolean ok = true;
                for (Map.Entry<String, String> c : conditions.entrySet()) {
                    String actual = row.get(c.getKey());
                    if (actual == null) {
                        ok = false;
                        break;
                    }
                    String expected = c.getValue() == null ? "" : c.getValue().trim();
                    if (!actual.trim().equals(expected)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return row;
                }
            }
            return null;
        }

        /** Minimaler CSV-Zeilenparser mit Anfuehrungszeichen ("" als Escape). */
        static List<String> split(String line, char delimiter) {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (quoted) {
                    if (ch == '"') {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            cur.append('"');
                            i++;
                        } else {
                            quoted = false;
                        }
                    } else {
                        cur.append(ch);
                    }
                } else if (ch == '"') {
                    quoted = true;
                } else if (ch == delimiter) {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else if (ch != '\r') {
                    cur.append(ch);
                }
            }
            out.add(cur.toString());
            return out;
        }
    }
}
