package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountPersistenceIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    UserAccountRepository users;

    @Autowired
    ClientRepository clients;

    @Test
    @Transactional
    void storesAClientAgainstOneNormalizedUserAccount() {
        UserAccount user = users.save(UserAccount.client(
                "ana@example.com", "{noop}not-used-in-production", Instant.now()));
        Client client = clients.save(Client.create(user, "Ana Pérez", "+56912345678", Instant.now()));

        assertThat(users.findByEmailNormalized("ana@example.com")).contains(user);
        assertThat(clients.findByUserId(user.getId())).contains(client);
    }

    @Test
    @Transactional
    void preventsCaseVariantFromCreatingAnotherAccount() {
        users.saveAndFlush(UserAccount.client(
                "Ana@Example.com", "{noop}not-used-in-production", Instant.now()));

        assertThatThrownBy(() -> users.saveAndFlush(UserAccount.client(
                "ana@example.com", "{noop}not-used-in-production", Instant.now())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
