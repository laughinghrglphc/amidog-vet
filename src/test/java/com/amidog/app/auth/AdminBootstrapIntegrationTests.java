package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminBootstrapIntegrationTests extends PostgresIntegrationTest {

    @Autowired UserAccountRepository users;
    @Autowired ClientRepository clients;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AmidogProperties properties;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        clients.deleteAll();
        users.deleteAll();
    }

    @Test
    void seedsExactlyOneVerifiedAdministratorWithoutAClientRecordAndDoesNotRotateItsPassword() {
        AdminAccountBootstrap bootstrap = bootstrap(
                " Admin@Example.cl ", "Correct-Horse-Admin-9!");
        bootstrap.run(null);
        String originalHash = users.findAllByAccountType(AccountType.ADMIN).getFirst().getPasswordHash();

        bootstrap("other@example.cl", "Different-Admin-Password-9!").run(null);
        bootstrap.run(null);

        assertThat(users.findAllByAccountType(AccountType.ADMIN)).singleElement()
                .satisfies(admin -> {
                    assertThat(admin.getEmailNormalized()).isEqualTo("admin@example.cl");
                    assertThat(admin.getPasswordHash()).isEqualTo(originalHash);
                    assertThat(admin.getPasswordHash()).startsWith("{bcrypt}");
                    assertThat(passwordEncoder.matches("Correct-Horse-Admin-9!", originalHash)).isTrue();
                    assertThat(admin.isVerified()).isTrue();
                    assertThat(admin.isEnabled()).isTrue();
                });
        assertThat(clients.count()).isZero();
    }

    @Test
    void databaseConstraintAndBootstrapRaceCreateExactlyOneAdminAcrossInstances() throws Exception {
        AdminAccountBootstrap first = bootstrap(
                "first@example.cl", "Correct-Horse-Admin-9!");
        AdminAccountBootstrap second = bootstrap(
                "second@example.cl", "Correct-Horse-Admin-10!");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> attempts = List.of(
                    executor.submit(() -> runTogether(first, ready, start)),
                    executor.submit(() -> runTogether(second, ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var attempt : attempts) {
                attempt.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(users.findAllByAccountType(AccountType.ADMIN)).hasSize(1);
        assertThat(clients.count()).isZero();
    }

    @Test
    void refusesToConvertAnExistingClientEmailWithASanitizedException() {
        Instant now = clock.instant();
        UserAccount clientAccount = users.saveAndFlush(UserAccount.client(
                "client@example.cl", passwordEncoder.encode("Client-Password-9!"), now));
        clients.saveAndFlush(Client.create(clientAccount, "Cliente", "+56912345678", now));

        assertThatThrownBy(() -> bootstrap(
                "client@example.cl", "Do-Not-Leak-This-Admin-Password!").run(null))
                .isInstanceOf(AdminAccountBootstrap.AdminBootstrapException.class)
                .hasMessageNotContaining("client@example.cl")
                .hasMessageNotContaining("Do-Not-Leak");

        assertThat(users.findAllByAccountType(AccountType.ADMIN)).isEmpty();
        assertThat(clients.count()).isEqualTo(1);
    }

    private Void runTogether(
            AdminAccountBootstrap bootstrap,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent bootstrap did not start");
        }
        bootstrap.run(null);
        return null;
    }

    private AdminAccountBootstrap bootstrap(String email, String password) {
        AmidogProperties configured = new AmidogProperties(
                properties.clinicZone(),
                properties.frontendBaseUrl(),
                properties.frontendOrigin(),
                properties.booking(),
                new AmidogProperties.Admin(email, password, "Administradora AmiDog", ""),
                properties.contact(),
                properties.email());
        return new AdminAccountBootstrap(
                users, passwordEncoder, configured, clock, transactionManager);
    }
}
