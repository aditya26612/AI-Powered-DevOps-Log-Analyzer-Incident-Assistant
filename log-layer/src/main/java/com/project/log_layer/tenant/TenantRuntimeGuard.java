package com.project.log_layer.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tenant.runtime.guard", havingValue = "true")
public class TenantRuntimeGuard implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final WebhookCipher cipher;

    @Override
    public void run(ApplicationArguments args) {
        Boolean restricted = jdbc.queryForObject("""
                select count(*)=5 and bool_and(not r.rolsuper and not r.rolbypassrls
                       and pg_has_role(current_user,'devinsight_log_tenant','USAGE')
                       and c.relrowsecurity and c.relforcerowsecurity and c.relowner != r.oid)
                from pg_roles r cross join pg_class c
                where r.rolname=current_user and c.oid in
                    ('public.logs'::regclass, 'public.incidents'::regclass, 'public.incident_memberships'::regclass,
                     'public.diagnosis_jobs'::regclass, 'public.diagnosis_attempts'::regclass)
                """, Boolean.class);
        if (!Boolean.TRUE.equals(restricted)) {
            throw new IllegalStateException("Log Layer requires a non-owner runtime role and enforced tenant policies");
        }
        Boolean hasEncrypted = jdbc.queryForObject("""
                select exists(select 1 from public.application_integrations where slack_webhook_ciphertext is not null)
                """, Boolean.class);
        if (Boolean.TRUE.equals(hasEncrypted) && !cipher.isConfigured()) {
            throw new IllegalStateException("Existing encrypted webhooks require the protected encryption key");
        }
    }
}
