package com.amidog.app.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailVerificationTokenTests {

    private static final Instant CREATED_AT = Instant.parse("2026-07-29T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void consumesImmediatelyBeforeExpiry() {
        EmailVerificationToken token = token();
        Instant now = EXPIRES_AT.minusNanos(1);

        token.consume(now);

        assertThat(token.getConsumedAt()).isEqualTo(now);
    }

    @Test
    void rejectsConsumptionExactlyAtExpiry() {
        assertThatThrownBy(() -> token().consume(EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token cannot be consumed");
    }

    @Test
    void rejectsExpiredConsumption() {
        assertThatThrownBy(() -> token().consume(EXPIRES_AT.plusNanos(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token cannot be consumed");
    }

    @Test
    void firstConsumptionSucceeds() {
        EmailVerificationToken token = token();
        Instant now = CREATED_AT.plusSeconds(1);

        token.consume(now);

        assertThat(token.getConsumedAt()).isEqualTo(now);
    }

    @Test
    void rejectsDoubleConsumptionAndPreservesFirstTimestamp() {
        EmailVerificationToken token = token();
        Instant firstConsumption = CREATED_AT.plusSeconds(1);
        token.consume(firstConsumption);

        assertThatThrownBy(() -> token.consume(firstConsumption.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token cannot be consumed");
        assertThat(token.getConsumedAt()).isEqualTo(firstConsumption);
    }

    @Test
    void rejectsNullConsumptionTime() {
        assertThatThrownBy(() -> token().consume(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consumption time must not be null");
    }

    private static EmailVerificationToken token() {
        return EmailVerificationToken.create(
                UserAccount.client("ana@example.com", "hash", CREATED_AT),
                "a".repeat(64), EXPIRES_AT, CREATED_AT);
    }
}
