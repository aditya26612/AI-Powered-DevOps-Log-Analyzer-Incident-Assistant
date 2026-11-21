package com.project.log_layer.incident;

import com.project.log_layer.security.TenantIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organization}/incidents")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentQueries queries;

    record Transition(@NotNull @PositiveOrZero Integer version) {}

    @GetMapping
    public IncidentQueries.Page<IncidentQueries.Summary> list(@PathVariable UUID organization,
            @RequestParam(required = false) IncidentQueries.Status status,
            @RequestParam(required = false) IncidentQueries.Severity severity,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            HttpServletRequest request) {
        return queries.list(organization, TenantIdentity.requireUserId(request), status, severity, applicationId,
                from, to, page, size);
    }

    @GetMapping("/{incident}")
    public IncidentQueries.Summary detail(@PathVariable UUID organization, @PathVariable UUID incident,
                                          HttpServletRequest request) {
        return queries.detail(organization, TenantIdentity.requireUserId(request), incident);
    }

    @GetMapping("/{incident}/logs")
    public IncidentQueries.Page<IncidentQueries.Member> members(@PathVariable UUID organization,
            @PathVariable UUID incident, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size, HttpServletRequest request) {
        return queries.members(organization, TenantIdentity.requireUserId(request), incident, page, size);
    }

    @PostMapping("/{incident}/acknowledge")
    public IncidentQueries.Summary acknowledge(@PathVariable UUID organization, @PathVariable UUID incident,
            @Valid @RequestBody Transition input, HttpServletRequest request) {
        return queries.transition(organization, TenantIdentity.requireUserId(request), incident,
                input.version(), IncidentQueries.Status.ACKNOWLEDGED);
    }

    @PostMapping("/{incident}/resolve")
    public IncidentQueries.Summary resolve(@PathVariable UUID organization, @PathVariable UUID incident,
            @Valid @RequestBody Transition input, HttpServletRequest request) {
        return queries.transition(organization, TenantIdentity.requireUserId(request), incident,
                input.version(), IncidentQueries.Status.RESOLVED);
    }
}
