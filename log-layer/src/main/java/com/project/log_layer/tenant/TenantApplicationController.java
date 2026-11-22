package com.project.log_layer.tenant;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.TechStack;
import com.project.log_layer.security.TenantIdentity;
import com.project.log_layer.util.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organization}/applications")
@RequiredArgsConstructor
public class TenantApplicationController {

    private final TenantApplications applications;

    record Create(@NotBlank @Size(max = 100) String applicationName, @NotNull TechStack techStack,
                  @NotNull Environment environment, @Size(max = 255) String description) {}
    record Update(@NotNull Environment environment, @Size(max = 255) String description) {}
    record KeyRequest(@NotBlank @Size(max = 80) String name, OffsetDateTime expiresAt) {}
    record Webhook(@Size(max = 500) String url) {}
    record SlackPolicy(@NotNull Boolean enabled, @NotNull @Min(1) @Max(3) Integer minimumSeverity) {}

    @GetMapping
    public List<TenantApplications.Application> list(@PathVariable UUID organization, HttpServletRequest request) {
        return applications.list(organization, TenantIdentity.requireUserId(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantApplications.Application create(@PathVariable UUID organization, @Valid @RequestBody Create input,
                                                    HttpServletRequest request) {
        return applications.create(organization, TenantIdentity.requireUserId(request), CurrentUser.getEmail(request),
                input.applicationName(), input.techStack(), input.environment(), input.description());
    }

    @GetMapping("/{id}")
    public TenantApplications.Application get(@PathVariable UUID organization, @PathVariable long id, HttpServletRequest request) {
        return applications.get(organization, TenantIdentity.requireUserId(request), id);
    }

    @PatchMapping("/{id}")
    public TenantApplications.Application update(@PathVariable UUID organization, @PathVariable long id,
                                                    @Valid @RequestBody Update input, HttpServletRequest request) {
        return applications.update(organization, TenantIdentity.requireUserId(request), id,
                input.description(), input.environment());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID organization, @PathVariable long id, HttpServletRequest request) {
        applications.deactivate(organization, TenantIdentity.requireUserId(request), id);
    }

    @PutMapping("/{id}/slack")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void slack(@PathVariable UUID organization, @PathVariable long id, @Valid @RequestBody Webhook input,
                      HttpServletRequest request) {
        applications.setWebhook(organization, TenantIdentity.requireUserId(request), id, input.url());
    }

    @PutMapping("/{id}/slack/policy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void slackPolicy(@PathVariable UUID organization, @PathVariable long id,
                            @Valid @RequestBody SlackPolicy input, HttpServletRequest request) {
        applications.setSlackPolicy(organization, TenantIdentity.requireUserId(request),id,
                input.enabled(),input.minimumSeverity());
    }

    @GetMapping("/{id}/credentials")
    public List<ApplicationCredentials.Metadata> listKeys(@PathVariable UUID organization, @PathVariable long id,
                                                           HttpServletRequest request) {
        return applications.credentials(organization, TenantIdentity.requireUserId(request), id);
    }

    @PostMapping("/{id}/credentials")
    public ResponseEntity<ApplicationCredentials.Issued> issue(@PathVariable UUID organization, @PathVariable long id,
                                                               @Valid @RequestBody KeyRequest input, HttpServletRequest request) {
        return showOnce(applications.issue(organization, TenantIdentity.requireUserId(request), id,
                input.name(), input.expiresAt()));
    }

    @PostMapping("/{id}/credentials/{key}/rotate")
    public ResponseEntity<ApplicationCredentials.Issued> rotate(@PathVariable UUID organization, @PathVariable long id,
            @PathVariable UUID key, @Valid @RequestBody KeyRequest input, HttpServletRequest request) {
        return showOnce(applications.rotate(organization, TenantIdentity.requireUserId(request), id, key,
                input.name(), input.expiresAt()));
    }

    @DeleteMapping("/{id}/credentials/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID organization, @PathVariable long id, @PathVariable UUID key,
                       HttpServletRequest request) {
        applications.revoke(organization, TenantIdentity.requireUserId(request), id, key);
    }

    private ResponseEntity<ApplicationCredentials.Issued> showOnce(ApplicationCredentials.Issued credential) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(credential);
    }
}
