package com.amidog.app.notification;

import com.amidog.app.auth.UserAccount;
import com.amidog.app.auth.UserAccountRepository;
import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.common.security.RateLimitService;
import com.amidog.app.support.PostgresIntegrationTest;
import com.amidog.app.support.RateLimitTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ClientDeactivationNotificationIntegrationTests
        extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-9!";
    private static final String GENERIC_LOGIN_FAILURE =
            "Correo o contrase\u00f1a incorrectos, o cuenta a\u00fan no verificada.";
    private static final String CLIENT_NOT_FOUND =
            "No se encontr\u00f3 el perfil de cliente.";
    private static final String NOTIFICATION_NOT_FOUND =
            "No se encontr\u00f3 la notificaci\u00f3n.";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired UserAccountRepository users;
    @Autowired ClientRepository clients;
    @Autowired RateLimitService rateLimits;

    @BeforeEach
    void cleanDatabase() {
        RateLimitTestSupport.reset(rateLimits);
        jdbc.sql("delete from spring_session_attributes").update();
        jdbc.sql("delete from spring_session").update();
        jdbc.sql("delete from notifications").update();
        jdbc.sql("delete from reservation_items").update();
        jdbc.sql("delete from reservation_events").update();
        jdbc.sql("delete from reservations").update();
        jdbc.sql("delete from availability_blocks").update();
        jdbc.sql("delete from weekly_availability").update();
        jdbc.sql("delete from pets").update();
        jdbc.sql("delete from clients").update();
        jdbc.sql("delete from email_delivery_jobs").update();
        jdbc.sql("delete from email_verification_tokens").update();
        jdbc.sql("delete from password_reset_tokens").update();
        jdbc.sql("delete from user_external_identities").update();
        jdbc.sql("delete from users").update();
        jdbc.sql("delete from services").update();
    }

    @Test
    void adminDeactivationBlocksFreshLoginAndEveryNotificationOperationUntilReactivation()
            throws Exception {
        verifiedAdmin("admin@example.com");
        Client ana = verifiedClient("ana@example.com", "Ana");
        Client bob = verifiedClient("bob@example.com", "Bob");
        long anaReservation = reservation(
                ana.getId(), Instant.parse("2026-08-10T14:00:00Z"));
        long bobReservation = reservation(
                bob.getId(), Instant.parse("2026-08-10T15:00:00Z"));
        long anaFirst = notification(
                ana.getUser().getId(), anaReservation, "ana-first");
        long anaSecond = notification(
                ana.getUser().getId(), anaReservation, "ana-second");
        long bobNotification = notification(
                bob.getUser().getId(), bobReservation, "bob");
        Cookie anaSession = login("ana@example.com", PASSWORD);
        Cookie adminSession = login("admin@example.com", PASSWORD);

        updateClient(adminSession, ana, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/v1/me/notifications")
                        .cookie(anaSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value(CLIENT_NOT_FOUND));
        assertInactiveMarkIsNonEnumerating(
                anaSession, anaFirst);
        assertInactiveMarkIsNonEnumerating(
                anaSession, bobNotification);
        mvc.perform(post("/api/v1/me/notifications/read-all")
                        .cookie(anaSession)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value(CLIENT_NOT_FOUND));
        assertThat(unreadCount(anaFirst, anaSecond, bobNotification))
                .isEqualTo(3L);

        assertGenericLoginFailure("ana@example.com", PASSWORD);
        assertGenericLoginFailure("unknown@example.com", PASSWORD);

        updateClient(adminSession, ana, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(get("/api/v1/me/notifications")
                        .cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(anaSecond))
                .andExpect(jsonPath("$[1].id").value(anaFirst));
        mvc.perform(patch(
                        "/api/v1/me/notifications/{id}/read",
                        bobNotification)
                        .cookie(anaSession)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value(NOTIFICATION_NOT_FOUND));
        mvc.perform(patch(
                        "/api/v1/me/notifications/{id}/read",
                        anaSecond)
                        .cookie(anaSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(false));
        mvc.perform(post("/api/v1/me/notifications/read-all")
                        .cookie(anaSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedRead").value(1));
        assertThat(unreadCount(anaFirst, anaSecond))
                .isZero();
        assertThat(unreadCount(bobNotification)).isEqualTo(1L);

        login("ana@example.com", PASSWORD);
    }

    private org.springframework.test.web.servlet.ResultActions updateClient(
            Cookie adminSession, Client client, boolean active)
            throws Exception {
        return mvc.perform(patch(
                        "/api/v1/admin/clients/{id}",
                        client.getId())
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "phone": "%s",
                                  "active": %s
                                }
                                """.formatted(
                                client.getName(),
                                client.getPhone(),
                                active)));
    }

    private void assertInactiveMarkIsNonEnumerating(
            Cookie session, long notificationId) throws Exception {
        mvc.perform(patch(
                        "/api/v1/me/notifications/{id}/read",
                        notificationId)
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value(CLIENT_NOT_FOUND));
    }

    private Cookie login(String email, String password)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private void assertGenericLoginFailure(
            String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value(GENERIC_LOGIN_FAILURE))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    private UserAccount verifiedAdmin(String email) {
        return users.saveAndFlush(UserAccount.admin(
                email,
                new BCryptPasswordEncoder(4).encode(PASSWORD),
                Instant.now()));
    }

    private Client verifiedClient(String email, String name) {
        UserAccount user = UserAccount.client(
                email,
                new BCryptPasswordEncoder(4).encode(PASSWORD),
                Instant.now());
        user.verify(Instant.now());
        user = users.saveAndFlush(user);
        return clients.saveAndFlush(Client.create(
                user, name, "+56912345678", Instant.now()));
    }

    private long reservation(long clientId, Instant start) {
        return jdbc.sql("""
                insert into reservations(
                    client_id,scheduled_start,scheduled_end,status)
                values (
                    :client,:start,:end,'CONFIRMED')
                returning id
                """)
                .param("client", clientId)
                .param("start", start)
                .param("end", start.plusSeconds(1_800))
                .query(Long.class).single();
    }

    private long notification(
            long recipientUserId,
            long reservationId,
            String key) {
        return jdbc.sql("""
                insert into notifications(
                    recipient_user_id,type,title,body,reservation_id,
                    deduplication_key,created_at)
                values (
                    :recipient,'RESERVATION_CONFIRMED',
                    'Reserva confirmada','Tu reserva fue confirmada.',
                    :reservation,:key,now())
                returning id
                """)
                .param("recipient", recipientUserId)
                .param("reservation", reservationId)
                .param("key", key)
                .query(Long.class).single();
    }

    private long unreadCount(long... notificationIds) {
        long unread = 0;
        for (long notificationId : notificationIds) {
            unread += jdbc.sql("""
                    select count(*)
                    from notifications
                    where id = :id
                      and read_at is null
                    """)
                    .param("id", notificationId)
                    .query(Long.class).single();
        }
        return unread;
    }
}
