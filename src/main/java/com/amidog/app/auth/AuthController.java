package com.amidog.app.auth;

import com.amidog.app.common.api.TooManyRequestsException;
import com.amidog.app.common.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final int REGISTER_LIMIT = 5;
    private static final int EMAIL_OPERATION_LIMIT = 3;
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final RegistrationService registrations;
    private final PasswordRecoveryService passwordRecovery;
    private final RateLimitService rateLimits;

    public AuthController(
            RegistrationService registrations,
            PasswordRecoveryService passwordRecovery,
            RateLimitService rateLimits
    ) {
        this.registrations = registrations;
        this.passwordRecovery = passwordRecovery;
        this.rateLimits = rateLimits;
    }

    @PostMapping("/register")
    ResponseEntity<AuthDtos.MessageResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        acquire("register:" + remoteAddress(servletRequest), REGISTER_LIMIT, ONE_HOUR);
        return ResponseEntity.accepted().body(registrations.register(request));
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody AuthDtos.TokenRequest request) {
        registrations.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    ResponseEntity<AuthDtos.MessageResponse> resendVerification(@Valid @RequestBody AuthDtos.EmailRequest request) {
        acquire("resend-verification:" + normalizeEmail(request.email()), EMAIL_OPERATION_LIMIT, ONE_HOUR);
        return ResponseEntity.accepted().body(registrations.resend(request.email()));
    }

    @PostMapping("/forgot-password")
    ResponseEntity<AuthDtos.MessageResponse> forgotPassword(@Valid @RequestBody AuthDtos.EmailRequest request) {
        acquire("forgot-password:" + normalizeEmail(request.email()), EMAIL_OPERATION_LIMIT, ONE_HOUR);
        return ResponseEntity.accepted().body(passwordRecovery.requestReset(request.email()));
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        passwordRecovery.reset(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    AuthDtos.AuthResponse me(Authentication authentication) {
        return AuthDtos.AuthResponse.from(authentication);
    }

    @GetMapping("/csrf")
    AuthDtos.CsrfResponse csrf(CsrfToken csrfToken) {
        return new AuthDtos.CsrfResponse(
                csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    private void acquire(String key, int limit, Duration duration) {
        if (!rateLimits.tryAcquire(key, limit, duration)) {
            throw new TooManyRequestsException();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String remoteAddress(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
