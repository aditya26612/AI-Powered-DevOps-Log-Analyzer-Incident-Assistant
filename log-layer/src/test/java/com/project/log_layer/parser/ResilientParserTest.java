package com.project.log_layer.parser;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.parser.impl.DockerParser;
import com.project.log_layer.parser.impl.GenericLogParser;
import com.project.log_layer.parser.impl.KubernetesParser;
import com.project.log_layer.parser.impl.NodeJsParser;
import com.project.log_layer.parser.impl.PythonParser;
import com.project.log_layer.parser.impl.SpringBootParser;
import com.project.log_layer.parser.model.ParsedLogData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResilientParserTest {

    private SpringBootParser springBootParser;
    private DockerParser dockerParser;
    private KubernetesParser kubernetesParser;
    private GenericLogParser genericLogParser;
    private ParserFactory parserFactory;

    @BeforeEach
    void setUp() {
        springBootParser = new SpringBootParser();
        dockerParser = new DockerParser();
        kubernetesParser = new KubernetesParser();
        genericLogParser = new GenericLogParser();

        parserFactory = new ParserFactory(List.of(
                springBootParser,
                dockerParser,
                kubernetesParser,
                genericLogParser
        ));
    }

    @Test
    void springBootParser_shouldParseMultiLineStackTraceWithoutFailing() {
        String multiLineLog = """
                2026-06-26T20:15:31.123+05:30 ERROR 14201 --- [main] o.s.b.web.embedded.tomcat.TomcatWebServer : Port 8080 was already in use.
                org.springframework.boot.web.server.PortInUseException: Port 8080 is already in use
                \tat org.springframework.boot.web.embedded.tomcat.TomcatWebServer.start(TomcatWebServer.java:242)
                \tat org.springframework.boot.SpringApplication.startWebServer(SpringApplication.java:312)
                """.trim();

        LogIngestRequest request = LogIngestRequest.builder()
                .rawLog(multiLineLog)
                .source(LogSource.SPRING_BOOT)
                .environment(Environment.PRODUCTION)
                .applicationName("payment-service")
                .build();

        ParsedLogData parsed = springBootParser.parse(request);

        assertNotNull(parsed);
        assertEquals(LogLevel.ERROR, parsed.getLogLevel());
        assertEquals("payment-service", parsed.getApplicationName());
        assertTrue(parsed.getMessage().contains("PortInUseException"));
        assertTrue(parsed.getMessage().contains("TomcatWebServer.start"));
    }

    @Test
    void springBootParser_shouldGracefullyFallbackOnNonStandardLog() {
        String oddLog = "CRITICAL: Database connection died unexpectedly on socket 5432";

        LogIngestRequest request = LogIngestRequest.builder()
                .rawLog(oddLog)
                .source(LogSource.SPRING_BOOT)
                .environment(Environment.PRODUCTION)
                .applicationName("order-service")
                .build();

        ParsedLogData parsed = springBootParser.parse(request);

        assertNotNull(parsed);
        assertEquals(LogLevel.ERROR, parsed.getLogLevel());
        assertTrue(parsed.getMessage().contains("Database connection died"));
    }

    @Test
    void dockerParser_shouldDetectErrorSeverityAndPreserveMultiLine() {
        String dockerLog = """
                2026-06-26T20:15:31.123456789Z Traceback (most recent call last):
                  File "app.py", line 42, in <module>
                    raise ConnectionError("Redis host unreachable")
                ConnectionError: Redis host unreachable
                """.trim();

        LogIngestRequest request = LogIngestRequest.builder()
                .rawLog(dockerLog)
                .source(LogSource.DOCKER)
                .environment(Environment.PRODUCTION)
                .applicationName("python-worker")
                .build();

        ParsedLogData parsed = dockerParser.parse(request);

        assertNotNull(parsed);
        assertEquals(LogLevel.ERROR, parsed.getLogLevel());
        assertTrue(parsed.getMessage().contains("ConnectionError"));
        assertTrue(parsed.getMessage().contains("Redis host unreachable"));
    }

    @Test
    void kubernetesParser_shouldDetectStderrAsError() {
        String k8sLog = "2026-06-26T20:15:31.123456789Z stderr F Failed to bind to port 8080";

        LogIngestRequest request = LogIngestRequest.builder()
                .rawLog(k8sLog)
                .source(LogSource.KUBERNETES)
                .environment(Environment.PRODUCTION)
                .applicationName("auth-pod")
                .build();

        ParsedLogData parsed = kubernetesParser.parse(request);

        assertNotNull(parsed);
        assertEquals(LogLevel.ERROR, parsed.getLogLevel());
        assertEquals("auth-pod", parsed.getApplicationName());
    }

    @Test
    void kubernetesParser_shouldHonorExplicitWarnBeforeFailureKeywordInFallback() {
        // The benchmark fixture is a simplified Kubernetes log without stdout/stderr + flag.
        String log = "2026-11-18T12:00:00Z WARN [pod/benchmarkcalculator] "
                + "Restarting container because of synthetic probe failure";
        LogIngestRequest request = LogIngestRequest.builder()
                .rawLog(log)
                .source(LogSource.KUBERNETES)
                .environment(Environment.TESTING)
                .applicationName("benchmarkcalculator")
                .build();

        ParsedLogData parsed = kubernetesParser.parse(request);
        assertEquals(LogLevel.WARN, parsed.getLogLevel());
        assertTrue(parsed.getMessage().contains("probe failure"));
    }

    @Test
    void kubernetesParser_shouldHonorExplicitWarnInStandardStdoutFormat() {
        String log = "2026-11-18T12:00:00Z stdout F WARN synthetic probe failure";
        LogIngestRequest request = LogIngestRequest.builder()
                .rawLog(log)
                .source(LogSource.KUBERNETES)
                .environment(Environment.TESTING)
                .build();

        ParsedLogData parsed = kubernetesParser.parse(request);
        assertEquals(LogLevel.WARN, parsed.getLogLevel());
        assertEquals("stdout", parsed.getMetadata().get("stream"));
    }

    @Test
    void genericParser_doesNotPromoteFailureWordsToSeverity() {
        LogIngestRequest request = LogIngestRequest.builder()
                .rawLog("Database connection failed without a severity label")
                .source(LogSource.CUSTOM)
                .environment(Environment.TESTING)
                .build();

        ParsedLogData parsed = genericLogParser.parse(request);
        assertEquals(LogLevel.INFO, parsed.getLogLevel());
        assertTrue(parsed.isFallback());
        assertEquals(request.getRawLog(), parsed.getRawLog());
    }

    @Test
    void pythonAndNodeRecognizeTimestampSeverityAndMultilineContent() {
        String python = "2026-11-19 15:30:01,123+0530 WARNING [worker] app.module: failure handled\nnext line: café";
        var parsed = new PythonParser().parse(LogIngestRequest.builder().rawLog(python)
                .applicationName("payment-service").source(LogSource.PYTHON).build());
        assertEquals(LocalDateTime.parse("2026-11-19T10:00:01.123"), parsed.getTimestamp());
        assertEquals(LogLevel.WARN, parsed.getLogLevel());
        assertEquals("worker", parsed.getThreadName());
        assertEquals("app.module", parsed.getLoggerName());
        assertTrue(parsed.getMessage().contains("\nnext line: café"));
        assertEquals(python, parsed.getRawLog());

        String node = "2026-11-19T10:00:00Z [INFO] node: ERROR counter reset";
        parsed = new NodeJsParser().parse(LogIngestRequest.builder().rawLog(node)
                .applicationName("payment-service").source(LogSource.NODEJS).build());
        assertEquals(LogLevel.INFO, parsed.getLogLevel());
        assertEquals("ERROR counter reset", parsed.getMessage());

        var structured = new NodeJsParser().parse(LogIngestRequest.builder().source(LogSource.NODEJS)
                .applicationName("payment-service")
                .rawLog("{\"level\":\"error\",\"message\":\"Redis connection refused\"}").build());
        assertEquals(LogLevel.ERROR, structured.getLogLevel());
        assertEquals("Redis connection refused", structured.getMessage());
    }

    @Test
    void boundedJsonUsesOnlyRecognizedFieldsAndKeepsRaw() {
        String raw = "  {\"timestamp\":\"2026-11-19T15:30:00.125+05:30\",\"level\":\"info\","
                + "\"message\":\"ERROR counter \\\"零\\\"\",\"applicationName\":\"other\",\"nested\":{\"level\":\"FATAL\"}}  ";
        var parsed = genericLogParser.parse(LogIngestRequest.builder().rawLog(raw)
                .source(LogSource.CUSTOM).applicationName("verified").build());
        assertEquals(LogLevel.INFO, parsed.getLogLevel());
        assertEquals("ERROR counter \"零\"", parsed.getMessage());
        assertEquals("verified", parsed.getApplicationName());
        assertEquals(LocalDateTime.parse("2026-11-19T10:00:00.125"), parsed.getTimestamp());
        assertEquals(raw, parsed.getRawLog());
        assertFalse(parsed.isFallback());

        String deep = "{\"message\":\"ordinary\",\"nested\":" + "[".repeat(24) + "0" + "]".repeat(24) + "}";
        var fallback = genericLogParser.parse(LogIngestRequest.builder().rawLog(deep).build());
        assertTrue(fallback.isFallback());
        assertEquals(deep, fallback.getRawLog());
    }

    @ParameterizedTest
    @ValueSource(strings = {"  {\"message\":\"truncated", "{\"level\":\"FATAL\",\"message\":\"a\",\"message\":\"b\"}"})
    void invalidJsonFallsBackWithoutClaimingCriticalSeverity(String raw) {
        var parsed = genericLogParser.parse(LogIngestRequest.builder().rawLog(raw).build());
        assertEquals(LogLevel.INFO, parsed.getLogLevel());
        assertEquals(raw, parsed.getRawLog());
        assertTrue(parsed.isFallback());
    }

    @Test
    void rawLogLimitRejectsInsteadOfTruncating() {
        var request = LogIngestRequest.builder().rawLog("界".repeat(16_384)).build();
        assertEquals(request.getRawLog(), genericLogParser.parse(request).getRawLog());
        request.setRawLog(request.getRawLog() + "x");
        assertThrows(com.project.log_layer.exception.InvalidLogFormatException.class, () -> genericLogParser.parse(request));

        String unfinishedHeader = "2026-11-19T10:00:00Z INFO 1 --- [main]" + " ".repeat(16_000) + "incomplete";
        var malformed = LogIngestRequest.builder().rawLog(unfinishedHeader).source(LogSource.SPRING_BOOT).build();
        var parsed = assertTimeout(Duration.ofSeconds(2), () -> springBootParser.parse(malformed));
        assertEquals(unfinishedHeader, parsed.getRawLog());
        assertEquals(LogLevel.INFO, parsed.getLogLevel());
    }

    @Test
    void parser_shouldPreserveFatalLogLevel() {
        LogIngestRequest fatalRequest = LogIngestRequest.builder()
                .rawLog("2026-11-19T12:00:00Z FATAL [auth-pod] Core memory corrupted, emergency halt")
                .source(LogSource.KUBERNETES)
                .environment(Environment.PRODUCTION)
                .build();
        ParsedLogData parsedFatal = kubernetesParser.parse(fatalRequest);
        assertEquals(LogLevel.FATAL, parsedFatal.getLogLevel());

        LogIngestRequest fatalSpringRequest = LogIngestRequest.builder()
                .rawLog("2026-11-19 12:00:00.000 FATAL 1 --- [main] c.p.service : Fatal failure detected")
                .source(LogSource.SPRING_BOOT)
                .environment(Environment.PRODUCTION)
                .build();
        ParsedLogData parsedSpringFatal = springBootParser.parse(fatalSpringRequest);
        assertEquals(LogLevel.FATAL, parsedSpringFatal.getLogLevel());
    }

    @Test
    void parserFactory_shouldFallbackToGenericParserForUnknownSource() {
        LogParser parser = parserFactory.getParser(LogSource.KAFKA);
        assertNotNull(parser);
        assertTrue(parser instanceof GenericLogParser);
    }
}
