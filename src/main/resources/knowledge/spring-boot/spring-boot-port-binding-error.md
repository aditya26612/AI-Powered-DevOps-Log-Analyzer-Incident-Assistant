# Spring Boot Port Binding Error (Port 8080 was already in use, java.net.BindException: Address already in use)

Technology: Spring Boot 3.x embedded web server (Tomcat, Jetty, Undertow, Netty) and OS sockets. Scope: the embedded server cannot bind its listening port, or binds to an address clients cannot reach. Out of scope: Docker host port conflicts on `-p` (docker-container-network-error.md).

## Problem: Spring Boot web server failed to start because the port is in use

On startup the embedded server opens a listening socket on `server.port` (default 8080) and `server.address` (all interfaces if unset). If another process already listens on that port, the OS rejects the bind with "Address already in use", Spring Boot's PortInUseFailureAnalyzer reports it, and the application exits.

## Common Log Messages: Spring Boot port already in use

Canonical (Spring Boot PortInUseFailureAnalyzer source; port varies):

- `Web server failed to start. Port 8080 was already in use.`
- Action: `Identify and stop the process that's listening on port 8080 or configure this application to listen on another port.`

Representative (JDK / server wording):

- `java.net.BindException: Address already in use`
- `org.springframework.boot.web.server.PortInUseException: Port 8080 is already in use`
- `java.net.SocketException: Permission denied` (binding a privileged port below 1024 as a non-root user on Linux)

## Symptoms: Spring Boot port conflict

- `APPLICATION FAILED TO START` with the port-in-use description right after Tomcat/Netty initialization.
- Happens after a previous instance did not stop, when two services share a default port, or in tests running several contexts.
- In containers this is uncommon (each container has its own network namespace) unless two processes run in the same container or Pod, or host networking is used.

## Common Causes: Spring Boot BindException

### Cause 1: Another instance of the same application is still running

Supporting evidence: `ss -ltnp` / `lsof -i :8080` shows a `java` process; an IDE run or background process still active.
Weakening evidence: no process on the port at check time (then a race or a transient port).

### Cause 2: A different service uses the same port

Supporting evidence: the listening process is a different program (another app, proxy, dev tool).

### Cause 3: Shared network namespace (Kubernetes Pod sidecars, host networking)

Why: containers in one Pod share a network namespace; two containers listening on 8080 conflict. With `hostNetwork: true` or Docker `--network host`, the app competes with host processes.
Supporting evidence: conflict appears only in that Pod/host-network setup.

### Cause 4: Privileged port as non-root

Supporting evidence: `Permission denied` rather than `Address already in use` when `server.port` is below 1024.

### Cause 5: App binds but is unreachable (server.address)

Why: `server.address=127.0.0.1` (or equivalent) makes the server reachable only from inside the same host/container; Docker published ports and Kubernetes Services then get connection refused.
Supporting evidence: app logs show it started on the port, but clients outside the container get refused; `ss -ltn` inside shows `127.0.0.1:8080`.
This is not a BindException; it is a listen-address mismatch.

## Diagnostic Steps: Spring Boot port binding

1. Observation: `Port N was already in use` / `Address already in use` vs `Permission denied` vs started-but-unreachable.
2. Find the listener on the host/container where the app runs: `ss -ltnp 'sport = :8080'` or `lsof -i :8080` (Linux/macOS), `netstat -ano | findstr :8080` (Windows, PID in last column).
3. Decide whether the existing process is a stale instance (stop it) or a legitimate service (change port).
4. For Kubernetes Pods, compare `containerPort` and app ports of all containers in the Pod.
5. Conclusion: stale process, port collision, shared namespace, privileged port, or listen address.

## Useful Commands: port diagnostics for Spring Boot

Read-only:

- `ss -ltnp` (Linux): listening TCP sockets with owning process (`-p` needs root to show other users' processes).
- `lsof -i :8080` (Linux/macOS): processes using port 8080.
- `netstat -ano | findstr :8080` (Windows): connections with owning PID.
- `docker port CONTAINER`: host-side port mappings (Docker-level conflicts).

State-changing:

- Stopping the conflicting process (`kill PID` on Linux/macOS, `taskkill /PID PID` on Windows): only after confirming it is a stale instance and not a production service.

## Resolution: Spring Boot port already in use

- Stop the stale instance, or configure a different port with `server.port` / `SERVER_PORT` env var / `--server.port=8081`.
- In tests, use `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` or `server.port=0` for a random free port.
- In Pods, give each container a distinct port.
- Use a port >= 1024 for non-root processes, and map privileged ports at the proxy/Service layer.
- In containers, leave `server.address` unset (all interfaces) or set it to `0.0.0.0` so Docker/Kubernetes networking can reach the app.

## Prevention: Spring Boot port conflicts

Assign ports per service in configuration, avoid running two services on default 8080 on the same host, and use random ports in tests.

## Diagnostic Differentiation: Spring Boot port error vs similar errors

- Docker `Bind for 0.0.0.0:8080 failed: port is already allocated`: host-side publish conflict at `docker run`, the JVM never started (docker-container-network-error.md).
- Spring started fine but Nginx logs `connect() failed (111: Connection refused)`: listen address/port mismatch or app down (nginx-connection-refused.md).
- Invalid value for `server.port` (not a number): binding error (spring-boot-configuration-error.md).
- Kubernetes Service with wrong `targetPort`: kubernetes-service-unreachable.md.

## Related Errors: Spring Boot port already in use

Terms related to Spring Boot port already in use: `Port 8080 was already in use`, `PortInUseException`, `java.net.BindException: Address already in use`, `Permission denied`, `server.port`, `server.address`, `EADDRINUSE`.

## Root-Cause Summary: Spring Boot Port Binding Error

Spring Boot port binding. Typical rootCause statements: stale instance still listening, another service on the same port, two containers in one Pod or host networking using the same port, privileged port as non-root, server.address bound to loopback so clients are refused. Severity: high for the affected instance; usually local or deployment-specific. Recommendation pattern: identify the process that owns the port before stopping anything.
