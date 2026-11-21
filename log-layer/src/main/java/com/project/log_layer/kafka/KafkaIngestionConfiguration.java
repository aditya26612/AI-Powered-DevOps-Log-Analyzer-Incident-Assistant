package com.project.log_layer.kafka;

import com.project.log_layer.service.ExternalBatchIngestion;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "devinsight.kafka.enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaMode.class)
class KafkaIngestionConfiguration {
    @Bean KafkaLagMonitor kafkaLagMonitor(KafkaMode mode, MeterRegistry registry) {
        mode.validate();
        return new KafkaLagMonitor(mode, registry);
    }

    @Bean KafkaIngress kafkaIngress(KafkaMode mode, ExternalBatchIngestion batches, KafkaLagMonitor lag,
                                   MeterRegistry registry) {
        return new KafkaIngress(mode, batches, lag, registry);
    }

    @Bean KafkaRecordProcessor kafkaRecordProcessor(KafkaMode mode, ExternalBatchIngestion batches) {
        return new KafkaRecordProcessor(batches, mode);
    }

    @Bean
    @ConditionalOnProperty(name = "devinsight.kafka.consumer-enabled", havingValue = "true", matchIfMissing = true)
    KafkaWorker kafkaWorker(KafkaMode mode, KafkaRecordProcessor processor, MeterRegistry registry, KafkaLagMonitor lag) {
        return new KafkaWorker(mode, processor, registry);
    }

    @Bean HealthIndicator kafkaBrokerHealthIndicator(KafkaLagMonitor lag,
                                                      org.springframework.beans.factory.ObjectProvider<KafkaWorker> worker) {
        return () -> lag.healthy() && (worker.getIfAvailable() == null || worker.getObject().hasPolled())
                ? Health.up().build() : Health.down().build();
    }
}
