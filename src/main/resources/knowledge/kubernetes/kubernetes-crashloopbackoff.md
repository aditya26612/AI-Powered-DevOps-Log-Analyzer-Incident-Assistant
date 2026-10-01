# Kubernetes CrashLoopBackOff (Back-off restarting failed container)

Technology: Kubernetes (kubelet, Pod lifecycle, probes). Scope: a container in a Pod that starts, terminates, and is restarted repeatedly with increasing delay. Out of scope: Pods that never get scheduled (see kubernetes-pod-pending.md), images that cannot be pulled (see kubernetes-imagepullbackoff.md), missing ConfigMap/Secret at container creation (see kubernetes-deployment-failure.md).

## Problem: Kubernetes pod in CrashLoopBackOff

`CrashLoopBackOff` is not a root cause. It is the kubelet's state while it waits before restarting a container that keeps terminating. The real cause is whatever made the container exit: an application error, a killed process (OOMKilled), or a failing liveness/startup probe. Per current Kubernetes Pod lifecycle documentation, restart delays grow exponentially (10s, 20s, 40s, ...) capped at 5 minutes, and the backoff resets after a container runs successfully for 10 minutes. These defaults can be changed by feature gates/kubelet configuration in recent versions.

## Common Log Messages: Kubernetes CrashLoopBackOff

Canonical (Kubernetes documentation/source):

- Pod STATUS / waiting reason: `CrashLoopBackOff`
- Event: `Back-off restarting failed container` (newer versions may append the container name, for example `Back-off restarting failed container=app in pod ...`)
- Terminated reason: `OOMKilled` with `exitCode: 137`
- Terminated reason: `Error` or `Completed`, with an `Exit Code`
- Probe event example: `Liveness probe failed: ...` followed by the probe output (for example an HTTP status code or command output)

## Symptoms: Kubernetes container restart loop

- `kubectl get pods` shows `CrashLoopBackOff` (alternating with `Running` or `Error`) and a growing RESTARTS count.
- `kubectl describe pod` shows `Last State: Terminated` with `Reason` and `Exit Code`.
- `kubectl logs POD` may be empty because the current container has just restarted; `--previous` shows the crashed instance.

## Common Causes: Kubernetes CrashLoopBackOff

### Cause 1: Application exits on startup (Exit Code 1 or other non-zero)

Why: configuration error, missing environment variable, unreachable dependency at startup (for example a database), or an unhandled exception.
Supporting evidence: `Last State: Terminated, Reason: Error, Exit Code: 1`; `kubectl logs POD --previous` shows a stack trace (for Spring Boot: `APPLICATION FAILED TO START`).
Weakening evidence: `Reason: OOMKilled`, or a liveness probe failure event right before each restart.

### Cause 2: Container killed for memory (OOMKilled, Exit Code 137)

Why: the container exceeded its memory limit.
Supporting evidence: `Reason: OOMKilled`, `Exit Code: 137` in `lastState.terminated`.
Weakening evidence: Exit Code 137 with a different reason; then look for a probe kill or eviction. See docker-container-resource-error.md for JVM/heap differentiation.

### Cause 3: Liveness or startup probe kills a healthy-but-slow container

Why: when a liveness or startup probe fails beyond its threshold, the kubelet kills and restarts the container. Slow startup plus an aggressive liveness probe produces a restart loop even when the application is fine.
Supporting evidence: `Liveness probe failed` / `Startup probe failed` events preceding each restart; the application logs show normal startup progress that is interrupted; the exit code reflects the termination signal (commonly 143 for SIGTERM or 137 if the process had to be SIGKILLed after the grace period), depending on how the process handles SIGTERM.
Weakening evidence: no probe events; the app exits by itself with an error.
Note: a failing readiness probe does NOT restart the container; it removes the Pod from Service endpoints (see kubernetes-service-unreachable.md).

### Cause 4: Command/entrypoint wrong or not executable

Why: same as Docker exit 126/127.
Supporting evidence: exit code 126 or 127, or an event mentioning the runtime could not start the process; empty logs.
Weakening evidence: the application prints output before exiting. See docker-container-startup-failure.md.

### Cause 5: Process completes normally but restartPolicy is Always

Why: a Deployment requires restartPolicy Always, so a one-shot command that exits 0 is restarted forever.
Supporting evidence: `Reason: Completed`, `Exit Code: 0`.
Weakening evidence: non-zero exit code. Use a Job for one-shot work.

