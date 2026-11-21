package com.project.log_layer.tenant;

import com.project.log_layer.enums.Environment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationCredentials {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;

    public record Binding(UUID organizationId, long applicationId, String applicationName, Environment environment) {}
    public record Metadata(UUID id, String name, String scope, OffsetDateTime createdAt,
                           OffsetDateTime expiresAt, OffsetDateTime revokedAt) {}
    public record Issued(UUID id, String key) {}

    public Optional<Binding> authenticate(String key) {
        if (key == null || key.length() < 16 || key.length() > 160) {
            return Optional.empty();
        }
        return jdbc.query("""
                select a.organization_id,a.id,a.application_name,a.environment
                from public.application_credentials c join public.application_integrations a
                  on a.id=c.application_id and a.organization_id=c.organization_id
                join public.organizations o on o.id=c.organization_id
                where c.key_hash=? and c.scope='logs:ingest' and c.revoked_at is null
                  and (c.expires_at is null or c.expires_at>now())
                  and a.deactivated_at is null and o.deactivated_at is null
                """, (row, index) -> new Binding(row.getObject(1, UUID.class), row.getLong(2), row.getString(3),
                Environment.valueOf(row.getString(4))), digest(key)).stream().findFirst();
    }

    public Optional<Binding> activeApplication(UUID org, long application) {
        return jdbc.query("""
                select a.id,a.application_name,a.environment from public.application_integrations a
                join public.organizations o on o.id=a.organization_id
                where a.id=? and a.organization_id=? and a.deactivated_at is null and o.deactivated_at is null
                """, (row, index) -> new Binding(org, row.getLong(1), row.getString(2),
                Environment.valueOf(row.getString(3))), application, org).stream().findFirst();
    }

    public List<Metadata> list(UUID org, long app) {
        return jdbc.query("""
                select id,name,scope,created_at,expires_at,revoked_at from public.application_credentials
                where organization_id=? and application_id=? order by created_at desc,id
                """, (row, index) -> new Metadata(row.getObject(1, UUID.class), row.getString(2), row.getString(3),
                row.getObject(4, OffsetDateTime.class), row.getObject(5, OffsetDateTime.class),
                row.getObject(6, OffsetDateTime.class)), org, app);
    }

    public Issued create(UUID org, long app, String name, OffsetDateTime expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry must be in the future");
        }
        UUID id = UUID.randomUUID();
        byte[] entropy = new byte[32];
        RANDOM.nextBytes(entropy);
        String raw = "dvi_live_" + id + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        jdbc.update("""
                insert into public.application_credentials(id,organization_id,application_id,name,key_hash,scope,expires_at)
                values (?,?,?,?,?,'logs:ingest',?)
                """, id, org, app, name.strip(), digest(raw), expiresAt);
        return new Issued(id, raw);
    }

    public void revoke(UUID org, long app, UUID id) {
        if (jdbc.update("""
                update public.application_credentials set revoked_at=coalesce(revoked_at,now())
                where organization_id=? and application_id=? and id=?
                """, org, app, id) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found");
        }
    }

    @Transactional
    public Issued rotate(UUID org, long app, UUID old, String name, OffsetDateTime expiresAt) {
        if (jdbc.update("""
                update public.application_credentials set revoked_at=now()
                where organization_id=? and application_id=? and id=? and revoked_at is null
                """, org, app, old) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Credential is unavailable for rotation");
        }
        return create(org, app, name, expiresAt);
    }

    private static byte[] digest(String key) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
