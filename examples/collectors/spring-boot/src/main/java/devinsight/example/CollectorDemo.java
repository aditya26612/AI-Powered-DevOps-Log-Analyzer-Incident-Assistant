package devinsight.example;

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.client.DevInsightLogbackAppender;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CollectorDemo implements CommandLineRunner {
    public static void main(String[] args) {
        var app = new SpringApplication(CollectorDemo.class);
        app.setBannerMode(Banner.Mode.OFF);
        try (var context = app.run(args)) {
            // Closing the application also closes any remaining Logback appenders.
        }
    }

    @Override
    public void run(String... args) throws Exception {
        int count = Integer.parseInt(System.getenv().getOrDefault("DEMO_EVENTS", "6"));
        int delay = Integer.parseInt(System.getenv().getOrDefault("DEMO_DELAY_MS", "0"));
        boolean failures = !"false".equalsIgnoreCase(System.getenv("DEMO_ERRORS"));
        if (count < 1 || count > 5000 || delay < 0 || delay > 1000) {
            throw new IllegalArgumentException("Choose 1 to 5000 events and a delay between 0 and 1000 ms");
        }
        var logger = (Logger) LoggerFactory.getLogger("devinsight.example.events");
        var appender = (DevInsightLogbackAppender) logger.getAppender("DEVINSIGHT");
        if (appender == null || !appender.isStarted()) {
            throw new IllegalStateException("DevInsight appender is unavailable. Check protected configuration.");
        }
        try {
            for (int i = 0; i < count; i++) {
                if (failures && i == count - 1) {
                    logger.error("Database connection timed out during checkout",
                            new IllegalStateException("Synthetic database timeout"));
                } else if (failures && i == count - 2) {
                    logger.warn("Synthetic dependency slowdown");
                } else {
                    logger.info("Payment accepted in independent Spring Boot example");
                }
                if (delay != 0) Thread.sleep(delay);
            }
        } finally {
            appender.stop();
        }
        System.out.println("DEVINSIGHT_STATS " + new ObjectMapper().writeValueAsString(appender.stats()));
    }
}
