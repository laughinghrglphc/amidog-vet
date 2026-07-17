package com.amidog.app.auth;

import com.amidog.app.config.AmidogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);
    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> ADMIN_RACE_CONSTRAINTS = Set.of(
            "users_single_admin_idx",
            "users_email_normalized_key");
    private static final String FAILURE_MESSAGE =
            "No se pudo crear la cuenta administradora con la configuraci\u00f3n proporcionada.";

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AmidogProperties properties;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Autowired
    public AdminAccountBootstrap(
            UserAccountRepository users,
            PasswordEncoder passwordEncoder,
            AmidogProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this(users, passwordEncoder, properties, clock, new TransactionTemplate(transactionManager));
    }

    AdminAccountBootstrap(
            UserAccountRepository users,
            PasswordEncoder passwordEncoder,
            AmidogProperties properties,
            Clock clock,
            TransactionOperations transactions
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        AmidogProperties.Admin configuration = properties.admin();
        if (configuration == null
                || isBlank(configuration.email())
                || isBlank(configuration.password())) {
            return;
        }
        if (administratorExists()) {
            return;
        }

        String email = normalizeAndValidateEmail(configuration.email());
        validatePassword(configuration.password());

        try {
            transactions.executeWithoutResult(status -> createAdministrator(email, configuration.password()));
        } catch (DataIntegrityViolationException exception) {
            if (!isExpectedAdministratorRace(exception) || !administratorExists()) {
                throw new AdminBootstrapException();
            }
        }

        log.info("Administrator account bootstrap completed");
    }

    private void createAdministrator(String email, String rawPassword) {
        if (administratorExists()) {
            return;
        }
        users.findByEmailNormalized(email).ifPresent(existing -> {
            if (existing.getAccountType() != AccountType.ADMIN) {
                throw new AdminBootstrapException();
            }
        });
        if (users.findByEmailNormalized(email).isPresent()) {
            return;
        }

        Instant now = clock.instant();
        users.saveAndFlush(UserAccount.admin(email, passwordEncoder.encode(rawPassword), now));
    }

    private boolean administratorExists() {
        return !users.findAllByAccountType(AccountType.ADMIN).isEmpty();
    }

    private String normalizeAndValidateEmail(String suppliedEmail) {
        String normalized = suppliedEmail.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
            throw new AdminBootstrapException();
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password.length() < 12) {
            throw new AdminBootstrapException();
        }
    }

    private boolean isExpectedAdministratorRace(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.postgresql.util.PSQLException postgres
                    && "23505".equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null
                    && ADMIN_RACE_CONSTRAINTS.contains(
                            postgres.getServerErrorMessage().getConstraint())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static final class AdminBootstrapException extends IllegalStateException {
        public AdminBootstrapException() {
            super(FAILURE_MESSAGE);
        }
    }
}
