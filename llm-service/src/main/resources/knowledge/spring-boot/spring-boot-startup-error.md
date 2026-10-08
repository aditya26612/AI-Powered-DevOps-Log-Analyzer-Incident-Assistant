# Spring Boot Application Startup Failure (APPLICATION FAILED TO START, failure analysis, JVM and packaging errors)

Technology: Spring Boot 3.x startup (SpringApplication, FailureAnalyzers) and the JVM launcher. Scope: how to read a Spring Boot startup failure, and failures that happen before or around Spring context creation (Java version, packaging, main class, early exit). Specific root causes have their own documents: beans (spring-boot-bean-creation-error.md), datasource (spring-boot-datasource-error.md), properties (spring-boot-configuration-error.md), ports (spring-boot-port-binding-error.md).

## Problem: Spring Boot application fails to start

When startup fails, Spring Boot runs registered FailureAnalyzers. If one recognises the exception, Spring Boot prints a formatted block headed `APPLICATION FAILED TO START` with a `Description:` and an `Action:` section. If no analyzer matches, a full stack trace is printed. The process then exits, typically with a non-zero code, which a container platform reports as a crash. Some failures happen even earlier, in the JVM launcher, before any Spring log line appears.

## Common Log Messages: Spring Boot startup failure

Canonical (Spring Boot documentation and JVM):

- Spring Boot failure analysis banner: `APPLICATION FAILED TO START`, followed by `Description:` and `Action:`
- `Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.`

Representative JVM/launcher messages (standard JDK wording; class file numbers vary):

- `java.lang.UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java Runtime (class file version 61.0), this version of the Java Runtime only recognizes class file versions up to 55.0`
- `no main manifest attribute, in app.jar`
- `Error: Could not find or load main class com.example.Application`
- `Error: Unable to access jarfile app.jar`

## Symptoms: Spring Boot crashes during startup

- Banner and a few INFO lines appear, then `APPLICATION FAILED TO START` or `Application run failed` with a stack trace, then the JVM exits.
- No Spring output at all, only a single JVM error line (launcher or Java version problem).
- In Docker: container `Exited (1)`. In Kubernetes: CrashLoopBackOff with exit code 1.

## How to Read a Spring Boot Startup Failure

1. If `APPLICATION FAILED TO START` is present, read `Description` (what failed) and `Action` (Spring's suggested fix) first; they are more precise than the stack trace.
2. Otherwise find the innermost `Caused by:`; outer exceptions (`BeanCreationException`, `ApplicationContextException`) are wrappers.
3. Map the innermost cause to a specific document: DataSource -> spring-boot-datasource-error.md; bind/placeholder -> spring-boot-configuration-error.md; port in use -> spring-boot-port-binding-error.md; missing/ambiguous bean or cycle -> spring-boot-bean-creation-error.md.
4. If unclear, re-run with `--debug` to print the condition evaluation report (which auto-configurations matched or did not and why).

## Common Causes: Spring Boot startup errors not covered elsewhere

### Cause 1: Java runtime too old (UnsupportedClassVersionError)

Why: Spring Boot 3.x requires Java 17 or newer, and the application may be compiled for a newer Java than the runtime image provides. Class file version 61 corresponds to Java 17, 65 to Java 21, 55 to Java 11, 52 to Java 8.
Supporting evidence: `UnsupportedClassVersionError` naming class file versions; `java -version` in the runtime image shows an older JDK.
Weakening evidence: runtime Java version is equal to or newer than the compile target.

### Cause 2: Jar not built as an executable Spring Boot jar

Why: the Spring Boot Maven/Gradle plugin repackage step did not run, so the jar has no `Main-Class`/launcher manifest; or the wrong (plain) jar is copied into the image.
Supporting evidence: `no main manifest attribute`, or `Could not find or load main class`.
Weakening evidence: `jar tf app.jar` shows `BOOT-INF/` and `org/springframework/boot/loader/` entries.

### Cause 3: Wrong path or file missing in the image

Supporting evidence: `Unable to access jarfile`; Dockerfile `COPY` target differs from the `ENTRYPOINT` path.

### Cause 4: Failure in a startup runner or listener

Why: a `CommandLineRunner` or `ApplicationRunner` throws (for example a startup data migration or a call to an unavailable service), which fails the application run.
Supporting evidence: innermost cause originates from a runner class; context had already refreshed (web server may have started) before exit.

### Cause 5: Heap exhaustion during startup

Supporting evidence: `java.lang.OutOfMemoryError` during startup. If the process is killed without any Java error and exit code is 137, it is a container memory kill instead (docker-container-resource-error.md).

## Diagnostic Steps: Spring Boot startup failure

1. Observation: is there any Spring output? None -> JVM/launcher (Causes 1-3). Some -> Spring failure.
2. For Spring failures, read the FailureAnalyzer block or innermost cause and route to the specific document.
3. For JVM errors, compare `java -version` of the runtime with the build target, and inspect the jar layout.
4. Check the active profiles line in the log to ensure the expected configuration is in use.
5. Use `--debug` if auto-configuration seems wrong.
6. Conclusion: state the failing phase (JVM launch, context refresh, runner) and the specific cause.

## Useful Commands: Spring Boot startup diagnostics

Read-only:

- `java -version` (inside the runtime image, for example `docker run --rm --entrypoint java IMAGE -version`; the latter creates a throwaway container): runtime Java version.
- `jar tf app.jar | head`: lists jar entries to confirm a Spring Boot executable layout (`BOOT-INF/`).
- `java -jar app.jar --debug`: prints the condition evaluation report.
- `docker logs CONTAINER` or `kubectl logs POD --previous`: full startup output from the crashed instance.

## Resolution: Spring Boot startup failure

- Use a Java 17+ runtime for Spring Boot 3.x and match it to the compile target.
- Build with the Spring Boot plugin (`spring-boot:repackage` in Maven, `bootJar` in Gradle) and copy the correct jar.
- Fix file paths between Dockerfile `COPY` and `ENTRYPOINT`.
- Make runners resilient or move non-essential startup work out of the critical path.
- For specific Spring causes, apply the fix from the corresponding document.

## Prevention: Spring Boot startup errors

Pin the runtime base image to the Java version used in CI, run a container smoke test (start and hit `/actuator/health`), and keep a context-load test per profile.

## Diagnostic Differentiation: Spring Boot startup vs other failures

- Exit 1 with `APPLICATION FAILED TO START`: Spring-level, route by Description.
- Exit 137 without Java error: container memory kill (docker-container-resource-error.md).
- Exit 127 / `executable file not found`: container entrypoint problem (docker-container-startup-failure.md).
- Kubernetes restarts with probe failures but no Java exception: probe timing (kubernetes-crashloopbackoff.md).

## Related Errors: Spring Boot application startup failure

Terms related to Spring Boot application startup failure: `APPLICATION FAILED TO START`, `Application run failed`, `Error starting ApplicationContext`, `UnsupportedClassVersionError`, `no main manifest attribute`, `Could not find or load main class`, `ApplicationContextException`.

## Root-Cause Summary: Spring Boot Application Startup Failure

Spring Boot application startup failure. Typical rootCause statements: Java runtime older than the compile target or below Java 17 for Spring Boot 3.x, jar not repackaged as an executable Spring Boot jar, wrong jar path in the image, exception in a CommandLineRunner, heap exhaustion during startup. Severity: high because the service never becomes available. Recommendation pattern: identify the failing phase (JVM launch, context refresh, runner) and route to the specific document. When the log contains only a JVM launcher line and no Spring Boot banner, the failure happened before Spring started, so Spring configuration changes will not help; check the Java runtime version and the jar packaging first.
