package com.amidog.app.scheduling;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulingMutationLockTests {

    @Test
    void advisoryLockFailsClosedWhenAWriterForgetsItsTransaction() {
        PostgresSchedulingMutationLock lock = new PostgresSchedulingMutationLock(null);

        assertThatThrownBy(lock::acquire)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Scheduling mutation lock requires an active transaction");
    }
}
