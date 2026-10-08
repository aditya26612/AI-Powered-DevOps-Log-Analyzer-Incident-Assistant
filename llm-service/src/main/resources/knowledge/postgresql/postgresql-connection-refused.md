# PostgreSQL Connection Refused (server not listening, listen_addresses, port 5432, Unix socket, database system is starting up)

Technology: PostgreSQL server networking and availability (current PostgreSQL documentation; message formats changed in PostgreSQL 14 libpq). Scope: clients cannot reach a PostgreSQL server at all: TCP connection refused, Unix socket missing, or the server is not yet accepting connections. Out of scope: server reached but login rejected (postgresql-authentication-failure.md), connection limit reached (postgresql-too-many-connections.md), application/pool configuration (spring-boot-datasource-error.md).

## Problem: PostgreSQL refuses connections

"Connection refused" is a TCP-level answer: nothing accepted the connection on that host and port. The PostgreSQL server never saw the request, so this is not an authentication problem. Typical reasons are: the server is down or still starting, it listens only on localhost (default `listen_addresses = 'localhost'`), the client uses the wrong host or port, or a firewall actively rejects.

## Common Log Messages: PostgreSQL connection refused

Canonical (PostgreSQL documentation):

- PostgreSQL 14 and later (libpq/psql):
  `psql: error: connection to server at "HOST" (IP), port 5432 failed: Connection refused`
  `Is the server running on that host and accepting TCP/IP connections?`
- Before PostgreSQL 14 (older libpq):
  `could not connect to server: Connection refused`
  `Is the server running on host "HOST" (IP) and accepting TCP/IP connections on port 5432?`
- Unix domain socket (current docs; socket directory depends on build and `unix_socket_directories`):
  `connection to server on socket "/tmp/.s.PGSQL.5432" failed: No such file or directory`
  `Is the server running locally and accepting connections on that socket?`
- Server starting: `FATAL:  the database system is starting up` (SQLSTATE 57P03 cannot_connect_now)

Representative:

- `FATAL:  the database system is shutting down`
- JDBC (pgjdbc): `Connection to HOST:5432 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.`
- Server log at a healthy start: `LOG:  listening on IPv4 address "0.0.0.0", port 5432` and `LOG:  database system is ready to accept connections`

## Symptoms: PostgreSQL not reachable

- Every client fails immediately; nothing appears in the PostgreSQL server log for these attempts (the server never received them).
- Works from the database host itself (`localhost`) but not from other hosts/containers.
- Fails only for a period after a restart or failover (`starting up`).

## Common Causes: PostgreSQL connection refused

### Cause 1: Server not running or crashed

Supporting evidence: `pg_isready` returns `no response` (exit code 2); service/container not running; server log shows shutdown or crash.
Weakening evidence: `pg_isready` from the DB host returns `accepting connections`.

### Cause 2: listen_addresses only localhost

Why: the default `listen_addresses = 'localhost'` accepts TCP only on loopback. Changing it requires a server restart.
Supporting evidence: connections succeed on the DB host via 127.0.0.1 but are refused from other hosts; `SHOW listen_addresses;` returns `localhost`; `ss -ltn` shows `127.0.0.1:5432` only.
Weakening evidence: server listens on `*`/`0.0.0.0` (then check host/port/firewall).
Note: the official postgres Docker image sets `listen_addresses = '*'` in its sample configuration, so in Docker this cause is less common than a wrong host (Cause 3). If you mount a custom postgresql.conf into that image, its documentation states you must set `listen_addresses = '*'` yourself so other containers can connect.

### Cause 3: Client uses wrong host (localhost inside a container) or wrong port

Supporting evidence: the error shows `localhost`/`127.0.0.1` from an application container, or a port other than the server's `port` (default 5432).
Next: docker-container-network-error.md, kubernetes-service-unreachable.md.

### Cause 4: Server still starting up, in recovery, or shutting down

Supporting evidence: `the database system is starting up` / `shutting down`; `pg_isready` reports `rejecting connections` (exit 1); occurs after restarts.
Weakening evidence: persists long after startup completes (check server log for a stuck recovery).

