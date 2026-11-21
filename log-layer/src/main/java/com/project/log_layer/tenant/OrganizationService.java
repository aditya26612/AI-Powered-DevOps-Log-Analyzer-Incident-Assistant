package com.project.log_layer.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final JdbcTemplate jdbc;
    private final UserDirectory directory;

    public record Organization(UUID id, String name, String slug, OrganizationRole role) {}
    public record Member(String userId, OrganizationRole role) {}

    public List<Organization> list(long actor) {
        return jdbc.query("""
                select o.id, o.name, o.slug, m.role from public.organizations o
                join public.organization_members m on m.organization_id = o.id
                where m.user_id = ? and m.active and o.deactivated_at is null order by o.slug
                """, this::organization, actor);
    }

    @Transactional
    public Organization create(long actor, String name, String slug) {
        UUID id = UUID.randomUUID();
        if (jdbc.update("""
                insert into public.organizations(id,name,slug) values (?,?,?)
                on conflict (slug) do nothing
                """, id, name.strip(), slug) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization slug is already in use");
        }
        jdbc.update("""
                insert into public.organization_members(organization_id,user_id,role) values (?,?,'OWNER')
                """, id, actor);
        return new Organization(id, name.strip(), slug, OrganizationRole.OWNER);
    }

    public Organization get(UUID id, long actor) {
        return jdbc.query("""
                select o.id, o.name, o.slug, m.role from public.organizations o
                join public.organization_members m on m.organization_id = o.id
                where o.id = ? and m.user_id = ? and m.active and o.deactivated_at is null
                """, this::organization, id, actor).stream().findFirst().orElseThrow(OrganizationService::notFound);
    }

    public List<Member> members(UUID id, long actor) {
        get(id, actor);
        return jdbc.query("""
                select user_id,role from public.organization_members
                where organization_id = ? and active order by user_id
                """, (row, index) -> new Member(Long.toString(row.getLong(1)), OrganizationRole.valueOf(row.getString(2))), id);
    }

    @Transactional
    public Organization rename(UUID id, long actor, String name) {
        lock(id);
        Organization organization = get(id, actor);
        requireManager(organization.role());
        jdbc.update("update public.organizations set name=?,updated_at=now() where id=?", name.strip(), id);
        return new Organization(id, name.strip(), organization.slug(), organization.role());
    }

    @Transactional
    public void setMember(UUID id, long actor, long target, OrganizationRole role) {
        lock(id);
        OrganizationRole caller = get(id, actor).role();
        requireManager(caller);
        OrganizationRole before = activeRole(id, target);
        requireRoleAuthority(caller, before, role);
        if (before == OrganizationRole.OWNER && role != OrganizationRole.OWNER) {
            requireAnotherOwner(id);
        }
        if (before == null) {
            directory.requireUser(target);
        }
        jdbc.update("""
                insert into public.organization_members(organization_id,user_id,role) values (?,?,?)
                on conflict (organization_id,user_id)
                do update set role=excluded.role, active=true, updated_at=now()
                """, id, target, role.name());
    }

    @Transactional
    public void removeMember(UUID id, long actor, long target) {
        lock(id);
        OrganizationRole caller = get(id, actor).role();
        OrganizationRole before = activeRole(id, target);
        if (before == null) {
            throw notFound();
        }
        if (actor != target) {
            requireManager(caller);
            requireRoleAuthority(caller, before, null);
        }
        if (before == OrganizationRole.OWNER) {
            requireAnotherOwner(id);
        }
        jdbc.update("""
                update public.organization_members set active=false,updated_at=now()
                where organization_id=? and user_id=?
                """, id, target);
    }

    @Transactional
    public void deactivate(UUID id, long actor) {
        lock(id);
        if (get(id, actor).role() != OrganizationRole.OWNER) {
            throw forbidden();
        }
        jdbc.update("update public.organizations set deactivated_at=now(),updated_at=now() where id=?", id);
    }

    private void lock(UUID id) {
        if (jdbc.query("select id from public.organizations where id=? and deactivated_at is null for update",
                (row, index) -> row.getObject(1, UUID.class), id).isEmpty()) {
            throw notFound();
        }
    }

    private OrganizationRole activeRole(UUID id, long user) {
        return jdbc.query("select role from public.organization_members where organization_id=? and user_id=? and active",
                (row, index) -> OrganizationRole.valueOf(row.getString(1)), id, user).stream().findFirst().orElse(null);
    }

    private void requireAnotherOwner(UUID id) {
        Integer count = jdbc.queryForObject("""
                select count(*) from public.organization_members where organization_id=? and active and role='OWNER'
                """, Integer.class, id);
        if (count == null || count <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An organization requires an active owner");
        }
    }

    private static void requireManager(OrganizationRole role) {
        if (!role.managesOrganization()) {
            throw forbidden();
        }
    }

    private static void requireRoleAuthority(OrganizationRole caller, OrganizationRole before, OrganizationRole after) {
        if (caller != OrganizationRole.OWNER &&
                (before != null && before.managesOrganization() || after != null && after.managesOrganization())) {
            throw forbidden();
        }
    }

    private Organization organization(ResultSet row, int index) throws SQLException {
        return new Organization(row.getObject("id", UUID.class), row.getString("name"),
                row.getString("slug"), OrganizationRole.valueOf(row.getString("role")));
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient organization permissions");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization or membership not found");
    }
}
