package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationServiceLockOrderTests {

    @Test
    void verifyResolvesThenLocksUserBeforeAtomicallyConsumingToken() {
        List<String> calls = new ArrayList<>();
        UserAccount account = UserAccount.client("ana@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z"));
        UserAccountRepository users = repository(UserAccountRepository.class, (method, arguments) -> switch (method.getName()) {
            case "findByIdForUpdate" -> {
                calls.add("lock-user");
                yield Optional.of(account);
            }
            default -> defaultValue(method.getReturnType());
        });
        EmailVerificationTokenRepository tokens = repository(EmailVerificationTokenRepository.class, (method, arguments) -> switch (method.getName()) {
            case "findUserIdByTokenHash" -> {
                calls.add("resolve-token-user");
                yield Optional.of(7L);
            }
            case "consumeIfActiveByTokenHash" -> {
                calls.add("consume-token");
                yield 1;
            }
            default -> defaultValue(method.getReturnType());
        });

        service(users, emptyClients(), tokens).verify("raw-token");

        assertThat(calls).containsExactly("resolve-token-user", "lock-user", "consume-token");
        assertThat(account.isVerified()).isTrue();
    }

    @Test
    void resendLocksUserBeforeInvalidatingAndReplacingTokens() {
        List<String> calls = new ArrayList<>();
        UserAccount account = UserAccount.client("ana@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z"));
        Client client = Client.create(account, "Ana", "+56 9 1234 5678", Instant.parse("2026-07-29T00:00:00Z"));
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
        ClientRepository clients = repository(ClientRepository.class, (method, arguments) ->
                method.getName().equals("findByUserId") ? Optional.of(client) : defaultValue(method.getReturnType()));
        EmailVerificationTokenRepository tokens = repository(EmailVerificationTokenRepository.class, (method, arguments) -> {
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

        service(users, clients, tokens).resend("ana@example.com");

        assertThat(calls).containsExactly("lock-user", "invalidate-tokens", "managed-user-reference", "save-token");
    }

    private static RegistrationService service(
            UserAccountRepository users, ClientRepository clients, EmailVerificationTokenRepository tokens
    ) {
        return new RegistrationService(
                users, clients, tokens, new SecureTokenService(), new BCryptPasswordEncoder(4),
                (ApplicationEventPublisher) event -> { },
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC), noOpTransactions());
    }

    private static ClientRepository emptyClients() {
        return repository(ClientRepository.class, (method, arguments) -> defaultValue(method.getReturnType()));
    }

    private static TransactionTemplate noOpTransactions() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        });
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
