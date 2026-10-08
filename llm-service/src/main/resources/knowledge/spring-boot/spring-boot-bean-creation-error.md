# Spring Boot Bean Creation Failure (BeanCreationException, UnsatisfiedDependencyException, NoSuchBeanDefinitionException, circular references)

Technology: Spring Framework / Spring Boot 3.x dependency injection. Scope: the ApplicationContext fails to refresh because a bean cannot be created or wired. Out of scope: the specific DataSource failure (spring-boot-datasource-error.md), property binding (spring-boot-configuration-error.md), and port conflicts (spring-boot-port-binding-error.md), even though those also surface inside a BeanCreationException chain.

## Problem: Spring Boot fails with BeanCreationException

During startup Spring instantiates beans and injects their dependencies. If any bean fails, context refresh aborts and the application exits. The outermost exception is often a generic `BeanCreationException` or `UnsatisfiedDependencyException`; the real cause is the innermost `Caused by:` line at the bottom of the stack trace.

## Common Log Messages: Spring bean creation errors

Canonical exception types (Spring Framework API):

- `org.springframework.beans.factory.BeanCreationException`: a bean factory failed to create a bean.
- `org.springframework.beans.factory.UnsatisfiedDependencyException` (subclass of BeanCreationException): a required dependency could not be satisfied.
- `org.springframework.beans.factory.NoSuchBeanDefinitionException`: no bean of the requested type/name exists.
- `org.springframework.beans.factory.NoUniqueBeanDefinitionException`: several candidates matched where one was expected.

Representative messages (wording depends on bean names, injection point, and version):

- `Error creating bean with name 'orderService' defined in file [...]`
- `Unsatisfied dependency expressed through constructor parameter 0`
- `No qualifying bean of type 'com.example.PaymentClient' available: expected at least 1 bean which qualifies as autowire candidate.`
- Spring Boot failure analysis: `Parameter 0 of constructor in com.example.OrderService required a bean of type 'com.example.PaymentClient' that could not be found.` with Action `Consider defining a bean of type 'com.example.PaymentClient' in your configuration.`
- Circular reference failure analysis: `The dependencies of some of the beans in the application context form a cycle:`

## Symptoms: Spring Boot ApplicationContext fails to start

- Log shows `APPLICATION FAILED TO START` (when a FailureAnalyzer recognises the problem) or a long stack trace ending in `Caused by: ...`.
- Process exits shortly after startup; in containers this becomes exit code 1 and, in Kubernetes, CrashLoopBackOff.
- Often started after adding a dependency, a new `@Component`, a profile change, or a refactor of package structure.

## Common Causes: Spring BeanCreationException

### Cause 1: Missing bean (NoSuchBeanDefinitionException / "required a bean of type ... that could not be found")

Why: the class is not annotated as a component, is outside the component-scan base package (by default the package of the `@SpringBootApplication` class and below), an auto-configuration did not activate (missing starter or condition not met), or the bean is only defined under an inactive profile.
Supporting evidence: `No qualifying bean of type`; the condition evaluation report (`--debug`) shows the relevant auto-configuration under negative matches.
Weakening evidence: the bean exists in `/actuator/beans` in another working environment with the same profiles.

### Cause 2: Multiple candidates (NoUniqueBeanDefinitionException)

Why: two beans of the same type and no `@Primary` / `@Qualifier`.
Supporting evidence: message lists the matching bean names, for example `expected single matching bean but found 2`.

### Cause 3: Exception thrown inside bean construction or initialization

Why: constructor, `@PostConstruct`, or `afterPropertiesSet` throws (for example invalid configuration value, failed remote call at startup).
Supporting evidence: the innermost `Caused by:` is an application or library exception, not a Spring wiring exception.
Weakening evidence: the innermost cause is NoSuchBeanDefinitionException (then Cause 1).

### Cause 4: Circular dependency

