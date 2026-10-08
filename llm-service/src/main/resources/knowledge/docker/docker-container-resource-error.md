# Docker Container Resource Exhaustion (OOMKilled, Exit 137, CPU Throttling, No Space Left on Device)

Technology: Docker Engine resource limits (memory, CPU) and Docker host disk usage. Scope: containers killed for memory, slowed by CPU limits, or failing because the host or Docker data directory is out of space. Out of scope: Kubernetes OOMKilled restart loops (see kubernetes-crashloopbackoff.md), application bugs unrelated to resources.

## Problem: Docker container killed or failing due to resource limits

By default Docker containers have no memory or CPU limit. When a limit is set (`--memory`, `--cpus`, or Compose resource settings), a container that exceeds its memory limit can be killed by the kernel OOM killer. Without limits, host-wide memory pressure can cause the kernel to kill container processes or other host processes. Separately, disk exhaustion on the Docker host causes image pulls, builds, container writes and logs to fail.

## Common Log Messages: Docker resource exhaustion

Canonical / documented indicators:

- `Exited (137)`: Docker documents exit code 137 as SIGKILL (9).
- `"OOMKilled": true` in `docker inspect` State: the container's kill involved the OOM killer.
- `no space left on device`: appears in Docker documentation. Note: in the rootless troubleshooting page the documented cause is insufficient user namespaces, not a full disk, so the message alone does not prove disk exhaustion.
- `oom` event in `docker events` output.

Representative (application-dependent wording):

- `Killed` printed by a shell wrapper when its child is SIGKILLed.
- `java.lang.OutOfMemoryError: Java heap space` (JVM heap exhausted inside the JVM; different from a kernel OOM kill).
- `write /var/lib/docker/...: no space left on device` during pull or build.

## Symptoms: Docker container OOM or resource pressure

- Container stops with exit code 137; with a restart policy it restarts repeatedly.
- `docker stats` shows memory usage at or near the LIMIT column, or CPU % pinned at the `--cpus` limit.
- Latency increases without errors (CPU throttling).
- Image pulls, builds, or database writes fail with `no space left on device`.

## Common Causes: Docker exit 137 and OOMKilled

### Cause 1: Container memory limit too low for the workload (kernel OOM kill)

Why: the process working set exceeds `--memory`.
Supporting evidence: `docker inspect --format '{{.State.OOMKilled}}'` returns `true`; exit 137; `oom` event; memory near limit in `docker stats` before the kill.
Weakening evidence: `OOMKilled` is `false`; then the SIGKILL came from elsewhere (docker kill, stop timeout, external tooling).

### Cause 2: Memory leak or unbounded growth

Why: memory rises steadily until the limit is reached, regardless of the limit size.
Supporting evidence: repeated OOM kills at roughly regular intervals, increasing memory between restarts.
Weakening evidence: memory is flat and the kill happens at a load spike; that suggests under-sizing (Cause 1).

### Cause 3: Runtime not sized to the container limit (for example JVM heap)

Why: a runtime's heap plus non-heap memory (metaspace, threads, native buffers) can exceed the container limit. Modern JVMs are container-aware and size the default heap from the container limit; flags such as `-XX:MaxRAMPercentage` control the fraction.
Supporting evidence: kernel OOM kill (137, OOMKilled=true) without a prior `OutOfMemoryError` in logs.
Weakening evidence: `java.lang.OutOfMemoryError` in logs means the JVM hit its own heap limit first; the container may not have been OOM-killed at all.

### Cause 4: Host memory pressure without container limits

Why: with no limits, the kernel may kill processes from any container or the host.
Supporting evidence: kills across several containers at the same time, host kernel log entries from the OOM killer (`dmesg` on the host, requires privileges).
Weakening evidence: only one container with a limit is affected.

### Cause 5: CPU limit causing throttling (not a crash)

Why: `--cpus` caps CPU time; work slows instead of failing.
Supporting evidence: CPU % at the limit in `docker stats`, timeouts in callers, no exit.
Weakening evidence: CPU well below the limit.

### Cause 6: Disk exhaustion on the Docker host (no space left on device)

Why: images, build cache, stopped containers, volumes, and container log files accumulate in the Docker data directory. Inodes can also run out even when bytes are free.
Supporting evidence: `docker system df` shows large reclaimable space; `df -h` or `df -i` on the host shows the filesystem full.
Weakening evidence: plenty of free space and inodes; in rootless mode check the namespace cause documented by Docker.

