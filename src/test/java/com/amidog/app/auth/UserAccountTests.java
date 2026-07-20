package com.amidog.app.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountTests {

    @Test
    void clientCanonicalizesWhitespaceAndCaseInEmail() {
        UserAccount account = UserAccount.client(
                "  Ana.Perez@Example.COM  ", "hash", Instant.parse("2026-07-29T00:00:00Z"));

        assertThat(account.getEmailNormalized()).isEqualTo("ana.perez@example.com");
    }

    @Test
    void adminRejectsBlankEmail() {
        assertThatThrownBy(() -> UserAccount.admin("   ", "hash", Instant.parse("2026-07-29T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email must not be blank");
    }

    @Test
    void clientRejectsNullEmail() {
        assertThatThrownBy(() -> UserAccount.client(null, "hash", Instant.parse("2026-07-29T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email must not be null");
    }
}
