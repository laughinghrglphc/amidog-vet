package com.amidog.app.reservation;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "amidog.booking.auto-confirm=false",
        "amidog.booking.minimum-notice-hours=0",
        "amidog.booking.horizon-days=365"
})
class ReservationCreationIntegrationTests extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from reservation_items").update();
        jdbc.sql("delete from reservation_events").update();
        jdbc.sql("delete from notifications").update();
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
        jdbc.sql("""
                insert into users(
                    email_normalized,password_hash,email_verified_at,
                    account_type,enabled)
                values (
                    'notification-admin@example.com','{noop}password',
                    now(),'ADMIN',true)
                """).update();
    }

    @Test
    void createsOneThirtyMinutePendingReservationWithOneServicePerPet()
            throws Exception {
        Fixture ana = client("ana@example.com", true);
        long milo = pet(ana.clientId(), "Milo", true);
        long luna = pet(ana.clientId(), "Luna", true);
        long consult = service("consulta", "Consulta general", true);
        long vaccine = service("vacuna", "Vacunaci\u00f3n", true);
        OffsetDateTime start = openSlot();

        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(ana.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(start, """
                                [
                                  {"petId":%d,"serviceId":%d},
                                  {"petId":%d,"serviceId":%d}
                                ]
                                """.formatted(milo, consult, luna, vaccine),
                                "\"  Control anual  \"")))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/v1/me/reservations/\\d+")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.note").value("Control anual"))
                .andExpect(jsonPath("$.startsAt").value(apiTimestamp(start)))
                .andExpect(jsonPath("$.endsAt").value(
                        apiTimestamp(start.plusMinutes(30))))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].serviceName").value("Consulta general"))
                .andExpect(jsonPath("$.items[1].serviceName").value("Vacunaci\u00f3n"));

        assertThat(jdbc.sql("""
                select extract(epoch from (scheduled_end - scheduled_start))::bigint
                from reservations
                """).query(Long.class).single()).isEqualTo(1800L);
        assertThat(jdbc.sql("""
                select service_name_snapshot from reservation_items order by id
                """).query(String.class).list())
                .containsExactly("Consulta general", "Vacunaci\u00f3n");
        assertThat(jdbc.sql("""
                select event_type || ':' || actor_type
                from reservation_events
                """).query(String.class).single()).isEqualTo("CREATED:CLIENT");
    }

    @Test
    void listingIsOwnedAndDeterministicallyNewestFirst() throws Exception {
        Fixture ana = client("ana@example.com", true);
        Fixture bob = client("bob@example.com", true);
        long anaPet = pet(ana.clientId(), "Milo", true);
        long bobPet = pet(bob.clientId(), "Luna", true);
        long consult = service("consulta", "Consulta", true);
        OffsetDateTime first = openSlot();
        OffsetDateTime second = first.plusMinutes(30);

        create(ana, first, anaPet, consult);
        create(ana, second, anaPet, consult);
        create(bob, first.plusDays(7), bobPet, consult);

        mvc.perform(get("/api/v1/me/reservations").with(user(ana.principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startsAt").value(
                        apiTimestamp(second)))
                .andExpect(jsonPath("$[1].startsAt").value(
                        apiTimestamp(first)));
        AccountPrincipal spoofed = new AccountPrincipal(
                ana.userId(), bob.clientId(), ana.email(), "Ana",
                AccountType.CLIENT, "{noop}password", true, true);
        mvc.perform(get("/api/v1/me/reservations").with(user(spoofed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejectsDuplicateForeignInactiveAndMissingReservationInputs()
            throws Exception {
        Fixture ana = client("ana@example.com", true);
        Fixture bob = client("bob@example.com", true);
        long milo = pet(ana.clientId(), "Milo", true);
        long archived = pet(ana.clientId(), "Viejo", false);
        long foreign = pet(bob.clientId(), "Luna", true);
        long consult = service("consulta", "Consulta", true);
        long inactiveService = service("viejo", "Servicio viejo", false);
        OffsetDateTime start = openSlot();

        assertPostBadRequestCode(ana, start, """
                [{"petId":%d,"serviceId":%d},{"petId":%d,"serviceId":%d}]
                """.formatted(milo, consult, milo, consult),
                null, "DUPLICATE_RESERVATION_PET");
        assertPostStatus(ana, start, foreign, consult, status().isNotFound());
        assertPostStatus(ana, start, archived, consult, status().isNotFound());
        assertPostStatus(ana, start, milo, inactiveService, status().isNotFound());
        assertPostStatus(ana, start, milo, 999999L, status().isNotFound());
        assertPostCode(
                ana, start.plusMinutes(15),
                "[{\"petId\":%d,\"serviceId\":%d}]".formatted(milo, consult),
                null, "SLOT_UNAVAILABLE");
        ZoneOffset wrongOffset = start.getOffset().equals(ZoneOffset.ofHours(-3))
                ? ZoneOffset.ofHours(-4)
                : ZoneOffset.ofHours(-3);
        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(ana.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                OffsetDateTime.of(
                                        start.toLocalDateTime(), wrongOffset),
                                "[{\"petId\":%d,\"serviceId\":%d}]"
                                        .formatted(milo, consult),
                                null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_RESERVATION_START"));
        assertPostCode(
                ana, start.minusDays(14),
                "[{\"petId\":%d,\"serviceId\":%d}]".formatted(milo, consult),
                null, "SLOT_UNAVAILABLE");
        assertPostCode(
                ana, start.plusDays(371),
                "[{\"petId\":%d,\"serviceId\":%d}]".formatted(milo, consult),
                null, "SLOT_UNAVAILABLE");
        assertThat(jdbc.sql("select count(*) from reservations")
                .query(Long.class).single()).isZero();
    }

    @Test
    void securityVerificationValidationAndCsrfAreEnforced() throws Exception {
        Fixture verified = client("verified@example.com", true);
        Fixture unverified = client("unverified@example.com", false);
        long pet = pet(verified.clientId(), "Milo", true);
        long service = service("consulta", "Consulta", true);
        OffsetDateTime start = openSlot();
        String content = request(start,
                "[{\"petId\":%d,\"serviceId\":%d}]".formatted(pet, service),
                null);

        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(verified.principal()))
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(unverified.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(verified.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"%s\",\"items\":[]}"
                                .formatted(start)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void create(
            Fixture fixture, OffsetDateTime start, long petId, long serviceId)
            throws Exception {
        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(fixture.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(start,
                                "[{\"petId\":%d,\"serviceId\":%d}]"
                                        .formatted(petId, serviceId),
                                null)))
                .andExpect(status().isCreated());
    }

    private void assertPostCode(
            Fixture fixture,
            OffsetDateTime start,
            String items,
            String note,
            String code) throws Exception {
        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(fixture.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(start, items, note)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void assertPostBadRequestCode(
            Fixture fixture,
            OffsetDateTime start,
            String items,
            String note,
            String code) throws Exception {
        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(fixture.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(start, items, note)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void assertPostStatus(
            Fixture fixture,
            OffsetDateTime start,
            long petId,
            long serviceId,
            org.springframework.test.web.servlet.ResultMatcher statusMatcher)
            throws Exception {
        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(fixture.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(start,
                                "[{\"petId\":%d,\"serviceId\":%d}]"
                                        .formatted(petId, serviceId),
                                null)))
                .andExpect(statusMatcher);
    }

    private OffsetDateTime openSlot() {
        LocalDate date = LocalDate.now(ZoneId.of("America/Santiago")).plusDays(7);
        OffsetDateTime start = date.atTime(10, 0)
                .atZone(ZoneId.of("America/Santiago")).toOffsetDateTime();
        jdbc.sql("""
                insert into weekly_availability(
                    day_of_week, local_start_time, local_end_time)
                values (:day, '10:00', '12:00')
                """).param("day", date.getDayOfWeek().getValue()).update();
        return start;
    }

    private static String apiTimestamp(OffsetDateTime value) {
        return value.format(DateTimeFormatter.ofPattern(
                "uuuu-MM-dd'T'HH:mm:ssXXX"));
    }

    private Fixture client(String email, boolean verified) {
        long userId = jdbc.sql("""
                insert into users(
                    email_normalized, password_hash, email_verified_at,
                    account_type, enabled)
                values (:email, '{noop}password',
                        case when :verified then now() else null end,
                        'CLIENT', :verified)
                returning id
                """)
                .param("email", email).param("verified", verified)
                .query(Long.class).single();
        long clientId = jdbc.sql("""
                insert into clients(user_id, name, phone)
                values (:user, 'Ana', '+56911111111') returning id
                """).param("user", userId).query(Long.class).single();
        return new Fixture(userId, clientId, email, verified);
    }

    private long pet(long clientId, String name, boolean active) {
        return jdbc.sql("""
                insert into pets(client_id, name, species, active)
                values (:client, :name, 'Mascota', :active) returning id
                """).param("client", clientId).param("name", name)
                .param("active", active).query(Long.class).single();
    }

    private long service(String code, String name, boolean active) {
        return jdbc.sql("""
                insert into services(code, name, active)
                values (:code, :name, :active) returning id
                """).param("code", code).param("name", name)
                .param("active", active).query(Long.class).single();
    }

    private static String request(
            OffsetDateTime start, String items, String noteJson) {
        return """
                {"startsAt":"%s","items":%s,"note":%s}
                """.formatted(start, items, noteJson == null ? "null" : noteJson);
    }

    private static AccountPrincipal admin() {
        return new AccountPrincipal(
                999L, null, "admin@example.com", "Admin",
                AccountType.ADMIN, "{noop}password", true, true);
    }

    private record Fixture(
            long userId, long clientId, String email, boolean verified) {
        AccountPrincipal principal() {
            return new AccountPrincipal(
                    userId, clientId, email, "Ana", AccountType.CLIENT,
                    "{noop}password", true, verified);
        }
    }
}
