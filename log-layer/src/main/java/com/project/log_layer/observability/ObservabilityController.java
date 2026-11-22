package com.project.log_layer.observability;

import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.security.TenantIdentity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organization}/observability")
@RequiredArgsConstructor
public class ObservabilityController {
    private final ObservabilityQueries queries;

    @GetMapping("/summary")
    public ObservabilityQueries.Summary summary(@PathVariable UUID organization,
            @RequestParam(defaultValue = "DAY") ObservabilityQueries.Range range,
            @RequestParam(required = false) Long applicationId, HttpServletRequest request) {
        return queries.summary(organization,TenantIdentity.requireUserId(request),range,applicationId,now());
    }

    @RequestMapping(path = "/logs", method = {RequestMethod.GET, RequestMethod.POST})
    public ObservabilityQueries.LogPage logs(@PathVariable UUID organization,
            @RequestParam(defaultValue = "DAY") ObservabilityQueries.Range range,
            @RequestParam(required = false) Long applicationId, @RequestParam(required = false) LogLevel level,
            @RequestParam(required = false) LogSource source, @RequestParam(required = false) Environment environment,
            @RequestParam(required = false) AnalysisStatus analysisStatus,
            @RequestParam(required = false) ObservabilityQueries.Classification classification,
            @RequestParam(defaultValue = "false") boolean operationalErrors,
            @RequestParam(required = false) UUID incident, @RequestParam(required = false) UUID correlation,
            @RequestParam(required = false) String trace, @RequestBody(required = false) SearchText search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) Instant before, HttpServletRequest request) {
        long actor = TenantIdentity.requireUserId(request);
        return queries.logs(organization, actor, range, applicationId, now(),
                new ObservabilityQueries.LogFilter(level,source,environment,analysisStatus,classification,operationalErrors,incident,
                        correlation,trace,search == null ? null : search.text,page,size,before));
    }

    @GetMapping("/logs/{log}/source")
    public ObservabilityQueries.Source source(@PathVariable UUID organization, @PathVariable long log,
            HttpServletRequest request) {
        return queries.source(organization,TenantIdentity.requireUserId(request),log);
    }

    public record SearchText(String text) {}

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
