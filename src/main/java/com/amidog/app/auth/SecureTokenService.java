package com.amidog.app.auth;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class SecureTokenService {

    private final SecureRandom random;

    public SecureTokenService() {
        this(new SecureRandom());
    }

    SecureTokenService(SecureRandom random) {
        this.random = random;
    }

    public IssuedToken issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(raw, hash(raw));
    }

    public String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedToken(String raw, String hash) {

        @Override
        public String toString() {
            return "IssuedToken[redacted]";
        }
    }
}
