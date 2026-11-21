package com.project.log_layer.controller;

import com.project.log_layer.kafka.KafkaIngress;
import com.project.log_layer.security.TenantIdentity;
import com.project.log_layer.tenant.ApplicationCredentials.Binding;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "devinsight.kafka.enabled", havingValue = "true")
public class KafkaBatchController {
    private final KafkaIngress ingress;

    @PostMapping("/api/v2/logs/batch")
    ResponseEntity<KafkaIngress.Accepted> accept(@RequestBody byte[] body, HttpServletRequest request) {
        if (!(request.getAttribute(TenantIdentity.INGESTION) instanceof Binding owner)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An application credential is required");
        }
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ingress.accept(body, owner));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> response(ResponseStatusException error) {
        var builder = ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore());
        if (error.getStatusCode().value() == 503) {
            builder.header(HttpHeaders.RETRY_AFTER, "1");
        } else if (error.getStatusCode().value() == 429) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(60 - Math.floorMod(System.currentTimeMillis() / 1000, 60)));
        }
        return builder.body(Map.of("message", error.getReason()));
    }
}
