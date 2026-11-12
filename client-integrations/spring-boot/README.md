# DevInsight Spring Boot Auto-Logging Integration

This guide shows how to connect **any** existing Spring Boot application to DevInsight in under 2 minutes.

---

### How It Works

1. Whenever your Spring Boot application logs an `ERROR` (or throws an unhandled exception / stack trace):
2. The `DevInsightLogbackAppender` captures the event asynchronously in the background.
3. It ships the log via HTTP to DevInsight's API Gateway (`http://localhost:8080/api/v1/logs`).
4. DevInsight immediately returns `201 Created` in **< 15ms** without blocking your application.
5. In the background:
   - DevInsight's ML Isolation Forest scores the log for anomaly severity.
   - If an Anomaly or `ERROR` is detected, the RAG + LLM engine automatically retrieves the runbook and diagnoses the root cause.
   - An incident alert is automatically dispatched to **Slack** (`#devops-alerts`) and printed in the observability logs.

---

### 2-Minute Setup in Any Spring Boot App

#### Step 1: Copy `DevInsightLogbackAppender.java`
Copy `DevInsightLogbackAppender.java` into your Spring Boot application (e.g. in `src/main/java/com/example/logging/` or any package).

#### Step 2: Configure `src/main/resources/logback-spring.xml`
Add or update your `logback-spring.xml` with:

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level 1 --- [%thread] %logger{36} : %msg%n</pattern>
        </encoder>
    </appender>

    <!-- DevInsight AI Incident Assistant Appender -->
    <appender name="DEVINSIGHT" class="com.project.client.DevInsightLogbackAppender">
        <devInsightUrl>http://localhost:8080/api/v1/logs</devInsightUrl>
        <apiKey>devinsight_internal_ml_key_secret_12345</apiKey>
        <userEmail>devops@company.com</userEmail>
        <serviceName>my-spring-boot-service</serviceName>
        <environment>PRODUCTION</environment>
    </appender>

    <!-- Async Queue so app performance is unaffected -->
    <appender name="ASYNC_DEVINSIGHT" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="DEVINSIGHT" />
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>WARN</level>
        </filter>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="ASYNC_DEVINSIGHT" />
    </root>
</configuration>
```

#### Step 3: Run your app and trigger an error
Whenever your app executes:
```java
log.error("Database connection refused on port 5432: pool exhausted", new SQLException("Connection timed out"));
```
It is automatically ingested and diagnosed by DevInsight!

