package com.project.log_layer.tenant;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.TechStack;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantApplications {

    private final JdbcTemplate jdbc;
    private final OrganizationService organizations;
    private final WebhookCipher webhooks;
    private final ApplicationCredentials keys;

    public record Application(String id, String applicationName, TechStack techStack, Environment environment,
                              String description, boolean slackConfigured) {}

    private static final String VIEW = """
            select id,application_name,tech_stack,environment,description,
                   slack_webhook_ciphertext is not null as slack_configured
            from public.application_integrations
            where organization_id=? and deactivated_at is null
            """;

    @Transactional(readOnly = true)
    public List<Application> list(UUID org, long actor) {
        organizations.get(org, actor);
        return jdbc.query(VIEW + " order by application_name", this::application, org);
    }

    @Transactional(readOnly = true)
    public Application get(UUID org, long actor, long id) {
        organizations.get(org, actor);
        return find(org, id);
    }

    @Transactional
    public Application create(UUID org, long actor, String email, String name, TechStack stack,
                              Environment environment, String description) {
        if (!organizations.get(org, actor).role().managesApplications()) {
            throw denied();
        }
        List<Long> rows = jdbc.query("""
                insert into public.application_integrations
                  (organization_id,user_email,application_name,tech_stack,environment,description,
                   alert_on_anomaly,alert_on_error,created_at,updated_at)
                values (?,?,?,?,?,?,true,true,now(),now())
                on conflict (organization_id, application_name) do nothing
                returning id
                """, (row, index) -> row.getLong(1), org, email, name.strip(), stack.name(), environment.name(), description);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application name is already in use in this organization");
        }
        return find(org, rows.getFirst());
    }

    @Transactional
    public Application update(UUID org, long actor, long id, String description, Environment environment) {
        OrganizationRole role = organizations.get(org, actor).role();
        if (!role.managesApplications()) {
            throw denied();
        }
        requireOne(jdbc.update("""
                update public.application_integrations set description=?,environment=?,updated_at=now()
                where organization_id=? and id=? and deactivated_at is null
                """, description, environment.name(), org, id));
        return find(org, id);
    }

    @Transactional
    public void deactivate(UUID org, long actor, long id) {
        requireManager(org, actor);
        requireOne(jdbc.update("""
                update public.application_integrations set deactivated_at=now(),updated_at=now()
                where organization_id=? and id=? and deactivated_at is null
                """, org, id));
    }

    @Transactional
    public void setWebhook(UUID org, long actor, long id, String url) {
        manage(org, actor, id);
        String encrypted = url == null || url.isBlank() ? null : webhooks.encrypt(org, id, url);
        requireOne(jdbc.update("""
                update public.application_integrations
                set slack_webhook_ciphertext=?,updated_at=now()
                where organization_id=? and id=? and deactivated_at is null
                """, encrypted, org, id));
    }

    @Transactional(readOnly = true)
    public List<ApplicationCredentials.Metadata> credentials(UUID org, long actor, long id) {
        manage(org, actor, id);
        return keys.list(org, id);
    }

    @Transactional
    public ApplicationCredentials.Issued issue(UUID org, long actor, long id, String name, OffsetDateTime expiry) {
        manage(org, actor, id);
        return keys.create(org, id, name, expiry);
    }

    @Transactional
    public ApplicationCredentials.Issued rotate(UUID org, long actor, long id, UUID previous, String name,
                                                 OffsetDateTime expiry) {
        manage(org, actor, id);
        return keys.rotate(org, id, previous, name, expiry);
    }

    @Transactional
    public void revoke(UUID org, long actor, long id, UUID key) {
        manage(org, actor, id);
        keys.revoke(org, id, key);
    }

    private Application find(UUID org, long id) {
        return jdbc.query(VIEW + " and id=?", this::application, org, id)
                .stream().findFirst().orElseThrow(TenantApplications::missing);
    }

    private void manage(UUID org, long actor, long id) {
        requireManager(org, actor);
        find(org, id);
    }

    private void requireManager(UUID org, long actor) {
        if (!organizations.get(org, actor).role().managesOrganization()) {
            throw denied();
        }
    }

    private Application application(ResultSet row, int index) throws SQLException {
        return new Application(Long.toString(row.getLong("id")), row.getString("application_name"),
                TechStack.valueOf(row.getString("tech_stack")), Environment.valueOf(row.getString("environment")),
                row.getString("description"), row.getBoolean("slack_configured"));
    }

    private static void requireOne(int changed) {
        if (changed != 1) {
            throw missing();
        }
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient application permissions");
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
    }
}
