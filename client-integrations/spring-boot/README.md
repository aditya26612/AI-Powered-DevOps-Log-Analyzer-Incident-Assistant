# DevInsight Logback appender

Build the Java 21 module locally with `mvn -f client-integrations/spring-boot/pom.xml install` from the repository root. Add `com.project.client:devinsight-logback:0.3.0` to your application. Use the included Logback configuration and keep your existing local appenders.

The appender reads the scoped key, registered application name, environment and batch endpoint from protected process configuration. It captures regular Logback events, sends batches from a bounded background worker, and exposes sanitized delivery counters. The existing environment settings control severity, timeouts, queue capacity and retry behavior.

See the [Spring Boot collector example](../../examples/collectors/spring-boot).
