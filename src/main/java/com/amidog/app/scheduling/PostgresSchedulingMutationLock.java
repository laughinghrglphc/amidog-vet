package com.amidog.app.scheduling;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PostgresSchedulingMutationLock implements SchedulingMutationLock {

    /**
     * Fixed signed 64-bit key derived once for the AmiDog scheduling domain.
     * All scheduling writers use this exact key.
     */
    public static final long LOCK_KEY = 4_701_527_609_061_356_132L;

    private final JdbcClient jdbc;

    public PostgresSchedulingMutationLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void acquire() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Scheduling mutation lock requires an active transaction");
        }
        jdbc.sql("select pg_advisory_xact_lock(:key)")
                .param("key", LOCK_KEY)
                .query((row, number) -> Boolean.TRUE)
                .single();
    }
}
