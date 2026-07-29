package com.amidog.app.migration;

import com.amidog.app.support.DomainTestFixtures;
import com.amidog.app.support.PostgresIntegrationTest;
import com.amidog.app.support.TestRows;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
class SchedulingMigrationIntegrationTests extends PostgresIntegrationTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String MIGRATION_CONFLICT_REASON =
            "Conflicto detectado durante migración Phase 1";

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PostgreSQLContainer postgres;

    @Test
    void freshSchemaContainsSchedulingTablesColumnsIndexesAndConstraints() {
        Integer tableCount = jdbc.sql("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'services', 'weekly_availability', 'availability_blocks',
                    'reservations', 'reservation_items', 'reservation_events',
                    'notifications')
                """).query(Integer.class).single();
        assertThat(tableCount).isEqualTo(7);

        List<String> columns = jdbc.sql("""
                select table_name || '.' || column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name in (
                    'services', 'weekly_availability', 'availability_blocks',
                    'reservations', 'reservation_items', 'reservation_events',
                    'notifications')
                order by table_name, column_name
                """).query(String.class).list();
        assertThat(columns).containsExactly(
                "availability_blocks.created_at",
                "availability_blocks.end_at",
                "availability_blocks.id",
                "availability_blocks.reason",
                "availability_blocks.start_at",
                "notifications.body",
                "notifications.created_at",
                "notifications.deduplication_key",
                "notifications.id",
                "notifications.read_at",
                "notifications.recipient_user_id",
                "notifications.reservation_id",
                "notifications.title",
                "notifications.type",
                "reservation_events.actor_type",
                "reservation_events.created_at",
                "reservation_events.event_type",
                "reservation_events.id",
                "reservation_events.new_start",
                "reservation_events.new_status",
                "reservation_events.previous_start",
                "reservation_events.previous_status",
                "reservation_events.reason",
                "reservation_events.reservation_id",
                "reservation_items.id",
                "reservation_items.pet_id",
                "reservation_items.reservation_id",
                "reservation_items.service_id",
                "reservation_items.service_name_snapshot",
                "reservations.cancellation_reason",
                "reservations.cancelled_at",
                "reservations.cancelled_by",
                "reservations.client_id",
                "reservations.client_note",
                "reservations.created_at",
                "reservations.id",
                "reservations.scheduled_end",
                "reservations.scheduled_start",
                "reservations.status",
                "reservations.updated_at",
                "reservations.version",
                "services.active",
                "services.code",
                "services.created_at",
                "services.description",
                "services.display_order",
                "services.id",
                "services.name",
                "services.updated_at",
                "services.version",
                "weekly_availability.active",
                "weekly_availability.day_of_week",
                "weekly_availability.id",
                "weekly_availability.local_end_time",
                "weekly_availability.local_start_time");

        List<String> timestampColumns = jdbc.sql("""
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'reservations'
                  and data_type = 'timestamp with time zone'
                order by column_name
                """).query(String.class).list();
        assertThat(timestampColumns).containsExactly(
                "cancelled_at", "created_at", "scheduled_end",
                "scheduled_start", "updated_at");

        List<String> indexes = jdbc.sql("""
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and indexname in (
                    'availability_blocks_range_idx',
                    'notifications_recipient_idx',
                    'reservation_events_reservation_idx',
                    'reservations_client_start_idx',
                    'reservations_status_start_idx')
                order by indexname
                """).query(String.class).list();
        assertThat(indexes).containsExactly(
                "availability_blocks_range_idx",
                "notifications_recipient_idx",
                "reservation_events_reservation_idx",
                "reservations_client_start_idx",
                "reservations_status_start_idx");

        List<String> constraints = jdbc.sql("""
                select conname
                from pg_constraint
                where conrelid in (
                    'services'::regclass,
                    'weekly_availability'::regclass,
                    'availability_blocks'::regclass,
                    'reservations'::regclass,
                    'reservation_items'::regclass,
                    'reservation_events'::regclass,
                    'notifications'::regclass)
                order by conname
                """).query(String.class).list();
        assertThat(constraints).containsExactly(
                "availability_blocks_pkey",
                "availability_blocks_time_order_ck",
                "notifications_deduplication_key_key",
                "notifications_pkey",
                "notifications_recipient_user_id_fkey",
                "notifications_reservation_id_fkey",
                "reservation_events_pkey",
                "reservation_events_reservation_id_fkey",
                "reservation_items_pet_id_fkey",
                "reservation_items_pkey",
                "reservation_items_reservation_id_fkey",
                "reservation_items_reservation_pet_uq",
                "reservation_items_service_id_fkey",
                "reservations_client_id_fkey",
                "reservations_duration_ck",
                "reservations_no_occupied_overlap",
                "reservations_pkey",
                "reservations_status_ck",
                "services_code_key",
                "services_pkey",
                "weekly_availability_day_of_week_ck",
                "weekly_availability_interval_uq",
                "weekly_availability_pkey",
                "weekly_availability_time_order_ck");
        assertThat(jdbc.sql("""
                select contype from pg_constraint
                where conname = 'reservations_no_occupied_overlap'
                  and connamespace = 'public'::regnamespace
                """).query(String.class).single()).isEqualTo("x");
    }

    @Test
    void enforcesThirtyMinuteDurationAndExactStatusDomain() {
        long clientId = TestRows.verifiedClient(jdbc);

        assertThatThrownBy(() -> jdbc.sql("""
                insert into reservations(
                    client_id, scheduled_start, scheduled_end, status)
                values (
                    :clientId, '2027-01-04T13:00:00Z',
                    '2027-01-04T13:29:00Z', 'PENDING')
                """).param("clientId", clientId).update())
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.sql("""
                insert into reservations(
                    client_id, scheduled_start, scheduled_end, status)
                values (
                    :clientId, '2027-01-04T14:00:00Z',
                    '2027-01-04T14:30:00Z', 'DELETED')
                """).param("clientId", clientId).update())
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.sql("""
                insert into weekly_availability(
                    day_of_week, local_start_time, local_end_time)
                values (0, '09:00', '10:00')
                """).update())
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.sql("""
                insert into weekly_availability(
                    day_of_week, local_start_time, local_end_time)
                values (1, '10:00', '09:00')
                """).update())
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.sql("""
                insert into availability_blocks(start_at, end_at)
                values (
                    '2027-01-04T15:00:00Z',
                    '2027-01-04T14:00:00Z')
                """).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsOccupiedOverlapsButAllowsAdjacentReservations() {
        long clientId = TestRows.verifiedClient(jdbc);
        DomainTestFixtures fixtures = new DomainTestFixtures(jdbc);
        fixtures.reservation(
                clientId, Instant.parse("2027-02-08T13:00:00Z"), "PENDING");

        assertThatCode(() -> fixtures.reservation(
                clientId, Instant.parse("2027-02-08T13:30:00Z"), "CONFIRMED"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> fixtures.reservation(
                clientId, Instant.parse("2027-02-08T13:15:00Z"), "CONFIRMED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void cancelledCompletedAndNoShowReservationsDoNotOccupyTheCalendar() {
        long clientId = TestRows.verifiedClient(jdbc);
        DomainTestFixtures fixtures = new DomainTestFixtures(jdbc);

        long cancelled = fixtures.reservation(
                clientId, Instant.parse("2027-03-01T13:00:00Z"), "PENDING");
        jdbc.sql("update reservations set status = 'CANCELLED' where id = :id")
                .param("id", cancelled)
                .update();
        assertThatCode(() -> fixtures.reservation(
                clientId, Instant.parse("2027-03-01T13:00:00Z"), "PENDING"))
                .doesNotThrowAnyException();

        fixtures.reservation(
                clientId, Instant.parse("2027-03-01T14:00:00Z"), "COMPLETED");
        assertThatCode(() -> fixtures.reservation(
                clientId, Instant.parse("2027-03-01T14:00:00Z"), "CONFIRMED"))
                .doesNotThrowAnyException();

        fixtures.reservation(
                clientId, Instant.parse("2027-03-01T15:00:00Z"), "NO_SHOW");
        assertThatCode(() -> fixtures.reservation(
                clientId, Instant.parse("2027-03-01T15:00:00Z"), "PENDING"))
                .doesNotThrowAnyException();
    }

    @Test
    void enforcesSchedulingRelationshipsAndBusinessKeys() {
        long clientId = TestRows.verifiedClient(jdbc);
        DomainTestFixtures fixtures = new DomainTestFixtures(jdbc);
        long petId = fixtures.pet(clientId, "Luna", "Perro");
        long serviceId = fixtures.service("consulta-test", "Consulta");
        long reservationId = fixtures.reservation(
                clientId, Instant.parse("2027-04-05T13:00:00Z"), "PENDING");
        fixtures.reservationItem(reservationId, petId, serviceId, "Consulta");

        assertThatThrownBy(() -> fixtures.reservationItem(
                reservationId, petId, serviceId, "Consulta duplicada"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> fixtures.service(
                "consulta-test", "Otra consulta"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.sql("""
                insert into reservation_items(
                    reservation_id, pet_id, service_id, service_name_snapshot)
                values (9999999, :petId, :serviceId, 'Consulta')
                """)
                .param("petId", petId)
                .param("serviceId", serviceId)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.sql("""
                insert into notifications(
                    recipient_user_id, type, title, body, deduplication_key)
                select user_id, 'TEST', 'Título', 'Cuerpo', 'same-key'
                from clients where id = :clientId
                """).param("clientId", clientId).update();
        assertThatThrownBy(() -> jdbc.sql("""
                insert into notifications(
                    recipient_user_id, type, title, body, deduplication_key)
                select user_id, 'TEST', 'Título', 'Cuerpo', 'same-key'
                from clients where id = :clientId
                """).param("clientId", clientId).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void freshFoundationMigrationIsRepeatableAndLegacyImportIsANoOp() throws Exception {
        withDatabase("fresh_scheduling", dataSource -> {
            Flyway flyway = flyway(dataSource);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(9);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            flyway.validate();

            JdbcClient fresh = JdbcClient.create(dataSource);
            assertThat(fresh.sql("select count(*) from users")
                    .query(Integer.class).single()).isZero();
            assertThat(fresh.sql("select count(*) from reservations")
                    .query(Integer.class).single()).isZero();
        });
    }

    @Test
    void upgradesRealPhaseOneDataWithoutLossAndAddsCollisionGuarantee()
            throws Exception {
        withDatabase("phase1_upgrade", dataSource -> {
            createPhaseOneFixture(dataSource);
            Flyway throughV6 = flyway(dataSource, "6");
            assertThat(throughV6.migrate().migrationsExecuted).isEqualTo(6);
            JdbcClient upgraded = JdbcClient.create(dataSource);
            List<String> frozenChecksumsBefore = foundationChecksums(upgraded);

            Flyway finalFlyway = flyway(dataSource);
            assertThat(finalFlyway.migrate().migrationsExecuted).isEqualTo(3);
            finalFlyway.validate();
            assertThat(foundationChecksums(upgraded))
                    .containsExactlyElementsOf(frozenChecksumsBefore);

            assertThat(upgraded.sql("""
                    select count(*) from clients where id between 10 and 13
                    """).query(Integer.class).single()).isEqualTo(4);
            assertThat(upgraded.sql("""
                    select count(*) from pets where id in (20, 21)
                    """).query(Integer.class).single()).isEqualTo(2);
            assertThat(upgraded.sql("""
                    select email_normalized
                    from users u join clients c on c.user_id = u.id
                    where c.id = 10
                    """).query(String.class).single()).isEqualTo("ana@example.com");
            assertThat(upgraded.sql("""
                    select count(*)
                    from users u join clients c on c.user_id = u.id
                    where c.id between 10 and 13
                      and u.enabled = false
                      and u.password_hash is null
                      and u.email_verified_at is null
                    """).query(Integer.class).single()).isEqualTo(4);
            assertThat(upgraded.sql("""
                    select count(*)
                    from users u join clients c on c.user_id = u.id
                    where c.id in (11, 12, 13)
                      and u.email_normalized like '%@legacy.amidog.invalid'
                    """).query(Integer.class).single()).isEqualTo(3);
            assertThat(upgraded.sql("""
                    select count(*) from services
                    where name in ('Consulta General', 'Servicio legado')
                    """).query(Integer.class).single()).isEqualTo(2);

            assertThat(upgraded.sql("""
                    select status from reservations where id = 30
                    """).query(String.class).single()).isEqualTo("PENDING");
            assertThat(upgraded.sql("""
                    select status from reservations where id = 31
                    """).query(String.class).single()).isEqualTo("CANCELLED");
            assertThat(upgraded.sql("""
                    select cancellation_reason from reservations where id = 31
                    """).query(String.class).single())
                    .isEqualTo(MIGRATION_CONFLICT_REASON);
            assertThat(upgraded.sql("""
                    select status from reservations where id = 32
                    """).query(String.class).single()).isEqualTo("CONFIRMED");
            assertThat(upgraded.sql("""
                    select status from reservations where id = 33
                    """).query(String.class).single()).isEqualTo("CANCELLED");
            assertThat(upgraded.sql("""
                    select cancellation_reason from reservations where id = 33
                    """).query(String.class).single())
                    .isEqualTo(MIGRATION_CONFLICT_REASON);

            OffsetDateTime winterStart = upgraded.sql("""
                    select scheduled_start from reservations where id = 30
                    """).query(OffsetDateTime.class).single();
            OffsetDateTime summerStart = upgraded.sql("""
                    select scheduled_start from reservations where id = 34
                    """).query(OffsetDateTime.class).single();
            assertThat(winterStart.toInstant())
                    .isEqualTo(Instant.parse("2026-08-10T13:00:00Z"));
            assertThat(summerStart.toInstant())
                    .isEqualTo(Instant.parse("2026-01-12T12:00:00Z"));
            assertThat(upgraded.sql("""
                    select count(*) from reservation_items
                    where reservation_id in (30, 31, 32, 33, 34)
                    """).query(Integer.class).single()).isEqualTo(5);
            assertThat(upgraded.sql("""
                    select count(*)
                    from reservation_items ri
                    join services s on s.id = ri.service_id
                    where ri.service_name_snapshot = s.name
                    """).query(Integer.class).single()).isEqualTo(5);
            assertThat(upgraded.sql("""
                    select count(*)
                    from reservation_items ri
                    join reservations r on r.id = ri.reservation_id
                    join pets p on p.id = ri.pet_id
                    where r.client_id <> p.client_id
                    """).query(Integer.class).single()).isZero();
            assertThat(upgraded.sql("""
                    select count(*)
                    from reservation_items
                    where reservation_id = 30 and pet_id = 20
                    """).query(Integer.class).single()).isOne();

            assertThat(upgraded.sql("""
                    select count(*) from legacy_clients
                    """).query(Integer.class).single()).isEqualTo(4);
            assertThat(upgraded.sql("""
                    select count(*) from legacy_reservation_pets
                    """).query(Integer.class).single()).isEqualTo(7);
            assertThat(upgraded.sql("""
                    select count(*) from legacy_reservation_pets
                    where id_reservation = 30 and id_pet = 21
                    """).query(Integer.class).single()).isOne();
            assertThat(upgraded.sql("""
                    select count(*) from medical_records
                    """).query(Integer.class).single()).isOne();
            assertThat(upgraded.sql("""
                    select count(*)
                    from reservations a
                    join reservations b on a.id < b.id
                    where a.status in ('PENDING', 'CONFIRMED')
                      and b.status in ('PENDING', 'CONFIRMED')
                      and tstzrange(a.scheduled_start, a.scheduled_end, '[)')
                          && tstzrange(b.scheduled_start, b.scheduled_end, '[)')
                    """).query(Integer.class).single()).isZero();

            long userId = upgraded.sql("""
                    insert into users(
                        email_normalized, password_hash, account_type, enabled)
                    values ('sequence@example.com', null, 'CLIENT', false)
                    returning id
                    """).query(Long.class).single();
            long clientId = upgraded.sql("""
                    insert into clients(user_id, name, phone)
                    values (:userId, 'Secuencia', '0')
                    returning id
                    """).param("userId", userId).query(Long.class).single();
            long petId = upgraded.sql("""
                    insert into pets(client_id, name, species)
                    values (:clientId, 'Secuencia', 'Perro')
                    returning id
                    """).param("clientId", clientId).query(Long.class).single();
            long serviceId = upgraded.sql("""
                    insert into services(code, name)
                    values ('sequence-service', 'Secuencia')
                    returning id
                    """).query(Long.class).single();
            long reservationId = upgraded.sql("""
                    insert into reservations(
                        client_id, scheduled_start, scheduled_end, status)
                    values (
                        :clientId, '2027-05-03T13:00:00Z',
                        '2027-05-03T13:30:00Z', 'PENDING')
                    returning id
                    """).param("clientId", clientId).query(Long.class).single();
            long itemId = upgraded.sql("""
                    insert into reservation_items(
                        reservation_id, pet_id, service_id, service_name_snapshot)
                    values (:reservationId, :petId, :serviceId, 'Secuencia')
                    returning id
                    """)
                    .param("reservationId", reservationId)
                    .param("petId", petId)
                    .param("serviceId", serviceId)
                    .query(Long.class).single();
            assertThat(clientId).isGreaterThan(13);
            assertThat(petId).isGreaterThan(21);
            assertThat(serviceId).isGreaterThan(2);
            assertThat(reservationId).isGreaterThan(34);
            assertThat(itemId).isGreaterThan(5);
        });
    }

    @Test
    void importsDstTransitionRowsUsingPostgresqlStandardOffsetConvention()
            throws Exception {
        DstCases cases = chileDstCases();
        withDatabase("phase1_dst", dataSource -> {
            createDstFixture(dataSource, cases);
            Flyway migrated = flyway(dataSource);
            assertThat(migrated.migrate().migrationsExecuted).isEqualTo(9);
            migrated.validate();

            JdbcClient upgraded = JdbcClient.create(dataSource);
            OffsetDateTime gapStart = upgraded.sql("""
                    select scheduled_start from reservations where id = 60
                    """).query(OffsetDateTime.class).single();
            OffsetDateTime overlapStart = upgraded.sql("""
                    select scheduled_start from reservations where id = 61
                    """).query(OffsetDateTime.class).single();

            // PostgreSQL resolves a nonexistent local time with the offset
            // before the gap, and an ambiguous local time with the offset
            // after the overlap. In both cases that is its standard-time
            // preference. Later ClinicTime input validation may reject these
            // values; V8 must preserve deterministic legacy instants.
            assertThat(gapStart.toInstant()).isEqualTo(cases.gapExpected());
            assertThat(overlapStart.toInstant())
                    .isEqualTo(cases.overlapExpected());
            assertThat(upgraded.sql("""
                    select count(*) from reservations
                    where id in (60, 61)
                      and scheduled_end =
                          scheduled_start + interval '30 minutes'
                    """).query(Integer.class).single()).isEqualTo(2);
            assertThat(upgraded.sql("""
                    select count(*) from reservation_items
                    where reservation_id in (60, 61)
                    """).query(Integer.class).single()).isEqualTo(2);
            assertThat(upgraded.sql("""
                    select count(*) from pg_constraint
                    where conname = 'reservations_no_occupied_overlap'
                      and connamespace = 'public'::regnamespace
                      and contype = 'x'
                    """).query(Integer.class).single()).isOne();
        });
    }

    @Test
    void reservesAllLegacyIdsBeforeAllocatingCollisionReplacements()
            throws Exception {
        withDatabase("phase1_existing_accounts", dataSource -> {
            createCollisionFixture(dataSource);
            flyway(dataSource, "6").migrate();
            JdbcClient upgraded = JdbcClient.create(dataSource);
            upgraded.sql("""
                    insert into users(
                        id, email_normalized, password_hash, email_verified_at,
                        account_type, enabled)
                    values (
                        1, 'admin@amidog.cl', '{noop}admin', now(),
                        'ADMIN', true),
                        (2, 'real@example.com', '{noop}real', now(),
                        'CLIENT', true),
                        (3, 'high@example.com', '{noop}high', now(),
                        'CLIENT', true)
                    """).update();
            upgraded.sql("""
                    insert into clients(id, user_id, name, phone) values
                        (1, 2, 'Cliente real', '+56911111111'),
                        (50, 3, 'Cliente real alto', '+56955555555')
                    """).update();
            upgraded.sql("""
                    insert into pets(id, client_id, name, species) values
                        (1, 1, 'Mascota real', 'Perro'),
                        (50, 50, 'Mascota real alta', 'Gato')
                    """).update();

            flyway(dataSource, "7").migrate();
            upgraded.sql("""
                    insert into services(id, code, name)
                    values (1, 'real-service', 'Servicio real')
                    """).update();
            upgraded.sql("""
                    insert into reservations(
                        id, client_id, scheduled_start, scheduled_end, status)
                    values
                        (1, 1, '2027-06-07T13:00:00Z',
                            '2027-06-07T13:30:00Z', 'CONFIRMED'),
                        (50, 50, '2027-06-08T13:00:00Z',
                            '2027-06-08T13:30:00Z', 'CONFIRMED')
                    """).update();

            flyway(dataSource).migrate();

            assertThat(upgraded.sql("""
                    select email_normalized from users where id = 1
                    """).query(String.class).single()).isEqualTo("admin@amidog.cl");
            assertThat(upgraded.sql("""
                    select account_type from users where id = 1
                    """).query(String.class).single()).isEqualTo("ADMIN");
            assertThat(upgraded.sql("""
                    select name from clients where id = 1
                    """).query(String.class).single()).isEqualTo("Cliente real");
            assertThat(upgraded.sql("""
                    select name from pets where id = 1
                    """).query(String.class).single()).isEqualTo("Mascota real");

            long collidedClientId = upgraded.sql("""
                    select c.id
                    from clients c join users u on u.id = c.user_id
                    where c.name = 'Cliente legado colisionado'
                    """).query(Long.class).single();
            assertThat(collidedClientId).isGreaterThan(50);
            assertThat(upgraded.sql("""
                    select id from clients where name = 'Cliente legado libre'
                    """).query(Long.class).single()).isEqualTo(2);
            assertThat(upgraded.sql("""
                    select id from clients where name = 'Cliente legado hueco'
                    """).query(Long.class).single()).isEqualTo(10);
            assertThat(upgraded.sql("""
                    select u.email_normalized
                    from users u join clients c on c.user_id = u.id
                    where c.id = :clientId
                    """).param("clientId", collidedClientId)
                    .query(String.class).single())
                    .endsWith("@legacy.amidog.invalid");
            assertThat(upgraded.sql("""
                    select client_id from pets where name = 'Mascota legada'
                    """).query(Long.class).single()).isEqualTo(collidedClientId);
            long collidedPetId = upgraded.sql("""
                    select id from pets where name = 'Mascota legada'
                    """).query(Long.class).single();
            assertThat(collidedPetId).isGreaterThan(50);
            assertThat(upgraded.sql("""
                    select id from pets where name = 'Mascota legada libre'
                    """).query(Long.class).single()).isEqualTo(2);
            assertThat(upgraded.sql("""
                    select id from pets where name = 'Mascota legada hueco'
                    """).query(Long.class).single()).isEqualTo(10);
            assertThat(upgraded.sql("""
                    select client_id from reservations
                    where scheduled_start = '2026-08-10T13:00:00Z'
                    """).query(Long.class).single()).isEqualTo(collidedClientId);
            long collidedReservationId = upgraded.sql("""
                    select id from reservations
                    where scheduled_start = '2026-08-10T13:00:00Z'
                    """).query(Long.class).single();
            assertThat(collidedReservationId).isGreaterThan(50);
            assertThat(upgraded.sql("""
                    select id from reservations
                    where scheduled_start = '2026-08-11T13:00:00Z'
                    """).query(Long.class).single()).isEqualTo(2);
            assertThat(upgraded.sql("""
                    select id from reservations
                    where scheduled_start = '2026-08-12T13:00:00Z'
                    """).query(Long.class).single()).isEqualTo(10);
            assertThat(upgraded.sql("""
                    select count(*) from reservation_items ri
                    join pets p on p.id = ri.pet_id
                    where p.name = 'Mascota legada'
                    """).query(Integer.class).single()).isOne();
            assertThat(upgraded.sql("""
                    select count(*)
                    from reservation_items ri
                    join reservations r on r.id = ri.reservation_id
                    join pets p on p.id = ri.pet_id
                    where r.client_id <> p.client_id
                    """).query(Integer.class).single()).isZero();
            assertThat(upgraded.sql("""
                    select reservation_id || ':' || pet_id
                    from reservation_items
                    order by reservation_id
                    """).query(String.class).list()).containsExactly(
                    "2:2",
                    "10:10",
                    collidedReservationId + ":" + collidedPetId);

            long maxUserBefore = upgraded.sql("select max(id) from users")
                    .query(Long.class).single();
            long maxClientBefore = upgraded.sql("select max(id) from clients")
                    .query(Long.class).single();
            long maxPetBefore = upgraded.sql("select max(id) from pets")
                    .query(Long.class).single();
            long maxReservationBefore = upgraded.sql(
                    "select max(id) from reservations")
                    .query(Long.class).single();
            long maxItemBefore = upgraded.sql(
                    "select max(id) from reservation_items")
                    .query(Long.class).single();

            long nextUserId = upgraded.sql("""
                    insert into users(
                        email_normalized, password_hash, account_type, enabled)
                    values ('after-collision@example.com', null, 'CLIENT', false)
                    returning id
                    """).query(Long.class).single();
            long nextClientId = upgraded.sql("""
                    insert into clients(user_id, name, phone)
                    values (:userId, 'Cliente posterior', '0')
                    returning id
                    """).param("userId", nextUserId).query(Long.class).single();
            long nextPetId = upgraded.sql("""
                    insert into pets(client_id, name, species)
                    values (:clientId, 'Mascota posterior', 'Perro')
                    returning id
                    """).param("clientId", nextClientId)
                    .query(Long.class).single();
            long nextReservationId = upgraded.sql("""
                    insert into reservations(
                        client_id, scheduled_start, scheduled_end, status)
                    values (
                        :clientId, '2027-07-01T13:00:00Z',
                        '2027-07-01T13:30:00Z', 'PENDING')
                    returning id
                    """).param("clientId", nextClientId)
                    .query(Long.class).single();
            long nextItemId = upgraded.sql("""
                    insert into reservation_items(
                        reservation_id, pet_id, service_id,
                        service_name_snapshot)
                    values (
                        :reservationId, :petId, 1, 'Servicio real')
                    returning id
                    """)
                    .param("reservationId", nextReservationId)
                    .param("petId", nextPetId)
                    .query(Long.class).single();
            assertThat(nextUserId).isGreaterThan(maxUserBefore);
            assertThat(nextClientId).isGreaterThan(maxClientBefore);
            assertThat(nextPetId).isGreaterThan(maxPetBefore);
            assertThat(nextReservationId).isGreaterThan(maxReservationBefore);
            assertThat(nextItemId).isGreaterThan(maxItemBefore);
        });
    }

    private List<String> foundationChecksums(JdbcClient database) {
        return database.sql("""
                select version || ':' || checksum
                from flyway_schema_history
                where version in ('1', '2', '3', '4', '5', '6')
                order by installed_rank
                """).query(String.class).list();
    }

    private void createPhaseOneFixture(DataSource dataSource) throws SQLException {
        execute(dataSource, phaseOneTables());
        execute(dataSource, """
                insert into clients(id_client, name, email, phone) values
                    (10, 'Ana', '  ANA@Example.COM ', '+56910000010'),
                    (11, 'Ana duplicada', 'ana@example.com', '+56910000011'),
                    (12, 'Sin email', null, null),
                    (13, 'Email vacío', '   ', '');
                insert into pets(id_pet, name, species, birthdate, breed, id_client)
                values
                    (20, 'Luna', 'Perro', '2020-01-02', 'Mestiza', 10),
                    (21, 'Milo', null, null, null, 11);
                insert into reservations(
                    id_reservation, reservation_date, reservation_time,
                    id_client, service, veterinarian, confirmed)
                values
                    (30, '2026-08-10', '09:00', 10,
                        ' Consulta General ', 'Veterinaria', false),
                    (31, '2026-08-10', '09:00', 10,
                        'Consulta General', 'Veterinaria', true),
                    (32, '2026-08-10', '09:30', 11,
                        '   ', 'Veterinaria', true),
                    (33, '2026-08-10', '09:15', 10,
                        'Consulta General', 'Veterinaria', true),
                    (34, '2026-01-12', '09:00', 11,
                        'Consulta General', 'Veterinaria', false);
                insert into reservation_pets(id_reservation, id_pet) values
                    (30, 20), (31, 20), (32, 21), (33, 20), (34, 21),
                    (30, 21), (30, 20);
                insert into medical_records(id_record, note)
                values (1, 'Debe permanecer hasta la migración de retiro');
                """);
    }

    private void createDstFixture(DataSource dataSource, DstCases cases)
            throws SQLException {
        execute(dataSource, phaseOneTables());
        JdbcClient legacy = JdbcClient.create(dataSource);
        legacy.sql("""
                insert into clients(id_client, name, email, phone)
                values (60, 'Cliente DST', 'dst@example.com', '0')
                """).update();
        legacy.sql("""
                insert into pets(id_pet, name, species, id_client)
                values (60, 'Mascota DST', 'Perro', 60)
                """).update();
        legacy.sql("""
                insert into reservations(
                    id_reservation, reservation_date, reservation_time,
                    id_client, service, veterinarian, confirmed)
                values (
                    60, :date, :time, 60, 'Consulta',
                    'Veterinaria', false)
                """)
                .param("date", cases.gapLocal().toLocalDate())
                .param("time", cases.gapLocal().toLocalTime())
                .update();
        legacy.sql("""
                insert into reservations(
                    id_reservation, reservation_date, reservation_time,
                    id_client, service, veterinarian, confirmed)
                values (
                    61, :date, :time, 60, 'Consulta',
                    'Veterinaria', false)
                """)
                .param("date", cases.overlapLocal().toLocalDate())
                .param("time", cases.overlapLocal().toLocalTime())
                .update();
        legacy.sql("""
                insert into reservation_pets(id_reservation, id_pet)
                values (60, 60), (61, 60)
                """).update();
    }

    private void createCollisionFixture(DataSource dataSource) throws SQLException {
        execute(dataSource, phaseOneTables());
        execute(dataSource, """
                insert into clients(id_client, name, email, phone) values
                    (1, 'Cliente legado colisionado', 'real@example.com', null),
                    (2, 'Cliente legado libre', 'legacy2@example.com', null),
                    (10, 'Cliente legado hueco', 'legacy10@example.com', null);
                insert into pets(id_pet, name, species, id_client) values
                    (1, 'Mascota legada', 'Gato', 1),
                    (2, 'Mascota legada libre', 'Perro', 2),
                    (10, 'Mascota legada hueco', 'Ave', 10);
                insert into reservations(
                    id_reservation, reservation_date, reservation_time,
                    id_client, service, veterinarian, confirmed)
                values
                    (1, '2026-08-10', '09:00', 1,
                        'Consulta', 'Veterinaria', false),
                    (2, '2026-08-11', '09:00', 2,
                        'Consulta', 'Veterinaria', false),
                    (10, '2026-08-12', '09:00', 10,
                        'Consulta', 'Veterinaria', false);
                insert into reservation_pets(id_reservation, id_pet)
                values (1, 1), (2, 2), (10, 10);
                """);
    }

    private DstCases chileDstCases() {
        ZoneRules rules = ZoneId.of("America/Santiago").getRules();
        Instant cursor = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2028-01-01T00:00:00Z");
        ZoneOffsetTransition gap = null;
        ZoneOffsetTransition overlap = null;

        while (cursor.isBefore(end) && (gap == null || overlap == null)) {
            ZoneOffsetTransition transition = rules.nextTransition(cursor);
            if (transition == null || !transition.getInstant().isBefore(end)) {
                break;
            }
            if (transition.isGap() && gap == null) {
                gap = transition;
            } else if (transition.isOverlap() && overlap == null) {
                overlap = transition;
            }
            cursor = transition.getInstant().plusSeconds(1);
        }
        if (gap == null || overlap == null) {
            throw new IllegalStateException(
                    "America/Santiago needs a gap and overlap fixture");
        }

        LocalDateTime gapLocal = gap.getDateTimeBefore()
                .plusSeconds(gap.getDuration().getSeconds() / 2);
        LocalDateTime overlapLocal = overlap.getDateTimeAfter()
                .plusSeconds(Math.abs(overlap.getDuration().getSeconds()) / 2);
        return new DstCases(
                gapLocal,
                gapLocal.toInstant(gap.getOffsetBefore()),
                overlapLocal,
                overlapLocal.toInstant(overlap.getOffsetAfter())
        );
    }

    private String phaseOneTables() {
        return """
                create table clients (
                    id_client integer primary key,
                    name varchar(255) not null,
                    email varchar(255),
                    phone varchar(255)
                );
                create table pets (
                    id_pet integer primary key,
                    name varchar(255) not null,
                    species varchar(255),
                    birthdate date,
                    breed varchar(255),
                    id_client integer not null references clients(id_client)
                );
                create table reservations (
                    id_reservation integer primary key,
                    reservation_date date not null,
                    reservation_time time not null,
                    id_client integer not null references clients(id_client),
                    service varchar(255),
                    veterinarian varchar(255),
                    confirmed boolean
                );
                create table reservation_pets (
                    id_reservation integer not null
                        references reservations(id_reservation),
                    id_pet integer not null references pets(id_pet)
                );
                create table medical_records (
                    id_record integer primary key,
                    note text
                );
                """;
    }

    private Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema("public")
                .schemas("public")
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load();
    }

    private Flyway flyway(DataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema("public")
                .schemas("public")
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .target(MigrationVersion.fromVersion(target))
                .load();
    }

    private void withDatabase(String prefix, DatabaseWork work) throws Exception {
        String databaseName = prefix + "_" + UUID.randomUUID()
                .toString().replace("-", "");
        jdbc.sql("create database " + databaseName).update();
        PGSimpleDataSource isolated = new PGSimpleDataSource();
        isolated.setURL(postgres.getJdbcUrl().replace(
                "/" + postgres.getDatabaseName(), "/" + databaseName));
        isolated.setUser(postgres.getUsername());
        isolated.setPassword(postgres.getPassword());
        try {
            work.run(isolated);
        } finally {
            jdbc.sql("drop database " + databaseName + " with (force)").update();
        }
    }

    private void execute(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @FunctionalInterface
    private interface DatabaseWork {
        void run(DataSource dataSource) throws Exception;
    }

    private record DstCases(
            LocalDateTime gapLocal,
            Instant gapExpected,
            LocalDateTime overlapLocal,
            Instant overlapExpected) {
    }
}
