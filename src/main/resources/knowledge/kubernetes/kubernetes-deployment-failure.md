# Kubernetes Deployment Rollout Failure (ProgressDeadlineExceeded, rollout stuck, CreateContainerConfigError)

Technology: Kubernetes Deployments, ReplicaSets, rolling updates. Scope: a new Deployment revision does not become available, `kubectl rollout status` hangs or fails, or Pods cannot be created from the template. This document explains the Deployment-level symptom and routes to the Pod-level cause.

## Problem: Kubernetes deployment rollout stuck or failed

During a rolling update, the Deployment controller creates a new ReplicaSet and scales it up while scaling the old one down, bounded by `maxUnavailable` and `maxSurge` (both default to 25%). If new Pods never become available, the rollout stalls. After `progressDeadlineSeconds` (default 600 seconds) the Deployment reports `Progressing=False` with reason `ProgressDeadlineExceeded`. Kubernetes does NOT automatically roll back; it keeps trying.

## Common Log Messages: Kubernetes rollout failure

Canonical (Kubernetes Deployment documentation and source):

- `error: deployment "NAME" exceeded its progress deadline` (from `kubectl rollout status`, which exits non-zero)
- Condition `Progressing` status `False`, reason `ProgressDeadlineExceeded`
- Condition `ReplicaFailure` status `True`, reason `FailedCreate` (ReplicaSet cannot create Pods, for example quota or admission rejection)
- Waiting reason `CreateContainerConfigError` (container config could not be generated, typically a missing ConfigMap/Secret or key)

Representative:

- `Waiting for deployment "NAME" rollout to finish: 1 out of 3 new replicas have been updated...`
- ReplicaSet event: `Error creating: pods "..." is forbidden: exceeded quota: ...`
- Event for CreateContainerConfigError: `Error: configmap "NAME" not found` / `Error: secret "NAME" not found` / `couldn't find key ... in ConfigMap` (wording varies by version)

## Symptoms: Kubernetes rollout not progressing

- `kubectl rollout status deployment/NAME` waits and eventually fails.
- `kubectl get deploy` shows `UP-TO-DATE` lower than desired or `AVAILABLE` lower than `READY` expectations.
- Two ReplicaSets exist: the old one still serving, the new one with Pods in CrashLoopBackOff, ImagePullBackOff, Pending, or CreateContainerConfigError.

## Common Causes: Kubernetes Deployment failure

### Cause 1: New Pods crash or never become ready

Supporting evidence: new ReplicaSet Pods in `CrashLoopBackOff` or `0/1 READY`.
Next step: kubernetes-crashloopbackoff.md (crash) or kubernetes-service-unreachable.md (readiness).

### Cause 2: New image cannot be pulled

Supporting evidence: new Pods `ErrImagePull` / `ImagePullBackOff` after an image tag change.
Next step: kubernetes-imagepullbackoff.md.

### Cause 3: New Pods cannot be scheduled

Supporting evidence: new Pods `Pending` with `FailedScheduling` (for example larger requests in the new revision, or maxSurge requires extra capacity).
Next step: kubernetes-pod-pending.md.

### Cause 4: Missing ConfigMap/Secret or key (CreateContainerConfigError)

Why: env vars or volumes reference a ConfigMap/Secret or key that does not exist in the namespace and is not marked `optional: true`.
Supporting evidence: waiting reason `CreateContainerConfigError`; event names the missing object/key.
Weakening evidence: `kubectl get configmap NAME` / `kubectl get secret NAME` in the Pod namespace returns the object with the key.

### Cause 5: ReplicaSet cannot create Pods (ReplicaFailure / FailedCreate)

Why: ResourceQuota exceeded, admission webhook or Pod Security rejection, missing ServiceAccount.
Supporting evidence: `ReplicaFailure=True`, `FailedCreate` events on the ReplicaSet; no new Pods appear at all.

