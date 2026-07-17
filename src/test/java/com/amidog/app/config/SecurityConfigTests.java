package com.amidog.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTests {

    @Test
    void writesPrefixedHashesAndStillMatchesTaskFourLegacyBcryptHashes() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
        String legacyHash = new BCryptPasswordEncoder(4).encode("Correct-Horse-9!");
        String modernHash = encoder.encode("Correct-Horse-9!");

        assertThat(encoder.matches("Correct-Horse-9!", legacyHash)).isTrue();
        assertThat(modernHash).startsWith("{bcrypt}");
        assertThat(encoder.matches("Correct-Horse-9!", modernHash)).isTrue();
    }
}
