package com.fherrmann.wahlen.api;

import com.fherrmann.wahlen.config.CacheConfig;
import com.fherrmann.wahlen.config.WahlenProperties;
import com.fherrmann.wahlen.dawum.DawumImportService;
import com.fherrmann.wahlen.domain.ImportState;
import com.fherrmann.wahlen.repository.SurveyRepository;
import com.fherrmann.wahlen.service.ElectionCalendarService;
import com.fherrmann.wahlen.service.ParliamentViewService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oeffentliche JSON-API.
 *
 * <p>Sie ist bewusst offen: die Datengrundlage steht unter der ODbL, und diese
 * Aufbereitung gibt sie in derselben Form weiter. Wer damit etwas Eigenes bauen
 * will, soll das koennen, ohne zu scrapen — genau der Dienst, den DAWUM uns
 * erweist.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final ParliamentViewService view;
    private final ElectionCalendarService calendar;
    private final DawumImportService importService;
    private final SurveyRepository surveys;
    private final WahlenProperties properties;

    public ApiController(ParliamentViewService view,
                         ElectionCalendarService calendar,
                         DawumImportService importService,
                         SurveyRepository surveys,
                         WahlenProperties properties) {
        this.view = view;
        this.calendar = calendar;
        this.importService = importService;
        this.surveys = surveys;
        this.properties = properties;
    }

    @GetMapping("/parliaments")
    @Cacheable(CacheConfig.OVERVIEW)
    public List<Dtos.ParliamentSummaryDto> parliaments() {
        return view.overview();
    }

    @GetMapping("/parliaments/{slug}")
    public ResponseEntity<Dtos.ParliamentDetailDto> parliament(
            @PathVariable String slug,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return view.detail(slug, from, to)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/featured")
    public Dtos.FeaturedDto featured() {
        return view.featured();
    }

    @GetMapping("/meta")
    public Dtos.MetaDto meta() {
        Map<String, String> state = importService.currentState();
        return new Dtos.MetaDto(
                state.get(ImportState.DAWUM_LAST_UPDATE),
                state.get(ImportState.LAST_CHECK),
                state.get(ImportState.LAST_SUCCESSFUL_RUN),
                state.get(ImportState.LAST_ERROR),
                surveys.count(),
                "ODC Open Database License (ODbL)",
                "https://opendatacommons.org/licenses/odbl/1-0/",
                "DAWUM",
                "https://dawum.de/",
                properties.trend().sigmaDays());
    }
}
