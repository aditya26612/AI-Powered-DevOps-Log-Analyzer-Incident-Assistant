# Docker Container Networking and Connectivity Errors

Technology: Docker Engine networking (bridge networks, port publishing, container DNS). Scope: a running container cannot reach another container or the host, or clients cannot reach a published container port. Out of scope: container not running at all (see docker-container-startup-failure.md), Kubernetes Service routing (see kubernetes-service-unreachable.md).

## Problem: Docker container cannot connect to another container or service

Applications in containers report `connection refused`, `connection timed out`, or `unknown host` when calling another container, a database, or the host. Or a client outside Docker cannot reach a port that was supposedly published. Most cases come down to: wrong address (especially `localhost`), wrong network, missing port publishing, or the target only listening on 127.0.0.1.

## Common Log Messages: Docker networking

Canonical (Docker documentation):

- `ping: bad address 'alpine2'`: name resolution failed on the default bridge network, where containers cannot resolve each other by name.

Representative (exact text comes from the client library, not Docker):

- `dial tcp 127.0.0.1:5432: connect: connection refused` (Go)
- `java.net.ConnectException: Connection refused` (Java)
- `java.net.UnknownHostException: db` (Java, name not resolvable)
- `getaddrinfo ENOTFOUND db` (Node.js)
- `Bind for 0.0.0.0:8080 failed: port is already allocated` (docker run / Compose, host port already used)

## Symptoms: Docker container network failure

- The application container is running, but every request to a dependency fails immediately (refused) or after a delay (timeout).
- It works with `localhost` on the developer machine and fails when containerized.
- A published port is not reachable from another machine, or only reachable from the Docker host.
- Container starts fail with a port allocation error.

## Key Facts: how Docker networking behaves

- `localhost` / `127.0.0.1` inside a container refers to that container's own network namespace, not the Docker host and not other containers.
- On the default `bridge` network, containers can reach each other by IP but not by container name (legacy `--link` aside).
- On a user-defined bridge network (`docker network create`), Docker provides automatic DNS so containers resolve each other by name or alias. Docker Compose creates a user-defined network per project, so service names resolve there.
- Containers on the same user-defined network can reach each other's ports without `-p`. Publishing (`-p HOST:CONTAINER`) is needed for access from the host or outside.
- `-p 8080:80` without a host IP binds on all host addresses; `-p 127.0.0.1:8080:80` restricts access to the Docker host. (Docker notes that releases before 28.0.0 could allow same-L2 hosts to reach localhost-published ports.)
- The application inside the container must listen on `0.0.0.0` (or the container interface). A process bound only to 127.0.0.1 inside the container is unreachable through published ports or from other containers.
- `host.docker.internal` resolves to the host on Docker Desktop. Do not assume it exists on native Linux Docker Engine without an explicit host mapping.

## Common Causes: Docker connection refused or unknown host

### Cause 1: Using localhost to reach another container or the host

Why: `localhost` resolves to the calling container itself.
Supporting evidence: error shows `127.0.0.1` or `localhost` as the target, for example `dial tcp 127.0.0.1:5432: connect: connection refused`, or a JDBC URL with `localhost` in a containerized Spring Boot app.
Weakening evidence: the target address is a service name or another container's IP.

### Cause 2: Containers on different networks, or on the default bridge using names

Why: name resolution only works on user-defined networks, and containers on separate networks cannot talk without being attached to a common network.
Supporting evidence: `unknown host` / `bad address`; `docker inspect` shows the two containers on different networks.
Weakening evidence: both containers appear in the same `docker network inspect NETWORK` output.

### Cause 3: Target listens only on 127.0.0.1 inside its container

Why: some servers default to binding loopback only.
Supporting evidence: `connection refused` although the target container is running; inside the target, `ss -ltn` (if available) shows `127.0.0.1:PORT` instead of `0.0.0.0:PORT`.
Weakening evidence: the target listens on 0.0.0.0 or `*`.

### Cause 4: Port not published, or published to the wrong port

Why: `EXPOSE` in a Dockerfile documents a port but does not publish it; the host-side port may differ from the container port.
Supporting evidence: `docker port CONTAINER` returns nothing or a different mapping.
Weakening evidence: correct mapping exists; then check host firewall or bind address.

