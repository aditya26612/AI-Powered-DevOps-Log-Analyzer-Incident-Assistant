# Nginx 502 Bad Gateway (upstream errors: prematurely closed, no live upstreams, could not be resolved, too big header, permission denied, SSL)

Technology: Nginx as a reverse proxy (ngx_http_proxy_module, ngx_http_upstream_module). Scope: Nginx returns 502 because communication with the upstream failed or the upstream response was invalid. This document is the 502 triage guide. Two causes have dedicated documents: TCP connection refused (nginx-connection-refused.md) and timeouts, which return 504 rather than 502 (nginx-upstream-timeout.md).

## Problem: Nginx returns 502 Bad Gateway

A 502 means Nginx accepted the client request but could not get a valid response from the upstream (proxied) server. It is never the root cause by itself. The access log only shows the status; the error log line for the same request states the actual failure, and different 502 causes need different fixes.

## Common Log Messages: Nginx 502 error log

Canonical Nginx error log phrases (errno numbers and text are OS-dependent; 111/110/13 are Linux values):

- `connect() failed (111: Connection refused) while connecting to upstream` (see nginx-connection-refused.md)
- `upstream prematurely closed connection while reading response header from upstream`
- `no live upstreams while connecting to upstream`
- `HOST could not be resolved (3: Host not found)` (runtime DNS resolution failure)
- `upstream sent too big header while reading response header from upstream`
- `(13: Permission denied) while connecting to upstream` (commonly SELinux on RHEL-family hosts)
- `SSL_do_handshake() failed ... while SSL handshaking to upstream` (OpenSSL reason text varies)
- `recv() failed (104: Connection reset by peer) while reading response header from upstream` (representative)

Error log lines usually also include `client:`, `server:`, `request:`, and `upstream: "http://IP:PORT/..."`, which identify the exact upstream address.

## Symptoms: Nginx 502 Bad Gateway

- Clients receive `502 Bad Gateway` (HTML page from Nginx).
- Access log status 502; with upstream logging, `$upstream_status` shows 502 or `-`.
- Either all requests fail (upstream down, misconfigured address) or only some (one bad peer, large headers, crashes on specific requests).

## Common Causes: Nginx 502 Bad Gateway

### Cause 1: Upstream refused the connection

Evidence: `connect() failed (111: Connection refused)`. Nothing is listening at the upstream address/port. Full guide: nginx-connection-refused.md.

### Cause 2: Upstream closed or reset the connection before sending headers

Why: the application crashed or restarted mid-request, a worker was killed (for example OOM), a keep-alive idle timeout on the upstream that closes reused connections, or a protocol mismatch (for example an HTTPS-only upstream proxied with `http://`), in which case the upstream may close the connection or answer with something Nginx cannot parse as a valid response header.
Supporting evidence: `upstream prematurely closed connection while reading response header` or `Connection reset by peer`; application logs show crashes/restarts at the same timestamps.
Weakening evidence: the upstream logs a successful response for the same request.

### Cause 3: No live upstreams

Why: all servers in an `upstream` block are currently considered unavailable (passive failures reached `max_fails` within `fail_timeout`, defaults 1 and 10s), marked `down`, or limited by `max_conns`. With a single server in the group, passive marking is not applied.
Supporting evidence: `no live upstreams while connecting to upstream`, preceded by other errors for the same peers.
Weakening evidence: only one upstream server is configured.

### Cause 4: DNS resolution failure at request time

Why: when `proxy_pass` uses a variable, Nginx resolves the name at runtime via the `resolver` directive; failure yields 502.
Supporting evidence: `could not be resolved` in the error log.
Note: a hostname written literally in `proxy_pass` is resolved at configuration load; failure there is `host not found in upstream` and Nginx fails to start or reload (nginx-connection-refused.md).

### Cause 5: Response header larger than proxy_buffer_size

Why: large cookies, JWTs, or many headers exceed `proxy_buffer_size` (default 4k or 8k, one memory page).
Supporting evidence: `upstream sent too big header while reading response header from upstream`; affects specific users/endpoints.
Fix: increase `proxy_buffer_size` (and `proxy_buffers` if needed) moderately, and investigate why headers are so large.

### Cause 6: SELinux blocks outbound connections (Permission denied)

Supporting evidence: `(13: Permission denied) while connecting to upstream` on a SELinux-enforcing host; AVC denials in the audit log.
Fix: enable the documented boolean `setsebool -P httpd_can_network_connect 1` (persistent, state-changing) rather than disabling SELinux.

