package com.project.log_layer.notification.outbox;

import com.project.log_layer.diagnosis.DiagnosisQueue;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@EnableConfigurationProperties(DeliverySettings.class)
@RequiredArgsConstructor
class NotificationDeliveryWorker {
    private final NotificationQueue queue;
    private final DiagnosisQueue organizations;
    private final SlackDeliveryClient client;
    private final DeliverySettings settings;
    private final MeterRegistry meters;
    private final UUID identity = UUID.randomUUID();
    private UUID cursor;
    private ScheduledExecutorService slots;
    private ScheduledExecutorService renewal;

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        if (!settings.worker() || settings.transport() == DeliverySettings.Transport.OFF) {
            return;
        }
        slots = Executors.newScheduledThreadPool(settings.workers(),
                Thread.ofPlatform().daemon().name("slack-dispatch-",0).factory());
        renewal = Executors.newScheduledThreadPool(settings.workers(),
                Thread.ofPlatform().daemon().name("slack-lease-",0).factory());
        for (int index=0; index<settings.workers(); index++) {
            slots.scheduleWithFixedDelay(this::poll,0,settings.poll().toMillis(),TimeUnit.MILLISECONDS);
        }
    }

    private void poll() {
        try {
            for (UUID tenant : nextOrganizations()) {
                var claim = queue.claim(tenant,identity);
                if (claim != null) {
                    deliver(claim);
                    return;
                }
            }
        } catch (RuntimeException ex) {
            // Transport exceptions and sensitive context are never copied into diagnostic output.
            log.debug("Notification poll postponed");
        }
    }

    private synchronized List<UUID> nextOrganizations() {
        List<UUID> tenants = organizations.organizationsAfter(cursor);
        if (tenants.isEmpty() && cursor != null) {
            tenants = organizations.organizationsAfter(null);
        }
        cursor = tenants.isEmpty() ? null : tenants.getFirst();
        return tenants;
    }

    void deliver(NotificationQueue.Claim claim) {
        var work = queue.begin(claim);
        if (work == null) {
            return;
        }
        long interval = settings.heartbeat().toMillis();
        if (renewal == null) {
            send(claim,work);
            return;
        }
        var heartbeat = renewal.scheduleWithFixedDelay(() -> {
            try {
                queue.heartbeat(claim);
            } catch (RuntimeException ex) {
                log.debug("Notification lease renewal postponed");
            }
        },interval,interval,TimeUnit.MILLISECONDS);
        try {
            send(claim,work);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void send(NotificationQueue.Claim claim, NotificationQueue.Work work) {
        var duration = io.micrometer.core.instrument.Timer.start(meters);
        var result = client.send(work);
        duration.stop(meters.timer("devinsight.slack.http", "outcome", result.status()));
        if (!queue.finish(claim,result)) {
            meters.counter("devinsight.slack.fenced").increment();
        }
    }

    @PreDestroy
    void stop() {
        if (slots == null) {
            return;
        }
        slots.shutdown();
        try {
            if (!slots.awaitTermination(4,TimeUnit.SECONDS)) {
                slots.shutdownNow();
            }
        } catch (InterruptedException ex) {
            slots.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            renewal.shutdownNow();
        }
    }
}
