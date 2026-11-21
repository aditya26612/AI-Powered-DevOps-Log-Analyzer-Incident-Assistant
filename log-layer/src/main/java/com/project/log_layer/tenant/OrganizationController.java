package com.project.log_layer.tenant;

import com.project.log_layer.security.TenantIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizations;

    record Create(@NotBlank @Size(max = 120) String name,
                  @NotBlank @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]{0,78}[a-z0-9])?") String slug) {}
    record Rename(@NotBlank @Size(max = 120) String name) {}
    record MemberUpdate(@NotNull OrganizationRole role) {}

    @GetMapping
    public List<OrganizationService.Organization> list(HttpServletRequest request) {
        return organizations.list(TenantIdentity.requireUserId(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationService.Organization create(@Valid @RequestBody Create input, HttpServletRequest request) {
        return organizations.create(TenantIdentity.requireUserId(request), input.name(), input.slug());
    }

    @GetMapping("/{id}")
    public OrganizationService.Organization get(@PathVariable UUID id, HttpServletRequest request) {
        return organizations.get(id, TenantIdentity.requireUserId(request));
    }

    @PatchMapping("/{id}")
    public OrganizationService.Organization rename(@PathVariable UUID id, @Valid @RequestBody Rename input,
                                                    HttpServletRequest request) {
        return organizations.rename(id, TenantIdentity.requireUserId(request), input.name());
    }

    @GetMapping("/{id}/members")
    public List<OrganizationService.Member> members(@PathVariable UUID id, HttpServletRequest request) {
        return organizations.members(id, TenantIdentity.requireUserId(request));
    }

    @PutMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setMember(@PathVariable UUID id, @PathVariable long userId, @Valid @RequestBody MemberUpdate input,
                          HttpServletRequest request) {
        organizations.setMember(id, TenantIdentity.requireUserId(request), userId, input.role());
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID id, @PathVariable long userId, HttpServletRequest request) {
        organizations.removeMember(id, TenantIdentity.requireUserId(request), userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id, HttpServletRequest request) {
        organizations.deactivate(id, TenantIdentity.requireUserId(request));
    }
}
