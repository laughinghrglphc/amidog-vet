package com.amidog.app.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 30) String phone) {
        public RegisterRequest {
            email = trim(email);
        }
    }

    public record TokenRequest(@NotBlank @Size(max = 256) String token) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
        public EmailRequest {
            email = trim(email);
        }
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 256) String token,
            @NotBlank @Size(min = 12, max = 128) String password) {
    }

    public record MessageResponse(String message) {
    }

    public record AuthResponse(Long userId, Long clientId, String email,
                               String name, AccountType accountType) {
        public static AuthResponse from(Authentication authentication) {
            if (!(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
                throw new IllegalStateException("Unsupported authenticated principal");
            }
            return new AuthResponse(
                    principal.getUserId(), principal.getClientId(), principal.getEmail(),
                    principal.getDisplayName(), principal.getAccountType());
        }
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
