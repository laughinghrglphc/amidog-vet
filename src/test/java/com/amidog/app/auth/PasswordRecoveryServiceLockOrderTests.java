package com.amidog.app.auth;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRecoveryServiceLockOrderTests {

    @Test
    void resetResolvesTheTokenThenLocksUserBeforeAnyTokenMutation() {
        List<String> calls = new ArrayList<>();
        UserAccount account = UserAccount.client("ana@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z"));
        UserAccountRepository users = repository(UserAccountRepository.class, (method, arguments) -> switch (method.getName()) {
            case "findByIdForUpdate" -> {
                calls.add("lock-user");
                yield Optional.of(account);
            }
            default -> defaultValue(method.getReturnType());
        });
        PasswordResetTokenRepository tokens = repository(PasswordResetTokenRepository.class, (method, arguments) -> switch (method.getName()) {
            case "findUserIdByTokenHash" -> {
                calls.add("resolve-token-user");
                yield Optional.of(7L);
            }
            case "consumeIfActiveByTokenHash" -> {
                calls.add("consume-token");
                yield 1;
            }
            case "invalidateAllForUser" -> {
                calls.add("invalidate-tokens");
                yield 1;
            }
            default -> defaultValue(method.getReturnType());
        });

        service(users, tokens).reset("raw-token", "New-Password-10!");

        assertThat(calls).containsExactly("resolve-token-user", "lock-user", "consume-token", "invalidate-tokens");
    }

    @Test
    void forgotPasswordLocksTheUserBeforeInvalidatingAndReplacingTokens() {
        List<String> calls = new ArrayList<>();
        UserAccount account = UserAccount.client("ana@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z"));
        account.verify(Instant.parse("2026-07-29T00:00:00Z"));
        UserAccountRepository users = repository(UserAccountRepository.class, (method, arguments) -> switch (method.getName()) {
            case "findByEmailNormalizedForUpdate" -> {
                calls.add("lock-user");
                yield Optional.of(account);
            }
            case "getReferenceById" -> {
                calls.add("managed-user-reference");
                yield account;
            }
            default -> defaultValue(method.getReturnType());
        });
        PasswordResetTokenRepository tokens = repository(PasswordResetTokenRepository.class, (method, arguments) -> {
            if (method.getName().equals("invalidateAllForUser")) {
                calls.add("invalidate-tokens");
                return 0;
            }
            if (method.getName().equals("save")) {
                calls.add("save-token");
                return arguments[0];
            }
            return defaultValue(method.getReturnType());
        });

        service(users, tokens).requestReset("ana@example.com");

        assertThat(calls).containsExactly("lock-user", "invalidate-tokens", "managed-user-reference", "save-token");
    }

    private static PasswordRecoveryService service(UserAccountRepository users, PasswordResetTokenRepository tokens) {
        return new PasswordRecoveryService(users, tokens, new SecureTokenService(), new BCryptPasswordEncoder(4),
                (ApplicationEventPublisher) event -> { },
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type, RepositoryInvocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) ->
                invocation.invoke(method, arguments == null ? new Object[0] : arguments));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    @FunctionalInterface
    private interface RepositoryInvocation {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }
}
