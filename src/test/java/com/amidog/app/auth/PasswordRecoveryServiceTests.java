package com.amidog.app.auth;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordRecoveryServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void requestIsNonEnumeratingAndPersistsOnlyAHashForEligibleAccounts() {
        Fixture fixture = fixture();
        UserAccount user = verifiedUser("ana@example.com", "Old-Password-9!");
        fixture.usersByEmail.put(user.getEmailNormalized(), user);

        AuthDtos.MessageResponse existing = fixture.service.requestReset(" ANA@EXAMPLE.COM ");
        AuthDtos.MessageResponse unknown = fixture.service.requestReset("unknown@example.com");

        assertThat(existing).isEqualTo(unknown);
        assertThat(fixture.tokens).singleElement().extracting(PasswordResetToken::getTokenHash)
                .asString().hasSize(64);
        assertThat(fixture.events).singleElement().isInstanceOf(PasswordResetEmailRequested.class);
        PasswordResetEmailRequested event = (PasswordResetEmailRequested) fixture.events.getFirst();
        assertThat(event.rawToken()).isNotEqualTo(fixture.tokens.getFirst().getTokenHash());
        assertThat(fixture.tokens.getFirst().getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void resetChangesPasswordAndConsumesEveryTokenForTheUser() {
        Fixture fixture = fixture();
        UserAccount user = verifiedUser("ana@example.com", "Old-Password-9!");
        fixture.usersById.put(7L, user);
        SecureTokenService.IssuedToken issued = fixture.tokensService.issue();
        PasswordResetToken token = PasswordResetToken.create(user, issued.hash(), NOW.plusSeconds(3600), NOW);
        fixture.tokensByHash.put(issued.hash(), token);

        fixture.service.reset(issued.raw(), "New-Password-10!");

        assertThat(fixture.passwordEncoder.matches("New-Password-10!", user.getPasswordHash())).isTrue();
        assertThat(fixture.passwordEncoder.matches("Old-Password-9!", user.getPasswordHash())).isFalse();
        assertThat(token.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    void resetRejectsRandomExpiredAndReusedTokensWithTheSameStableError() {
        Fixture fixture = fixture();
        UserAccount user = verifiedUser("ana@example.com", "Old-Password-9!");
        fixture.usersById.put(7L, user);
        SecureTokenService.IssuedToken expired = fixture.tokensService.issue();
        fixture.tokensByHash.put(expired.hash(), PasswordResetToken.create(user, expired.hash(), NOW, NOW.minusSeconds(3600)));

        assertInvalid(fixture, "not-a-real-token");
        assertInvalid(fixture, expired.raw());

        SecureTokenService.IssuedToken consumed = fixture.tokensService.issue();
        PasswordResetToken consumedToken = PasswordResetToken.create(user, consumed.hash(), NOW.plusSeconds(3600), NOW);
        consumedToken.consume(NOW);
        fixture.tokensByHash.put(consumed.hash(), consumedToken);
        assertInvalid(fixture, consumed.raw());
    }

    private static void assertInvalid(Fixture fixture, String token) {
        assertThatThrownBy(() -> fixture.service.reset(token, "New-Password-10!"))
                .isInstanceOf(PasswordRecoveryService.InvalidPasswordResetTokenException.class)
                .hasMessage(PasswordRecoveryService.INVALID_TOKEN_MESSAGE);
    }

    private static UserAccount verifiedUser(String email, String password) {
        UserAccount user = UserAccount.client(email, new BCryptPasswordEncoder(4).encode(password), NOW);
        user.verify(NOW);
        return user;
    }

    private static Fixture fixture() {
        Map<String, UserAccount> usersByEmail = new LinkedHashMap<>();
        Map<Long, UserAccount> usersById = new LinkedHashMap<>();
        Map<String, PasswordResetToken> tokensByHash = new LinkedHashMap<>();
        List<PasswordResetToken> tokens = new ArrayList<>();
        List<Object> events = new ArrayList<>();
        SecureTokenService secureTokens = new SecureTokenService();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        UserAccountRepository users = repository(UserAccountRepository.class, (method, arguments) -> switch (method.getName()) {
            case "findByEmailNormalizedForUpdate" -> Optional.ofNullable(usersByEmail.get(arguments[0]));
            case "findByIdForUpdate" -> Optional.ofNullable(usersById.get(arguments[0]));
            default -> defaultValue(method.getReturnType());
        });
        PasswordResetTokenRepository resetTokens = repository(PasswordResetTokenRepository.class, (method, arguments) -> switch (method.getName()) {
            case "findUserIdByTokenHash" -> Optional.ofNullable(tokensByHash.get(arguments[0])).map(token -> 7L);
            case "save" -> {
                PasswordResetToken token = (PasswordResetToken) arguments[0];
                tokens.add(token);
                tokensByHash.put(token.getTokenHash(), token);
                yield token;
            }
            case "invalidateAllForUser" -> {
                Instant now = (Instant) arguments[1];
                tokensByHash.values().stream().filter(token -> token.getConsumedAt() == null)
                        .forEach(token -> token.consume(now));
                yield 1;
            }
            case "consumeIfActiveByTokenHash" -> {
                PasswordResetToken token = tokensByHash.get(arguments[0]);
                Instant now = (Instant) arguments[1];
                if (token == null || !token.canConsume(now)) yield 0;
                token.consume(now);
                yield 1;
            }
            default -> defaultValue(method.getReturnType());
        });
        PasswordRecoveryService service = new PasswordRecoveryService(
                users, resetTokens, secureTokens, encoder, (ApplicationEventPublisher) events::add,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, usersByEmail, usersById, tokensByHash, tokens, events, secureTokens, encoder);
    }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type, RepositoryInvocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return method.invoke(invocation, arguments);
            return invocation.invoke(method, arguments == null ? new Object[0] : arguments);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private record Fixture(PasswordRecoveryService service, Map<String, UserAccount> usersByEmail,
                           Map<Long, UserAccount> usersById, Map<String, PasswordResetToken> tokensByHash,
                           List<PasswordResetToken> tokens, List<Object> events, SecureTokenService tokensService,
                           BCryptPasswordEncoder passwordEncoder) { }

    @FunctionalInterface
    private interface RepositoryInvocation {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }
}
