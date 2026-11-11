package com.project.log_layer.parser;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.parser.impl.DockerParser;
import com.project.log_layer.parser.impl.GenericLogParser;
import com.project.log_layer.parser.impl.KubernetesParser;
import com.project.log_layer.parser.impl.SpringBootParser;
import com.project.log_layer.parser.model.ParsedLogData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void parserFactory_shouldFallbackToGenericParserForUnknownSource() {
        LogParser parser = parserFactory.getParser(LogSource.KAFKA);
        assertNotNull(parser);
        assertTrue(parser instanceof GenericLogParser);
    }
}
