package com.project.log_layer.logsource;

import com.project.log_layer.service.ExternalBatchIngestion;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
class LokiPoller {
    private static final int LOOKAHEAD = ExternalBatchIngestion.MAX_EVENTS + 1;
    private final LokiClient client;
    private final LokiSources sources;
    private ScheduledExecutorService scheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (client.organization() == null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("devinsight-loki-poll").factory());
        scheduler.scheduleWithFixedDelay(this::tick, 0, 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void tick() {
        try {
            var poll = sources.due(client.organization());
            if (poll != null) sync(poll);
        } catch (RuntimeException error) {
            log.warn("Loki poll deferred ({})", error.getClass().getSimpleName());
        }
    }

    void sync(LokiSources.Poll poll) {
        try {
            // Let collectors finish shipping recent records before closing the window.
            long safeEnd = LokiSources.nowNanos() - 15 * LokiSources.SECOND;
            if (safeEnd <= poll.cursor()) return;
            long end = Math.min(safeEnd, poll.cursor() + LokiSources.WINDOW);
            for (int attempt = 0; attempt < 12; attempt++) {
                var lines = client.query(poll.endpoint(), poll.label(), poll.value(), poll.cursor(), end, LOOKAHEAD);
                if (lines.size() < LOOKAHEAD) {
                    sources.importWindow(poll, end, end < safeEnd, lines);
                    return;
                }
                end = poll.cursor() + (end - poll.cursor()) / 2;
                if (end <= poll.cursor()) break;
            }
            throw new LokiClient.Failure("Loki window is too dense to import safely. Narrow the stream selector.");
        } catch (LokiClient.Failure failure) {
            sources.fail(poll, failure.getMessage());
        } catch (RuntimeException error) {
            sources.fail(poll, "DevInsight could not persist the Loki batch. The checkpoint was kept for retry.");
            log.warn("Loki batch import deferred ({})", error.getClass().getSimpleName());
        }
    }
}