## Diagnostic Steps: Kubernetes CrashLoopBackOff

1. Observation: confirm `CrashLoopBackOff` and restart count with `kubectl get pods`.
2. Read the last termination: `kubectl describe pod POD` and look at `Last State: Terminated` (Reason, Exit Code, Started, Finished) and the Events list.
3. Hypothesis by reason/exit code: OOMKilled/137 = memory; Error/1 = application; probe events = probe configuration; 126/127 = command; Completed/0 = wrong workload type.
4. Verify with logs from the crashed instance: `kubectl logs POD -c CONTAINER --previous`.
5. For probe hypotheses, compare the time the app needs to become healthy with `initialDelaySeconds`, `periodSeconds`, `failureThreshold`, or the startup probe settings.
6. For dependency hypotheses (database refused/auth), follow the matching document (spring-boot-datasource-error.md, postgresql-connection-refused.md).
7. Conclusion: state the evidence (reason, exit code, log line, event) that supports the cause.

## Useful Commands: Kubernetes CrashLoopBackOff diagnostics

Read-only:

- `kubectl get pods -o wide`: status, restarts, node.
- `kubectl describe pod POD`: container state, last termination reason and exit code, probe configuration, events.
- `kubectl logs POD -c CONTAINER --previous`: logs of the previous (crashed) container instance.
- `kubectl get events --field-selector involvedObject.name=POD --sort-by=.metadata.creationTimestamp`: chronological events for the Pod.
- `kubectl get pod POD -o jsonpath='{.status.containerStatuses[*].lastState.terminated}'`: raw last termination (reason, exitCode).
- `kubectl top pod POD`: current CPU/memory usage; requires Metrics Server.

## Resolution: Kubernetes CrashLoopBackOff

- Application error: fix configuration/code according to the `--previous` logs; make the app tolerate dependency startup delays (retry) instead of crashing permanently.
- OOMKilled: raise the memory limit to observed peak plus headroom, or reduce memory use; for JVMs size the heap below the limit.
- Probe kills: add a startupProbe for slow-starting apps, or relax liveness thresholds; keep liveness checks independent of downstream dependencies so a database outage does not restart every Pod.
- Command errors: fix the image ENTRYPOINT/CMD or the Pod `command`/`args`.
- Completed/0: use a Job/CronJob for run-to-completion tasks.

Deleting the Pod (`kubectl delete pod POD`, state-changing) only creates a replacement with the same spec; it does not fix the cause and discards the current container state.

## Prevention: Kubernetes CrashLoopBackOff

Set realistic resource requests/limits, use startup probes for slow starters, validate configuration in CI, and alert on restart counts.

## Diagnostic Differentiation: CrashLoopBackOff vs similar Kubernetes states

- `ImagePullBackOff` / `ErrImagePull`: image never pulled, container never ran (kubernetes-imagepullbackoff.md).
- `Pending` with `FailedScheduling`: Pod not placed on a node (kubernetes-pod-pending.md).
- `CreateContainerConfigError`: container cannot be created because referenced config is missing (kubernetes-deployment-failure.md).
- Running but `0/1 READY`: readiness probe failing, no restarts; Service has no ready endpoints (kubernetes-service-unreachable.md).
- Rollout stuck because new Pods crash: the Deployment symptom is `ProgressDeadlineExceeded` (kubernetes-deployment-failure.md); the root cause is still found here.

## Related Errors: Kubernetes CrashLoopBackOff

Terms related to Kubernetes CrashLoopBackOff: `Back-off restarting failed container`, `OOMKilled`, `Exit Code 137`, `Exit Code 1`, `Liveness probe failed`, `Startup probe failed`, `Error`, `Completed`.

## Root-Cause Summary: Kubernetes CrashLoopBackOff

Kubernetes CrashLoopBackOff. Typical rootCause statements: application exits on startup (exit code 1 with a stack trace in the previous logs), container OOMKilled (exit code 137), liveness or startup probe kills a slow container, wrong command (126 or 127), one-shot process run as a Deployment (Completed, exit 0). Severity: high when all replicas crash; medium when only new rollout Pods crash and old Pods still serve. Recommendation pattern: cite reason, exit code and the last log line from kubectl logs --previous.
