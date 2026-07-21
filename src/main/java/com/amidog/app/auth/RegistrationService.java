package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class RegistrationService {

    public static final String ACCEPTED_MESSAGE = "Revisa tu correo para verificar tu cuenta.";
    public static final String INVALID_TOKEN_MESSAGE = "El enlace de verificación no es válido o expiró.";
    private static final Duration VERIFICATION_TOKEN_LIFETIME = Duration.ofHours(24);

    private final UserAccountRepository users;
    private final ClientRepository clients;
    private final EmailVerificationTokenRepository verificationTokens;
    private final SecureTokenService secureTokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final EmailDeliveryOutbox outbox;

    @Autowired
    public RegistrationService(
            UserAccountRepository users,
            ClientRepository clients,
            EmailVerificationTokenRepository verificationTokens,
            SecureTokenService secureTokens,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            Clock clock,
            TransactionTemplate transactions, EmailDeliveryOutbox outbox
    ) {
        this.users = users;
        this.clients = clients;
        this.verificationTokens = verificationTokens;
        this.secureTokens = secureTokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
        this.transactions = transactions;
        this.outbox = outbox;
    }

    RegistrationService(UserAccountRepository users, ClientRepository clients, EmailVerificationTokenRepository verificationTokens,
                        SecureTokenService secureTokens, PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
                        Clock clock, TransactionTemplate transactions) {
        this(users, clients, verificationTokens, secureTokens, passwordEncoder, events, clock, transactions, null);
    }

    public AuthDtos.MessageResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.findByEmailNormalized(email).isPresent()) {
            return accepted();
        }

        try {
            transactions.executeWithoutResult(status -> createClientAccount(email, request));
        } catch (DataIntegrityViolationException ignored) {
            // A concurrent equivalent registration won the database unique key race.
        }
        return accepted();
    }

    @Transactional
    public void verify(String rawToken) {
        String tokenHash = secureTokens.hash(rawToken);
        Long userId = verificationTokens.findUserIdByTokenHash(tokenHash)
                .orElseThrow(InvalidVerificationTokenException::new);
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(InvalidVerificationTokenException::new);
        Instant now = clock.instant();

        if (verificationTokens.consumeIfActiveByTokenHash(tokenHash, now) != 1) {
            throw new InvalidVerificationTokenException();
        }
        user.verify(now);
    }

    @Transactional
    public AuthDtos.MessageResponse resend(String suppliedEmail) {
        String email = normalizeEmail(suppliedEmail);
        UserAccount user = users.findByEmailNormalizedForUpdate(email).orElse(null);
        if (user == null || user.isVerified() || clients.findByUserId(user.getId()).isEmpty()) {
            return accepted();
        }

        Instant now = clock.instant();
        verificationTokens.invalidateAllForUser(user.getId(), now);
        SecureTokenService.IssuedToken issued = secureTokens.issue();
        UserAccount tokenOwner = users.getReferenceById(user.getId());
        verificationTokens.save(EmailVerificationToken.create(
                tokenOwner, issued.hash(), now.plus(VERIFICATION_TOKEN_LIFETIME), now));
        DeliveryAttempt attempt = outbox == null ? transientAttempt() :
                outbox.enqueue(tokenOwner, EmailDeliveryType.VERIFICATION, issued.hash());
        events.publishEvent(new VerificationEmailRequested(user.getId(), attempt.jobId(), attempt.fence(), issued.raw()));
        return accepted();
    }

    private void createClientAccount(String normalizedEmail, AuthDtos.RegisterRequest request) {
        if (users.findByEmailNormalized(normalizedEmail).isPresent()) {
            return;
        }

        Instant now = clock.instant();
        UserAccount user = users.saveAndFlush(UserAccount.client(
                normalizedEmail, passwordEncoder.encode(request.password()), now));
        clients.save(Client.create(user, request.name().trim(), request.phone().trim(), now));
        SecureTokenService.IssuedToken issued = secureTokens.issue();
        verificationTokens.save(EmailVerificationToken.create(
                user, issued.hash(), now.plus(VERIFICATION_TOKEN_LIFETIME), now));
        DeliveryAttempt attempt = outbox == null ? transientAttempt() :
                outbox.enqueue(user, EmailDeliveryType.VERIFICATION, issued.hash());
        events.publishEvent(new VerificationEmailRequested(user.getId(), attempt.jobId(), attempt.fence(), issued.raw()));
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

    public static class InvalidVerificationTokenException extends RuntimeException {
        public InvalidVerificationTokenException() {
            super(INVALID_TOKEN_MESSAGE);
        }
    }
}
