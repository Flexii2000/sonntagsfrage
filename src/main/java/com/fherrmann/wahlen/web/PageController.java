package com.fherrmann.wahlen.web;

import tools.jackson.databind.ObjectMapper;
import com.fherrmann.wahlen.api.Dtos;
import com.fherrmann.wahlen.config.WahlenProperties;
import com.fherrmann.wahlen.dawum.DawumImportService;
import com.fherrmann.wahlen.domain.ImportState;
import com.fherrmann.wahlen.repository.SurveyRepository;
import com.fherrmann.wahlen.service.ParliamentViewService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Serverseitig gerenderte Seiten.
 *
 * <p>Die Daten werden als JSON mit ins HTML gelegt, damit die erste Ansicht ohne
 * zusaetzlichen Roundtrip steht; das JavaScript zeichnet daraus die Charts und
 * holt nur bei Interaktion nach.
 */
@Controller
public class PageController {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final ParliamentViewService view;
    private final DawumImportService importService;
    private final SurveyRepository surveys;
    private final WahlenProperties properties;
    private final ObjectMapper objectMapper;

    public PageController(ParliamentViewService view,
                          DawumImportService importService,
                          SurveyRepository surveys,
                          WahlenProperties properties,
                          ObjectMapper objectMapper) {
        this.view = view;
        this.importService = importService;
        this.surveys = surveys;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public String index(Model model) {
        Dtos.FeaturedDto featured = view.featured();
        List<Dtos.ParliamentSummaryDto> overview = view.overview();

        Dtos.ParliamentDetailDto hero = featured.slug() == null ? null
                : view.detail(featured.slug(), null, null).orElse(null);

        model.addAttribute("featured", featured);
        model.addAttribute("hero", hero);
        model.addAttribute("overview", overview);
        model.addAttribute("bootstrap", json(Map.of(
                "featured", featured,
                "hero", hero,
                "overview", overview)));
        model.addAttribute("pageTitle", "Wahlen — Sonntagsfrage");
        return "index";
    }

    @GetMapping("/{slug}")
    public String parliament(@PathVariable String slug,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             Model model) {
        Dtos.ParliamentDetailDto detail = view.detail(slug, from, to)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unbekanntes Parlament"));

        model.addAttribute("detail", detail);
        model.addAttribute("bootstrap", json(Map.of("detail", detail)));
        model.addAttribute("pageTitle", detail.parliament().name() + " — Sonntagsfrage");
        return "parliament";
    }

    @GetMapping("/{slug}/umfragen")
    public String surveys(@PathVariable String slug, Model model) {
        Dtos.ParliamentDetailDto detail = view.detail(slug, LocalDate.of(2017, 1, 1), null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unbekanntes Parlament"));

        model.addAttribute("detail", detail);
        model.addAttribute("bootstrap", json(Map.of("detail", detail)));
        model.addAttribute("pageTitle", "Alle Umfragen — " + detail.parliament().name());
        return "surveys";
    }

    @GetMapping("/daten")
    public String data(Model model) {
        Map<String, String> state = importService.currentState();
        model.addAttribute("dawumLastUpdate", state.get(ImportState.DAWUM_LAST_UPDATE));
        model.addAttribute("lastCheck", humanTime(state.get(ImportState.LAST_CHECK)));
        model.addAttribute("lastRun", humanTime(state.get(ImportState.LAST_SUCCESSFUL_RUN)));
        model.addAttribute("lastError", state.get(ImportState.LAST_ERROR));
        model.addAttribute("surveyCount", surveys.count());
        model.addAttribute("sigmaDays", properties.trend().sigmaDays());
        model.addAttribute("preElectionDays", properties.featured().preElectionDays());
        model.addAttribute("postElectionDays", properties.featured().postElectionDays());
        model.addAttribute("pageTitle", "Daten & Methodik");
        return "data";
    }

    /** robots.txt: die API und Detailseiten duerfen indexiert werden, Rohtabellen nicht. */
    @GetMapping(value = "/robots.txt", produces = "text/plain")
    public void robots(HttpServletResponse response) throws java.io.IOException {
        response.setContentType("text/plain");
        response.getWriter().write("""
                User-agent: *
                Allow: /wahlen/
                Disallow: /wahlen/api/
                """);
    }

    /**
     * Zeitstempel so, wie ein Mensch ihn lesen will: Ortszeit plus Abstand zu
     * jetzt. Gerade bei "laeuft der Abruf noch?" ist "vor 4 Minuten" die
     * Antwort, nicht ein ISO-Zeitstempel in UTC.
     */
    private String humanTime(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) {
            return null;
        }
        try {
            Instant instant = Instant.parse(isoInstant);
            String absolute = DateTimeFormatter
                    .ofPattern("d.M.yyyy, HH:mm 'Uhr'", Locale.GERMAN)
                    .withZone(BERLIN)
                    .format(instant);
            return "%s (%s)".formatted(absolute, relative(instant));
        } catch (DateTimeParseException e) {
            return isoInstant;
        }
    }

    private String relative(Instant instant) {
        long minutes = Duration.between(instant, Instant.now()).toMinutes();
        if (minutes < 1) {
            return "gerade eben";
        }
        if (minutes == 1) {
            return "vor einer Minute";
        }
        if (minutes < 90) {
            return "vor %d Minuten".formatted(minutes);
        }
        long hours = minutes / 60;
        if (hours < 36) {
            return hours == 1 ? "vor einer Stunde" : "vor %d Stunden".formatted(hours);
        }
        long days = hours / 24;
        return days == 1 ? "vor einem Tag" : "vor %d Tagen".formatted(days);
    }

    /**
     * JSON fuer die Einbettung in ein {@code <script>}-Element.
     *
     * <p>{@code </} wird maskiert, damit ein Zeichenkettenwert das Skript-Element
     * nicht vorzeitig schliessen kann — der klassische XSS-Weg beim Einbetten von
     * JSON in HTML.
     */
    private String json(Object value) {
        return objectMapper.writeValueAsString(value).replace("</", "<\\/");
    }
}