### Cause 5: Target not ready yet (startup ordering)

Why: Compose `depends_on` without a health condition only orders container start, not application readiness.
Supporting evidence: refused errors only during the first seconds after startup, success later.
Weakening evidence: failures persist long after the target logs that it is ready.

### Cause 6: Host port already allocated

Why: another container or host process uses the host port.
Supporting evidence: `port is already allocated` at container start.
Weakening evidence: the container starts; the issue is reaching it.

## Diagnostic Steps: Docker container connectivity

1. Observation: note the exact target host and port in the error and whether it is refused (fast) or timed out (slow).
2. Hypothesis A (wrong address): if the target is `localhost`/`127.0.0.1` and the dependency is another container, the address is wrong.
3. Hypothesis B (network): check both containers' networks with `docker inspect --format '{{json .NetworkSettings.Networks}}' CONTAINER`.
4. Verification of name resolution and reachability from the caller's network namespace (see commands).
5. Hypothesis C (listener): check the target's logs for the address it listens on.
6. Hypothesis D (publishing): `docker port CONTAINER` for host-side access.
7. Conclusion: refused + correct address = nothing listening at that address/port; timeout = packet path or firewall issue; unknown host = DNS/network membership.

## Useful Commands: Docker network diagnostics

Read-only:

- `docker network ls`: lists networks.
- `docker network inspect NETWORK`: shows attached containers, IPs and subnet.
- `docker inspect --format '{{json .NetworkSettings.Networks}}' CONTAINER`: networks and IP of a container.
- `docker port CONTAINER`: published port mappings.
- `docker logs TARGET_CONTAINER`: look for the listen address and readiness message.
- `docker exec CALLER_CONTAINER getent hosts db`: resolves a name from inside the caller (only works if the image has `getent`; many minimal images do not).

State-changing (use deliberately):

- `docker network create NAME` and `docker network connect NAME CONTAINER`: create a user-defined network and attach a container. Changes connectivity of running containers.

## Resolution: Docker connectivity errors

- Replace `localhost` with the target's service/container name on a shared user-defined network (for example `jdbc:postgresql://db:5432/app` in Compose).
- Attach both containers to the same user-defined network.
- Configure the target server to listen on `0.0.0.0` inside its container (for PostgreSQL see `listen_addresses` in postgresql-connection-refused.md; for Spring Boot see `server.address` in spring-boot-port-binding-error.md).
- Publish the correct port with `-p HOST:CONTAINER`; bind to 127.0.0.1 if only local access is required (safer than exposing on all interfaces).
- Add readiness handling: Compose healthchecks with `depends_on` conditions, or client retry with backoff.
- Free or change the host port when `port is already allocated`.

## Prevention: Docker networking

Use Compose or explicit user-defined networks, never hard-code `localhost` for cross-container dependencies, externalize hostnames as configuration, and add healthchecks.

## Diagnostic Differentiation: Docker networking vs other failures

- `connection refused` from Nginx to an upstream container: see nginx-connection-refused.md (same root causes, Nginx-specific log format).
- Spring Boot `Connection to localhost:5432 refused` in a container: almost always Cause 1; also see spring-boot-datasource-error.md.
- PostgreSQL reachable but rejecting login: authentication, not networking (postgresql-authentication-failure.md).
- In Kubernetes, use Service DNS names and EndpointSlices instead (kubernetes-service-unreachable.md).
- A container that exits immediately is a startup problem, not networking.

## Related Errors: Docker container networking

Terms related to Docker container networking: `Connection refused`, `UnknownHostException`, `ENOTFOUND`, `bad address`, `port is already allocated`, `connection timed out`.

## Root-Cause Summary: Docker Container Networking and Connectivity Errors

Docker container networking. Typical rootCause statements: client uses localhost inside a container, containers not on a shared user-defined network, target listens only on 127.0.0.1, port not published, dependency not ready yet. Severity: high when a core dependency such as the database is unreachable for all requests; low when failures only occur for a few seconds during startup ordering. Recommendation pattern: cite the target host and port from the error and whether it was refused, timed out, or unresolvable.
