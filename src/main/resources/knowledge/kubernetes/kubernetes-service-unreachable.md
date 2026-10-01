# Kubernetes Service Unreachable (no endpoints, selector mismatch, targetPort, DNS, NetworkPolicy)

Technology: Kubernetes Services, EndpointSlices, cluster DNS, kube-proxy, NetworkPolicy, readiness probes. Scope: clients inside or outside the cluster cannot reach a workload through its Service name or ClusterIP while Pods exist. Out of scope: Pods not running at all (kubernetes-crashloopbackoff.md, kubernetes-pod-pending.md).

## Problem: Kubernetes Service not reachable or returns connection refused

A Service routes traffic only to Pods that its selector matches AND that are ready. A Service can exist with zero usable endpoints. Requests then fail with connection refused, timeouts, or 502/503 from an ingress or reverse proxy. Other failure points are port mapping (`port` vs `targetPort`), DNS names, and NetworkPolicy.

## Common Log Messages: Kubernetes Service unreachable

These are representative client-side messages; Kubernetes itself does not emit one universal "no endpoints" error:

- `connect: connection refused` / `java.net.ConnectException: Connection refused` when calling `SERVICE:PORT`
- `i/o timeout` / `context deadline exceeded` / `Connection timed out`
- `could not resolve host` / `no such host` / `UnknownHostException: SERVICE.NAMESPACE.svc.cluster.local` (DNS)
- Ingress/Nginx: `502 Bad Gateway` or `503 Service Unavailable` when the backend Service has no endpoints (exact behavior depends on the ingress controller)
- Readiness event: `Readiness probe failed: ...`

## Symptoms: Kubernetes Service has no endpoints

- Pods are `Running` but `READY` shows `0/1`.
- `kubectl get endpointslices -l kubernetes.io/service-name=SERVICE` shows no endpoints, or endpoints with `ready: false`.
- Calling the Pod IP directly works, calling the Service does not (selector or port issue).
- Calling the Service from one namespace works, from another fails (DNS name or NetworkPolicy).

## Key Facts: Kubernetes Service routing

- `spec.selector` must match Pod labels exactly; otherwise the Service has no endpoints.
- `port` is the Service port; `targetPort` is the container port and defaults to `port` if omitted. A named `targetPort` must match a named `containerPort`.
- Pods failing readiness are excluded from ready endpoints; the readiness probe gates traffic without restarting the container.
- In-cluster DNS name: `SERVICE.NAMESPACE.svc.cluster.local` (cluster domain can be customized). From another namespace, the short name `SERVICE` alone does not resolve to it.
- NetworkPolicy can block ingress to Pods or egress from clients; a default-deny egress policy also blocks DNS unless DNS egress is allowed.
- Version note: the Endpoints API is deprecated as of Kubernetes v1.33 in favor of EndpointSlices; `kubectl get endpoints` still works on many clusters but EndpointSlices are the current source of truth.

## Common Causes: Kubernetes Service unreachable

### Cause 1: Selector does not match Pod labels

Supporting evidence: EndpointSlices empty; `kubectl get pods -l KEY=VALUE` with the Service's selector returns nothing.
Weakening evidence: selector returns the expected Pods.

### Cause 2: Pods not ready (readiness probe failing)

Supporting evidence: `READY 0/1`, `Readiness probe failed` events, endpoints present but `ready: false`.
Weakening evidence: Pods `1/1` ready.

### Cause 3: targetPort does not match the port the container listens on

Supporting evidence: endpoints exist and are ready, but connections are refused; app logs show it listens on a different port (for example Spring Boot on 8080 while targetPort is 80), or the app binds to 127.0.0.1 only.
Weakening evidence: `kubectl port-forward pod/POD LOCAL:TARGETPORT` succeeds.

### Cause 4: Wrong DNS name or namespace

Supporting evidence: `no such host` / `UnknownHostException`; caller in another namespace uses the short name.
Weakening evidence: the FQDN resolves from the caller Pod.

### Cause 5: NetworkPolicy blocks traffic

Supporting evidence: timeouts rather than refusals; NetworkPolicies exist in the target or caller namespace; traffic works from an allowed namespace.
Weakening evidence: no NetworkPolicies apply, or the CNI plugin does not enforce them.

