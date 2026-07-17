package com.amidog.app.support;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

public final class TestRows {

    private TestRows() {
    }

    public static long verifiedClient(JdbcClient jdbc) {
        String unique = UUID.randomUUID().toString();
        long userId = jdbc.sql("""
                insert into users(
                    email_normalized, password_hash, email_verified_at,
                    account_type, enabled)
                values (:email, '{noop}test-password', now(), 'CLIENT', true)
                returning id
                """)
                .param("email", "test-" + unique + "@example.com")
                .query(Long.class)
                .single();

        return jdbc.sql("""
                insert into clients(user_id, name, phone)
                values (:userId, :name, '+56900000000')
                returning id
                """)
                .param("userId", userId)
                .param("name", "Cliente " + unique.substring(0, 8))
                .query(Long.class)
                .single();
    }
}
