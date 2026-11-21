package com.project.log_layer.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantSqlScope {
    private final JdbcTemplate jdbc;

    public void enter(UUID organization) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Tenant queries require an active transaction");
        }
        jdbc.queryForObject("select set_config('devinsight.organization_id', ?, true)",
                String.class, organization.toString());
    }
}
