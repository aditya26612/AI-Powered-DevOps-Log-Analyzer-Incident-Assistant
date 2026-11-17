package com.project.log_layer.appintegration.service.impl;

import com.project.log_layer.appintegration.dto.request.CreateIntegrationRequest;
import com.project.log_layer.appintegration.dto.request.TestIntegrationRequest;
import com.project.log_layer.appintegration.dto.response.ApiKeyVerificationResponse;
import com.project.log_layer.appintegration.dto.response.ApplicationIntegrationResponse;
import com.project.log_layer.appintegration.dto.response.TestIntegrationResponse;
import com.project.log_layer.appintegration.entity.ApplicationIntegration;
import com.project.log_layer.appintegration.repository.ApplicationIntegrationRepository;
import com.project.log_layer.appintegration.service.ApplicationIntegrationService;
import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.dto.response.log.LogResponse;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.enums.TechStack;
import com.project.log_layer.exception.LogNotFoundException;
import com.project.log_layer.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationIntegrationServiceImpl implements ApplicationIntegrationService {

    private final ApplicationIntegrationRepository integrationRepository;
    private final LogService logService;

    @Override
    @Transactional
    public ApplicationIntegrationResponse createOrUpdateIntegration(CreateIntegrationRequest request, String userEmail) {
        log.info("Registering application integration for service={} techStack={} by user={}",
                request.getApplicationName(), request.getTechStack(), userEmail);

        ApplicationIntegration integration = integrationRepository.findByApplicationName(request.getApplicationName())
                .orElseGet(() -> ApplicationIntegration.builder()
                        .applicationName(request.getApplicationName())
                        .apiKey("dvi_" + UUID.randomUUID().toString().replace("-", ""))
                        .userEmail(userEmail)
                        .build());

        integration.setTechStack(request.getTechStack());
        integration.setEnvironment(request.getEnvironment());
        integration.setSlackWebhookUrl(request.getSlackWebhookUrl());
        integration.setSlackChannelName(request.getSlackChannelName());
        integration.setAlertOnAnomaly(request.getAlertOnAnomaly() != null ? request.getAlertOnAnomaly() : true);
        integration.setAlertOnError(request.getAlertOnError() != null ? request.getAlertOnError() : true);
        integration.setAnomalyThreshold(request.getAnomalyThreshold() != null ? request.getAnomalyThreshold() : -0.1);
        integration.setDescription(request.getDescription());
        integration.setUserEmail(userEmail);

        ApplicationIntegration saved = integrationRepository.save(integration);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationIntegrationResponse getIntegration(String applicationName, String userEmail) {
        ApplicationIntegration integration = integrationRepository.findByApplicationName(applicationName)
                .orElseThrow(() -> new LogNotFoundException("Integration not found for application: " + applicationName));
        return mapToResponse(integration);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationIntegrationResponse> getAllIntegrations(String userEmail) {
        return integrationRepository.findAllByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteIntegration(Long id, String userEmail) {
        ApplicationIntegration integration = integrationRepository.findById(id)
                .orElseThrow(() -> new LogNotFoundException("Integration not found with id: " + id));
        integrationRepository.delete(integration);
        log.info("Deleted integration id={} for application={}", id, integration.getApplicationName());
    }

    @Override
    public TestIntegrationResponse runTestSimulation(TestIntegrationRequest request, String userEmail) {
        long startTime = System.currentTimeMillis();
        String appName = request.getApplicationName();

        ApplicationIntegration integration = integrationRepository.findByApplicationName(appName)
                .orElse(null);

        TechStack stack = (integration != null) ? integration.getTechStack() : TechStack.SPRING_BOOT;
        String env = (integration != null) ? integration.getEnvironment().name() : "PRODUCTION";
        String channel = (integration != null && integration.getSlackChannelName() != null)
                ? integration.getSlackChannelName() : "#devops-alerts";

        String simulatedRawLog = (request.getCustomLogMessage() != null && !request.getCustomLogMessage().isBlank())
                ? request.getCustomLogMessage()
                : generateSimulatedLog(stack, appName);

        LogSource source = mapTechStackToSource(stack);

        LogIngestRequest ingestRequest = LogIngestRequest.builder()
                .rawLog(simulatedRawLog)
                .source(source)
                .environment(integration != null ? integration.getEnvironment() : com.project.log_layer.enums.Environment.PRODUCTION)
                .applicationName(appName)
                .build();

        try {
            LogResponse ingestResponse = logService.ingest(ingestRequest);
            long elapsed = System.currentTimeMillis() - startTime;

            return TestIntegrationResponse.builder()
                    .success(true)
                    .logId(ingestResponse.getId())
                    .applicationName(appName)
                    .techStack(stack.name())
                    .anomalyDetected(Boolean.TRUE.equals(ingestResponse.getAnomaly()))
                    .anomalyScore(ingestResponse.getDecisionScore())
                    .diagnosisSummary("Automated incident diagnosis queued in background for Ollama & Slack.")
                    .remediation("Review thread logs and connection pool saturation settings.")
                    .slackDispatched(integration != null && integration.getSlackWebhookUrl() != null && !integration.getSlackWebhookUrl().isBlank())
                    .slackChannel(channel)
                    .durationMs(elapsed)
                    .message("Test telemetry event successfully ingested and dispatched through ML pipeline in " + elapsed + "ms.")
                    .build();

        } catch (Exception ex) {
            log.error("Simulation test failed for app={}: {}", appName, ex.getMessage());
            return TestIntegrationResponse.builder()
                    .success(false)
                    .applicationName(appName)
                    .techStack(stack.name())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .message("Failed to ingest test event: " + ex.getMessage())
                    .build();
        }
    }

    private ApplicationIntegrationResponse mapToResponse(ApplicationIntegration entity) {
        String snippet = generateCodeSnippet(
                entity.getTechStack(),
                entity.getApplicationName(),
                entity.getApiKey(),
                entity.getEnvironment().name()
        );

        return ApplicationIntegrationResponse.builder()
                .id(entity.getId())
                .applicationName(entity.getApplicationName())
                .techStack(entity.getTechStack())
                .environment(entity.getEnvironment())
                .apiKey(entity.getApiKey())
                .slackWebhookUrl(entity.getSlackWebhookUrl())
                .slackChannelName(entity.getSlackChannelName())
                .alertOnAnomaly(entity.getAlertOnAnomaly())
                .alertOnError(entity.getAlertOnError())
                .anomalyThreshold(entity.getAnomalyThreshold())
                .description(entity.getDescription())
                .userEmail(entity.getUserEmail())
                .codeSnippet(snippet)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String generateCodeSnippet(TechStack stack, String appName, String apiKey, String environment) {
        switch (stack) {
            case SPRING_BOOT:
                return "<!-- Add to src/main/resources/logback-spring.xml -->\n" +
                        "<configuration>\n" +
                        "    <appender name=\"DEVINSIGHT\" class=\"com.project.client.DevInsightLogbackAppender\">\n" +
                        "        <gatewayUrl>http://localhost:8080</gatewayUrl>\n" +
                        "        <apiKey>" + apiKey + "</apiKey>\n" +
                        "        <applicationName>" + appName + "</applicationName>\n" +
                        "        <environment>" + environment + "</environment>\n" +
                        "    </appender>\n\n" +
                        "    <root level=\"INFO\">\n" +
                        "        <appender-ref ref=\"DEVINSIGHT\" />\n" +
                        "    </root>\n" +
                        "</configuration>";

            case PYTHON:
                return "# Python (FastAPI / Django / Flask / standard logging)\n" +
                        "import logging\n" +
                        "from devinsight import DevInsightHandler\n\n" +
                        "logger = logging.getLogger(\"" + appName + "\")\n" +
                        "handler = DevInsightHandler(\n" +
                        "    gateway_url=\"http://localhost:8080\",\n" +
                        "    api_key=\"" + apiKey + "\",\n" +
                        "    application_name=\"" + appName + "\",\n" +
                        "    environment=\"" + environment + "\"\n" +
                        ")\n" +
                        "logger.addHandler(handler)\n" +
                        "logger.error(\"Example critical failure encountered!\")";

            case NODEJS:
                return "// Node.js (Express / Winston / Pino)\n" +
                        "const winston = require('winston');\n" +
                        "const DevInsightTransport = require('./devinsight-transport');\n\n" +
                        "const logger = winston.createLogger({\n" +
                        "  transports: [\n" +
                        "    new DevInsightTransport({\n" +
                        "      gatewayUrl: 'http://localhost:8080',\n" +
                        "      apiKey: '" + apiKey + "',\n" +
                        "      applicationName: '" + appName + "',\n" +
                        "      environment: '" + environment + "'\n" +
                        "    })\n" +
                        "  ]\n" +
                        "});\n" +
                        "logger.error('Unhandled database exception occurred');";

            case GO:
                return "// Go (Zap / Slog / Logrus)\n" +
                        "import \"github.com/devinsight/go-sdk\"\n\n" +
                        "hook := devinsight.NewHook(&devinsight.Config{\n" +
                        "    GatewayURL:      \"http://localhost:8080\",\n" +
                        "    APIKey:          \"" + apiKey + "\",\n" +
                        "    ApplicationName: \"" + appName + "\",\n" +
                        "    Environment:     \"" + environment + "\",\n" +
                        "})\n" +
                        "log.AddHook(hook)";

            case KUBERNETES:
                return "# Kubernetes Fluent Bit ConfigMap Output Filter\n" +
                        "[OUTPUT]\n" +
                        "    Name        http\n" +
                        "    Match       *\n" +
                        "    Host        api-gateway.devinsight.svc.cluster.local\n" +
                        "    Port        8080\n" +
                        "    URI         /api/v1/logs\n" +
                        "    Header      X-API-Key " + apiKey + "\n" +
                        "    Header      Content-Type application/json\n" +
                        "    Format      json";

            default:
                return "# cURL Direct Ingestion\n" +
                        "curl -X POST http://localhost:8080/api/v1/logs \\\n" +
                        "  -H \"Content-Type: application/json\" \\\n" +
                        "  -H \"X-API-Key: " + apiKey + "\" \\\n" +
                        "  -d '{\n" +
                        "    \"rawLog\": \"ERROR [main] Critical system failure\",\n" +
                        "    \"source\": \"CUSTOM\",\n" +
                        "    \"environment\": \"" + environment + "\",\n" +
                        "    \"applicationName\": \"" + appName + "\"\n" +
                        "  }'";
        }
    }

    private String generateSimulatedLog(TechStack stack, String appName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        switch (stack) {
            case PYTHON:
                return timestamp + " ERROR [worker-3] " + appName + " : Critical unhandled exception\n" +
                        "Traceback (most recent call last):\n" +
                        "  File \"/app/services/checkout.py\", line 142, in process_transaction\n" +
                        "    db_conn = pool.acquire(timeout=5.0)\n" +
                        "  File \"/usr/local/lib/python3.11/asyncpg/pool.py\", line 498, in acquire\n" +
                        "    raise exceptions.PoolTimeoutError('connection pool exhausted after 5000ms')\n" +
                        "asyncpg.exceptions.PoolTimeoutError: connection pool exhausted after 5000ms";

            case NODEJS:
                return timestamp + " [error] " + appName + " : Fatal database connection timeout\n" +
                        "Error: Connection terminated unexpectedly\n" +
                        "    at Connection.parseE (/app/node_modules/pg/lib/connection.js:614:13)\n" +
                        "    at Connection.parseMessage (/app/node_modules/pg/lib/connection.js:413:19)\n" +
                        "    at Socket.<anonymous> (/app/node_modules/pg/lib/connection.js:129:22)\n" +
                        "    at Socket.emit (node:events:517:28)";

            case GO:
                return timestamp + " [ERROR] " + appName + " : panic: runtime error: invalid memory address or nil pointer dereference\n" +
                        "[signal SIGSEGV: segmentation violation code=0x1 addr=0x0 pc=0x10a2f4c]\n" +
                        "goroutine 42 [running]:\n" +
                        "main.handleTransaction(0xc00012e000)\n" +
                        "\t/app/handlers/payment.go:88 +0x3c";

            case SPRING_BOOT:
            default:
                return timestamp + "+05:30 ERROR 4201 --- [http-nio-8080-exec-1] com.project.service.OrderService : Critical failure processing checkout order #994812\n" +
                        "org.postgresql.util.PSQLException: FATAL: remaining connection slots are reserved for non-replication superuser connections\n" +
                        "\tat org.postgresql.core.v3.ConnectionFactoryImpl.doAuthentication(ConnectionFactoryImpl.java:684)\n" +
                        "\tat org.postgresql.core.v3.ConnectionFactoryImpl.tryConnect(ConnectionFactoryImpl.java:204)\n" +
                        "\tat org.postgresql.Driver.makeConnection(Driver.java:441)\n" +
                        "\tat com.zaxxer.hikari.pool.PoolBase.newConnection(PoolBase.java:364)\n" +
                        "\tat com.zaxxer.hikari.pool.HikariPool.createPoolEntry(HikariPool.java:478)\n" +
                        "\tat com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:188)";
        }
    }

    private LogSource mapTechStackToSource(TechStack stack) {
        switch (stack) {
            case PYTHON:
                return LogSource.PYTHON;
            case NODEJS:
                return LogSource.NODEJS;
            case GO:
                return LogSource.GO;
            case KUBERNETES:
                return LogSource.KUBERNETES;
            case POSTGRESQL:
                return LogSource.MYSQL;
            case NGINX:
                return LogSource.NGINX;
            case SPRING_BOOT:
            default:
                return LogSource.SPRING_BOOT;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiKeyVerificationResponse verifyApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return ApiKeyVerificationResponse.builder().valid(false).build();
        }
        return integrationRepository.findByApiKey(apiKey.trim())
                .map(app -> ApiKeyVerificationResponse.builder()
                        .valid(true)
                        .applicationName(app.getApplicationName())
                        .userEmail(app.getUserEmail() != null ? app.getUserEmail() : "service@devinsight.io")
                        .build())
                .orElseGet(() -> ApiKeyVerificationResponse.builder().valid(false).build());
    }
}
