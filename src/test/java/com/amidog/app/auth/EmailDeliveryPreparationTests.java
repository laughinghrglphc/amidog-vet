package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDeliveryPreparationTests {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final String RAW_TOKEN = "fresh-raw-token-never-persisted";
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final String INITIAL_HASH = "b".repeat(64);

    @Test
    void lostFenceReturnsBeforeUserTokenOrOutboxMutation() {
        UserAccount user = client();
        Fixture fixture = new Fixture(user, EmailDeliveryType.PASSWORD_RESET, true);
        String lostFence = fixture.job.getDeliveryFence();
        assertThat(fixture.job.claim(NOW.plusSeconds(61))).isTrue();
        fixture.clock = Clock.fixed(NOW.plusSeconds(62), ZoneOffset.UTC);

        assertThat(fixture.prepare(fixture.attempt(lostFence))).isNull();

        assertThat(fixture.operations).containsExactly("USER_LOCK", "JOB_LOCK");
        assertThat(fixture.secureTokens.issueCalls).isZero();
        assertThat(fixture.tokenMutationCount()).isZero();
        assertThat(fixture.job.getState()).isEqualTo(EmailDeliveryJob.State.PROCESSING);
        assertThat(fixture.job.getDeliveryFence()).isNotEqualTo(lostFence);
    }

    @Test
    void disabledPasswordResetClosesTheOwnedFenceWithoutIssuingOrSavingAToken() {
        UserAccount user = verifiedClient();
        setField(user, "enabled", false);

        assertIneligibleAttemptIsTerminal(user, EmailDeliveryType.PASSWORD_RESET, true);
    }

    @Test
    void unverifiedPasswordResetClosesTheOwnedFenceWithoutIssuingOrSavingAToken() {
        UserAccount user = client();
        // Isolate the verification predicate from the enabled predicate.
        setField(user, "enabled", true);

        assertIneligibleAttemptIsTerminal(user, EmailDeliveryType.PASSWORD_RESET, true);
    }

    @Test
    void alreadyVerifiedVerificationClosesTheOwnedFenceWithoutIssuingOrSavingAToken() {
        assertIneligibleAttemptIsTerminal(verifiedClient(), EmailDeliveryType.VERIFICATION, true);
    }

    @Test
    void administratorVerificationClosesTheOwnedFenceWithoutIssuingOrSavingAToken() {
        UserAccount admin = UserAccount.admin("admin@example.com", "hash", NOW.minusSeconds(60));
        setId(admin, 41L);

        assertIneligibleAttemptIsTerminal(admin, EmailDeliveryType.VERIFICATION, true);
    }

    @Test
    void verificationWithoutAClientRowClosesTheOwnedFenceWithoutIssuingOrSavingAToken() {
        assertIneligibleAttemptIsTerminal(client(), EmailDeliveryType.VERIFICATION, false);
    }

    @Test
    void validPasswordResetLocksUserBeforeTokenMutationAndPersistsOnlyTheHash() {
        Fixture fixture = new Fixture(verifiedClient(), EmailDeliveryType.PASSWORD_RESET, true);
        String fence = fixture.job.getDeliveryFence();

        Object prepared = fixture.prepare(fixture.attempt(fence));

        assertThat(prepared).isInstanceOf(PasswordResetEmailRequested.class);
        PasswordResetEmailRequested event = (PasswordResetEmailRequested) prepared;
        assertThat(event.userId()).isEqualTo(fixture.user.getId());
        assertThat(event.jobId()).isEqualTo(fixture.job.getId());
        assertThat(event.fence()).isEqualTo(fence);
        assertThat(event.rawToken()).isEqualTo(RAW_TOKEN);
        assertThat(event.toString()).doesNotContain(RAW_TOKEN);
        assertThat(fixture.savedResetToken).isNotNull();
        assertThat(fixture.savedResetToken.getTokenHash()).isEqualTo(TOKEN_HASH).doesNotContain(RAW_TOKEN);
        assertThat(fixture.operations).containsExactly(
                "USER_LOCK", "JOB_LOCK", "RESET_ACTIVE", "RESET_CONSUME", "RESET_SAVE");
        assertThat(fixture.secureTokens.issueCalls).isOne();
        assertThat(fixture.savedVerificationToken).isNull();
    }

    @Test
    void validVerificationLocksUserBeforeTokenMutationAndCarriesTheExactFence() {
        Fixture fixture = new Fixture(client(), EmailDeliveryType.VERIFICATION, true);
        String fence = fixture.job.getDeliveryFence();

        Object prepared = fixture.prepare(fixture.attempt(fence));

        assertThat(prepared).isInstanceOf(VerificationEmailRequested.class);
        VerificationEmailRequested event = (VerificationEmailRequested) prepared;
        assertThat(event.userId()).isEqualTo(fixture.user.getId());
        assertThat(event.jobId()).isEqualTo(fixture.job.getId());
        assertThat(event.fence()).isEqualTo(fence);
        assertThat(event.rawToken()).isEqualTo(RAW_TOKEN);
        assertThat(event.toString()).doesNotContain(RAW_TOKEN);
        assertThat(fixture.savedVerificationToken).isNotNull();
        assertThat(fixture.savedVerificationToken.getTokenHash())
                .isEqualTo(TOKEN_HASH)
                .doesNotContain(RAW_TOKEN);
        assertThat(fixture.operations).containsExactly(
                "USER_LOCK", "JOB_LOCK", "CLIENT_LOOKUP", "VERIFICATION_ACTIVE",
                "VERIFICATION_CONSUME", "VERIFICATION_SAVE");
        assertThat(fixture.secureTokens.issueCalls).isOne();
        assertThat(fixture.savedResetToken).isNull();
    }

    @Test
    void supersededConsumedOrExactlyExpiredLinkedTokenClosesJobWithoutMinting() {
        Fixture fixture = new Fixture(verifiedClient(), EmailDeliveryType.PASSWORD_RESET, true);
        fixture.linkedTokenActive = false;
        String fence = fixture.job.getDeliveryFence();

        assertThat(fixture.prepare(fixture.attempt(fence))).isNull();

        assertThat(fixture.job.getState()).isEqualTo(EmailDeliveryJob.State.COMPLETED);
        assertThat(fixture.secureTokens.issueCalls).isZero();
        assertThat(fixture.operations).containsExactly("USER_LOCK", "JOB_LOCK", "RESET_ACTIVE");
        assertThat(fixture.tokenMutationCount()).isZero();
    }

    private static void assertIneligibleAttemptIsTerminal(
            UserAccount user,
            EmailDeliveryType type,
            boolean clientPresent
    ) {
        Fixture fixture = new Fixture(user, type, clientPresent);
        String fence = fixture.job.getDeliveryFence();

        assertThat(fixture.prepare(fixture.attempt(fence))).isNull();

        assertThat(fixture.job.getState()).isEqualTo(EmailDeliveryJob.State.COMPLETED);
        assertThat(fixture.job.getDeliveryFence()).isEqualTo(fence);
        assertThat(fixture.job.getCompletedAt()).isEqualTo(NOW);
        assertThat(fixture.secureTokens.issueCalls).isZero();
        assertThat(fixture.tokenMutationCount()).isZero();
        assertThat(fixture.savedVerificationToken).isNull();
        assertThat(fixture.savedResetToken).isNull();
        assertThat(fixture.operations).startsWith("USER_LOCK", "JOB_LOCK");
    }

    private static UserAccount client() {
        UserAccount user = UserAccount.client("client@example.com", "hash", NOW.minusSeconds(60));
        setId(user, 31L);
        return user;
    }

    private static UserAccount verifiedClient() {
        UserAccount user = client();
        user.verify(NOW.minusSeconds(30));
        return user;
    }

    private static void setId(Object entity, Long id) {
        setField(entity, "id", id);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class Fixture {
        private final UserAccount user;
        private final EmailDeliveryJob job;
        private final boolean clientPresent;
        private final List<String> operations = new ArrayList<>();
        private final CountingTokenService secureTokens = new CountingTokenService();
        private int verificationMutations;
        private int resetMutations;
        private EmailVerificationToken savedVerificationToken;
        private PasswordResetToken savedResetToken;
        private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        private boolean linkedTokenActive = true;

        private Fixture(UserAccount user, EmailDeliveryType type, boolean clientPresent) {
            this.user = user;
            this.clientPresent = clientPresent;
            this.job = EmailDeliveryJob.create(user, type, INITIAL_HASH, NOW);
            setId(job, 71L);
        }

        private DeliveryAttempt attempt(String fence) {
            return new DeliveryAttempt(job.getId(), fence, user.getId(), job.getDeliveryType(), INITIAL_HASH);
        }

        private Object prepare(DeliveryAttempt attempt) {
            EmailDeliveryPreparation preparation = new EmailDeliveryPreparation(
                    jobs(), users(), clients(), verificationTokens(), resetTokens(),
                    secureTokens, clock);
            return preparation.prepare(attempt);
        }

        private int tokenMutationCount() {
            return verificationMutations + resetMutations;
        }

        private EmailDeliveryJobRepository jobs() {
            return proxy(EmailDeliveryJobRepository.class, (method, arguments) -> {
                if (method.equals("findByIdForUpdate")) {
                    operations.add("JOB_LOCK");
                    return Optional.of(job);
                }
                return defaultValue(method);
            });
        }

        private UserAccountRepository users() {
            return proxy(UserAccountRepository.class, (method, arguments) -> {
                if (method.equals("findByIdForUpdate")) {
                    operations.add("USER_LOCK");
                    return Optional.of(user);
                }
                return defaultValue(method);
            });
        }

        private ClientRepository clients() {
            return proxy(ClientRepository.class, (method, arguments) -> {
                if (method.equals("findByUserId")) {
                    operations.add("CLIENT_LOOKUP");
                    return clientPresent
                            ? Optional.of(Client.create(user, "Client", "+56900000000", NOW))
                            : Optional.empty();
                }
                return defaultValue(method);
            });
        }

        private EmailVerificationTokenRepository verificationTokens() {
            return proxy(EmailVerificationTokenRepository.class, (method, arguments) -> {
                if (method.equals("invalidateAllForUser")) {
                    operations.add("VERIFICATION_INVALIDATE");
                    verificationMutations++;
                    return 1;
                }
                if (method.equals("existsActiveForUser")) {
                    operations.add("VERIFICATION_ACTIVE");
                    return linkedTokenActive;
                }
                if (method.equals("consumeIfActiveByTokenHash")) {
                    operations.add("VERIFICATION_CONSUME");
                    verificationMutations++;
                    return 1;
                }
                if (method.equals("save")) {
                    operations.add("VERIFICATION_SAVE");
                    verificationMutations++;
                    savedVerificationToken = (EmailVerificationToken) arguments[0];
                    return savedVerificationToken;
                }
                return defaultValue(method);
            });
        }

        private PasswordResetTokenRepository resetTokens() {
            return proxy(PasswordResetTokenRepository.class, (method, arguments) -> {
                if (method.equals("invalidateAllForUser")) {
                    operations.add("RESET_INVALIDATE");
                    resetMutations++;
                    return 1;
                }
                if (method.equals("existsActiveForUser")) {
                    operations.add("RESET_ACTIVE");
                    return linkedTokenActive;
                }
                if (method.equals("consumeIfActiveByTokenHash")) {
                    operations.add("RESET_CONSUME");
                    resetMutations++;
                    return 1;
                }
                if (method.equals("save")) {
                    operations.add("RESET_SAVE");
                    resetMutations++;
                    savedResetToken = (PasswordResetToken) arguments[0];
                    return savedResetToken;
                }
                return defaultValue(method);
            });
        }
    }

    private static final class CountingTokenService extends SecureTokenService {
        private int issueCalls;

        @Override
        public IssuedToken issue() {
            issueCalls++;
            return new IssuedToken(RAW_TOKEN, TOKEN_HASH);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, RepositoryCall call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> call.invoke(method.getName(), arguments));
    }

    private static Object defaultValue(String method) {
        if (method.equals("toString")) {
            return "repository-proxy";
        }
        return null;
    }

    @FunctionalInterface
    private interface RepositoryCall {
        Object invoke(String method, Object[] arguments);
    }
}
