# Docker Volume and Bind Mount Errors (permission denied, bind source path does not exist, SELinux)

Technology: Docker Engine storage (bind mounts, named volumes). Scope: containers that fail to start because a mount is invalid, or run but cannot read/write mounted data. Out of scope: disk full (see docker-container-resource-error.md), Kubernetes PersistentVolumeClaims (see kubernetes-pod-pending.md).

## Problem: Docker mount fails or container cannot access mounted files

A bind mount maps a host path into the container; a named volume is storage managed by Docker. Failures happen when the source path is wrong, when the mount hides files the image needs, or when the container process lacks permission (Unix ownership, SELinux labels, rootless/user-namespace UID mapping).

## Common Log Messages: Docker mount errors

Canonical (Docker documentation):

- `docker: Error response from daemon: invalid mount config for type "bind": bind source path does not exist: /dev/noexist.`
- `... exec: "nginx": executable file not found in $PATH` when a bind mount obscures a directory such as `/usr` that the image depends on.

Representative (wording depends on the application):

- `permission denied` / `Permission denied` when opening or writing a file under the mount path.
- `chown: ... Operation not permitted` from an entrypoint that tries to change ownership of mounted files.
- `Read-only file system` when the mount was declared read-only (`:ro` / `readonly`).

## Symptoms: Docker volume or bind mount problems

- Container fails at creation with `invalid mount config`.
- Container starts but the application logs permission errors or sees an empty directory instead of expected files.
- Data written by the container "disappears" after recreation (data was not on a volume, or a different volume name/path was used).
- Works on one host or with root, fails on a SELinux-enforcing host or in rootless Docker.

## Key Facts: Docker mounts

- `--mount type=bind,src=...,dst=...` fails if the source path does not exist. `-v /host/path:/container/path` creates a missing host directory automatically, which can hide typos (the container sees an empty directory).
- Mounting over a non-empty container directory obscures the image's files at that path.
- Named volumes are managed by Docker; bind mounts depend on the host filesystem layout and permissions.
- On SELinux hosts, `:z` relabels content for sharing between containers and `:Z` makes it private to one container. Docker warns that relabeling system directories (for example with `:Z` on `/home` or `/usr`) can make the host inoperable.
- In rootless Docker and with userns-remap, container UIDs map to different host UIDs, so host ownership may not match the UID the container runs as.

## Common Causes: Docker permission denied on mount

### Cause 1: Bind source path does not exist or is wrong

Why: relative path mistakes, running Compose from a different directory, or paths on a remote daemon host.
Supporting evidence: `bind source path does not exist`, or an unexpectedly empty directory with `-v`.
Weakening evidence: `docker inspect --format '{{json .Mounts}}'` shows the expected source path.

### Cause 2: UID/GID mismatch between container process and host files

Why: the container runs as a non-root user whose numeric UID lacks permission on host files, or rootless/userns mapping changes effective ownership.
Supporting evidence: `permission denied` only on mounted paths; `ls -ln` on the host shows a different owner than `id -u` inside the container.
Weakening evidence: the same error occurs on paths that are not mounted.

### Cause 3: SELinux denies access

Why: host files carry a label the container domain cannot access.
Supporting evidence: SELinux enforcing on the host, permission denied despite correct Unix permissions, AVC denials in the host audit log.
Weakening evidence: SELinux disabled/permissive, or the same mount works with an appropriate `:z` label.

### Cause 4: Mount hides image content

Why: mounting a host directory on top of an image directory (for example application code or binaries).
Supporting evidence: missing files or `executable file not found` right after adding a mount.
Weakening evidence: the files are missing even without the mount.

### Cause 5: Read-only mount

Why: mount declared `:ro` / `readonly`.
Supporting evidence: `Read-only file system` errors; `RW: false` in the mounts JSON.

## Diagnostic Steps: Docker mount errors

1. Observation: did the container fail to start (mount config) or does it run with access errors (permissions)?
2. Inspect mounts: `docker inspect --format '{{json .Mounts}}' CONTAINER` (source, destination, type, RW).
3. Verify the host path exists and its ownership: `ls -ld /host/path` and `ls -ln /host/path`.
4. Verify the container user: `docker exec CONTAINER id` (container must be running).
5. If SELinux is enforcing (`getenforce` on the host), check for denials in the host audit log.
6. Conclusion: path, ownership, label, obscured content, or read-only.

## Useful Commands: Docker mount diagnostics

Read-only:

- `docker inspect --format '{{json .Mounts}}' CONTAINER`: mount type, source, destination and RW flag.
- `docker volume ls` and `docker volume inspect VOLUME`: named volumes and their mountpoint on the host.
- `docker exec CONTAINER id`: UID/GID the process runs as.
- `ls -ln /host/path` (host): numeric ownership and permissions.
- `getenforce` (host, SELinux systems): Enforcing / Permissive / Disabled.

State-changing:

- `chown` / `chmod` on host paths: changes host file ownership/permissions. Apply to the specific data directory only, never recursively to system directories.

## Resolution: Docker volume mount errors

- Use absolute paths or Compose paths relative to the compose file; prefer `--mount` so missing sources fail loudly.
- Align ownership: run the container with a UID that owns the data (`--user UID:GID`) or set ownership of the specific data directory.
- On SELinux hosts, use `:z` or `:Z` on application-specific directories only. Do not disable SELinux as a fix; if you set it permissive for diagnosis, treat it as temporary and revert.
- Mount to a dedicated path instead of over image directories.
- Use named volumes for database data to avoid host permission coupling.

WARNING: `docker volume rm`, `docker volume prune`, and `docker rm -v` delete volume data permanently. Back up (for databases, a logical dump) and confirm the volume name before removing anything.

## Prevention: Docker mounts

Document required UIDs in the image, use named volumes for stateful services, keep bind mounts for configuration (often read-only), and test on SELinux/rootless hosts if production uses them.

## Diagnostic Differentiation: Docker mount vs other errors

- `executable file not found` without any mount: startup problem (docker-container-startup-failure.md).
- `no space left on device` while writing to a volume: disk exhaustion (docker-container-resource-error.md).
- PostgreSQL container failing on its data directory permissions is a mount/ownership problem here; PostgreSQL refusing clients is postgresql-connection-refused.md.
- Kubernetes volume problems usually appear as Pending pods with unbound PVCs (kubernetes-pod-pending.md).

## Related Errors: Docker volume and bind mount

Terms related to Docker volume and bind mount: `permission denied`, `bind source path does not exist`, `invalid mount config`, `Read-only file system`, `Operation not permitted`, SELinux AVC denial.

## Root-Cause Summary: Docker Volume and Bind Mount Errors

Docker volume and bind mount errors. Typical rootCause statements: bind source path does not exist, UID or GID mismatch with host files, SELinux label denies access, mount hides image content, read-only mount. Severity: high for databases because data may be inaccessible; never resolve by deleting volumes. Recommendation pattern: cite the mount source and destination from docker inspect and the container user ID.
