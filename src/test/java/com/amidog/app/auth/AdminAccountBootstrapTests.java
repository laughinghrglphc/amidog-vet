package com.amidog.app.auth;

import com.amidog.app.config.AmidogProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAccountBootstrapTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void repeatedRunsCreateOneVerifiedEnabledAdminWithPrefixedPasswordHash() throws Exception {
        InMemoryUsers store = new InMemoryUsers();
        AtomicReference<String> encodedPassword = new AtomicReference<>();
        PasswordEncoder encoder = recordingEncoder(encodedPassword);
        AdminAccountBootstrap bootstrap = bootstrap(store, properties(
                " Admin@Example.cl ", "Correct-Horse-Admin-9!"), encoder);

        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(store.accounts).singleElement().satisfies(account -> {
            assertThat(account.getEmailNormalized()).isEqualTo("admin@example.cl");
            assertThat(account.getAccountType()).isEqualTo(AccountType.ADMIN);
            assertThat(account.isVerified()).isTrue();
            assertThat(account.isEnabled()).isTrue();
            assertThat(account.getPasswordHash()).startsWith("{bcrypt}");
        });
        assertThat(encodedPassword).hasValue("Correct-Horse-Admin-9!");
    }

    @Test
    void blankOrPartiallyConfiguredCredentialsAreANoop() throws Exception {
        InMemoryUsers store = new InMemoryUsers();

        bootstrap(store, properties("", ""), recordingEncoder(new AtomicReference<>())).run(null);
        bootstrap(store, properties("admin@example.cl", ""), recordingEncoder(new AtomicReference<>())).run(null);
        bootstrap(store, properties("", "Correct-Horse-Admin-9!"), recordingEncoder(new AtomicReference<>())).run(null);

        assertThat(store.accounts).isEmpty();
    }

    @Test
    void existingAdminIsNeverChangedOnRestart() throws Exception {
        InMemoryUsers store = new InMemoryUsers();
        UserAccount existing = UserAccount.admin(
                "first@example.cl", "{bcrypt}existing", CLOCK.instant());
        store.accounts.add(existing);
        AtomicReference<String> encoded = new AtomicReference<>();

        bootstrap(store, properties("other@example.cl", "Different-Password-9!"),
                recordingEncoder(encoded)).run(null);

        assertThat(store.accounts).containsExactly(existing);
        assertThat(existing.getPasswordHash()).isEqualTo("{bcrypt}existing");
        assertThat(encoded).hasValue(null);
    }

    @Test
    void refusesToReuseAClientEmailAndKeepsExceptionSanitized() {
        InMemoryUsers store = new InMemoryUsers();
        store.accounts.add(UserAccount.client(
                "client@example.cl", "{bcrypt}secret-hash", CLOCK.instant()));
        AdminAccountBootstrap bootstrap = bootstrap(store, properties(
                "client@example.cl", "Never-Expose-This-Password!"),
                recordingEncoder(new AtomicReference<>()));

        assertThatThrownBy(() -> bootstrap.run(null))
                .isInstanceOf(AdminAccountBootstrap.AdminBootstrapException.class)
                .hasMessage("No se pudo crear la cuenta administradora con la configuración proporcionada.")
                .hasMessageNotContaining("client@example.cl")
                .hasMessageNotContaining("Never-Expose");
    }

    @Test
    void rejectsInvalidConfiguredEmailAndShortPasswordWithoutEchoingEither() {
        InMemoryUsers store = new InMemoryUsers();

        assertThatThrownBy(() -> bootstrap(store, properties(
                "not-an-email", "short"), recordingEncoder(new AtomicReference<>())).run(null))
                .isInstanceOf(AdminAccountBootstrap.AdminBootstrapException.class)
                .hasMessageNotContaining("not-an-email")
                .hasMessageNotContaining("short");
    }

    @Test
    void acceptsOnlyExactPostgresUniqueRacesWhenTheWinnerIsObservable() throws Exception {
        for (String constraint : List.of(
                "users_single_admin_idx", "users_email_normalized_key")) {
            InMemoryUsers store = new InMemoryUsers();
            store.failureOnSave = integrityViolation("23505", constraint);
            store.visibleWinnerAfterFailure = UserAccount.admin(
                    "winner@example.cl", "{bcrypt}winner", CLOCK.instant());

            bootstrap(store, properties(
                    "configured@example.cl", "Correct-Horse-Admin-9!"),
                    recordingEncoder(new AtomicReference<>())).run(null);

            assertThat(store.accounts).singleElement()
                    .extracting(UserAccount::getEmailNormalized)
                    .isEqualTo("winner@example.cl");
        }
    }

    @Test
    void exactExpectedUniqueViolationStillFailsWhenNoWinnerIsObservable() {
        InMemoryUsers store = new InMemoryUsers();
        store.failureOnSave = integrityViolation(
                "23505", "users_single_admin_idx");

        assertThatThrownBy(() -> bootstrap(store, properties(
                "configured@example.cl", "Correct-Horse-Admin-9!"),
                recordingEncoder(new AtomicReference<>())).run(null))
                .isInstanceOf(AdminAccountBootstrap.AdminBootstrapException.class)
                .hasMessageNotContaining("configured@example.cl");
    }

    @Test
    void unrelatedIntegrityViolationIsNotAcceptedEvenWhenAnAdminIsObservable() {
        InMemoryUsers store = new InMemoryUsers();
        store.failureOnSave = new DataIntegrityViolationException(
                "unrelated failure that mentions users_single_admin_idx");
        store.visibleWinnerAfterFailure = UserAccount.admin(
                "winner@example.cl", "{bcrypt}winner", CLOCK.instant());

        assertThatThrownBy(() -> bootstrap(store, properties(
                "configured@example.cl", "Correct-Horse-Admin-9!"),
                recordingEncoder(new AtomicReference<>())).run(null))
                .isInstanceOf(AdminAccountBootstrap.AdminBootstrapException.class)
                .hasMessageNotContaining("users_single_admin_idx")
                .hasMessageNotContaining("configured@example.cl");
    }

    @Test
    void matchingConstraintWithWrongSqlStateIsNotAccepted() {
        InMemoryUsers store = new InMemoryUsers();
        store.failureOnSave = integrityViolation(
                "23503", "users_single_admin_idx");
        store.visibleWinnerAfterFailure = UserAccount.admin(
                "winner@example.cl", "{bcrypt}winner", CLOCK.instant());

        assertThatThrownBy(() -> bootstrap(store, properties(
                "configured@example.cl", "Correct-Horse-Admin-9!"),
                recordingEncoder(new AtomicReference<>())).run(null))
                .isInstanceOf(AdminAccountBootstrap.AdminBootstrapException.class)
                .hasMessageNotContaining("users_single_admin_idx");
    }

    private AdminAccountBootstrap bootstrap(
            InMemoryUsers store,
            AmidogProperties properties,
            PasswordEncoder encoder
    ) {
        return new AdminAccountBootstrap(
                store.repository(), encoder, properties, CLOCK, transactions());
    }

    private AmidogProperties properties(String email, String password) {
        return new AmidogProperties(
                ZoneOffset.UTC,
                URI.create("http://localhost:5173"),
                URI.create("http://localhost:5173"),
                new AmidogProperties.Booking(false, 30, 2, 90),
                new AmidogProperties.Admin(email, password, "Administradora AmiDog", ""),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email("log"));
    }

    private PasswordEncoder recordingEncoder(AtomicReference<String> password) {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                password.set(rawPassword.toString());
                return "{bcrypt}encoded";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }
        };
    }

    private TransactionTemplate transactions() {
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

    private DataIntegrityViolationException integrityViolation(
            String sqlState,
            String constraint
    ) {
        ServerErrorMessage serverError = new ServerErrorMessage("") {
            @Override
            public String getSQLState() {
                return sqlState;
            }

            @Override
            public String getConstraint() {
                return constraint;
            }
        };
        PSQLException postgres = new PSQLException(serverError);
        return new DataIntegrityViolationException("sanitized test wrapper", postgres);
    }

    private static final class InMemoryUsers {
        private final List<UserAccount> accounts = new ArrayList<>();
        private DataIntegrityViolationException failureOnSave;
        private UserAccount visibleWinnerAfterFailure;

        UserAccountRepository repository() {
            return (UserAccountRepository) Proxy.newProxyInstance(
                    UserAccountRepository.class.getClassLoader(),
                    new Class<?>[]{UserAccountRepository.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "findAllByAccountType" -> accounts.stream()
                                .filter(account -> account.getAccountType() == arguments[0])
                                .toList();
                        case "findByEmailNormalized" -> accounts.stream()
                                .filter(account -> account.getEmailNormalized().equals(arguments[0]))
                                .findFirst();
                        case "saveAndFlush" -> {
                            UserAccount account = (UserAccount) arguments[0];
                            if (failureOnSave != null) {
                                if (visibleWinnerAfterFailure != null) {
                                    accounts.add(visibleWinnerAfterFailure);
                                }
                                throw failureOnSave;
                            }
                            accounts.add(account);
                            yield account;
                        }
                        case "toString" -> "InMemoryUsers";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