## Diagnostic Steps: Kubernetes rollout failure

1. Observation: `kubectl rollout status deployment/NAME` and `kubectl describe deployment NAME` (Conditions, Events).
2. Identify the new ReplicaSet: `kubectl get rs -l LABEL` (highest revision / newest age).
3. Check whether new Pods exist. None -> Cause 5 (`kubectl describe rs NEW_RS`). Present -> inspect their status.
4. Map Pod status to cause: CrashLoopBackOff, ImagePullBackOff, Pending, CreateContainerConfigError, or Running-but-not-ready.
5. Verify with `kubectl describe pod` and `kubectl logs --previous` for the new Pods.
6. Decide: fix forward (correct the manifest) or roll back to the previous known-good revision.

## Useful Commands: Kubernetes rollout diagnostics

Read-only:

- `kubectl rollout status deployment/NAME`: waits for progress, returns non-zero if the deadline is exceeded.
- `kubectl rollout history deployment/NAME`: revisions (add `--revision=N` for detail).
- `kubectl describe deployment NAME`: conditions (`Progressing`, `Available`, `ReplicaFailure`), strategy, events.
- `kubectl get rs -l app=NAME`: ReplicaSets of the Deployment with desired/current/ready counts (use the Deployment's selector labels).
- `kubectl describe rs RS_NAME`: `FailedCreate` events.
- `kubectl get configmap NAME -n NAMESPACE` and `kubectl get secret NAME -n NAMESPACE`: existence of referenced config (avoid printing secret values).

State-changing:

- `kubectl rollout undo deployment/NAME` (optionally `--to-revision=N`): rolls back to a previous revision by creating a new rollout with the old template. Check `rollout history` first; data/schema migrations performed by the new version are not reverted.
- `kubectl rollout restart deployment/NAME`: restarts all Pods by triggering a new rollout; does not fix template errors.

## Resolution: Kubernetes deployment failure

- Fix the underlying Pod-level cause (crash, pull, scheduling, missing config) and apply the corrected manifest.
- For an urgent production outage, roll back with `kubectl rollout undo` after confirming the target revision, then fix forward.
- Create missing ConfigMaps/Secrets before the Deployment, or mark truly optional references `optional: true`.
- Ensure capacity for `maxSurge`, or tune `maxSurge`/`maxUnavailable` to the cluster's spare capacity.

## Prevention: Kubernetes rollout failures

Use readiness probes so bad revisions never receive traffic, keep `progressDeadlineSeconds` aligned with real startup time, validate manifests in CI, and deploy config objects before workloads.

## Diagnostic Differentiation: rollout failure vs Pod failures

- `ProgressDeadlineExceeded` is a symptom; the cause lives in the new Pods' status.
- `CreateContainerConfigError` is unique to this document: the container never started, unlike CrashLoopBackOff.
- `FailedCreate` with no new Pods: quota/admission problem, not an application problem.
- Old Pods still serve traffic during a stalled rolling update, so severity is often lower than a full outage unless `maxUnavailable` already removed capacity.

## Related Errors: Kubernetes Deployment rollout failure

Terms related to Kubernetes Deployment rollout failure: `ProgressDeadlineExceeded`, `exceeded its progress deadline`, `ReplicaFailure`, `FailedCreate`, `CreateContainerConfigError`, `configmap not found`, `secret not found`, `rollout undo`, `maxUnavailable`, `maxSurge`.

## Root-Cause Summary: Kubernetes Deployment Rollout Failure

Kubernetes Deployment rollout failure. Typical rootCause statements: new Pods crash, image cannot be pulled, new Pods cannot be scheduled, missing ConfigMap or Secret (CreateContainerConfigError), ReplicaSet FailedCreate due to quota or admission. Severity: often medium because old Pods keep serving during a stalled rolling update; high if capacity was already reduced. Recommendation pattern: name the new Pod status and decide between fix forward and a verified rollout undo.
