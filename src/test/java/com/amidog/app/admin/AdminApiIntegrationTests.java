package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.sql.Types;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import({
        AdminApiIntegrationTests.FixedClockConfiguration.class,
        AdminApiIntegrationTests.QueryCountingConfiguration.class
})
@TestPropertySource(properties = {
        "amidog.admin.name=Nataly Apablaza",
        "amidog.notifications.reminder.enabled=false"
})
class AdminApiIntegrationTests extends PostgresIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-04-05T14:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired QueryCounter queryCounter;

    private long adminUserId;

    @BeforeEach
    void cleanAndSeedAdmin() {
        jdbc.sql("""
                drop trigger if exists test_hold_client_update
                on clients
                """).update();
        jdbc.sql("""
                drop function if exists test_hold_client_update()
                """).update();
        jdbc.sql("delete from spring_session_attributes").update();
        jdbc.sql("delete from spring_session").update();
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
        adminUserId = insertUser(
                "admin@amidog.cl", "ADMIN", true);
    }

    @Test
    void dashboardUsesClinicBoundariesBoundedCollectionsAndMultiPetSnapshots()
            throws Exception {
        ClientRow ana = client(
                "Ana", "ana@example.com", "+56911111111", true);
        ClientRow inactive = client(
                "Inactiva", "inactive@example.com", "+56922222222", false);
        long luna = pet(ana.clientId(), "Luna", "Perro", true);
        long milo = pet(ana.clientId(), "Milo", "Gato", true);
        pet(inactive.clientId(), "Oculta", "Perro", true);
        long consulta = service("consulta", "Consulta");
        long vacuna = service("vacuna", "Vacuna");

        long today = reservation(
                ana.clientId(),
                Instant.parse("2026-04-05T15:00:00Z"),
                "CONFIRMED");
        item(today, luna, consulta, "Consulta");
        item(today, milo, vacuna, "Vacuna");
        event(today, "CREATED", "CLIENT", null, "CONFIRMED",
                null, Instant.parse("2026-04-05T15:00:00Z"),
                Instant.parse("2026-04-01T12:00:00Z"));

        queryCounter.reset();
        mvc.perform(get("/api/v1/admin/dashboard")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments.length()")
                        .value(1));
        assertThat(queryCounter.count()).isEqualTo(7);

        for (int index = 1; index <= 11; index++) {
            long upcoming = reservation(
                    ana.clientId(),
                    Instant.parse("2026-04-06T12:00:00Z")
                            .plusSeconds(index * 1800L),
                    index % 2 == 0 ? "PENDING" : "CONFIRMED");
            item(upcoming, luna, consulta, "Consulta");
        }
        long cancelled = reservation(
                ana.clientId(),
                Instant.parse("2026-04-02T12:00:00Z"),
                "CANCELLED");
        item(cancelled, luna, vacuna, "Vacuna");

        queryCounter.reset();
        mvc.perform(get("/api/v1/admin/dashboard")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.name")
                        .value("Nataly Apablaza"))
                .andExpect(jsonPath("$.profile.email")
                        .value("admin@amidog.cl"))
                .andExpect(jsonPath("$.profile.role").value("ADMIN"))
                .andExpect(jsonPath("$.stats.todayAppointments").value(1))
                .andExpect(jsonPath("$.stats.clients").value(1))
                .andExpect(jsonPath("$.stats.pets").value(3))
                .andExpect(jsonPath("$.appointments.length()").value(10))
                .andExpect(jsonPath("$.appointments[0].startsAt")
                        .value("2026-04-05T11:00:00-04:00"))
                .andExpect(jsonPath("$.schedule.length()").value(1))
                .andExpect(jsonPath("$.schedule[0].items.length()")
                        .value(2))
                .andExpect(jsonPath("$.services[0].name")
                        .value("Consulta"))
                .andExpect(jsonPath("$.services[0].percentage")
                        .isNumber())
                .andExpect(jsonPath(
                        "$.chart.currentWeek.values.length()")
                        .value(7))
                .andExpect(jsonPath(
                        "$.chart.previousWeek.values.length()")
                        .value(7))
                .andExpect(jsonPath(
                        "$.chart.currentMonth.values.length()")
                        .value(5))
                .andExpect(jsonPath("$.messages").doesNotExist())
                .andExpect(jsonPath("$.settings").doesNotExist());
        assertThat(queryCounter.count()).isEqualTo(7);
    }

    @Test
    void reservationFiltersEscapeWildcardsPaginateRootsAndDetailOrdersChildren()
            throws Exception {
        ClientRow ana = client(
                "Ana 100%_!", "ana@example.com", "+56911111111", true);
        long luna = pet(ana.clientId(), "Luna", "Perro", true);
        long milo = pet(ana.clientId(), "Milo", "Gato", true);
        long consulta = service("consulta", "Consulta 100%_!");
        long first = reservation(
                ana.clientId(),
                Instant.parse("2026-08-10T13:00:00Z"),
                "CONFIRMED");
        item(first, milo, consulta, "Consulta 100%_!");
        item(first, luna, consulta, "Consulta 100%_!");
        event(first, "RESCHEDULED", "ADMIN", "PENDING", "CONFIRMED",
                Instant.parse("2026-08-10T12:00:00Z"),
                Instant.parse("2026-08-10T13:00:00Z"),
                Instant.parse("2026-07-02T12:00:00Z"));
        event(first, "CREATED", "CLIENT", null, "PENDING", null,
                Instant.parse("2026-08-10T12:00:00Z"),
                Instant.parse("2026-07-01T12:00:00Z"));
        long second = reservation(
                ana.clientId(),
                Instant.parse("2026-08-11T13:00:00Z"),
                "PENDING");
        item(second, luna, consulta, "Consulta");
        ClientRow decoy = client(
                "Decoy 100AB!", "decoy@example.com",
                "+56933333333", true);
        long decoyPet = pet(
                decoy.clientId(), "Otro", "Perro", true);
        long decoyReservation = reservation(
                decoy.clientId(),
                Instant.parse("2026-08-10T14:00:00Z"),
                "CONFIRMED");
        item(decoyReservation, decoyPet, consulta, "Consulta");

        queryCounter.reset();
        mvc.perform(get("/api/v1/admin/reservations")
                        .with(user(admin()))
                        .param("from", "2026-08-10")
                        .param("to", "2026-08-10")
                        .param("status", "CONFIRMED")
                        .param("q", "100%_!")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[0].items.length()").value(2))
                .andExpect(jsonPath("$.content[0].items[0].petName")
                        .value("Milo"))
                .andExpect(jsonPath("$.content[0].startsAt")
                        .value("2026-08-10T09:00:00-04:00"));
        assertThat(queryCounter.count()).isEqualTo(3);

        long extraPet = pet(
                ana.clientId(), "Nina", "Gato", true);
        item(first, extraPet, consulta, "Consulta 100%_!");
        queryCounter.reset();
        mvc.perform(get("/api/v1/admin/reservations")
                        .with(user(admin()))
                        .param("from", "2026-08-10")
                        .param("to", "2026-08-10")
                        .param("status", "CONFIRMED")
                        .param("q", "100%_!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].items.length()")
                        .value(3));
        assertThat(queryCounter.count()).isEqualTo(3);

        mvc.perform(get("/api/v1/admin/reservations")
                        .with(user(admin()))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].id").value(second));

        queryCounter.reset();
        mvc.perform(get(
                        "/api/v1/admin/reservations/{id}", first)
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].eventType")
                        .value("CREATED"))
                .andExpect(jsonPath("$.events[1].eventType")
                        .value("RESCHEDULED"))
                .andExpect(jsonPath("$.events[1].previousStartsAt")
                        .value("2026-08-10T08:00:00-04:00"));
        assertThat(queryCounter.count()).isEqualTo(3);
    }

    @Test
    void clientAndPetListsDetailsAndFullFormUpdatesPreserveHistoryAndOwnership()
            throws Exception {
        ClientRow ana = client(
                "Ana", "ana@example.com", "+56911111111", true);
        long luna = pet(
                ana.clientId(), "Luna", "Perro", true);
        long service = service("consulta", "Consulta");
        long historical = reservation(
                ana.clientId(),
                Instant.parse("2026-03-01T12:00:00Z"),
                "COMPLETED");
        item(historical, luna, service, "Consulta");

        mvc.perform(get("/api/v1/admin/clients")
                        .with(user(admin()))
                        .param("q", " ANA ")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email")
                        .value("ana@example.com"));
        mvc.perform(get(
                        "/api/v1/admin/clients/{id}",
                        ana.clientId())
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pets.length()").value(1))
                .andExpect(jsonPath(
                        "$.reservationCounts.completed").value(1));
        mvc.perform(patch(
                        "/api/v1/admin/clients/{id}",
                        ana.clientId())
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Ana P\u00e9rez  ",
                                 "phone":"  +56999999999  ",
                                 "active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana P\u00e9rez"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.email")
                        .value("ana@example.com"));

        assertThat(jdbc.sql(
                "select count(*) from reservations where id=:id")
                .param("id", historical)
                .query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql(
                "select count(*) from pets where id=:id")
                .param("id", luna)
                .query(Long.class).single()).isEqualTo(1L);
        mvc.perform(get("/api/v1/me/profile")
                        .with(user(ana.principal())))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/admin/pets")
                        .with(user(admin()))
                        .param("clientId",
                                String.valueOf(ana.clientId()))
                        .param("q", "luna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ownerName")
                        .value("Ana P\u00e9rez"));
        mvc.perform(patch(
                        "/api/v1/admin/pets/{id}", luna)
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Lunita  ","species":"Canina",
                                 "breed":null,"birthdate":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId")
                        .value(ana.clientId()))
                .andExpect(jsonPath("$.name").value("Lunita"))
                .andExpect(jsonPath("$.breed").doesNotExist());
        assertThat(jdbc.sql(
                "select client_id from pets where id=:id")
                .param("id", luna).query(Long.class).single())
                .isEqualTo(ana.clientId());
    }

    @Test
    void reservationDetailPreservesFrozenV8MigrationCancellationActor()
            throws Exception {
        ClientRow ana = client(
                "Ana", "ana@example.com", "+56911111111", true);
        long luna = pet(
                ana.clientId(), "Luna", "Perro", true);
        long consulta = service("consulta", "Consulta");
        long migrated = reservation(
                ana.clientId(),
                Instant.parse("2026-03-01T12:00:00Z"),
                "CANCELLED");
        item(migrated, luna, consulta, "Consulta");
        jdbc.sql("""
                update reservations
                set cancelled_at = :cancelledAt,
                    cancelled_by = 'MIGRATION',
                    cancellation_reason =
                      'Conflicto detectado durante migraci\u00f3n Phase 1'
                where id = :id
                """)
                .param("cancelledAt",
                        Instant.parse("2026-03-01T11:00:00Z"))
                .param("id", migrated)
                .update();

        queryCounter.reset();
        mvc.perform(get(
                        "/api/v1/admin/reservations/{id}",
                        migrated)
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledBy")
                        .value("MIGRATION"))
                .andExpect(jsonPath("$.cancellationReason")
                        .value("Conflicto detectado durante migraci\u00f3n Phase 1"));
        assertThat(queryCounter.count()).isEqualTo(3);
    }

    @Test
    void validationRoleCsrfAndGenericNotFoundBoundariesAreClosed()
            throws Exception {
        ClientRow ana = client(
                "Ana", "ana@example.com", "+56911111111", true);
        long luna = pet(ana.clientId(), "Luna", "Perro", true);

        mvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard")
                        .with(user(ana.principal())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reservations")
                        .with(user(admin()))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ADMIN_PAGINATION"));
        mvc.perform(get("/api/v1/admin/reservations")
                        .with(user(admin()))
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/admin/reservations")
                        .with(user(admin()))
                        .param("from", "2026-08-11")
                        .param("to", "2026-08-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ADMIN_DATE_RANGE"));
        mvc.perform(get("/api/v1/admin/reservations")
                        .with(user(admin()))
                        .param("to", LocalDate.MAX.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ADMIN_DATE_RANGE"));
        mvc.perform(get("/api/v1/admin/reservations")
                        .with(user(admin()))
                        .param("from", "1899-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ADMIN_DATE_RANGE"));
        mvc.perform(get("/api/v1/admin/pets")
                        .with(user(admin()))
                        .param("q", "x".repeat(121)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ADMIN_SEARCH"));
        mvc.perform(get(
                        "/api/v1/admin/clients/{id}", 9_999_999)
                        .with(user(admin())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(patch(
                        "/api/v1/admin/pets/{id}", luna)
                        .with(user(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Luna","species":"Perro"}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(patch(
                        "/api/v1/admin/pets/{id}", luna)
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Luna","species":"Perro",
                                 "birthdate":"2099-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));
    }

    @Test
    void acceptedAdministratorModulesRemainCanonicalAndUsable()
            throws Exception {
        mvc.perform(get("/api/v1/admin/services")
                        .with(user(admin())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/availability/weekly")
                        .with(user(admin())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/notifications")
                        .with(user(admin())))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentClientEditsReturnOneSuccessAndOneClosedConflict()
            throws Exception {
        ClientRow ana = client(
                "Ana", "ana@example.com", "+56911111111", true);
        jdbc.sql("""
                create function test_hold_client_update()
                returns trigger language plpgsql as $$
                begin
                  perform pg_advisory_xact_lock(881177);
                  perform pg_sleep(0.5);
                  return new;
                end
                $$
                """).update();
        jdbc.sql("""
                create trigger test_hold_client_update
                before update on clients
                for each row execute function
                  test_hold_client_update()
                """).update();

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<org.springframework.test.web.servlet.MvcResult>>
                    results = List.of(
                    executor.submit(() -> updateClient(
                            start, ana.clientId(), "Ana Uno")),
                    executor.submit(() -> updateClient(
                            start, ana.clientId(), "Ana Dos")));
            start.countDown();

            List<Integer> statuses = results.stream()
                    .map(future -> {
                        try {
                            return future.get().getResponse().getStatus();
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .sorted()
                    .toList();
            assertThat(statuses).containsExactly(200, 409);
            String conflictBody = results.stream()
                    .map(future -> {
                        try {
                            return future.get().getResponse()
                                    .getContentAsString();
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .filter(body -> body.contains(
                            "CLIENT_CONCURRENT_UPDATE"))
                    .findFirst()
                    .orElseThrow();
            assertThat(conflictBody)
                    .doesNotContain("ObjectOptimisticLocking")
                    .doesNotContain("clients");
        }
    }

    private org.springframework.test.web.servlet.MvcResult updateClient(
            CountDownLatch start,
            long clientId,
            String name) throws Exception {
        start.await();
        return mvc.perform(patch(
                        "/api/v1/admin/clients/{id}", clientId)
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s",
                                 "phone":"+56911111111",
                                 "active":true}
                                """.formatted(name)))
                .andReturn();
    }

    private long insertUser(
            String email,
            String type,
            boolean enabled) {
        return jdbc.sql("""
                insert into users(
                    email_normalized,password_hash,email_verified_at,
                    account_type,enabled)
                values (:email,'{noop}password',now(),:type,:enabled)
                returning id
                """)
                .param("email", email)
                .param("type", type)
                .param("enabled", enabled)
                .query(Long.class).single();
    }

    private ClientRow client(
            String name,
            String email,
            String phone,
            boolean active) {
        long userId = insertUser(email, "CLIENT", true);
        long clientId = jdbc.sql("""
                insert into clients(user_id,name,phone,active)
                values (:user,:name,:phone,:active)
                returning id
                """)
                .param("user", userId)
                .param("name", name)
                .param("phone", phone)
                .param("active", active)
                .query(Long.class).single();
        return new ClientRow(
                userId, clientId, email, name);
    }

    private long pet(
            long clientId,
            String name,
            String species,
            boolean active) {
        return jdbc.sql("""
                insert into pets(client_id,name,species,active)
                values (:client,:name,:species,:active)
                returning id
                """)
                .param("client", clientId)
                .param("name", name)
                .param("species", species)
                .param("active", active)
                .query(Long.class).single();
    }

    private long service(String code, String name) {
        return jdbc.sql("""
                insert into services(code,name)
                values (:code,:name)
                returning id
                """)
                .param("code", code)
                .param("name", name)
                .query(Long.class).single();
    }

    private long reservation(
            long clientId,
            Instant start,
            String status) {
        return jdbc.sql("""
                insert into reservations(
                    client_id,scheduled_start,scheduled_end,status,
                    created_at,updated_at)
                values (:client,:start,:end,:status,:created,:created)
                returning id
                """)
                .param("client", clientId)
                .param("start", start)
                .param("end", start.plusSeconds(1800))
                .param("status", status)
                .param("created", NOW.minusSeconds(86400))
                .query(Long.class).single();
    }

    private void item(
            long reservationId,
            long petId,
            long serviceId,
            String snapshot) {
        jdbc.sql("""
                insert into reservation_items(
                    reservation_id,pet_id,service_id,
                    service_name_snapshot)
                values (:reservation,:pet,:service,:snapshot)
                """)
                .param("reservation", reservationId)
                .param("pet", petId)
                .param("service", serviceId)
                .param("snapshot", snapshot)
                .update();
    }

    private void event(
            long reservationId,
            String type,
            String actor,
            String previousStatus,
            String newStatus,
            Instant previousStart,
            Instant newStart,
            Instant created) {
        JdbcClient.StatementSpec statement = jdbc.sql("""
                insert into reservation_events(
                    reservation_id,event_type,actor_type,
                    previous_status,new_status,previous_start,
                    new_start,created_at)
                values (:reservation,:type,:actor,
                    :previousStatus,:newStatus,:previousStart,
                    :newStart,:created)
                """)
                .param("reservation", reservationId)
                .param("type", type)
                .param("actor", actor)
                .param("created", created);
        statement = previousStatus == null
                ? statement.param("previousStatus", null, Types.VARCHAR)
                : statement.param("previousStatus", previousStatus);
        statement = newStatus == null
                ? statement.param("newStatus", null, Types.VARCHAR)
                : statement.param("newStatus", newStatus);
        statement = previousStart == null
                ? statement.param(
                        "previousStart", null,
                        Types.TIMESTAMP_WITH_TIMEZONE)
                : statement.param("previousStart", previousStart);
        statement = newStart == null
                ? statement.param(
                        "newStart", null,
                        Types.TIMESTAMP_WITH_TIMEZONE)
                : statement.param("newStart", newStart);
        statement.update();
    }

    private AccountPrincipal admin() {
        return new AccountPrincipal(
                adminUserId, null, "admin@amidog.cl", "Admin",
                AccountType.ADMIN, "{noop}password", true, true);
    }

    private record ClientRow(
            long userId,
            long clientId,
            String email,
            String name) {

        AccountPrincipal principal() {
            return new AccountPrincipal(
                    userId, clientId, email, name,
                    AccountType.CLIENT, "{noop}password",
                    true, true);
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock adminApiClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @TestConfiguration
    static class QueryCountingConfiguration {

        private static final QueryCounter COUNTER =
                new QueryCounter();

        @Bean
        QueryCounter queryCounter() {
            return COUNTER;
        }

        @Bean
        static BeanPostProcessor queryCountingDataSourcePostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(
                        Object bean,
                        String beanName) {
                    if ("dataSource".equals(beanName)
                            && bean instanceof DataSource dataSource
                            && !(bean instanceof CountingDataSource)) {
                        return new CountingDataSource(
                                dataSource, COUNTER);
                    }
                    return bean;
                }
            };
        }
    }

    static final class QueryCounter {

        private final AtomicInteger statementExecutions =
                new AtomicInteger();

        void reset() {
            statementExecutions.set(0);
        }

        int count() {
            return statementExecutions.get();
        }

        void increment() {
            statementExecutions.incrementAndGet();
        }
    }

    static final class CountingDataSource
            extends DelegatingDataSource {

        private final QueryCounter counter;

        CountingDataSource(
                DataSource targetDataSource,
                QueryCounter counter) {
            super(targetDataSource);
            this.counter = counter;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrapConnection(super.getConnection());
        }

        @Override
        public Connection getConnection(
                String username,
                String password) throws SQLException {
            return wrapConnection(
                    super.getConnection(username, password));
        }

        private Connection wrapConnection(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        Object result = invoke(
                                connection, method, arguments);
                        if (result instanceof Statement statement
                                && isStatementFactory(
                                        method.getName())) {
                            return wrapStatement(statement);
                        }
                        return result;
                    });
        }

        private Statement wrapStatement(Statement statement) {
            Class<?> statementType =
                    statement instanceof CallableStatement
                            ? CallableStatement.class
                            : statement instanceof PreparedStatement
                            ? PreparedStatement.class
                            : Statement.class;
            return (Statement) Proxy.newProxyInstance(
                    Statement.class.getClassLoader(),
                    new Class<?>[]{statementType},
                    (proxy, method, arguments) -> {
                        if (isExecution(method.getName())) {
                            counter.increment();
                        }
                        return invoke(statement, method, arguments);
                    });
        }

        private boolean isStatementFactory(String methodName) {
            return "createStatement".equals(methodName)
                    || "prepareStatement".equals(methodName)
                    || "prepareCall".equals(methodName);
        }

        private boolean isExecution(String methodName) {
            return "execute".equals(methodName)
                    || "executeQuery".equals(methodName)
                    || "executeUpdate".equals(methodName)
                    || "executeLargeUpdate".equals(methodName)
                    || "executeBatch".equals(methodName)
                    || "executeLargeBatch".equals(methodName);
        }

        private Object invoke(
                Object target,
                java.lang.reflect.Method method,
                Object[] arguments) throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }
}