### Cause 7: TLS handshake to an HTTPS upstream fails

Why: upstream requires SNI (`proxy_ssl_server_name` is off by default), certificate verification fails when `proxy_ssl_verify on`, or protocol/cipher mismatch.
Supporting evidence: `SSL_do_handshake() failed` while SSL handshaking to upstream.
Fix: `proxy_ssl_server_name on;`, correct `proxy_ssl_name`, configure `proxy_ssl_trusted_certificate`. Do not turn verification off as a permanent fix.

## Diagnostic Steps: Nginx 502 triage

1. Observation: take the timestamp of a 502 from the access log and find the matching error log line.
2. Classify by the error phrase: refused / prematurely closed or reset / no live upstreams / could not be resolved / too big header / permission denied / SSL handshake.
3. Verify the upstream address from the `upstream:` field and test it from the Nginx host or container (see commands).
4. Correlate with upstream application logs (restarts, OOM kills, exceptions) at the same timestamps.
5. Check the configuration actually loaded with `nginx -T`.
6. Conclusion: one cause with its error-log evidence; if several phrases appear, address the earliest one first (for example refusals that later produce `no live upstreams`).

## Useful Commands: Nginx 502 diagnostics

Read-only:

- `nginx -t`: tests configuration syntax and referenced files.
- `nginx -T`: tests and prints the full effective configuration (includes all included files).
- Error log: commonly `/var/log/nginx/error.log` (path set by `error_log`; compiled default is `logs/error.log`). In the official nginx Docker image, logs go to the container stdout/stderr, so use `docker logs CONTAINER` or `kubectl logs POD`.
- `curl -sv http://UPSTREAM_HOST:PORT/health` from the Nginx host/container: tests the upstream directly, bypassing Nginx.
- `getsebool httpd_can_network_connect` (SELinux hosts): shows the boolean state.

State-changing:

- `nginx -s reload`: applies a new configuration gracefully (old workers finish requests). Run `nginx -t` first.

## Resolution: Nginx 502 Bad Gateway

Fix the upstream, not Nginx, when the upstream crashes or refuses. Adjust Nginx only for proxy-layer causes: buffer sizes for oversized headers, SNI/trust for TLS upstreams, runtime DNS (`resolver` with variable `proxy_pass`, or the upstream `server ... resolve` parameter, available in open-source Nginx since 1.27.3) for changing upstream IPs, and the SELinux boolean for permission denials.

## Prevention: Nginx 502

Log `$upstream_addr`, `$upstream_status`, `$upstream_connect_time`, `$upstream_response_time` in a custom `log_format`; health-check upstreams; deploy upstreams with graceful shutdown so in-flight requests complete.

## Diagnostic Differentiation: Nginx 502 vs 504 vs 503 vs 499

- 502: upstream connection refused/reset, invalid or oversized response header, DNS failure, no live upstream.
- 504 Gateway Timeout: upstream did not connect or respond within `proxy_connect_timeout` / `proxy_read_timeout` (nginx-upstream-timeout.md). `upstream timed out` lines belong there.
- 503: often Nginx rate limiting (`limit_req_status` default 503) or an upstream that itself returned 503; not a proxy failure.
- 499: Nginx access-log code meaning the client closed the connection before a response was sent; usually the client gave up waiting on a slow upstream.

## Related Errors: Nginx 502 Bad Gateway

Terms related to Nginx 502 Bad Gateway: `502 Bad Gateway`, `upstream prematurely closed connection`, `no live upstreams`, `could not be resolved`, `upstream sent too big header`, `Permission denied while connecting to upstream`, `SSL_do_handshake() failed`, `Connection reset by peer`.

## Root-Cause Summary: Nginx 502 Bad Gateway

Nginx 502 Bad Gateway. Typical rootCause statements: upstream refused the connection, upstream closed or reset before sending headers, no live upstreams, runtime DNS resolution failure, response header larger than proxy_buffer_size, SELinux permission denied, TLS handshake failure to the upstream. Severity: high when all requests return 502; medium when only some endpoints or peers fail. Recommendation pattern: always quote the matching error log phrase, because 502 alone is not a root cause. If the only evidence is a 502 status in the access log without the matching error log line, say that the cause is undetermined and ask for the error log rather than guessing.
