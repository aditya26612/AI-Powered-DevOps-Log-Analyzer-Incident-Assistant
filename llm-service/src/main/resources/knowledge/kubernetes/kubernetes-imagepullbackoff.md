# Kubernetes ImagePullBackOff and ErrImagePull

Technology: Kubernetes (kubelet image pulls, imagePullSecrets, imagePullPolicy) with container registries. Scope: Pods stuck in `ErrImagePull`, `ImagePullBackOff`, `InvalidImageName`, or `ErrImageNeverPull`. Registry-side causes are shared with docker-image-pull-error.md; this document focuses on the Kubernetes-specific layer.

## Problem: Kubernetes cannot pull the container image

The kubelet on the node asks the container runtime to pull the image. If the pull fails, the container reason becomes `ErrImagePull`; after repeated failures the kubelet backs off and reports `ImagePullBackOff`, retrying with increasing delay capped at 5 minutes per the Kubernetes Images documentation. The container never starts, so there are no application logs.

## Common Log Messages: Kubernetes image pull failure

Canonical (Kubernetes documentation/kubelet source):

- Waiting reasons: `ErrImagePull`, `ImagePullBackOff`, `InvalidImageName`, `ErrImageNeverPull`
- Events: `Failed to pull image "IMAGE": ...` and `Back-off pulling image "IMAGE"`
- `FailedToRetrieveImagePullSecret` with message `Unable to retrieve some image pull secrets (...)`

Representative (the text after `Failed to pull image` comes from the runtime and registry, and varies):

- `... not found` / `manifest unknown` (tag does not exist)
- `... pull access denied ...` / `401 Unauthorized` / `authorization failed`
- `... 429 Too Many Requests` / `toomanyrequests` (registry rate limit)
- `... no match for platform in manifest` (architecture mismatch)
- `... x509: certificate signed by unknown authority`
- `... dial tcp: lookup REGISTRY: no such host` / `i/o timeout`

## Symptoms: Kubernetes ImagePullBackOff

- `kubectl get pods` STATUS shows `ErrImagePull` or `ImagePullBackOff`; RESTARTS stays 0.
- `kubectl logs` returns an error that the container is waiting to start.
- New Deployment revisions do not become available; old ReplicaSet keeps serving if a rolling update is in progress.

## Key Facts: Kubernetes image pull behavior

- Default `imagePullPolicy`: `Always` when the tag is `:latest` or omitted; `IfNotPresent` otherwise. `Never` uses only a local image and fails with `ErrImageNeverPull` if absent.
- `imagePullSecrets` must reference a Secret in the same namespace as the Pod, of type `kubernetes.io/dockerconfigjson` (or legacy `kubernetes.io/dockercfg`).
- Pulls run on the node, so node-level network, DNS, proxy, CA trust and architecture all matter.

## Common Causes: Kubernetes ErrImagePull

### Cause 1: Wrong image name, tag, or registry (not found)

Supporting evidence: event `Failed to pull image ... not found` / `manifest unknown`; typo visible in `kubectl get pod POD -o jsonpath='{.spec.containers[*].image}'`.
Weakening evidence: the same reference pulls from a workstation with the same credentials.

### Cause 2: Missing or wrong imagePullSecret (private registry)

Supporting evidence: `unauthorized` / `pull access denied` / `FailedToRetrieveImagePullSecret`; Pod spec has no `imagePullSecrets`, or the Secret is in another namespace or has the wrong registry host.
Weakening evidence: public image, or other Pods using the same Secret pull fine on the same node.

### Cause 3: Registry rate limiting

Supporting evidence: `429` / `toomanyrequests`, many nodes pulling the same public image simultaneously.
Weakening evidence: private registry without rate limits, single isolated failure.

### Cause 4: Architecture mismatch

Supporting evidence: `no match for platform`; nodes are arm64 and image is amd64-only (check `kubectl get node NODE -o jsonpath='{.status.nodeInfo.architecture}'`).
Weakening evidence: image index includes the node architecture.

