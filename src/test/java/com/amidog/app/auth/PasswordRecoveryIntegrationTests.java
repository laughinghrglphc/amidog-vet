package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.email.EmailMessage;
import com.amidog.app.email.EmailSender;
import com.amidog.app.support.PostgresIntegrationTest;
import com.amidog.app.support.RateLimitTestSupport;
import com.amidog.app.common.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(PasswordRecoveryIntegrationTests.EmailCaptureConfiguration.class)
class PasswordRecoveryIntegrationTests extends PostgresIntegrationTest {

    private static final String ACCEPTED = "Si la cuenta existe, enviaremos instrucciones al correo.";
    private static final String INVALID =
            "El enlace para restablecer la contrase\u00f1a no es v\u00e1lido o expir\u00f3.";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([^\\s]+)");

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired ClientRepository clients;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired CapturingEmailSender emailSender;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired RateLimitService rateLimits;

    @BeforeEach
    void cleanDatabase() {
        RateLimitTestSupport.reset(rateLimits);
        resetTokens.deleteAll();
        clients.deleteAll();
        users.deleteAll();
        emailSender.clear();
    }

    @Test
    void forgotPasswordIsNonEnumeratingAndResetConsumesTheToken() throws Exception {
        createVerifiedClient("ana@example.com", "Old-Password-9!");

        forgot("ana@example.com");
        String rawToken = rawToken(emailSender.awaitMessages(1).getFirst());
        mvc.perform(post("/api/v1/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson(rawToken, "New-Password-10!")))
                .andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("New-Password-10!",
                users.findByEmailNormalized("ana@example.com").orElseThrow().getPasswordHash())).isTrue();
        assertThat(resetTokens.findAll()).allSatisfy(token -> assertThat(token.getConsumedAt()).isNotNull());
        login("ana@example.com", "New-Password-10!").andExpect(status().isOk());
        login("ana@example.com", "Old-Password-9!").andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUnverifiedDisabledAndAdministratorRequestsHaveTheSameAcceptedResponseWithoutLeakingState() throws Exception {
        createUnverifiedClient("unverified@example.com", "Correct-Horse-9!");
        UserAccount disabled = createVerifiedClient("disabled@example.com", "Correct-Horse-9!");
        jdbc.update("update users set enabled = false where id = ?", disabled.getId());
        createAdmin("admin@example.com", "Correct-Horse-9!");

        forgot("unknown@example.com");
        forgot("unverified@example.com");
        forgot("disabled@example.com");
        forgot("admin@example.com");

        // Verified administrators may use recovery, but every request receives the same response.
        assertThat(emailSender.awaitMessages(1)).hasSize(1);
        assertThat(emailSender.awaitMessages(1).getFirst().to()).isEqualTo("admin@example.com");
        assertThat(resetTokens.findAll()).hasSize(1);
    }

    @Test
    void resetLinkContainsOnlyTheRawTokenWhilePersistenceContainsOnlyItsHash() throws Exception {
        createVerifiedClient("ana@example.com", "Old-Password-9!");

        forgot("ana@example.com");

        String raw = rawToken(emailSender.awaitMessages(1).getFirst());
        PasswordResetToken token = resetTokens.findAll().getFirst();
        assertThat(emailSender.awaitMessages(1).getFirst().text())
                .contains("http://localhost:5173/restablecer-contrasena?token=");
        assertThat(token.getTokenHash()).hasSize(64).isNotEqualTo(raw);
        assertThat(resetTokens.findByTokenHash(raw)).isEmpty();
        assertThat(resetTokens.findByTokenHash(new SecureTokenService().hash(raw))).isPresent();
        assertThat(token.getExpiresAt()).isAfter(token.getCreatedAt()).isBeforeOrEqualTo(token.getCreatedAt().plusSeconds(3600));
    }

    @Test
    void randomExpiredAndReusedTokensReturnTheSameBadRequest() throws Exception {
        UserAccount user = createVerifiedClient("ana@example.com", "Old-Password-9!");
        forgot("ana@example.com");
        String raw = rawToken(emailSender.awaitMessages(1).getFirst());
        PasswordResetToken issued = resetTokens.findAll().getFirst();
        resetTokens.deleteAll();
        resetTokens.saveAndFlush(PasswordResetToken.create(user, issued.getTokenHash(), Instant.now(), Instant.now().minusSeconds(3600)));

        reset("not-a-real-token").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID));
        reset(raw).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID));

        resetTokens.deleteAll();
        emailSender.clear();
        forgot("ana@example.com");
        String valid = rawToken(emailSender.awaitMessages(1).getFirst());
        reset(valid).andExpect(status().isNoContent());
        reset(valid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID));
    }

    @Test
    void aNewForgotRequestRevokesThePriorTokenAndConcurrentResetsHaveAtMostOneSuccess() throws Exception {
        createVerifiedClient("ana@example.com", "Old-Password-9!");
        forgot("ana@example.com");
        String first = rawToken(emailSender.awaitMessages(1).getFirst());
        emailSender.clear();
        forgot("ana@example.com");
        String second = rawToken(emailSender.awaitMessages(1).getFirst());

        reset(first).andExpect(status().isBadRequest());
        List<MvcResult> attempts = concurrently(() -> reset(second).andReturn(), () -> reset(second).andReturn());
        assertThat(attempts).extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(204, 400);
    }

    @Test
    void resetEmailEventIsNotDeliveredWhenItsTransactionRollsBack() {
        UserAccount user = createVerifiedClient("ana@example.com", "Old-Password-9!");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            events.publishEvent(new PasswordResetEmailRequested(user.getId(), "raw-token-for-rollback"));
            assertThat(emailSender.messages()).isEmpty();
            status.setRollbackOnly();
        });

        assertThat(emailSender.messages()).isEmpty();
    }

    private void forgot(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(ACCEPTED));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/reset-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetJson(token, "New-Password-10!")));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", email).param("password", password));
    }

    private static String resetJson(String token, String password) {
        return "{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}";
    }

    private static String rawToken(EmailMessage email) {
        Matcher matcher = TOKEN_PATTERN.matcher(email.text());
        assertThat(matcher.find()).isTrue();
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private List<MvcResult> concurrently(ConcurrentRequest first, ConcurrentRequest second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> firstResult = executor.submit(() -> awaitAndRun(ready, start, first));
            Future<MvcResult> secondResult = executor.submit(() -> awaitAndRun(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult awaitAndRun(CountDownLatch ready, CountDownLatch start, ConcurrentRequest request) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
        return request.run();
    }

    private UserAccount createVerifiedClient(String email, String password) {
        Instant now = Instant.now();
        UserAccount user = users.saveAndFlush(UserAccount.client(email, passwordEncoder.encode(password), now));
        user.verify(now);
        user = users.saveAndFlush(user);
        clients.saveAndFlush(Client.create(user, "Ana P\u00e9rez", "+56912345678", now));
        return user;
    }

    private void createUnverifiedClient(String email, String password) {
        Instant now = Instant.now();
        UserAccount user = users.saveAndFlush(UserAccount.client(email, passwordEncoder.encode(password), now));
        clients.saveAndFlush(Client.create(user, "Sin verificar", "+56912345678", now));
    }

    private void createAdmin(String email, String password) {
        users.saveAndFlush(UserAccount.admin(email, passwordEncoder.encode(password), Instant.now()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmailCaptureConfiguration {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static final class CapturingEmailSender implements EmailSender {
        private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            synchronized (messages) { messages.add(message); messages.notifyAll(); }
        }

        List<EmailMessage> messages() {
            return List.copyOf(messages);
        }

        List<EmailMessage> awaitMessages(int count) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            synchronized (messages) {
                while (messages.size() < count && System.nanoTime() < deadline) {
                    try { TimeUnit.NANOSECONDS.timedWait(messages, deadline - System.nanoTime()); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                }
                return List.copyOf(messages);
            }
        }

        void clear() {
            synchronized (messages) { messages.clear(); }
        }
    }

    @FunctionalInterface
    private interface ConcurrentRequest {
        MvcResult run() throws Exception;
    }
}
