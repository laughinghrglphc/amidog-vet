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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationServiceTests {

    @Test
    void normalizesAndCreatesOneHashedUnverifiedAccountAcrossEquivalentRegistrations() {
        Map<String, UserAccount> savedUsers = new LinkedHashMap<>();
        List<Client> savedClients = new ArrayList<>();
        List<EmailVerificationToken> savedTokens = new ArrayList<>();
        List<Object> events = new ArrayList<>();

        UserAccountRepository users = repository(UserAccountRepository.class, (method, arguments) -> switch (method.getName()) {
            case "findByEmailNormalized", "findByEmailNormalizedForUpdate" ->
                    Optional.ofNullable(savedUsers.get(arguments[0]));
            case "saveAndFlush" -> {
                UserAccount user = (UserAccount) arguments[0];
                savedUsers.put(user.getEmailNormalized(), user);
                yield user;
            }
            default -> defaultValue(method.getReturnType());
        });
        ClientRepository clients = repository(ClientRepository.class, (method, arguments) -> {
            if (method.getName().equals("save")) {
                Client client = (Client) arguments[0];
                savedClients.add(client);
                return client;
            }
            return defaultValue(method.getReturnType());
        });
        EmailVerificationTokenRepository tokens = repository(EmailVerificationTokenRepository.class, (method, arguments) -> {
            if (method.getName().equals("save")) {
                EmailVerificationToken token = (EmailVerificationToken) arguments[0];
                savedTokens.add(token);
                return token;
            }
            return defaultValue(method.getReturnType());
        });

        RegistrationService service = new RegistrationService(
                users, clients, tokens, new SecureTokenService(), new BCryptPasswordEncoder(4),
                (ApplicationEventPublisher) events::add,
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC),
                noOpTransactions());

        AuthDtos.MessageResponse first = service.register(new AuthDtos.RegisterRequest(
                " Ana@Example.COM ", "Correct-Horse-9!", "Ana Pérez", "+56 9 1234 5678"));
        AuthDtos.MessageResponse duplicate = service.register(new AuthDtos.RegisterRequest(
                "ana@example.com", "Different-Correct-9!", "Changed Name", "+56 9 5555 5555"));

        UserAccount account = savedUsers.get("ana@example.com");
        assertThat(first).isEqualTo(duplicate);
        assertThat(savedUsers).hasSize(1);
        assertThat(savedClients).hasSize(1);
        assertThat(savedTokens).singleElement().extracting(EmailVerificationToken::getTokenHash)
                .asString().hasSize(64);
        assertThat(account.isVerified()).isFalse();
        assertThat(account.isEnabled()).isFalse();
        assertThat(account.getPasswordHash()).startsWith("$2").doesNotContain("Correct-Horse-9!");
        assertThat(events).singleElement().isInstanceOf(VerificationEmailRequested.class);
        assertThat(((VerificationEmailRequested) events.getFirst()).rawToken())
                .isNotEqualTo(savedTokens.getFirst().getTokenHash());
    }

    private static TransactionTemplate noOpTransactions() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type, RepositoryInvocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(invocation, arguments);
            }
            return invocation.invoke(method, arguments == null ? new Object[0] : arguments);
        });
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
