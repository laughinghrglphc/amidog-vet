package com.amidog.app.auth;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class PasswordRecoveryService {

    public static final String ACCEPTED_MESSAGE = "Si la cuenta existe, enviaremos instrucciones al correo.";
    public static final String INVALID_TOKEN_MESSAGE =
            "El enlace para restablecer la contrase\u00f1a no es v\u00e1lido o expir\u00f3.";
    private static final Duration RESET_TOKEN_LIFETIME = Duration.ofHours(1);

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final SecureTokenService secureTokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final EmailDeliveryOutbox outbox;

    @Autowired
    public PasswordRecoveryService(UserAccountRepository users, PasswordResetTokenRepository resetTokens,
                                   SecureTokenService secureTokens, PasswordEncoder passwordEncoder,
                                   ApplicationEventPublisher events, Clock clock, EmailDeliveryOutbox outbox) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.secureTokens = secureTokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
        this.outbox = outbox;
    }
    PasswordRecoveryService(UserAccountRepository users, PasswordResetTokenRepository resetTokens, SecureTokenService secureTokens,
                            PasswordEncoder passwordEncoder, ApplicationEventPublisher events, Clock clock) {
        this(users, resetTokens, secureTokens, passwordEncoder, events, clock, null);
    }

    @Transactional
    public AuthDtos.MessageResponse requestReset(String suppliedEmail) {
        String email = normalizeEmail(suppliedEmail);
        UserAccount user = users.findByEmailNormalizedForUpdate(email).orElse(null);
        if (user == null || !user.isEnabled() || !user.isVerified()) {
            // Do comparable non-delivery cryptographic work without persisting or publishing a token.
            // The unavoidable remaining difference is the eligible account's transactional token write.
            secureTokens.issue();
            return accepted();
        }

        Instant now = clock.instant();
        resetTokens.invalidateAllForUser(user.getId(), now);
        SecureTokenService.IssuedToken issued = secureTokens.issue();
        UserAccount tokenOwner = users.getReferenceById(user.getId());
        resetTokens.save(PasswordResetToken.create(
                tokenOwner, issued.hash(), now.plus(RESET_TOKEN_LIFETIME), now));
        DeliveryAttempt attempt = outbox == null ? transientAttempt() :
                outbox.enqueue(tokenOwner, EmailDeliveryType.PASSWORD_RESET, issued.hash());
        events.publishEvent(new PasswordResetEmailRequested(user.getId(), attempt.jobId(), attempt.fence(), issued.raw()));
        return accepted();
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        String tokenHash = secureTokens.hash(rawToken);
        Long userId = resetTokens.findUserIdByTokenHash(tokenHash)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        // Every reset path locks USER before changing TOKEN rows. This matches requestReset.
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        Instant now = clock.instant();
        if (resetTokens.consumeIfActiveByTokenHash(tokenHash, now) != 1) {
            throw new InvalidPasswordResetTokenException();
        }
        user.replacePassword(passwordEncoder.encode(newPassword), now);
        resetTokens.invalidateAllForUser(user.getId(), now);
    }

    private AuthDtos.MessageResponse accepted() {
        return new AuthDtos.MessageResponse(ACCEPTED_MESSAGE);
    }

    private DeliveryAttempt transientAttempt() {
        return new DeliveryAttempt(null, null, null, null, null);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static class InvalidPasswordResetTokenException extends RuntimeException {
        public InvalidPasswordResetTokenException() {
            super(INVALID_TOKEN_MESSAGE);
        }
    }
}

record PasswordResetEmailRequested(Long userId, Long jobId, String fence, String rawToken) {
    PasswordResetEmailRequested(Long userId, String rawToken) { this(userId, null, null, rawToken); }
    @Override
    public String toString() {
        return "PasswordResetEmailRequested[userId=" + userId + ", jobId=" + jobId + ", rawToken=redacted]";
    }
}