Why: beans depend on each other in a cycle. Since Spring Boot 2.6, circular references are prohibited by default (`spring.main.allow-circular-references` defaults to `false`).
Supporting evidence: `The dependencies of some of the beans in the application context form a cycle` with a diagram of the cycle.
Resolution note: the recommended fix is to break the cycle. Setting `spring.main.allow-circular-references=true` is a temporary workaround only.

### Cause 5: Classpath / version mismatch

Why: incompatible library versions, or `javax.*` imports in a Spring Boot 3 application (Boot 3 moved to Jakarta EE, `jakarta.*` packages).
Supporting evidence: `ClassNotFoundException`, `NoClassDefFoundError`, `NoSuchMethodError` as the root cause.

## Diagnostic Steps: Spring bean creation failure

1. Observation: find the `APPLICATION FAILED TO START` block or the last `Caused by:` in the stack trace.
2. Hypothesis by innermost exception type: NoSuchBean -> missing/inactive bean; NoUniqueBean -> ambiguity; app exception -> init logic; cycle message -> circular dependency; class loading errors -> dependency versions.
3. Verify missing beans: run with `--debug` (or `debug=true`) to print the condition evaluation report; check package of the missing class vs the main application package; check active profiles in the startup log.
4. Verify dependency versions with the build tool's dependency tree (`mvn dependency:tree` or `gradle dependencies`).
5. Conclusion: identify the failing bean name and the specific missing/ambiguous/throwing dependency.

## Useful Commands: Spring Boot bean diagnostics

Read-only:

- `java -jar app.jar --debug`: starts with the condition evaluation report (auto-configuration positive/negative matches).
- `mvn dependency:tree` / `gradle dependencies`: resolve library versions and conflicts.
- Actuator `GET /actuator/beans` and `GET /actuator/conditions` on a running instance: list beans and auto-configuration conditions. In Spring Boot 3 only `health` is exposed over HTTP by default; these endpoints must be explicitly exposed and secured.

## Resolution: Spring Boot bean creation errors

- Missing bean: add the stereotype annotation or `@Bean` method, move the class under the main application package or configure scanning, add the missing starter, or activate the correct profile.
- Ambiguity: mark one bean `@Primary` or inject with `@Qualifier`.
- Initialization exception: fix the underlying error; avoid remote calls in constructors, or make them resilient.
- Circular dependency: refactor (extract a third component, use events, or move logic). Keep `allow-circular-references=true` only as a short-term, documented workaround.
- Classpath: align versions using the Spring Boot dependency management (BOM); migrate `javax.*` to `jakarta.*` for Boot 3.

## Prevention: Spring bean failures

Write a `@SpringBootTest` context-load test per profile, keep components under the main package, and use the Spring Boot BOM.

## Diagnostic Differentiation: BeanCreationException root causes

- `Failed to configure a DataSource` inside the chain: spring-boot-datasource-error.md.
- `Failed to bind properties under ...` or `Could not resolve placeholder`: spring-boot-configuration-error.md.
- `Port 8080 was already in use`: spring-boot-port-binding-error.md.
- JVM-level failures before Spring starts (`UnsupportedClassVersionError`, `no main manifest attribute`): spring-boot-startup-error.md.
- Rule: classify by the innermost `Caused by:`, not by the outer BeanCreationException.

## Related Errors: Spring Boot bean creation failure

Terms related to Spring Boot bean creation failure: `BeanCreationException`, `UnsatisfiedDependencyException`, `NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException`, `No qualifying bean of type`, `Error creating bean with name`, `BeanCurrentlyInCreationException`, `form a cycle`.

## Root-Cause Summary: Spring Boot Bean Creation Failure

Spring Boot bean creation failure. Typical rootCause statements: missing bean outside component scan or behind an inactive profile, ambiguous beans without a qualifier, exception thrown in a constructor or init method, circular dependency (prohibited by default since Spring Boot 2.6), classpath or javax versus jakarta mismatch. Severity: high because the application does not start. Recommendation pattern: classify by the innermost Caused by line and name the failing bean.