### Cause 5: Node cannot reach the registry (DNS, proxy, firewall, TLS)

Supporting evidence: `no such host`, `i/o timeout`, `x509` errors; failures limited to certain nodes or all images from one registry.
Weakening evidence: other images from the same registry pull fine on that node.

### Cause 6: InvalidImageName

Supporting evidence: reason `InvalidImageName`; the image string is malformed (for example uppercase repository name, illegal characters, empty variable substitution in a template).

## Diagnostic Steps: Kubernetes ImagePullBackOff

1. Observation: `kubectl describe pod POD`; read the `Failed to pull image` event text completely.
2. Hypothesis from the message tail: not found / unauthorized / 429 / platform / network-TLS / invalid name.
3. Verify the exact image reference in the Pod spec.
4. Verify secret wiring: Pod `imagePullSecrets`, Secret existence and type in the Pod namespace.
5. Verify node scope: are failures on one node or all nodes (`kubectl get pods -o wide`)?
6. Conclusion: fix the image reference, credentials, registry access, or platform, then let the kubelet retry (or trigger a new rollout).

## Useful Commands: Kubernetes image pull diagnostics

Read-only:

- `kubectl describe pod POD`: events with the full pull error.
- `kubectl get events --field-selector involvedObject.name=POD --sort-by=.metadata.creationTimestamp`: chronological pull attempts.
- `kubectl get pod POD -o jsonpath='{.spec.containers[*].image}'`: image references.
- `kubectl get pod POD -o jsonpath='{.spec.imagePullSecrets}'`: referenced pull secrets.
- `kubectl get secret SECRET -n NAMESPACE -o jsonpath='{.type}'`: Secret type (do not print the secret data into shared logs).
- `kubectl get node NODE -o jsonpath='{.status.nodeInfo.architecture}'`: node CPU architecture.

## Resolution: Kubernetes ImagePullBackOff

- Correct the image reference; prefer immutable tags or digests over `latest`.
- Create a registry Secret of type `kubernetes.io/dockerconfigjson` in the Pod's namespace and reference it via `imagePullSecrets` (or attach it to the ServiceAccount).
- Use authenticated pulls or a mirror/pull-through cache for rate limits.
- Publish images for all node architectures, or constrain scheduling to matching nodes.
- Fix node DNS/proxy/CA trust for the registry. Do not disable TLS verification on nodes as a routine fix.

## Prevention: Kubernetes image pull failures

Validate image references in CI, pin digests, mirror critical images, manage pull secrets centrally, and monitor registry rate-limit errors.

## Diagnostic Differentiation: ImagePullBackOff vs other Kubernetes waiting states

- `ImagePullBackOff`: image never pulled, no logs, restarts 0.
- `CrashLoopBackOff`: image pulled, container ran and terminated, restarts > 0 (kubernetes-crashloopbackoff.md).
- `CreateContainerConfigError`: image fine, referenced ConfigMap/Secret missing (kubernetes-deployment-failure.md).
- `Pending` with no node assigned: scheduling issue, image pull not attempted yet (kubernetes-pod-pending.md).
- Same registry errors outside Kubernetes: docker-image-pull-error.md.

## Related Errors: Kubernetes ImagePullBackOff and ErrImagePull

Terms related to Kubernetes ImagePullBackOff and ErrImagePull: `ErrImagePull`, `ImagePullBackOff`, `Back-off pulling image`, `Failed to pull image`, `InvalidImageName`, `ErrImageNeverPull`, `FailedToRetrieveImagePullSecret`, `manifest unknown`, `unauthorized`, `429 Too Many Requests`.

## Root-Cause Summary: Kubernetes ImagePullBackOff and ErrImagePull

Kubernetes ImagePullBackOff. Typical rootCause statements: wrong image reference, missing or wrong imagePullSecret in the Pod namespace, registry rate limit, architecture mismatch between image and node, node cannot reach the registry. Severity: high for new deployments and scale-outs; running Pods keep working. Recommendation pattern: quote the text after Failed to pull image, it names the cause class.