## Diagnostic Steps: Docker resource exhaustion

1. Observation: exit code 137 or `no space left on device`.
2. For 137, verify OOM: `docker inspect --format '{{.State.OOMKilled}}' CONTAINER`. `true` supports a memory cause; `false` points to an external SIGKILL.
3. Compare usage with limits: `docker stats --no-stream` and `docker inspect --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}' CONTAINER` (0 means no limit).
4. Distinguish leak vs under-sizing by observing memory over time with `docker stats`.
5. For JVM services, check logs for `OutOfMemoryError` to separate heap exhaustion from kernel OOM.
6. For disk errors, run `docker system df` and host `df -h` / `df -i` to find what is full.
7. Conclusion: memory limit vs leak vs host pressure vs disk.

## Useful Commands: Docker resource diagnostics

Read-only:

- `docker inspect --format '{{.State.OOMKilled}} {{.State.ExitCode}}' CONTAINER`: OOM flag and exit code.
- `docker stats --no-stream`: one-shot CPU, memory usage / limit, network and block I/O per container.
- `docker inspect --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}' CONTAINER`: configured memory limit in bytes and CPU limit in nano-CPUs (0 = unlimited).
- `docker events --filter event=oom`: stream OOM events as they happen.
- `docker system df` (add `-v` for detail): disk used by images, containers, local volumes and build cache, with reclaimable amounts.
- `df -h` and `df -i` (host): filesystem space and inode usage.

State-changing (see warnings):

- `docker update --memory 1g --memory-swap 1g CONTAINER`: changes limits on a running container. Validate the value; setting it below current usage can trigger an immediate kill.

## Resolution: Docker OOMKilled and disk exhaustion

- Size the memory limit to observed peak usage plus headroom, or reduce memory use.
- Fix leaks found by monitoring rather than only raising limits.
- For the JVM, keep heap below the container limit (for example with `-XX:MaxRAMPercentage`) and leave room for non-heap memory.
- Set limits on all containers on shared hosts to prevent host-wide OOM kills. Docker advises using `--oom-kill-disable` only together with a memory limit.
- For disk: remove what is known to be unused and configure log rotation for the logging driver.

WARNING: cleanup commands are destructive.
- `docker image prune` removes dangling images; `docker image prune -a` removes all images not used by a container.
- `docker system prune -a` removes all stopped containers, unused networks, all unused images and build cache. Adding `--volumes` also removes unused volumes, which can permanently delete database data.
- Potential impact: images must be re-pulled (registry rate limits may apply), data in removed volumes is lost.
- Appropriate when: `docker system df` shows large reclaimable space and you have confirmed nothing needed is stopped or unused.
- Verify first: list with `docker ps -a`, `docker images`, `docker volume ls`, and back up any volume holding state.

## Prevention: Docker resource exhaustion

Set memory and CPU limits per service, alert on memory approaching the limit and on host disk usage, rotate container logs, and schedule controlled cleanup of build cache on CI hosts.

## Diagnostic Differentiation: Docker exit 137 vs other exits

- Exit 137 + OOMKilled=true: memory limit or leak (this document).
- Exit 137 + OOMKilled=false: external SIGKILL, often `docker stop` timeout after SIGTERM was ignored.
- Exit 143: graceful SIGTERM stop, usually not a resource problem.
- `java.lang.OutOfMemoryError` with exit 1: JVM heap limit, the container limit may be fine.
- Kubernetes shows the same as `reason: OOMKilled`, exit code 137 (kubernetes-crashloopbackoff.md).
- Image pull failing with `no space left on device`: disk on the node/host, not the registry (docker-image-pull-error.md covers registry-side errors).

## Related Errors: Docker OOMKilled, exit 137 and disk exhaustion

Terms related to Docker OOMKilled, exit 137 and disk exhaustion: OOMKilled, exit code 137, SIGKILL, `Killed`, `OutOfMemoryError`, `no space left on device`, CPU throttling.

## Root-Cause Summary: Docker Container Resource Exhaustion

Docker resource exhaustion. Typical rootCause statements: container memory limit exceeded (exit 137 with OOMKilled true), memory leak, JVM sized larger than the container limit, host memory pressure, CPU throttling, host disk or inode exhaustion. Severity: high for repeated OOM kills or a full Docker data disk because all containers on the host can be affected. Recommendation pattern: cite OOMKilled and limit versus usage; never recommend prune with volumes without a backup.
