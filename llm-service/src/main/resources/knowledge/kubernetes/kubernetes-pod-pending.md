# Kubernetes Pod Pending (FailedScheduling, Insufficient cpu/memory, taints, unbound PVC)

Technology: Kubernetes scheduler, node resources, taints/tolerations, affinity, PersistentVolumeClaims, ResourceQuota. Scope: Pods that remain in phase `Pending` because they are not scheduled or their volumes are not bound. Out of scope: Pods scheduled but waiting on image pulls (kubernetes-imagepullbackoff.md).

## Problem: Kubernetes pod stuck in Pending

A Pod in `Pending` has been accepted by the API server but is not running. The common case is that the scheduler cannot find a node that satisfies the Pod's requirements and records a `FailedScheduling` event explaining why per node group. The Pod can also be Pending while waiting for volumes to bind or images to download.

## Common Log Messages: Kubernetes Pending and FailedScheduling

Canonical phrases (Kubernetes documentation/scheduler source; the full rendered event combines them with node counts and varies by version):

- Event reason: `FailedScheduling`
- `Insufficient cpu`, `Insufficient memory`
- `node(s) didn't match Pod's node affinity/selector`
- `node(s) had untolerated taint(s)` (often rendered with the taint, for example `{node-role.kubernetes.io/control-plane: }`)
- `pod has unbound immediate PersistentVolumeClaims`

Representative full event: `0/3 nodes are available: 1 node(s) had untolerated taint(s), 2 Insufficient memory. preemption: ...`

ResourceQuota rejections (representative): `exceeded quota: ...` (these usually block Pod creation at the API level; the Pod object is not created, so the error appears on the ReplicaSet/Deployment events instead).

## Symptoms: Kubernetes Pending pods

- `kubectl get pods` STATUS `Pending`; `kubectl get pods -o wide` shows NODE `<none>`.
- `kubectl describe pod` shows a `FailedScheduling` warning.
- A Deployment shows fewer available replicas than desired.
- PVC shows STATUS `Pending` in `kubectl get pvc`.

## Common Causes: Kubernetes FailedScheduling

### Cause 1: Insufficient cpu / memory (resource requests too large or cluster full)

Why: the scheduler places Pods by resource requests, not actual usage. If no node has enough unrequested allocatable capacity, the Pod waits.
Supporting evidence: `Insufficient cpu` or `Insufficient memory`; `kubectl describe node` "Allocated resources" near 100% of allocatable requests.
Weakening evidence: nodes have ample unrequested capacity; then check other predicates.

### Cause 2: nodeSelector / node affinity matches no node

Supporting evidence: `didn't match Pod's node affinity/selector`; labels required by the Pod do not exist on any node (`kubectl get nodes --show-labels`).
Weakening evidence: matching labels exist on Ready nodes.

### Cause 3: Taints without matching tolerations

Supporting evidence: `had untolerated taint(s)`; `kubectl describe node` lists taints such as NoSchedule.
Weakening evidence: Pod tolerations match all NoSchedule taints of candidate nodes.

### Cause 4: Unbound PersistentVolumeClaim

Why: no PersistentVolume matches the claim, the StorageClass cannot provision, or volume topology constraints conflict.
Supporting evidence: `pod has unbound immediate PersistentVolumeClaims`; `kubectl get pvc` shows `Pending`; `kubectl describe pvc` shows provisioning events.
Weakening evidence: PVC `Bound`.

### Cause 5: No Ready nodes or cluster autoscaler not scaling

Supporting evidence: `kubectl get nodes` shows NotReady nodes or no nodes for the required pool.

## Diagnostic Steps: Kubernetes Pod Pending

1. Observation: confirm `Pending` and NODE `<none>` with `kubectl get pods -o wide`.
2. Read the scheduler's reason: `kubectl describe pod POD`, Events section, `FailedScheduling` message.
3. Hypothesis from the phrases: Insufficient = capacity/requests; affinity/selector = labels; taint = tolerations; unbound PVC = storage.
4. Verify capacity: `kubectl describe node NODE`, compare Pod requests (`kubectl get pod POD -o jsonpath='{.spec.containers[*].resources}'`).
5. Verify storage: `kubectl get pvc` and `kubectl describe pvc PVC`.
6. If NODE is assigned but status remains Pending/ContainerCreating, the problem is after scheduling: image pull (kubernetes-imagepullbackoff.md) or volume attach/mount events.
7. Conclusion: name the predicate that fails and the evidence.

## Useful Commands: Kubernetes Pending diagnostics

Read-only:

- `kubectl get pods -o wide`: status and assigned node.
- `kubectl describe pod POD`: FailedScheduling message.
- `kubectl get events --field-selector involvedObject.name=POD --sort-by=.metadata.creationTimestamp`: event history.
- `kubectl describe node NODE`: allocatable, allocated requests, taints, conditions.
- `kubectl get nodes --show-labels`: labels for selector/affinity matching.
- `kubectl get pvc` / `kubectl describe pvc PVC`: claim binding status and provisioning events.
- `kubectl get resourcequota -n NAMESPACE` / `kubectl describe resourcequota -n NAMESPACE`: quota usage vs hard limits.

## Resolution: Kubernetes Pod Pending

- Insufficient resources: right-size requests from observed usage, add node capacity, or remove stuck workloads. Do not drop requests to zero just to schedule; that removes scheduling guarantees.
- Selector/affinity: correct the Pod rule or label the intended nodes (`kubectl label node`, state-changing).
- Taints: add a matching toleration only for workloads intended for those nodes; do not remove control-plane taints to fit workloads.
- PVC: fix the StorageClass name, ensure a provisioner exists, or create a matching PV.
- Quota: adjust requests or the quota with the namespace owner.

## Prevention: Kubernetes scheduling failures

Set requests based on measurements, use LimitRange defaults, monitor allocatable vs requested capacity, and validate StorageClass names in manifests.

## Diagnostic Differentiation: Pending vs other Kubernetes states

- Pending + NODE `<none>` + FailedScheduling: this document.
- Pending/ContainerCreating with a node assigned and `Failed to pull image`: kubernetes-imagepullbackoff.md.
- CrashLoopBackOff: the Pod was scheduled and ran (kubernetes-crashloopbackoff.md).
- Deployment not progressing because new Pods are Pending: the Deployment symptom (`ProgressDeadlineExceeded`) is in kubernetes-deployment-failure.md; the cause is here.

## Related Errors: Kubernetes Pod Pending and FailedScheduling

Terms related to Kubernetes Pod Pending and FailedScheduling: `FailedScheduling`, `0/N nodes are available`, `Insufficient cpu`, `Insufficient memory`, `untolerated taint`, `node affinity/selector`, `unbound immediate PersistentVolumeClaims`, `exceeded quota`.

## Root-Cause Summary: Kubernetes Pod Pending

Kubernetes Pod Pending. Typical rootCause statements: insufficient allocatable CPU or memory for the Pod requests, nodeSelector or affinity matching no node, untolerated taints, unbound PersistentVolumeClaim, no Ready nodes. Severity: high when replicas cannot be replaced after node failure; medium during scale-out. Recommendation pattern: quote the FailedScheduling phrases and state which predicate failed and on how many nodes. Pending is not a container failure: there are no application logs to read, so the evidence must come from scheduler events, node allocations, and PVC status.
