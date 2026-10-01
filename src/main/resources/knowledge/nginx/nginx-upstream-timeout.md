# Nginx Upstream Timeout (504 Gateway Timeout, upstream timed out (110: Connection timed out))

Technology: Nginx reverse proxy timeouts (ngx_http_proxy_module, ngx_http_upstream_module). Scope: Nginx gives up waiting for an upstream to accept a connection or send a response. Out of scope: immediate refusals (nginx-connection-refused.md) and other 502 causes (nginx-bad-gateway.md).

## Problem: Nginx upstream timed out

Nginx has separate timers for connecting to the upstream, sending the request, and reading the response. When one expires, Nginx logs `upstream timed out` and, if no other upstream is tried successfully, returns 504 Gateway Timeout. The phrase after "while" in the error log tells which phase timed out, and the phase points to very different root causes.

## Common Log Messages: Nginx upstream timeout

Canonical Nginx error log forms (errno 110 is the Linux value):

- `upstream timed out (110: Connection timed out) while connecting to upstream`
- `upstream timed out (110: Connection timed out) while reading response header from upstream`
- `upstream timed out (110: Connection timed out) while sending request to upstream` (less common)
- Variant: `upstream timed out (110: Connection timed out) while reading upstream` (timeout after headers, during the body)

Access log: status `504`. Client sees `504 Gateway Time-out`.

## Symptoms: Nginx 504 Gateway Timeout

- Requests hang for about 60 seconds (the default timeouts) and then fail with 504.
- Only slow endpoints (reports, exports, batch calls) fail, while fast ones succeed: read timeout.
- All requests to an upstream fail after the same delay: connect timeout (network path or overloaded listener).
- Access log may also show 499 when clients disconnect before Nginx's timeout.

## Key Facts: Nginx timeout directives and defaults

From the official ngx_http_proxy_module documentation:

- `proxy_connect_timeout 60s;` timeout for establishing the upstream connection; Nginx notes it usually cannot exceed 75 seconds.
- `proxy_read_timeout 60s;` timeout between two successive read operations, not for the whole response.
- `proxy_send_timeout 60s;` timeout between two successive write operations, not for the whole request.
- `proxy_next_upstream error timeout;` (default) retries the next server on connection errors and timeouts, but only if nothing has been sent to the client yet; non-idempotent requests (POST, LOCK, PATCH) are not retried after being sent upstream unless `non_idempotent` is specified.
- Passive health: `max_fails=1` and `fail_timeout=10s` by default per upstream server; timeouts count as failures under the default `proxy_next_upstream` setting.

## Common Causes: Nginx upstream timed out

### Cause 1: Slow upstream response (read timeout)

Why: long-running queries, slow downstream dependencies (database, external API), thread pool or connection pool exhaustion in the application, GC pauses.
Supporting evidence: `while reading response header from upstream`; `$upstream_connect_time` small but `$upstream_response_time` near 60s; application logs show slow requests or `Connection is not available, request timed out` (spring-boot-datasource-error.md).
Weakening evidence: the timeout occurs `while connecting`.

### Cause 2: Network path drops packets (connect timeout)

Why: firewall/security group silently dropping SYN packets, wrong IP that routes nowhere, NetworkPolicy blocking traffic.
Supporting evidence: `while connecting to upstream`; `curl` from the Nginx host to the upstream hangs instead of being refused.
Weakening evidence: immediate `Connection refused` (nothing listening, but the path works; see nginx-connection-refused.md).

### Cause 3: Upstream accept queue saturated

Why: the upstream process is overloaded and cannot accept new connections fast enough.
Supporting evidence: connect timeouts only under load, upstream CPU saturated, many pending connections.

### Cause 4: Timeout shorter than legitimate processing time

Why: a known long operation (export, report) legitimately takes longer than 60s.
Supporting evidence: only specific endpoints, consistent durations just above the timeout, upstream completes the work after Nginx gave up.
Fix: raise `proxy_read_timeout` for those locations only, or make the operation asynchronous.

## Diagnostic Steps: Nginx upstream timeout

1. Observation: read the phase in the error log (`while connecting` vs `while reading response header`).
2. Hypothesis: connecting -> network path or overloaded listener; reading -> slow application or dependency.
3. Verify timing with upstream log variables in the access log.
4. Verify directly: time a request from the Nginx host to the upstream (`curl -sv -o /dev/null -w '%{time_connect} %{time_starttransfer}\n' URL`).
5. Correlate with application metrics/logs: slow queries, pool waits, GC, CPU.
6. Conclusion: network vs application slowness vs legitimately long operation.

## Useful Commands: Nginx timeout diagnostics

Read-only:

- `nginx -T | grep -n -E 'proxy_(connect|read|send)_timeout'`: shows configured timeouts (absent means the 60s defaults).
- Access log with a custom format including `$upstream_connect_time`, `$upstream_header_time`, `$upstream_response_time`, `$upstream_addr`, `$upstream_status` (example `log_format` below; adding it requires a config change and reload).
- `curl -sv -o /dev/null -w '%{time_connect} %{time_starttransfer}\n' http://UPSTREAM:PORT/path`: measures connect time and time to first byte from the proxy host.

Example log format (configuration change):

```nginx
log_format upstream_diag '$remote_addr "$request" $status '
                         'upstream=$upstream_addr ustatus=$upstream_status '
                         'connect=$upstream_connect_time header=$upstream_header_time '
                         'response=$upstream_response_time';
```

## Resolution: Nginx 504 Gateway Timeout

- Fix the slow upstream (query optimization, pool sizing, dependency timeouts shorter than the proxy timeout) before raising Nginx timeouts.
- Raise `proxy_read_timeout` only for specific locations that legitimately need it.
- For connect timeouts, fix firewall rules, security groups, NetworkPolicies, or the target address.
- Be careful with retries: `proxy_next_upstream` can multiply load on a struggling upstream; use `proxy_next_upstream_tries` / `proxy_next_upstream_timeout` to bound them.

## Prevention: Nginx upstream timeouts

Align timeouts across layers (client > proxy > application > database), log upstream timings, and alert on rising `$upstream_response_time` percentiles.

## Diagnostic Differentiation: Nginx timeout vs refused vs 502

- `upstream timed out ... while connecting` (slow failure) vs `connect() failed (111: Connection refused)` (instant failure): timeout means no answer, refused means an active rejection, usually nothing listening.
- `upstream timed out ... while reading response header`: application slowness, not networking.
- `upstream prematurely closed connection`: the upstream closed the connection (crash/restart), which is 502 (nginx-bad-gateway.md).
- 499 in the access log: the client disconnected first; often a symptom of the same slowness.

## Related Errors: Nginx upstream timeout and 504 Gateway Timeout

Terms related to Nginx upstream timeout and 504 Gateway Timeout: `504 Gateway Timeout`, `upstream timed out`, `110: Connection timed out`, `proxy_read_timeout`, `proxy_connect_timeout`, `499`, `while reading response header from upstream`, `while connecting to upstream`.

## Root-Cause Summary: Nginx Upstream Timeout

Nginx upstream timeout. Typical rootCause statements: slow upstream response (read timeout, often slow queries or pool waits), packets dropped on the network path (connect timeout), saturated upstream accept queue, timeout shorter than a legitimately long operation. Severity: high when widespread because requests hold resources for the full timeout; medium for isolated slow endpoints. Recommendation pattern: state the timed-out phase and fix the slow layer before raising timeouts. Distinguish clearly: a 504 with upstream timed out points at slowness or a silent network path, while an immediate 502 with connection refused points at a missing listener, which is handled in nginx-connection-refused.md.
