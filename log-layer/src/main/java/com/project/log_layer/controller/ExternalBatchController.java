package com.project.log_layer.controller;

import com.project.log_layer.security.TenantIdentity;
import com.project.log_layer.service.ExternalBatchIngestion;
import com.project.log_layer.tenant.ApplicationCredentials.Binding;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ExternalBatchController {
    private final ExternalBatchIngestion ingestion;

    @PostMapping("/api/v1/logs/batch")
    public ResponseEntity<ExternalBatchIngestion.Result> submit(@RequestBody byte[] body, HttpServletRequest request) {
        if (!(request.getAttribute(TenantIdentity.INGESTION) instanceof Binding owner)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An application credential is required");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ingestion.submit(body, owner));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> batchError(ResponseStatusException error) {
        var response = ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore());
        if (error.getStatusCode().value() == 429) {
            long seconds = 60 - Math.floorMod(System.currentTimeMillis() / 1000, 60);
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return response.body(Map.of("message", error.getReason()));
    }
}
