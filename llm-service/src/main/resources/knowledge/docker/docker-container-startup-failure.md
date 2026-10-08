# Docker Container Startup Failure (Container Exits Immediately)

Technology: Docker Engine / containers. Scope: a container that fails to start, or starts and exits right away, with a non-zero exit code or an OCI runtime error. Out of scope: memory kills and disk exhaustion (see docker-container-resource-error.md), image pull failures (see docker-image-pull-error.md), mount errors (see docker-volume-mount-error.md).

## Problem: Docker container fails to start or exits immediately

A Docker container is only as alive as its main process (PID 1, the ENTRYPOINT/CMD). If that process cannot be executed, crashes, or simply finishes, the container stops. `docker ps -a` then shows a status such as `Exited (1)`, `Exited (127)` or `Created` (never started). The exit code is the most important first clue.

## Common Log Messages: Docker startup failure

Canonical messages (shown in Docker or Moby official documentation; exact formatting varies by Docker Engine and runc version):

- `docker: Error response from daemon: Container command '/etc' could not be invoked.` (exit code 126)
- `docker: Error response from daemon: Container command 'foo' not found or does not exist.` (exit code 127)
- `flag provided but not defined: --foo` (exit code 125, error in the docker CLI invocation itself)
- `failed to create task for container: failed to create shim task: OCI runtime create failed: runc create failed: unable to start container process: exec: "...": executable file not found in $PATH: unknown`
- `exec user process caused: exec format error` (older runc wording; newer versions may print `exec format error` differently)

Representative application-level messages (wording depends on the application, not Docker): a stack trace followed by process exit, `Error: Cannot find module`, `no such file or directory` when an entrypoint script is missing or has a bad interpreter line.

## Exit Code Interpretation: Docker container exited

Docker's run reference documents only three Docker-specific codes:

- **125**: the error is in Docker itself (daemon error or invalid docker run flags). The container command never ran.
- **126**: the container command was found but could not be invoked (for example not executable, or a directory).
- **127**: the container command could not be found.
- Any other code is the exit code returned by the container's own process.

By the shell convention 128+N (N = signal number, see signal(7)):

- **137** = 128 + 9 (SIGKILL). Docker documents 137 as SIGKILL. It can be an OOM kill, `docker kill`, or a stop timeout escalation. It is NOT proof of out-of-memory; check `State.OOMKilled` (see docker-container-resource-error.md).
- **139** = 128 + 11 (SIGSEGV), a segmentation fault in the process (inferred from the signal convention; Docker does not define it).
- **143** = 128 + 15 (SIGTERM), usually a normal stop requested by `docker stop`, Compose, or an orchestrator. Often not an error.
- **1** is a generic application failure; the meaning is application-specific. Read the logs.
- **0** means PID 1 finished successfully. A container that "exits immediately with 0" usually runs a command that completes (for example a shell with no TTY, or a process that daemonizes into the background).

## Symptoms: Docker container startup failure

- `docker ps` does not list the container; `docker ps -a` shows `Exited (N) X seconds ago`.
- A container with a restart policy (`--restart always` / `unless-stopped`, or Compose `restart:`) repeatedly shows `Restarting (N)`.
- `docker logs` is empty (process never executed: typical of 126/127/OCI errors) or contains an application error (typical of exit 1).
- Docker Compose reports a service as `exited with code N`.

## Common Causes: Docker container exits immediately

### Cause 1: Command or entrypoint not found (exit 127 / OCI runtime create failed)

