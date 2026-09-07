package com.fherrmann.wahlen.api;

import com.fherrmann.wahlen.config.WahlenProperties;
import com.fherrmann.wahlen.domain.ElectionReport;
import com.fherrmann.wahlen.domain.ResultKind;
import com.fherrmann.wahlen.service.ParliamentViewService;
import com.fherrmann.wahlen.wahlabend.ElectionReportService;
import com.fherrmann.wahlen.wahlabend.ReportException;
import com.fherrmann.wahlen.wahlabend.ReportInput;
import com.fherrmann.wahlen.wahlabend.WahlabendClock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wahlabend: Lesen fuer alle, Eintragen nur mit Token.
 *
 * <p>Das Token steht auf dem Server in der {@code .env} ({@code WAHLABEND_TOKEN})
 * und wird als {@code Authorization: Bearer …} oder {@code X-Wahlabend-Token}
 * mitgeschickt. Ohne konfiguriertes Token ist das Eintragen gesperrt, nicht offen.
 */
@RestController
@RequestMapping("/api")
public class WahlabendController {

    public static final String TOKEN_HEADER = "X-Wahlabend-Token";

    private final ParliamentViewService view;
    private final ElectionReportService reportService;
    private final WahlenProperties properties;

    public WahlabendController(ParliamentViewService view,
                               ElectionReportService reportService,
                               WahlenProperties properties) {
        this.view = view;
        this.reportService = reportService;
        this.properties = properties;
    }

    /**
     * Ein Stand, wie er von Hand oder per Skript kommt.
     *
     * @param reportedAt ISO-8601 ({@code 2026-09-06T18:00:00+02:00}) oder nur
     *                   {@code HH:mm}: ab 18 Uhr der Wahltag, davor der Folgetag —
     *                   ein Wahlabend geht bis in die Nacht
     * @param results    Parteikuerzel -> Prozent; fehlt "Sonstige", ist der Rest bis 100 gemeint
     * @param seats      Parteikuerzel -> Sitze, falls die Quelle welche nennt
     */
    public record ReportRequest(
            String kind, String reportedAt, String source, String sourceUrl,
            Double turnout, String note,
            Map<String, Double> results, Map<String, Integer> seats) {
    }

    @GetMapping("/parliaments/{slug}/wahlabend")
    public ResponseEntity<Dtos.WahlabendDto> wahlabend(@PathVariable String slug) {
        return view.wahlabend(slug)
                .map(dto -> ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(dto))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/wahlabend/{slug}/{date}/reports")
    public ResponseEntity<Dtos.ReportDto> add(
            @PathVariable String slug,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = TOKEN_HEADER, required = false) String tokenHeader,
            @RequestBody ReportRequest request) {
        requireToken(authorization, tokenHeader);

        ResultKind kind;
        try {
            kind = ResultKind.valueOf(String.valueOf(request.kind()).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ReportException.badRequest("Unbekannte Art: " + request.kind());
        }
        ReportInput input = new ReportInput(
                kind, parseReportedAt(request.reportedAt(), date),
                request.source(), request.sourceUrl(), request.turnout(), request.note(),
                request.results(), request.seats(),
                request.seats() != null && !request.seats().isEmpty(),
                null, true, null);
        ElectionReport saved = reportService.add(slug, date, input);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(com.fherrmann.wahlen.wahlabend.WahlabendService.reportDto(saved, date));
    }

    @DeleteMapping("/wahlabend/reports/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = TOKEN_HEADER, required = false) String tokenHeader) {
        requireToken(authorization, tokenHeader);
        reportService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ReportException.class)
    public ResponseEntity<Map<String, String>> onReportException(ReportException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    /** Sicherheitsnetz fuer den Unique-Index — der Normalfall wird vorher abgefangen. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> onConflict(org.springframework.dao.DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Diesen Stand gibt es schon (gleiche Quelle, Art und Uhrzeit)"));
    }

    private void requireToken(String authorization, String tokenHeader) {
        String expected = properties.wahlabend().token();
        if (expected == null || expected.isBlank()) {
            throw new ReportException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Eintragen ist nicht konfiguriert (WAHLABEND_TOKEN fehlt)");
        }
        String given = tokenHeader;
        if ((given == null || given.isBlank()) && authorization != null
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            given = authorization.substring(7).trim();
        }
        if (given == null || !MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            throw new ReportException(HttpStatus.UNAUTHORIZED, "Token fehlt oder ist falsch");
        }
    }

    /** ISO-Zeitstempel, oder "HH:mm" relativ zum Wahlabend; leer = jetzt. */
    static Instant parseReportedAt(String value, LocalDate electionDate) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        String v = value.trim();
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException ignored) {
            // kein voller Zeitstempel
        }
        try {
            LocalTime time = LocalTime.parse(v);
            LocalDate day = time.isBefore(WahlabendClock.POLLS_CLOSE) ? electionDate.plusDays(1) : electionDate;
            return day.atTime(time).atZone(WahlabendClock.BERLIN).toInstant();
        } catch (DateTimeParseException e) {
            throw ReportException.badRequest(
                    "Zeit nicht lesbar: \"" + v + "\" (erwartet HH:mm oder 2026-09-06T18:00:00+02:00)");
        }
    }
}