### Cause 5: Firewall actively rejecting

Supporting evidence: refusal only from certain networks while the server listens on all interfaces; host firewall REJECT rules. (A firewall that DROPs packets causes timeouts, not refusals.)

### Cause 6: Unix socket path mismatch (local connections without -h)

Supporting evidence: `No such file or directory` for a socket path; the server's `unix_socket_directories` differs from the client's compiled default.
Fix: connect with `-h /actual/socket/dir` or `-h 127.0.0.1`.

## Diagnostic Steps: PostgreSQL connection refused

1. Observation: TCP refused vs socket missing vs `starting up`. Note host and port in the message.
2. From the client's environment, run `pg_isready -h HOST -p PORT`. Exit 0 accepting, 1 rejecting (starting up), 2 no response, 3 no attempt (bad parameters).
3. On the server, verify the process and the listener: service/container status, `ss -ltn | grep 5432`.
4. Verify configuration (with access): `SHOW listen_addresses;` and `SHOW port;`.
5. Compare the client target host/port with what the server listens on; in containers ensure the client uses the DB service name.
6. Conclusion: server down, loopback-only listener, wrong host/port, not ready yet, firewall, or socket path.

## Useful Commands: PostgreSQL connectivity diagnostics

Read-only:

- `pg_isready -h HOST -p 5432`: connection status check without authentication; meaningful exit codes 0/1/2/3.
- `psql -h HOST -p 5432 -U USER -d DATABASE -c 'SELECT 1'`: end-to-end test including authentication.
- `SHOW listen_addresses;` / `SHOW port;` / `SHOW unix_socket_directories;`: server network settings (run in psql on the server).
- `ss -ltn` (server host/container): whether PostgreSQL listens on 127.0.0.1 only or on all addresses.
- `docker logs POSTGRES_CONTAINER` or `kubectl logs POD`: server startup log (`listening on ...`, `ready to accept connections`).

State-changing:

- Changing `listen_addresses` or `port` in postgresql.conf (or `ALTER SYSTEM SET listen_addresses = '*'`) followed by a server restart. Restart interrupts all sessions; schedule it. Widening `listen_addresses` also requires appropriate `pg_hba.conf` rules and firewalling; do not expose PostgreSQL to untrusted networks.

## Resolution: PostgreSQL connection refused

- Start or recover the server; investigate crash causes in the server log.
- Set `listen_addresses` to the needed interfaces (restart required) and restrict access with `pg_hba.conf` and network controls.
- Point clients to the correct host/port (service name in Docker/Kubernetes).
- Make clients wait for readiness (retry with backoff, healthchecks using `pg_isready`).

## Prevention: PostgreSQL connection refused

Healthchecks with `pg_isready`, startup ordering in Compose/Kubernetes, configuration management for `listen_addresses`, and monitoring of server availability.

## Diagnostic Differentiation: PostgreSQL connection refused vs authentication vs limits

- Refused / no response: network or server availability (this document). No `FATAL` from the server.
- `FATAL: password authentication failed` or `no pg_hba.conf entry`: server reached, rejected the login (postgresql-authentication-failure.md).
- `FATAL: sorry, too many clients already`: server reached, slots exhausted (postgresql-too-many-connections.md).
- `FATAL: the database system is starting up`: server reached but not ready (Cause 4 here).
- Connection timeout instead of refusal: packets dropped (firewall/routing), not a missing listener.

## Related Errors: PostgreSQL connection refused

Terms related to PostgreSQL connection refused: `Connection refused`, `Is the server running on that host and accepting TCP/IP connections`, `could not connect to server`, `No such file or directory .s.PGSQL.5432`, `the database system is starting up`, `pg_isready`, `listen_addresses`, `57P03`.

## Root-Cause Summary: PostgreSQL Connection Refused

PostgreSQL connection refused. Typical rootCause statements: server not running, listen_addresses limited to localhost, client using the wrong host or port, server still starting up or shutting down, firewall rejecting, Unix socket path mismatch. Severity: high because every dependent application fails. Recommendation pattern: use pg_isready exit codes and the absence of a FATAL message as evidence that the server was never reached.
