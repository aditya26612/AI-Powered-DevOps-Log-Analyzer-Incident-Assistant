# PostgreSQL Authentication Failure (password authentication failed, no pg_hba.conf entry, role/database does not exist, SCRAM)

Technology: PostgreSQL client authentication (pg_hba.conf, password methods md5/scram-sha-256, peer). Scope: the server is reachable but rejects the connection during authentication or authorization. Out of scope: server unreachable (postgresql-connection-refused.md), connection slots exhausted (postgresql-too-many-connections.md).

## Problem: PostgreSQL rejects login

When a client connects, PostgreSQL selects the first `pg_hba.conf` record matching connection type, database, user and client address, then applies that record's authentication method. Failures come from: no matching record, wrong password, a non-existent user/database, an authentication method the client driver does not support, or local peer authentication mismatches. These errors are reported as `FATAL` by the server, which proves the network path works.

## Common Log Messages: PostgreSQL authentication failure

Canonical (PostgreSQL documentation, Client Authentication Problems):

- `FATAL:  no pg_hba.conf entry for host "123.123.123.123", user "andym", database "testdb"`
- `FATAL:  password authentication failed for user "andym"`
- `FATAL:  user "andym" does not exist`
- `FATAL:  database "testdb" does not exist`

Representative (version/client dependent):

- `FATAL:  role "app" does not exist` (common current server wording for a missing user)
- `no pg_hba.conf entry ... , SSL off` or `..., no encryption` (suffix depends on version and whether SSL/GSS encryption was used)
- `FATAL:  Peer authentication failed for user "postgres"` (local connections with the peer method)
- pgjdbc: `SCRAM authentication is not supported by this driver. You need JDK >= 8 and pgjdbc >= 42.2.0 (not ".jre" versions)`
- Older drivers: `authentication method 10 not supported` (SCRAM unsupported; exact text varies)

SQLSTATE codes: `28P01` invalid_password, `28000` invalid_authorization_specification (used for pg_hba rejections).

## Symptoms: PostgreSQL login rejected

- Client fails immediately with a `FATAL` message from the server.
- The server log contains a matching `FATAL` line and often a `DETAIL` line (for example which pg_hba.conf line matched, or that the role has no valid password).
- Fails after a password rotation, migration to a new server version, or move of the client to a new network/subnet.

## Common Causes: PostgreSQL authentication failure

### Cause 1: Wrong password or stale secret

Supporting evidence: `password authentication failed for user`; recent rotation; app uses an old Secret/env var.
Weakening evidence: the same credentials work with `psql` from the same network.
Docker note: the official postgres image sets the superuser password from `POSTGRES_PASSWORD` only when it initializes an empty data directory (initdb at first start). Changing `POSTGRES_PASSWORD` later on an existing volume does not change the stored password, a frequent source of this error.

### Cause 2: No pg_hba.conf record for this host/user/database/SSL combination

Why: client IP not in any CIDR, connection type mismatch (`hostssl` only matches SSL connections, `hostnossl` only non-SSL, `host` matches both), or database/user columns don't match.
Supporting evidence: `no pg_hba.conf entry for host "IP", user "U", database "D"`; the IP shown is the address the server sees (may be a NAT/gateway address in Docker or Kubernetes).
Weakening evidence: `pg_hba_file_rules` shows a matching rule earlier in the file.

### Cause 3: Role or database does not exist

Supporting evidence: `user/role "X" does not exist` or `database "X" does not exist`. Common when the client defaults the database name to the user name.

### Cause 4: Authentication method incompatible with the driver (SCRAM)

Why: PostgreSQL 14 changed the default `password_encryption` to `scram-sha-256`. Newly set passwords are stored as SCRAM; old drivers that do not support SCRAM fail. pgjdbc added SCRAM support in 42.2.0. Existing MD5 hashes are not converted automatically.
Supporting evidence: driver-specific "SCRAM not supported" or "authentication method 10 not supported" messages; old driver version in the dependency tree.

### Cause 5: Peer authentication on local connections

