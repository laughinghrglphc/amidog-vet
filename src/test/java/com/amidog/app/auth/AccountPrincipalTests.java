package com.amidog.app.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPrincipalTests {

    @Test
    void keepsPasswordHashOutOfJsonAndToStringWhileExposingOnlyTheExactRole() throws Exception {
        AccountPrincipal principal = new AccountPrincipal(
                10L, 20L, "ana@example.com", "Ana Pérez", AccountType.CLIENT,
                "{bcrypt}$2a$10$secret-hash", true, true);

        String json = new ObjectMapper().writeValueAsString(principal);

        assertThat(json).doesNotContain("password", "secret-hash");
        assertThat(principal.toString()).doesNotContain("secret-hash");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_CLIENT");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(new AccountPrincipal(11L, null, "admin@example.com", "Administradora", AccountType.ADMIN,
                "hash", true, true).getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void requiresBothEnabledAndVerifiedFlags() {
        assertThat(new AccountPrincipal(1L, null, "a@example.com", "A", AccountType.ADMIN,
                "hash", true, false).isEnabled()).isFalse();
        assertThat(new AccountPrincipal(1L, null, "a@example.com", "A", AccountType.ADMIN,
                "hash", false, true).isEnabled()).isFalse();
    }

    @Test
    void excludesPasswordHashFromSerializedSecurityContextWhileKeepingSessionIdentityAndRoles() throws Exception {
        String passwordHash = "{bcrypt}$2a$10$session-secret-hash";
        AccountPrincipal principal = new AccountPrincipal(
                10L, 20L, "ana@example.com", "Ana Pérez", AccountType.CLIENT,
                passwordHash, true, true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));

        byte[] serialized = serialize(context);
        SecurityContext restoredContext = deserialize(serialized);
        AccountPrincipal restored = (AccountPrincipal) restoredContext.getAuthentication().getPrincipal();

        assertThat(indexOf(serialized, passwordHash.getBytes(StandardCharsets.UTF_8))).isNegative();
        assertThat(restored.getPassword()).isNull();
        assertThat(restored.getUserId()).isEqualTo(10L);
        assertThat(restored.getClientId()).isEqualTo(20L);
        assertThat(restored.getUsername()).isEqualTo("ana@example.com");
        assertThat(restored.getDisplayName()).isEqualTo("Ana Pérez");
        assertThat(restored.isEnabled()).isTrue();
        assertThat(restored.getAuthorities()).extracting("authority").containsExactly("ROLE_CLIENT");
    }

    @Test
    void providerManagerErasesPrincipalCredentialsBeforeSuccessfulResultCanBeStored() {
        var encoder = new com.amidog.app.config.SecurityConfig().passwordEncoder();
        for (String storedHash : java.util.List.of(
                encoder.encode("Correct-Horse-9!"),
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                        .encode("Correct-Horse-9!"))) {
            AccountPrincipal loaded = new AccountPrincipal(
                    10L, 20L, "ana@example.com", "Ana P\u00e9rez", AccountType.CLIENT,
                    storedHash, true, true);
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(username -> loaded);
            provider.setPasswordEncoder(encoder);

            var result = new ProviderManager(provider).authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            "ana@example.com", "Correct-Horse-9!"));
            AccountPrincipal authenticated = (AccountPrincipal) result.getPrincipal();

            assertThat(result.getCredentials()).isNull();
            assertThat(authenticated.getPassword()).isNull();
            assertThat(authenticated.getUserId()).isEqualTo(10L);
            assertThat(authenticated.getAuthorities()).extracting("authority")
                    .containsExactly("ROLE_CLIENT");
        }
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static SecurityContext deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (SecurityContext) input.readObject();
        }
    }

    private static int indexOf(byte[] bytes, byte[] sequence) {
        for (int offset = 0; offset <= bytes.length - sequence.length; offset++) {
            boolean match = true;
            for (int index = 0; index < sequence.length; index++) {
                if (bytes[offset + index] != sequence[index]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return offset;
            }
        }
        return -1;
    }
}
