# Spring Boot Configuration and Property Binding Errors (Failed to bind properties, Could not resolve placeholder, profiles, config import)

Technology: Spring Boot 3.x externalized configuration (application.properties/yml, profiles, environment variables, `@ConfigurationProperties`, `@Value`, `spring.config.import`). Scope: the application fails or misbehaves because configuration values are missing, mistyped, unbindable, or loaded from the wrong profile. Out of scope: DataSource-specific messages (spring-boot-datasource-error.md).

## Problem: Spring Boot property binding or configuration failure

Spring Boot collects configuration from many property sources with a defined precedence (command-line args and environment variables override packaged application properties). `@ConfigurationProperties` classes are bound with relaxed binding and optional validation; `@Value("${...}")` placeholders must resolve. If a value cannot be converted, fails validation, or a required placeholder is missing, startup fails.

## Common Log Messages: Spring Boot configuration errors

Canonical exception type:

- `org.springframework.boot.context.properties.ConfigurationPropertiesBindException`: thrown when `@ConfigurationProperties` binding fails.

Representative (wording varies by version and property):

- `Failed to bind properties under 'app.payment.timeout' to java.time.Duration:` followed by `Property:`, `Value:`, `Origin:` and `Reason:` lines in the failure analysis
- `Binding to target org.springframework.boot.context.properties.bind.BindException: Failed to bind properties under 'app' to com.example.AppProperties failed:` followed by validation details such as `Reason: must not be blank`
- `Could not resolve placeholder 'app.api-key' in value "${app.api-key}"`
- `No active profile set, falling back to 1 default profile: "default"`
- `Config data resource '...' via location '...' does not exist` (missing `spring.config.import` target)

## Symptoms: Spring Boot misconfiguration

- Startup fails with an `APPLICATION FAILED TO START` block naming a property, value, and origin (file and line, or environment variable).
- Application starts but uses defaults (for example connects to localhost or uses an embedded database) because the intended profile or env var was not applied.
- Works locally, fails in Docker/Kubernetes where configuration comes from environment variables or ConfigMaps.

## Key Facts: Spring Boot configuration binding

- Relaxed binding: `app.payment-timeout`, `app.paymentTimeout`, and `APP_PAYMENTTIMEOUT` can bind to the same `@ConfigurationProperties` field. Environment variable mapping: replace dots with underscores, remove dashes, uppercase (`spring.datasource.url` -> `SPRING_DATASOURCE_URL`).
- `@Value` placeholders are less flexible than `@ConfigurationProperties`; Spring Boot documentation recommends using the canonical kebab-case form in `@Value` references.
- Profile-specific files (`application-prod.yml`) apply only when the profile is active (`spring.profiles.active`, or `SPRING_PROFILES_ACTIVE`).
- `spring.config.import` fails startup if the imported location does not exist, unless prefixed with `optional:`.
- Boot 3 uses `jakarta.validation` annotations for `@Validated` configuration properties.

## Common Causes: Spring Boot property binding failure

### Cause 1: Type conversion failure

Why: the value cannot convert to the target type (for example `30 seconds` for a `Duration`, `abc` for an `int`, invalid enum constant).
Supporting evidence: `Failed to bind properties under '...'` with `Value:` and a conversion `Reason:`.
Weakening evidence: reason is a validation constraint (Cause 2).

### Cause 2: Validation failure on @ConfigurationProperties

Supporting evidence: binding failure listing constraint messages such as `must not be null` or `must not be blank`.

### Cause 3: Missing property for @Value placeholder

Why: no source defines the key, the profile containing it is inactive, or the env var name does not map to the key.
Supporting evidence: `Could not resolve placeholder 'KEY'`.
Weakening evidence: the key is visible in `/actuator/env` property sources of the failing instance.

### Cause 4: Wrong or missing profile

Supporting evidence: startup log shows `No active profile set` or an unexpected profile list; behavior matches defaults.

### Cause 5: Environment variable naming mismatch

Why: e.g. `SPRING_DATASOURCE-URL` or `spring.datasource.url` used as an env var name in a shell that rejects dots, or misspelling.
Supporting evidence: the property is set in the deployment manifest but the application does not see it.

### Cause 6: Missing imported config (spring.config.import)

Supporting evidence: `Config data resource ... does not exist` at startup.

## Diagnostic Steps: Spring Boot configuration error

1. Observation: read the failure analysis lines `Property`, `Value`, `Origin`, `Reason`; `Origin` tells which source supplied the bad value.
2. Hypothesis: conversion, validation, missing key, profile, env var naming, or missing import.
3. Verify active profiles in the startup log.
4. Verify effective values on a running instance via `/actuator/env/PROPERTY` or `/actuator/configprops` (exposed and secured; values masked by default in Boot 3).
5. Verify the deployment environment: env vars in the container (`docker inspect --format '{{json .Config.Env}}' CONTAINER`, or the Pod spec).
6. Conclusion: name the property, the source, and the corrective change.

## Useful Commands: Spring Boot configuration diagnostics

Read-only:

- `GET /actuator/env` and `GET /actuator/env/{property.name}`: property sources and the winning value. In Spring Boot 3, values are sanitized by default (`management.endpoint.env.show-values` default `never`).
- `GET /actuator/configprops`: bound `@ConfigurationProperties` beans (also sanitized by default).
- `docker inspect --format '{{json .Config.Env}}' CONTAINER`: environment variables given to a container. Output can include secrets; avoid pasting it into tickets.
- `kubectl get pod POD -o jsonpath='{.spec.containers[*].env}'`: env vars defined in the Pod spec (values from Secrets appear as references).

## Resolution: Spring Boot configuration errors

- Fix the value format (for example `30s` for Duration) or the target type.
- Supply missing keys in the correct profile or environment; provide defaults in `@Value("${key:default}")` only where a default is genuinely safe.
- Activate the intended profile explicitly in each environment.
- Use the documented env var mapping (`SPRING_DATASOURCE_URL`).
- Mark non-mandatory imports `optional:`.
- Do not expose `/actuator/env` publicly or set `show-values: always` in production; values can include secrets.

## Prevention: Spring Boot configuration failures

Prefer `@ConfigurationProperties` with `@Validated`, keep a minimal required-property checklist per environment, and run a startup test with production-like configuration.

## Diagnostic Differentiation: configuration error vs other startup errors

- Missing `spring.datasource.url` produces the DataSource-specific message (spring-boot-datasource-error.md), not a generic bind error.
- `No qualifying bean of type` is a wiring problem (spring-boot-bean-creation-error.md), though a missing property can disable a conditional bean.
- Invalid `server.port` value is a binding error here; a valid port already in use is spring-boot-port-binding-error.md.

## Related Errors: Spring Boot configuration and property binding

Terms related to Spring Boot configuration and property binding: `ConfigurationPropertiesBindException`, `BindException` (Spring binder, not java.net), `Failed to bind properties under`, `Could not resolve placeholder`, `No active profile set`, `Config data resource does not exist`, `spring.profiles.active`.

## Root-Cause Summary: Spring Boot Configuration and Property Binding Errors

Spring Boot configuration errors. Typical rootCause statements: value cannot be converted to the target type, @ConfigurationProperties validation failure, missing property for an @Value placeholder, intended profile not active, environment variable name not matching relaxed binding, missing spring.config.import location. Severity: high when startup fails; medium but risky when the app silently uses defaults. Recommendation pattern: cite the Property, Value and Origin lines.