Why: the `peer` method requires the OS user name to match the database user (or a mapping).
Supporting evidence: `Peer authentication failed`; connecting via Unix socket as a different OS user.
Fix: connect as the matching OS user, use `-h 127.0.0.1` to use a host (password) rule, or adjust pg_hba with a proper method.

### Cause 6: pg_hba.conf edited but not reloaded, or a syntax error in it

Why: pg_hba.conf is read at startup and on reload (SIGHUP). Rules with errors are reported in `pg_hba_file_rules.error`.

## Diagnostic Steps: PostgreSQL authentication failure

1. Observation: copy the exact `FATAL` line; it identifies which check failed.
2. Read the server log for the same timestamp; `DETAIL` lines often name the matched pg_hba line.
3. For pg_hba errors, compare the reported IP/user/database/SSL state with the rules: `SELECT * FROM pg_hba_file_rules;` (superuser by default).
4. For password errors, verify the secret the application actually uses (env var/Secret version) and test with `psql` using the same values.
5. For missing role/database, list them: `\du` and `\l` in psql.
6. For SCRAM issues, check the client driver version and the role's stored password type.
7. Conclusion: wrong secret, missing rule, missing role/database, method incompatibility, peer mismatch, or rules not reloaded.

## Useful Commands: PostgreSQL authentication diagnostics

Read-only:

- `psql -h HOST -p 5432 -U USER -d DATABASE`: reproduces the login with explicit parameters.
- `SHOW hba_file;`: path of the active pg_hba.conf.
- `SELECT * FROM pg_hba_file_rules;`: parsed rules with line numbers and an `error` column.
- `SHOW password_encryption;`: algorithm used when passwords are set.
- `\du` and `\l` (psql meta-commands): roles and databases.

State-changing:

- `SELECT pg_reload_conf();` (or `pg_ctl reload -D "$PGDATA"`): reloads pg_hba.conf and reloadable settings without dropping sessions. Validate `pg_hba_file_rules` first.
- `ALTER ROLE app WITH PASSWORD '...';`: resets a password, re-hashed with the current `password_encryption`. Update every client secret at the same time. Prefer `\password app` in psql so the plain password is not stored in history or logs.

## Resolution: PostgreSQL authentication errors

- Sync the application secret with the database password; for the Docker image on an existing volume, change the password with `ALTER ROLE` instead of only changing `POSTGRES_PASSWORD`.
- Add a narrowly scoped pg_hba.conf rule (specific database, user, CIDR, `scram-sha-256`) and reload.
- Create the missing role/database or fix the client's database name.
- Upgrade old JDBC drivers to a SCRAM-capable version rather than downgrading to md5.
- WARNING: never use `trust` in pg_hba.conf for remote addresses as a fix; it disables password checks entirely for matching connections. If used briefly for recovery on a local socket, revert immediately.

## Prevention: PostgreSQL authentication failures

Manage credentials in one secret store, rotate passwords with coordinated client updates, keep drivers current, and version-control pg_hba.conf.

## Diagnostic Differentiation: authentication vs connectivity vs limits

- `FATAL` from the server = reached. Refused/no response = not reached (postgresql-connection-refused.md).
- `too many clients` is capacity, not credentials (postgresql-too-many-connections.md).
- In Spring Boot, these appear wrapped as `PSQLException` inside Hikari pool initialization errors (spring-boot-datasource-error.md).

## Related Errors: PostgreSQL authentication failure

Terms related to PostgreSQL authentication failure: `password authentication failed for user`, `no pg_hba.conf entry for host`, `role does not exist`, `database does not exist`, `Peer authentication failed`, `SCRAM authentication is not supported`, `28P01`, `28000`, `pg_hba.conf`, `scram-sha-256`.

## Root-Cause Summary: PostgreSQL Authentication Failure

PostgreSQL authentication failure. Typical rootCause statements: wrong or stale password (including POSTGRES_PASSWORD changed after the data directory was initialized), no matching pg_hba.conf record, role or database missing, driver without SCRAM support, peer authentication mismatch, pg_hba.conf not reloaded. Severity: high for the affected application. Recommendation pattern: quote the FATAL line and never suggest trust authentication for remote access.
