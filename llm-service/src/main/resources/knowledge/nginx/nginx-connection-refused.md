# Nginx Connection Refused to Upstream (connect() failed (111: Connection refused) while connecting to upstream, host not found in upstream)

Technology: Nginx reverse proxy in front of an application (for example Spring Boot), on hosts, Docker, or Kubernetes. Scope: Nginx cannot open a TCP connection to the upstream because it is actively refused, or the upstream hostname cannot be resolved at configuration load. Results in 502 for clients. Other 502 causes: nginx-bad-gateway.md. Timeouts: nginx-upstream-timeout.md.

## Problem: Nginx connect() failed (111: Connection refused) while connecting to upstream

"Connection refused" (ECONNREFUSED) means the target host answered but no process accepted connections on that address and port (or a firewall actively rejected). For a reverse proxy the usual reasons are: the application is down or restarting, it listens on a different port, it listens only on 127.0.0.1 in another network namespace, or `proxy_pass` points to the wrong address.

## Common Log Messages: Nginx connection refused

Canonical Nginx error log patterns (errno text OS-dependent):

- `connect() failed (111: Connection refused) while connecting to upstream, client: ..., server: ..., request: "GET / HTTP/1.1", upstream: "http://127.0.0.1:8080/", host: "..."`
- At configuration test/start: `host not found in upstream "app"` (static hostname in `proxy_pass` or `upstream` cannot be resolved; `nginx -t` fails and Nginx will not start or reload)
- Follow-on: `no live upstreams while connecting to upstream` when all peers in a group are marked failed after refusals

Client sees: `502 Bad Gateway`.

## Symptoms: Nginx upstream connection refused

- Immediate 502 responses (no delay), unlike timeouts which take seconds.
- Starts right after deploying/restarting the application, or after moving Nginx and the app into containers.
- Nginx container exits at startup with `host not found in upstream` if the app container/service is not resolvable.

## Key Facts: Nginx upstream addressing

- `127.0.0.1`/`localhost` in `proxy_pass` means Nginx's own network namespace. In Docker, that is the Nginx container itself, not the application container.
- A static hostname in `proxy_pass` or an `upstream` server is resolved when the configuration is loaded and the IPs are cached until reload; adding `resolver` alone does not change that. Runtime re-resolution needs a variable in `proxy_pass` plus `resolver`, or the `resolve` parameter on upstream servers (open-source Nginx 1.27.3+, earlier only in NGINX Plus).
- If the upstream IP changes (container recreated, Pod rescheduled) and Nginx still uses the old IP, connections can be refused or time out until reload.

## Common Causes: Nginx connection refused to upstream

### Cause 1: Application not running or restarting

Supporting evidence: refusals coincide with application crash/restart; `docker ps` shows the app container restarting, or Kubernetes Pods in CrashLoopBackOff.
Weakening evidence: application healthy and reachable directly on the same address.

### Cause 2: Wrong port in proxy_pass

Supporting evidence: the `upstream:` field in the error log shows a port different from the app's actual listening port (Spring Boot default 8080, `server.port`).

### Cause 3: Wrong host: localhost inside a container

Supporting evidence: `upstream: "http://127.0.0.1:8080/"` while Nginx and the app run in separate containers.
Fix: use the app's service name on a shared Docker network (`proxy_pass http://app:8080;`) or the Kubernetes Service DNS name.

### Cause 4: Application listens only on loopback

Supporting evidence: on the app host/container, `ss -ltn` shows `127.0.0.1:8080` and Nginx connects from another address; for Spring Boot check `server.address`.

### Cause 5: Stale upstream IP after the application was recreated

Supporting evidence: refusals or timeouts start after the app container/Pod was recreated, and stop after `nginx -s reload`; the IP in the `upstream:` field differs from the app's current IP.

### Cause 6: Hostname not resolvable at startup (host not found in upstream)

Supporting evidence: `nginx -t` reports `host not found in upstream`; app container not on the same network or not started yet.

## Diagnostic Steps: Nginx connection refused

1. Observation: take the `upstream: "http://IP:PORT"` value from the error line.
2. Verify from Nginx's network namespace: `curl -sv http://IP:PORT/` from the Nginx host or `docker exec NGINX_CONTAINER ...` (if curl/wget exists in the image). Refused here confirms the problem is at the target.
3. Verify on the target: is the app running, and which address/port does it listen on (`ss -ltn`, app startup log such as Tomcat `started on port 8080`).
4. Compare the IP with the app's current IP (Docker `docker inspect`, Kubernetes `kubectl get endpointslices`).
5. Conclusion: app down, wrong port, wrong host (localhost), loopback-only bind, stale IP, or DNS at load time.

## Useful Commands: Nginx upstream refused diagnostics

Read-only:

- `nginx -T | grep -n -E 'proxy_pass|upstream|server '`: the configured upstream targets.
- `ss -ltn` on the upstream host/container: listening addresses (`0.0.0.0:8080` vs `127.0.0.1:8080`).
- `docker ps -a` / `docker logs APP_CONTAINER`: app status and restarts.
- `docker network inspect NETWORK`: whether Nginx and app share a network, and the app's current IP.
- `kubectl get endpointslices -l kubernetes.io/service-name=SERVICE`: ready backends behind a Service.

State-changing:

- `nginx -s reload`: re-reads configuration and re-resolves static hostnames; run `nginx -t` first.

## Resolution: Nginx connection refused to upstream

- Bring the application back (fix its crash cause first: docker-container-startup-failure.md, kubernetes-crashloopbackoff.md, spring-boot-startup-error.md).
- Correct `proxy_pass` host and port; use service names on shared networks instead of 127.0.0.1 across containers.
- Make the app listen on 0.0.0.0 inside its container.
- For dynamic upstream IPs, proxy to a stable name (Kubernetes Service, Compose service) and use runtime resolution or reload on changes.
- Ensure the app is resolvable before Nginx starts (start order, healthchecks), or use runtime resolution so startup does not fail.

## Prevention: Nginx upstream refused

Point Nginx at stable service names, use healthchecks/readiness so traffic only goes to listening instances, and deploy upstreams with graceful shutdown.

## Diagnostic Differentiation: Nginx refused vs timeout vs other 502

- Refused (111): instant, target reachable but nothing listening (this document).
- Timed out (110): delayed, no response at all, network path or slow app (nginx-upstream-timeout.md).
- `upstream prematurely closed connection`: connection was accepted then closed, app crashed mid-request (nginx-bad-gateway.md).
- `host not found in upstream`: DNS at config load, Nginx itself fails to start.
- The same refusal seen by a Java client is `java.net.ConnectException: Connection refused` (docker-container-network-error.md).

## Related Errors: Nginx connection refused to upstream

Terms related to Nginx connection refused to upstream: `connect() failed (111: Connection refused) while connecting to upstream`, `host not found in upstream`, `no live upstreams`, `502 Bad Gateway`, `ECONNREFUSED`, `proxy_pass`.

## Root-Cause Summary: Nginx Connection Refused to Upstream

Nginx connection refused to upstream. Typical rootCause statements: application not running or restarting, wrong port in proxy_pass, 127.0.0.1 used across containers, application listening on loopback only, stale upstream IP after the application was recreated, upstream hostname not resolvable at configuration load. Severity: high when the only upstream refuses. Recommendation pattern: cite the upstream address from the error line and the actual listener on the target.