## Diagnostic Steps: Kubernetes Service unreachable

1. Observation: note the target name/port and whether the error is DNS, refused, or timeout.
2. Verify the Service exists and its ports: `kubectl get svc SERVICE -n NAMESPACE -o wide`.
3. Verify endpoints: `kubectl get endpointslices -l kubernetes.io/service-name=SERVICE -n NAMESPACE`. Empty -> Cause 1 or 2. Present and ready -> Cause 3, 4 or 5.
4. Verify selector vs labels: `kubectl get pods -n NAMESPACE --show-labels`.
5. Verify readiness: `kubectl describe pod POD` for readiness probe events.
6. Verify the port: `kubectl port-forward` to the Pod's targetPort and test locally.
7. Verify DNS and policy from a client Pod (temporary debug Pod, state-changing).
8. Conclusion: selector, readiness, port, DNS, or policy.

## Useful Commands: Kubernetes Service diagnostics

Read-only:

- `kubectl get svc SERVICE -n NAMESPACE -o yaml`: selector, port, targetPort, type.
- `kubectl get endpointslices -l kubernetes.io/service-name=SERVICE -n NAMESPACE -o yaml`: endpoint addresses and `ready` conditions.
- `kubectl get endpoints SERVICE -n NAMESPACE`: legacy view (Endpoints API deprecated since v1.33).
- `kubectl get pods -n NAMESPACE --show-labels`: labels to compare with the selector.
- `kubectl get networkpolicy -n NAMESPACE`: policies that may restrict traffic.
- `kubectl port-forward pod/POD 8080:8080 -n NAMESPACE`: local tunnel to a Pod port; does not change cluster objects (stops on Ctrl+C).

State-changing (temporary):

- `kubectl run dns-test -it --rm --restart=Never --image=busybox:1.36 -n NAMESPACE -- nslookup SERVICE.NAMESPACE.svc.cluster.local`: creates a short-lived Pod to test DNS from inside the cluster; `--rm` deletes it afterwards. Requires permission to create Pods and access to the image.

## Resolution: Kubernetes Service unreachable

- Align Service selector and Pod template labels.
- Fix the readiness failure (see the probe's message); do not remove readiness probes to "make traffic flow", since unready Pods would then receive requests.
- Set `targetPort` to the container's actual listening port, and make the app listen on 0.0.0.0.
- Use `SERVICE.NAMESPACE` or the FQDN across namespaces.
- Add a NetworkPolicy rule allowing the required traffic (and DNS egress), rather than deleting policies.

## Prevention: Kubernetes Service connectivity

Template labels and selectors from one source, use named ports, add readiness probes that reflect real readiness, and test cross-namespace access in staging.

## Diagnostic Differentiation: Service unreachable vs similar problems

- Endpoints empty because Pods crash: root cause in kubernetes-crashloopbackoff.md.
- Ingress/Nginx 502 with `connect() failed (111: Connection refused)`: see nginx-connection-refused.md; check the Service/targetPort here.
- Ingress/Nginx 504 timeouts: nginx-upstream-timeout.md.
- Database Service unreachable from a Spring Boot Pod: also check spring-boot-datasource-error.md and postgresql-connection-refused.md.
- Docker Compose (not Kubernetes) networking: docker-container-network-error.md.

## Related Errors: Kubernetes Service unreachable

Terms related to Kubernetes Service unreachable: `connection refused`, `no endpoints`, `EndpointSlice`, `Readiness probe failed`, `targetPort`, `UnknownHostException`, `svc.cluster.local`, `NetworkPolicy`.

## Root-Cause Summary: Kubernetes Service Unreachable

Kubernetes Service unreachable. Typical rootCause statements: Service selector does not match Pod labels, Pods not ready due to a failing readiness probe, targetPort differs from the container listening port, wrong DNS name across namespaces, NetworkPolicy blocking traffic. Severity: high when the Service fronts user traffic or a database. Recommendation pattern: state whether EndpointSlices are empty, unready, or ready, since that splits the causes cleanly. Do not conclude that the network is broken until endpoints, readiness, and targetPort have been checked; most Kubernetes Service outages are selector, readiness, or port mismatches rather than CNI or kube-proxy faults.
