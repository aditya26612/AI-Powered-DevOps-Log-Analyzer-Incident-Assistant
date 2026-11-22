package com.project.log_layer.diagnosis;

import com.project.log_layer.integration.client.LlmInferenceClient;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;
import com.project.log_layer.integration.exception.LlmServiceException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.project.log_layer.diagnosis.DiagnosisQueue.Failure;

@Component
@EnableConfigurationProperties(DiagnosisSettings.class)
@RequiredArgsConstructor
@Slf4j
public class DiagnosisWorker {
    private final DiagnosisQueue queue;
    private final DiagnosisContext context;
    private final LlmInferenceClient llm;
    private final DiagnosisSettings settings;
    private final UUID worker = UUID.randomUUID();
    private ThreadPoolExecutor calls;
    private ScheduledExecutorService polling;
    private ScheduledExecutorService renewal;
    private UUID cursor;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!settings.enabled()) {
            return;
        }
        calls = new ThreadPoolExecutor(settings.workers(), settings.workers(), 0, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), Thread.ofVirtual().name("diagnosis-inference-", 0).factory());
        polling = Executors.newScheduledThreadPool(settings.workers(),
                Thread.ofPlatform().daemon().name("diagnosis-poll-", 0).factory());
        renewal = Executors.newScheduledThreadPool(settings.workers(),
                Thread.ofPlatform().daemon().name("diagnosis-renew-", 0).factory());
        for (int slot=0; slot<settings.workers(); slot++) {
            polling.scheduleWithFixedDelay(this::poll, 0, settings.poll().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void poll() {
        try {
            List<UUID> organizations = nextOrganizations();
            for (UUID organization : organizations) {
                var claim = queue.claim(organization, worker);
                if (claim != null) {
                    execute(claim);
                    return;
                }
            }
        } catch (RuntimeException ex) {
            // A lost database connection leaves the claim recoverable after expiry.
            log.debug("Diagnosis poll deferred ({})", ex.getClass().getSimpleName());
        }
    }

    private synchronized List<UUID> nextOrganizations() {
        var organizations = queue.organizationsAfter(cursor);
        if (organizations.isEmpty() && cursor != null) {
            organizations = queue.organizationsAfter(null);
        }
        // Advance even when the first tenant continuously has work.
        cursor = organizations.isEmpty() ? null : organizations.getFirst();
        return organizations;
    }

    private void execute(DiagnosisQueue.Claim claim) {
        DiagnosisContext.Input input;
        try {
            input = context.load(claim);
        } catch (DataAccessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            queue.fail(claim, Failure.CONTEXT, false);
            return;
        }
        if (input == null) {
            return;
        }

        var lost = new AtomicBoolean();
        Future<LlmAnalysisResponse> call;
        try {
            call = calls.submit(() -> llm.analyze(input.request()));
        } catch (RejectedExecutionException ex) {
            queue.fail(claim, Failure.OVERLOADED, true);
            return;
        }
        long interval = settings.heartbeat().toMillis();
        var heartbeat = renewal.scheduleWithFixedDelay(() -> {
            try {
                if (queue.heartbeat(claim)) {
                    return;
                }
            } catch (RuntimeException ex) {
                log.debug("Diagnosis heartbeat unavailable");
            }
            lost.set(true);
            call.cancel(true);
        }, interval, interval, TimeUnit.MILLISECONDS);
        try {
            LlmAnalysisResponse result = call.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!lost.get() && !valid(result)) {
                queue.fail(claim, Failure.INVALID_RESPONSE, false);
            } else if (!lost.get()) {
                queue.succeed(claim, result);
            }
        } catch (TimeoutException ex) {
            call.cancel(true);
            queue.fail(claim, Failure.DEADLINE, true);
        } catch (ExecutionException ex) {
            if (!lost.get()) {
                classify(claim, ex.getCause());
            }
        } catch (InterruptedException ex) {
            call.cancel(true);
            Thread.currentThread().interrupt();
        } catch (CancellationException ignored) {
            // Expiry or shutdown leaves the attempt to the database recovery path.
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void classify(DiagnosisQueue.Claim claim, Throwable failure) {
        if (failure instanceof LlmServiceException ex && ex.httpStatus() != null) {
            int status = ex.httpStatus();
            if (status == 408 || status == 429 || status == 503) {
                queue.fail(claim, Failure.OVERLOADED, true);
            } else if (status >= 500) {
                queue.fail(claim, Failure.UPSTREAM, true);
            } else {
                queue.fail(claim, Failure.REJECTED, false);
            }
        } else if (failure instanceof LlmServiceException ex && ex.invalidResponse()) {
            queue.fail(claim, Failure.INVALID_RESPONSE, false);
        } else {
            queue.fail(claim, Failure.TRANSPORT, true);
        }
    }

    private static boolean valid(LlmAnalysisResponse result) {
        return result != null && field(result.summary()) && field(result.rootCause())
                && field(result.recommendation()) && field(result.severity());
    }

    private static boolean field(String value) {
        return value != null && !value.isBlank() && value.length() <= 16000 && value.indexOf('\0') < 0;
    }

    @PreDestroy
    public void stop() {
        if (polling != null) {
            polling.shutdown();
            try {
                if (!polling.awaitTermination(3, TimeUnit.SECONDS)) {
                    polling.shutdownNow();
                }
            } catch (InterruptedException ex) {
                polling.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                renewal.shutdownNow();
            }
        }
        if (calls != null) {
            calls.shutdownNow();
        }
    }
}
