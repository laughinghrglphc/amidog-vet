package com.amidog.app.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenServiceTests {

    private final SecureTokenService tokens = new SecureTokenService(new SecureRandom());

    @Test
    void storesAStableHashInsteadOfTheRawToken() {
        SecureTokenService.IssuedToken issued = tokens.issue();

        assertThat(issued.raw()).doesNotContain("=");
        assertThat(issued.hash()).hasSize(64);
        assertThat(tokens.hash(issued.raw())).isEqualTo(issued.hash());
        assertThat(issued.hash()).doesNotContain(issued.raw());
    }

    @Test
    void twoIssuedTokensAreDifferent() {
        assertThat(tokens.issue().raw()).isNotEqualTo(tokens.issue().raw());
    }

    @Test
    void issuedTokenToStringRedactsBothSecrets() {
        SecureTokenService.IssuedToken issued = tokens.issue();

        assertThat(issued.toString())
                .doesNotContain(issued.raw())
                .doesNotContain(issued.hash());
    }
}
