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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "amidog.booking.auto-confirm=false",
        "amidog.booking.minimum-notice-hours=0",
        "amidog.booking.horizon-days=365"
})
class ReservationLifecycleIntegrationTests
        extends PostgresIntegrationTest {

    private static final ZoneId CLINIC_ZONE =
            ZoneId.of("America/Santiago");

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
    void clientCancellationRetainsHistoryAndImmediatelyReleasesTheSlot()
            throws Exception {
        ClientFixture ana = client("ana@example.com", true);
        long milo = pet(ana.clientId(), "Milo");
        long luna = pet(ana.clientId(), "Luna");
        long service = service();
        Slot slot = openTwoHourSchedule();
        long reservation = reservation(
                ana.clientId(), slot.start(), ReservationStatus.CONFIRMED);
        reservationItem(reservation, milo, service, "Consulta");

        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        reservation)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Cambio de planes  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservation))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("CLIENT"))
                .andExpect(jsonPath("$.reason")
                        .value("Cambio de planes"));

        assertThat(jdbc.sql(
                "select count(*) from reservations where id=:id")
                .param("id", reservation)
                .query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                select status || ':' || cancelled_by || ':'
                       || cancellation_reason
                from reservations where id=:id
                """).param("id", reservation)
                .query(String.class).single())
                .isEqualTo(
                        "CANCELLED:CLIENT:Cambio de planes");
        assertThat(jdbc.sql("""
                select event_type || ':' || actor_type
                from reservation_events where reservation_id=:id
                """).param("id", reservation)
                .query(String.class).single())
                .isEqualTo("CANCELLED:CLIENT");
        assertThat(jdbc.sql("""
                select count(*) from reservation_items
                where reservation_id=:id
                """).param("id", reservation)
                .query(Long.class).single()).isEqualTo(1L);

        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s",
                                 "items":[{"petId":%d,"serviceId":%d}]}
                                """.formatted(
                                slot.start(), luna, service)))
                .andExpect(status().isCreated());
    }

    @Test
    void clientOwnershipAndStrictFutureRuleDoNotLeakReservations()
            throws Exception {
        ClientFixture ana = client("ana@example.com", true);
        ClientFixture bob = client("bob@example.com", true);
        long foreign = reservation(
                bob.clientId(),
                OffsetDateTime.now(CLINIC_ZONE).plusDays(7),
                ReservationStatus.PENDING);
        long past = reservation(
                ana.clientId(),
                OffsetDateTime.now(CLINIC_ZONE).minusMinutes(30),
                ReservationStatus.PENDING);

        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        foreign)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        9_999_999L)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        past)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("RESERVATION_NOT_IN_FUTURE"));

        assertThat(jdbc.sql(
                "select count(*) from reservation_events")
                .query(Long.class).single()).isZero();
    }

    @Test
    void clientRescheduleKeepsItemsSnapshotsAndNoteButReappliesPending()
            throws Exception {
        ClientFixture ana = client("ana@example.com", true);
        long pet = pet(ana.clientId(), "Milo");
        long service = service();
        Slot slot = openTwoHourSchedule();
        long reservation = reservation(
                ana.clientId(), slot.start(),
                ReservationStatus.CONFIRMED, "Control anual");
        long item = reservationItem(
                reservation, pet, service, "Nombre histórico");

        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/reschedule",
                        reservation)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s"}
                                """.formatted(slot.target())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStartsAt")
                        .value(apiTimestamp(slot.start())))
                .andExpect(jsonPath("$.startsAt")
                        .value(apiTimestamp(slot.target())))
                .andExpect(jsonPath("$.endsAt")
                        .value(apiTimestamp(slot.target()
                                .plusMinutes(30))))
                .andExpect(jsonPath("$.previousStatus")
                        .value("CONFIRMED"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(jdbc.sql("""
                select id || ':' || service_name_snapshot
                from reservation_items where reservation_id=:reservation
                """).param("reservation", reservation)
                .query(String.class).single())
                .isEqualTo(item + ":Nombre histórico");
        assertThat(jdbc.sql("""
                select client_note from reservations where id=:id
                """).param("id", reservation)
                .query(String.class).single())
                .isEqualTo("Control anual");
        assertThat(jdbc.sql("""
                select extract(epoch from
                    (scheduled_end - scheduled_start))::bigint
                from reservations where id=:id
                """).param("id", reservation)
                .query(Long.class).single()).isEqualTo(1800L);
        assertThat(jdbc.sql("""
                select event_type || ':' || actor_type || ':'
                       || previous_status || ':' || new_status
                from reservation_events where reservation_id=:id
                """).param("id", reservation)
                .query(String.class).single())
                .isEqualTo(
                        "RESCHEDULED:CLIENT:CONFIRMED:PENDING");
    }

    @Test
    void adminStatusAndRescheduleUseMatrixAndPreserveConfirmedStatus()
            throws Exception {
        ClientFixture ana = client("ana@example.com", true);
        Slot slot = openTwoHourSchedule();
        long reservation = reservation(
                ana.clientId(), slot.start(),
                ReservationStatus.PENDING);

        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        reservation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED",
                                 "reason":"  Confirmada por teléfono  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus")
                        .value("PENDING"))
                .andExpect(jsonPath("$.status")
                        .value("CONFIRMED"))
                .andExpect(jsonPath("$.reason")
                        .value("Confirmada por teléfono"));

        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/reschedule",
                        reservation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s"}
                                """.formatted(slot.target())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("CONFIRMED"));

        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        reservation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(
                        "INVALID_RESERVATION_STATUS_TRANSITION"));

        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        reservation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED",
                                 "reason":"  Reprogramación administrativa  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus")
                        .value("CONFIRMED"))
                .andExpect(jsonPath("$.status")
                        .value("CANCELLED"))
                .andExpect(jsonPath("$.reason")
                        .value("Reprogramación administrativa"));

        assertThat(jdbc.sql("""
                select cancelled_by || ':' || cancellation_reason
                from reservations where id=:id
                """).param("id", reservation)
                .query(String.class).single())
                .isEqualTo(
                        "ADMIN:Reprogramación administrativa");
        assertThat(jdbc.sql("""
                select event_type || ':' || actor_type
                from reservation_events
                where reservation_id=:id
                order by created_at, id
                """).param("id", reservation)
                .query(String.class).list())
                .containsExactly(
                        "STATUS_CHANGED:ADMIN",
                        "RESCHEDULED:ADMIN",
                        "CANCELLED:ADMIN");
    }

    @Test
    void rescheduleRejectsWrongOffsetBlackoutAndOccupiedTarget()
            throws Exception {
        ClientFixture ana = client("ana@example.com", true);
        ClientFixture bob = client("bob@example.com", true);
        Slot slot = openTwoHourSchedule();
        long own = reservation(
                ana.clientId(), slot.start(),
                ReservationStatus.PENDING);
        ZoneOffset wrong = slot.target().getOffset()
                .equals(ZoneOffset.ofHours(-3))
                ? ZoneOffset.ofHours(-4)
                : ZoneOffset.ofHours(-3);

        assertRescheduleCode(
                ana,
                own,
                OffsetDateTime.of(
                        slot.target().toLocalDateTime(), wrong),
                status().isBadRequest(),
                "INVALID_RESERVATION_START");

        jdbc.sql("""
                insert into availability_blocks(start_at,end_at,reason)
                values (:start,:end,'Cirugía')
                """)
                .param("start", slot.target().toInstant())
                .param("end", slot.target()
                        .plusMinutes(30).toInstant())
                .update();
        assertRescheduleCode(
                ana, own, slot.target(),
                status().isConflict(), "SLOT_UNAVAILABLE");
        jdbc.sql("delete from availability_blocks").update();

        reservation(
                bob.clientId(), slot.target(),
                ReservationStatus.CONFIRMED);
        assertRescheduleCode(
                ana, own, slot.target(),
                status().isConflict(), "SLOT_ALREADY_BOOKED");

        assertThat(jdbc.sql("""
                select count(*) from reservation_events
                where reservation_id=:id
                """).param("id", own)
                .query(Long.class).single()).isZero();
    }

    @Test
    void rolesCsrfAndInvalidBodiesAreEnforcedForAllMutations()
            throws Exception {
        ClientFixture ana = client("ana@example.com", true);
        ClientFixture unverified =
                client("unverified@example.com", false);
        long reservation = reservation(
                ana.clientId(),
                OffsetDateTime.now(CLINIC_ZONE).plusDays(7),
                ReservationStatus.PENDING);

        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        reservation)
                        .with(user(ana.principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        reservation)
                        .with(user(unverified.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        reservation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        reservation)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        reservation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST"));
        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/reschedule",
                        reservation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));
        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        reservation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST"));
        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        reservation)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"%s"}
                                """.formatted("x".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));
    }

    @Test
    void normalizedReasonLengthAppliesToClientAndAdministratorRequests()
            throws Exception {
        ClientFixture ana = client("ana@example.com", true);
        OffsetDateTime firstStart =
                OffsetDateTime.now(CLINIC_ZONE).plusDays(7);
        OffsetDateTime secondStart = firstStart.plusMinutes(30);
        long clientCancellation = reservation(
                ana.clientId(), firstStart,
                ReservationStatus.PENDING);
        long adminCancellation = reservation(
                ana.clientId(), secondStart,
                ReservationStatus.CONFIRMED);
        String maximum = "x".repeat(300);

        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        clientCancellation)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"  %s  "}
                                """.formatted(maximum)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value(maximum));
        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        adminCancellation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED",
                                 "reason":"  %s  "}
                                """.formatted(maximum)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value(maximum));

        assertThat(jdbc.sql("""
                select count(*) from reservations
                where id in (:clientCancellation,:adminCancellation)
                  and char_length(cancellation_reason)=300
                  and cancellation_reason not like ' %'
                  and cancellation_reason not like '% '
                """)
                .param("clientCancellation", clientCancellation)
                .param("adminCancellation", adminCancellation)
                .query(Long.class).single()).isEqualTo(2L);
        assertThat(jdbc.sql("""
                select count(*) from reservation_events
                where reservation_id in (
                    :clientCancellation,:adminCancellation)
                  and char_length(reason)=300
                  and reason not like ' %'
                  and reason not like '% '
                """)
                .param("clientCancellation", clientCancellation)
                .param("adminCancellation", adminCancellation)
                .query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void overlongNormalizedReasonsRejectWithoutStateOrEvent()
            throws Exception {
        ClientFixture ana = client("ana@example.com", true);
        OffsetDateTime firstStart =
                OffsetDateTime.now(CLINIC_ZONE).plusDays(7);
        OffsetDateTime secondStart = firstStart.plusMinutes(30);
        long clientCancellation = reservation(
                ana.clientId(), firstStart,
                ReservationStatus.PENDING);
        long adminCancellation = reservation(
                ana.clientId(), secondStart,
                ReservationStatus.CONFIRMED);
        String overLimit = "x".repeat(301);

        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        clientCancellation)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"  %s  "}
                                """.formatted(overLimit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));
        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        adminCancellation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED",
                                 "reason":"  %s  "}
                                """.formatted(overLimit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        assertThat(jdbc.sql("""
                select status from reservations
                where id in (:clientCancellation,:adminCancellation)
                order by id
                """)
                .param("clientCancellation", clientCancellation)
                .param("adminCancellation", adminCancellation)
                .query(String.class).list())
                .containsExactly("PENDING", "CONFIRMED");
        assertThat(jdbc.sql("""
                select count(*) from reservation_events
                where reservation_id in (
                    :clientCancellation,:adminCancellation)
                """)
                .param("clientCancellation", clientCancellation)
                .param("adminCancellation", adminCancellation)
                .query(Long.class).single()).isZero();
    }

    private void assertRescheduleCode(
            ClientFixture client,
            long reservation,
            OffsetDateTime target,
            org.springframework.test.web.servlet.ResultMatcher statusMatcher,
            String code) throws Exception {
        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/reschedule",
                        reservation)
                        .with(user(client.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s"}
                                """.formatted(target)))
                .andExpect(statusMatcher)
                .andExpect(jsonPath("$.code").value(code));
    }

    private Slot openTwoHourSchedule() {
        LocalDate date =
                LocalDate.now(CLINIC_ZONE).plusDays(7);
        OffsetDateTime start = date.atTime(10, 0)
                .atZone(CLINIC_ZONE).toOffsetDateTime();
        jdbc.sql("""
                insert into weekly_availability(
                    day_of_week,local_start_time,local_end_time)
                values (:day,'10:00','12:00')
                """).param("day", date.getDayOfWeek().getValue())
                .update();
        return new Slot(start, start.plusMinutes(60));
    }

    private static String apiTimestamp(OffsetDateTime value) {
        return value.format(DateTimeFormatter.ofPattern(
                "uuuu-MM-dd'T'HH:mm:ssXXX"));
    }

    private ClientFixture client(
            String email,
            boolean verified) {
        long userId = jdbc.sql("""
                insert into users(
                    email_normalized,password_hash,email_verified_at,
                    account_type,enabled)
                values (:email,'{noop}password',
                        case when :verified then now() else null end,
                        'CLIENT',:verified)
                returning id
                """)
                .param("email", email)
                .param("verified", verified)
                .query(Long.class).single();
        long clientId = jdbc.sql("""
                insert into clients(user_id,name,phone)
                values (:user,'Ana','+56911111111') returning id
                """).param("user", userId)
                .query(Long.class).single();
        return new ClientFixture(
                userId, clientId, email, verified);
    }

    private long pet(long clientId, String name) {
        return jdbc.sql("""
                insert into pets(client_id,name,species)
                values (:client,:name,'Mascota') returning id
                """).param("client", clientId)
                .param("name", name)
                .query(Long.class).single();
    }

    private long service() {
        return jdbc.sql("""
                insert into services(code,name)
                values ('consulta','Consulta') returning id
                """).query(Long.class).single();
    }

    private long reservation(
            long clientId,
            OffsetDateTime start,
            ReservationStatus status) {
        return reservation(clientId, start, status, null);
    }

    private long reservation(
            long clientId,
            OffsetDateTime start,
            ReservationStatus status,
            String note) {
        return jdbc.sql("""
                insert into reservations(
                    client_id,scheduled_start,scheduled_end,status,
                    client_note)
                values (:client,:start,:end,:status,:note)
                returning id
                """)
                .param("client", clientId)
                .param("start", start.toInstant())
                .param("end", start.plusMinutes(30).toInstant())
                .param("status", status.name())
                .param("note", note)
                .query(Long.class).single();
    }

    private long reservationItem(
            long reservation,
            long pet,
            long service,
            String snapshot) {
        return jdbc.sql("""
                insert into reservation_items(
                    reservation_id,pet_id,service_id,
                    service_name_snapshot)
                values (:reservation,:pet,:service,:snapshot)
                returning id
                """)
                .param("reservation", reservation)
                .param("pet", pet)
                .param("service", service)
                .param("snapshot", snapshot)
                .query(Long.class).single();
    }

    private static AccountPrincipal admin() {
        return new AccountPrincipal(
                999L, null, "admin@example.com", "Admin",
                AccountType.ADMIN, "{noop}password", true, true);
    }

    private record Slot(
            OffsetDateTime start,
            OffsetDateTime target) {
    }

    private record ClientFixture(
            long userId,
            long clientId,
            String email,
            boolean verified) {
        AccountPrincipal principal() {
            return new AccountPrincipal(
                    userId, clientId, email, "Ana",
                    AccountType.CLIENT, "{noop}password",
                    true, verified);
        }
    }
}
