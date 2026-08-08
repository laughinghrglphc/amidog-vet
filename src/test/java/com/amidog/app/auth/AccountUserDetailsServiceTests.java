package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.config.AmidogProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountUserDetailsServiceTests {

    @Test
    void normalizesClientEmailAndBuildsAScalarClientPrincipal() throws Exception {
        UserAccount account = UserAccount.client("ana@example.com", "{bcrypt}hash", Instant.now());
        account.verify(Instant.now());
        setId(account, 10L);
        Client client = Client.create(account, "Ana Pérez", "+56912345678", Instant.now());
        setId(client, 20L);

        AccountUserDetailsService service = new AccountUserDetailsService(
                users("ana@example.com", account), clients(10L, client), properties());

        AccountPrincipal principal = (AccountPrincipal) service.loadUserByUsername(" ANA@EXAMPLE.COM ");

        assertThat(principal.getUserId()).isEqualTo(10L);
        assertThat(principal.getClientId()).isEqualTo(20L);
        assertThat(principal.getUsername()).isEqualTo("ana@example.com");
        assertThat(principal.getDisplayName()).isEqualTo("Ana Pérez");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_CLIENT");
    }

    @Test
    void usesConfiguredAdminDisplayNameAndGenericUnknownFailure() throws Exception {
        UserAccount admin = UserAccount.admin("admin@example.com", "{bcrypt}hash", Instant.now());
        setId(admin, 30L);
        AccountUserDetailsService service = new AccountUserDetailsService(
                users("admin@example.com", admin), clients(null, null), properties());

        AccountPrincipal principal = (AccountPrincipal) service.loadUserByUsername("admin@example.com");

        assertThat(principal.getClientId()).isNull();
        assertThat(principal.getDisplayName()).isEqualTo("Administradora AmiDog");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThatThrownBy(() -> service.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void inactiveClientUsesGenericCredentialFailureUntilReactivated()
            throws Exception {
        Instant now = Instant.parse("2026-07-29T12:00:00Z");
        UserAccount account = UserAccount.client(
                "ana@example.com", "{bcrypt}hash", now);
        account.verify(now);
        setId(account, 10L);
        Client client = Client.create(
                account, "Ana P\u00e9rez", "+56912345678", now);
        setId(client, 20L);
        client.updateByAdministrator(
                "Ana P\u00e9rez", "+56912345678", false, now);
        AccountUserDetailsService service = new AccountUserDetailsService(
                users("ana@example.com", account),
                clients(10L, client),
                properties());

        assertThatThrownBy(() ->
                service.loadUserByUsername("ana@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid credentials");

        client.updateByAdministrator(
                "Ana P\u00e9rez", "+56912345678", true, now);

        assertThat(service.loadUserByUsername("ana@example.com"))
                .isInstanceOf(AccountPrincipal.class)
                .extracting("clientId")
                .isEqualTo(20L);
    }

    private static UserAccountRepository users(String expectedEmail, UserAccount account) {
        return (UserAccountRepository) java.lang.reflect.Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(), new Class<?>[]{UserAccountRepository.class},
                (proxy, method, arguments) -> method.getName().equals("findByEmailNormalized")
                        ? Optional.ofNullable(expectedEmail.equals(arguments[0]) ? account : null)
                        : defaultValue(method.getReturnType()));
    }

    private static ClientRepository clients(Long expectedUserId, Client client) {
        return (ClientRepository) java.lang.reflect.Proxy.newProxyInstance(
                ClientRepository.class.getClassLoader(), new Class<?>[]{ClientRepository.class},
                (proxy, method, arguments) -> method.getName().equals("findByUserId")
                        || method.getName().equals("findByUserIdAndActiveTrue")
                        ? Optional.ofNullable(java.util.Objects.equals(expectedUserId, arguments[0])
                                && (method.getName().equals("findByUserId") || client.isActive())
                                ? client : null)
                        : defaultValue(method.getReturnType()));
    }

    private static AmidogProperties properties() {
        return new AmidogProperties(
                ZoneId.of("America/Santiago"), URI.create("http://localhost:5173"),
                URI.create("http://localhost:5173"),
                new AmidogProperties.Booking(false, 30, 2, 90),
                new AmidogProperties.Admin("", "", "Administradora AmiDog", ""),
                new AmidogProperties.Contact("", ""), new AmidogProperties.Email("log"));
    }

    private static void setId(Object entity, Long id) throws ReflectiveOperationException {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
