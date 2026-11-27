package com.project.log_layer.logsource;

import com.project.log_layer.security.TenantIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organization}")
@RequiredArgsConstructor
public class LokiSourceController {
    private final LokiSources sources;
    private final LokiConnections connections;

    record ConnectionInput(@NotBlank @Size(max = 512) String endpoint) {}

    @GetMapping("/loki-connection")
    public LokiConnections.Settings connection(@PathVariable UUID organization, HttpServletRequest request) {
        return connections.get(organization, TenantIdentity.requireUserId(request));
    }

    @PutMapping("/loki-connection")
    public LokiConnections.Settings saveConnection(@PathVariable UUID organization, @Valid @RequestBody ConnectionInput input,
                                                   HttpServletRequest request) {
        return connections.save(organization, TenantIdentity.requireUserId(request), input.endpoint());
    }

    record Settings(@NotNull @Pattern(regexp = "[a-zA-Z_][a-zA-Z0-9_]{0,127}") String labelName,
                    @NotNull @Pattern(regexp = "[a-zA-Z0-9._:/@-]{1,200}") String labelValue,
                    @NotNull Boolean enabled) {}

    @GetMapping("/applications/{app}/log-source")
    public LokiSources.Status get(@PathVariable UUID organization, @PathVariable long app, HttpServletRequest request) {
        return sources.get(organization, TenantIdentity.requireUserId(request), app);
    }

    @PutMapping("/applications/{app}/log-source")
    public LokiSources.Status save(@PathVariable UUID organization, @PathVariable long app,
                                   @Valid @RequestBody Settings settings, HttpServletRequest request) {
        return sources.save(organization, TenantIdentity.requireUserId(request), app,
                settings.labelName(), settings.labelValue(), settings.enabled());
    }

    @PostMapping("/applications/{app}/log-source/replay")
    public LokiSources.Status replay(@PathVariable UUID organization, @PathVariable long app, HttpServletRequest request) {
        return sources.replay(organization, TenantIdentity.requireUserId(request), app);
    }
}
