# Docker Image Pull and Build Errors (manifest unknown, pull access denied, rate limit, platform mismatch, x509)

Technology: Docker Engine / Docker Hub / OCI registries / Docker Buildx. Scope: `docker pull`, `docker run` (implicit pull), Compose pulls, and builds failing to fetch or produce an image. Out of scope: Kubernetes ImagePullBackOff (see kubernetes-imagepullbackoff.md, which reuses these registry causes).

## Problem: Docker cannot pull or build an image

Before a container can start, its image must exist locally or be pulled from a registry. Pulls fail for five broad reasons: the reference (name/tag/digest) does not exist, the registry denies access, the registry throttles requests, the image has no variant for the host platform, or the TLS/network path to the registry fails.

## Common Log Messages: Docker image pull errors

Canonical (Docker documentation):

- `You have reached your pull rate limit. You may increase the limit by authenticating and upgrading: https://www.docker.com/increase-rate-limits`
- `Too Many Requests` (HTTP 429 from Docker Hub)
- `x509: certificate signed by unknown authority` (documented by Docker for TLS-intercepting proxies and private CAs)

Representative (widely reported; wording varies by Docker Engine version and registry):

- `manifest unknown` / `manifest for IMAGE:TAG not found: manifest unknown` (tag or digest does not exist)
- `pull access denied for IMAGE, repository does not exist or may require 'docker login'`
- `unauthorized: authentication required`
- `no matching manifest for linux/arm64/v8 in the manifest list entries` (platform mismatch)
- `dial tcp: lookup REGISTRY_HOST: no such host` / `i/o timeout` (DNS or network to the registry)

## Symptoms: Docker pull failure

- `docker pull`, `docker run`, or `docker compose up` fails before any container is created.
- CI pipelines fail intermittently at pull time (often rate limiting).
- Works on one machine (logged in, or different architecture) but not another.

## Common Causes: Docker image pull failure

### Cause 1: Image name or tag does not exist (manifest unknown)

Why: typo, deleted tag, wrong repository namespace, or a tag never pushed.
Supporting evidence: `manifest unknown` / `not found`; `docker manifest inspect IMAGE:TAG` (experimental CLI command) also fails.
Weakening evidence: the same reference pulls successfully elsewhere with the same credentials.

### Cause 2: Authentication or authorization failure (private repository)

Why: not logged in, expired token, or the account lacks access. For Docker Hub, a non-existent repository and a private repository without access can produce the same `pull access denied` message.
Supporting evidence: `pull access denied`, `unauthorized`, HTTP 401/403.
Weakening evidence: pull works after `docker login` on the same host (then it was credentials).

### Cause 3: Docker Hub pull rate limit (429 Too Many Requests)

Why: Docker Hub limits pulls per time window. Current Docker documentation lists 100 pulls per 6 hours per IPv4 address or IPv6 /64 for unauthenticated users and 200 per 6 hours for authenticated Personal users; paid plans are documented as unlimited, subject to abuse limits. These numbers have changed over time: check the current Docker Hub usage page.
Supporting evidence: `Too Many Requests` or the rate-limit message; many pulls from a shared NAT IP or CI.
Weakening evidence: a single pull fails on an otherwise idle authenticated host.

### Cause 4: Platform / architecture mismatch

Why: the image index has no manifest for the host OS/architecture (for example amd64-only image on arm64).
Supporting evidence: `no matching manifest for linux/arm64...`; `docker manifest inspect` lists only other platforms.
Weakening evidence: the platform is listed; if the pull succeeds but the container fails with `exec format error`, see docker-container-startup-failure.md.

### Cause 5: TLS or network path to the registry

Why: corporate TLS-intercepting proxy, private registry with self-signed CA, DNS failure, or proxy configuration missing for the daemon.
Supporting evidence: `x509: certificate signed by unknown authority`, `no such host`, `i/o timeout`.
Weakening evidence: other registries also fail the same way (network-wide) vs only one registry (registry-specific CA).

### Cause 6: Build-time failures (docker build / Buildx)

Why: base image in `FROM` cannot be pulled (any cause above), or a build step fails. Since Docker Engine 23.0, `docker build` uses BuildKit/Buildx by default on Linux (except with `DOCKER_BUILDKIT=0` or Windows containers), so output format differs from the legacy builder.
Supporting evidence: error appears at the `FROM` step (pull problem) vs at a `RUN` step (build script problem).

## Diagnostic Steps: Docker image pull failure

1. Observation: copy the exact image reference and error from the output.
2. Hypothesis by message: not found = reference; denied/unauthorized = credentials; 429 = rate limit; no matching manifest = platform; x509/no such host = TLS/network.
3. Verify the reference exists and its platforms: `docker manifest inspect IMAGE:TAG`.
4. Verify credentials: retry after `docker login REGISTRY` (state-changing, stores credentials).
5. Verify platform: compare with `docker info --format '{{.OSType}}/{{.Architecture}}'`.
6. Verify network/TLS: test DNS and HTTPS to the registry host from the Docker host.
7. Conclusion: fix the specific layer; do not retry blindly (retries consume rate limit).

## Useful Commands: Docker pull diagnostics

Read-only:

- `docker manifest inspect IMAGE:TAG`: shows manifest/index and platforms in the registry without pulling. Docker marks `docker manifest` as experimental.
- `docker image inspect IMAGE`: details of a locally present image (fails if not present).
- `docker info`: daemon configuration including registry mirrors, proxies and architecture.
- `docker pull --platform linux/amd64 IMAGE:TAG`: pulls a specific platform variant (downloads data, does not change containers).

State-changing:

- `docker login REGISTRY`: stores credentials in the Docker credential store/config. Use a token with least privilege.

## Resolution: Docker image pull errors

- Fix the image name/tag; prefer pinned tags or digests in deployments.
- Authenticate with `docker login` (or CI secrets) using an account with access.
- For rate limits: authenticate pulls, use a registry mirror or pull-through cache, reduce redundant pulls in CI.
- For platform mismatch: use an image that publishes the host platform, or build multi-platform images with Buildx (`docker buildx build --platform linux/amd64,linux/arm64 ...`).
- For x509: install the corporate/private CA as Docker documents for registry certificates. Do NOT configure an `insecure-registries` entry as a first response; it disables TLS verification for that registry and should only be a short-lived, documented exception on isolated networks.

## Prevention: Docker image pull

Pin image versions, mirror critical base images into a private registry, authenticate all CI pulls, and publish multi-platform images.

## Diagnostic Differentiation: Docker pull vs other image problems

- Pull succeeded but container exits with `exec format error`: platform issue at runtime (docker-container-startup-failure.md).
- `no space left on device` during pull: host disk (docker-container-resource-error.md).
- In Kubernetes the same causes appear as `ErrImagePull` then `ImagePullBackOff` (kubernetes-imagepullbackoff.md).

## Related Errors: Docker image pull and build

Terms related to Docker image pull and build: `manifest unknown`, `pull access denied`, `unauthorized`, `toomanyrequests`, `429 Too Many Requests`, `no matching manifest`, `x509`, ErrImagePull, ImagePullBackOff.

## Root-Cause Summary: Docker Image Pull and Build Errors

Docker image pull failure. Typical rootCause statements: tag or repository does not exist, missing or invalid registry credentials, Docker Hub rate limit (429), no image variant for the host platform, TLS trust or DNS problem towards the registry. Severity: high when it blocks deployment of a fix or scale-out; existing running containers are not affected. Recommendation pattern: quote the registry error text, which identifies the cause class directly.