Why: ENTRYPOINT/CMD references a binary not present in the image or not on `$PATH`, a typo in the command, or a bind mount that hides the directory containing the binary (Docker's bind-mount docs show mounting over `/usr` producing `executable file not found in $PATH`).
Supporting evidence: exit code 127 or an OCI runtime create error naming the executable; `docker logs` empty.
Weakening evidence: logs show the application started and printed output before exiting.

### Cause 2: Command cannot be executed (exit 126, permission denied, bad interpreter)

Why: entrypoint script lacks the execute bit, the shebang points to an interpreter that is not in the image (for example `#!/bin/bash` in an Alpine image without bash), or the script has Windows CRLF line endings so the interpreter path is not found.
Supporting evidence: exit 126, `permission denied`, or `no such file or directory` for a file that does exist.
Weakening evidence: the same image runs fine with an explicit shell, for example `docker run --rm --entrypoint sh IMAGE -c 'ls -l /entrypoint.sh'`.

### Cause 3: Architecture mismatch (exec format error)

Why: the image (or a binary inside it) was built for a different CPU architecture, for example an amd64 image on an arm64 host without emulation.
Supporting evidence: `exec format error`; `docker image inspect --format '{{.Os}}/{{.Architecture}}' IMAGE` differs from the host architecture.
Weakening evidence: the image architecture matches the host; then suspect a corrupted or wrongly-copied binary or script.

### Cause 4: Application crashes during startup (exit 1 or other app code)

Why: missing environment variables, missing config files, failure to connect to a dependency (database, message broker) at startup, or unhandled exceptions. Many frameworks exit with code 1 when startup fails (for Spring Boot see spring-boot-startup-error.md).
Supporting evidence: `docker logs` contains a stack trace or explicit error before exit.
Weakening evidence: logs are empty and exit code is 125/126/127.

### Cause 5: Main process finishes or detaches (exit 0)

Why: the CMD is a one-shot command, or the service starts in background/daemon mode so PID 1 returns.
Supporting evidence: exit code 0, logs show normal completion.
Weakening evidence: a non-zero exit code.

### Cause 6: Docker daemon or CLI error (exit 125)

Why: invalid flags, conflicting container name, an unavailable port, or a daemon-side failure before the command starts.
Supporting evidence: exit 125 and an `Error response from daemon` message on the docker CLI.
Note: `Bind for 0.0.0.0:PORT failed: port is already allocated` is a host port conflict, covered in docker-container-network-error.md.

## Diagnostic Steps: Docker container startup failure

1. Observation: get the state and exit code. `docker ps -a` and `docker inspect --format '{{.State.ExitCode}} {{.State.Error}}' CONTAINER`.
2. Hypothesis by exit code: 125 = Docker/CLI; 126/127 = command cannot run; 137 = killed (check OOMKilled); 143 = graceful stop; 1/other = application.
3. Verification: read `docker logs CONTAINER`. Empty logs plus 126/127 points to the entrypoint; a stack trace points to the application.
4. Inspect the configured command: `docker inspect --format '{{json .Config.Entrypoint}} {{json .Config.Cmd}}' CONTAINER`.
5. Check architecture when `exec format error` appears: compare image `Os/Architecture` with the host (`uname -m`).
6. Reproduce interactively with an overridden entrypoint (creates a new throwaway container; see command notes).
7. Conclusion: map the result to a cause above and fix the image, command, or configuration rather than repeatedly restarting.

## Useful Commands: Docker startup diagnostics

Read-only diagnostics:

- `docker ps -a`: lists all containers including stopped ones with their `Exited (N)` status.
- `docker logs CONTAINER` (add `--tail 100` or `--timestamps`): shows the process stdout/stderr. Empty output suggests the process never ran.
- `docker inspect --format '{{.State.ExitCode}}' CONTAINER`: exact exit code.
- `docker inspect --format '{{.State.OOMKilled}}' CONTAINER`: `true` means the kernel OOM killer was involved.
- `docker inspect --format '{{json .Config.Entrypoint}} {{json .Config.Cmd}}' CONTAINER`: what command Docker tried to run.
- `docker image inspect --format '{{.Os}}/{{.Architecture}}' IMAGE`: platform of a local image.
- `docker events --filter container=CONTAINER`: streams lifecycle events (`start`, `die`, `kill`, `oom`). Press Ctrl+C to stop.

State-changing (creates a temporary container, does not modify the original):

- `docker run --rm -it --entrypoint sh IMAGE`: opens a shell in a new container from the same image to check file paths, permissions and interpreters. Use only if the image contains `sh`. `--rm` removes the temporary container when it exits.

## Resolution: Docker container startup failure

- Exit 127 / not found: correct the ENTRYPOINT/CMD path, install the missing binary in the Dockerfile, or remove a mount that hides the binary's directory.
- Exit 126 / permission denied: add the execute bit in the Dockerfile (`RUN chmod +x /entrypoint.sh`), fix the shebang to an interpreter that exists, convert line endings to LF.
- exec format error: build or pull an image for the host platform (multi-platform build with Buildx, or `--platform` on pull/build).
- Exit 1: fix the application error shown in logs (missing configuration, unreachable dependency, invalid arguments).
- Exit 0: run the service in the foreground as PID 1. Prefer exec-form `CMD ["executable","arg"]` so the process receives signals (Docker build check JSONArgsRecommended).
- Exit 125: correct the docker run flags or resolve the daemon error before retrying.

## Prevention: Docker startup failure

Test images with `docker run` in CI, use exec-form ENTRYPOINT/CMD, pin base images, build multi-platform images when hosts differ, and make the application log a clear error before exiting.

## Diagnostic Differentiation: Docker startup failure vs similar problems

- Exit 137 with `OOMKilled=true` is a memory problem, not a startup bug: use docker-container-resource-error.md.
- Exit 137 with `OOMKilled=false` usually means an external SIGKILL (docker kill, stop timeout).
- A container that never gets created because the image cannot be downloaded is an image pull problem (docker-image-pull-error.md).
- `invalid mount config` or `bind source path does not exist` is a mount problem (docker-volume-mount-error.md).
- In Kubernetes, the same failure appears as CrashLoopBackOff (kubernetes-crashloopbackoff.md); the exit code in `lastState.terminated` has the same meaning.
- The container runs but clients get `connection refused`: a networking or listen-address problem (docker-container-network-error.md).

## Related Errors: Docker container startup failure and exit codes

Terms related to Docker container startup failure and exit codes: CrashLoopBackOff (Kubernetes), `APPLICATION FAILED TO START` (Spring Boot), `exec format error`, `executable file not found in $PATH`, `Exited (127)`, `Exited (1)`.

## Root-Cause Summary: Docker Container Startup Failure

Docker container startup failure. Typical rootCause statements: wrong or missing entrypoint (exit 127), non-executable entrypoint or bad interpreter (exit 126), architecture mismatch (exec format error), application crash during startup (exit 1 with a stack trace), Docker CLI or daemon error (exit 125). Severity: high when the service has no other running replica; medium when an older container or replica still serves traffic. Recommendation pattern: quote the exit code and the first meaningful log line as evidence, then fix the image or configuration instead of restarting repeatedly.
