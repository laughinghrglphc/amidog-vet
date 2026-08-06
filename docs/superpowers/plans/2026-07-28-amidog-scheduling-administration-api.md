# AmiDog Scheduling and Administration API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver collision-safe 30-minute multi-pet booking, flexible Chilean availability, reservation history, service/client/pet management, administrator operations, and in-app notifications.

**Architecture:** Scheduling operates on `Instant` values internally and resolves clinic-local rules through `America/Santiago`. PostgreSQL is the final concurrency authority through a partial range-exclusion constraint, while application services provide readable validation and DTO mapping. Reservations are aggregate roots with item and event rows; cancellation changes status and never deletes history.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring MVC, Spring Data JPA, Flyway, PostgreSQL 17 range constraints, Spring Security sessions, JUnit 5, MockMvc, Testcontainers.

## Global Constraints

- Every reservation occupies exactly 30 minutes total, regardless of pet count.
- Every selected pet has exactly one selected active service.
- `PENDING` and `CONFIRMED` reservations occupy the single veterinarian's calendar.
- Overlapping occupied reservations are impossible, including concurrent requests.
- `CANCELLED` reservations remain stored and release their slot.
- Statuses are exactly `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, and `NO_SHOW`.
- New/client-rescheduled status is controlled by `BOOKING_AUTO_CONFIRM`, default `false`.
- Weekly intervals and full/partial blackout blocks determine availability.
- Clinic-local logic uses `America/Santiago`; persisted schedule values use `timestamptz`.
- There are no medical-record, ecommerce, veterinarian-choice, or message APIs.
- The uploaded project has no `.git`; never initialize Git implicitly.

---

## File Structure

### Catalog

- Create `src/main/java/com/amidog/app/catalog/ServiceOffering.java`.
- Create `src/main/java/com/amidog/app/catalog/ServiceOfferingRepository.java`.
- Create `src/main/java/com/amidog/app/catalog/ServiceCatalogService.java`.
- Create `src/main/java/com/amidog/app/catalog/ServiceController.java`.
- Create `src/main/java/com/amidog/app/catalog/ServiceDtos.java`.

### Client pets and current-client lookup

- Create `src/main/java/com/amidog/app/client/CurrentClient.java`.
- Create `src/main/java/com/amidog/app/client/PetService.java`.
- Create `src/main/java/com/amidog/app/client/PetReservationGuard.java`.
- Create `src/main/java/com/amidog/app/client/ClientProfileService.java`.
- Create `src/main/java/com/amidog/app/client/ClientController.java`.
- Create `src/main/java/com/amidog/app/client/ClientDtos.java`.

### Scheduling

- Create `src/main/java/com/amidog/app/scheduling/WeeklyAvailability.java`.
- Create `src/main/java/com/amidog/app/scheduling/WeeklyAvailabilityRepository.java`.
- Create `src/main/java/com/amidog/app/scheduling/AvailabilityBlock.java`.
- Create `src/main/java/com/amidog/app/scheduling/AvailabilityBlockRepository.java`.
- Create `src/main/java/com/amidog/app/scheduling/ClinicTime.java`.
- Create `src/main/java/com/amidog/app/scheduling/AvailabilityService.java`.
- Create `src/main/java/com/amidog/app/scheduling/OccupiedSlotReader.java`.
- Create `src/main/java/com/amidog/app/scheduling/AvailabilityController.java`.
- Create `src/main/java/com/amidog/app/scheduling/AvailabilityDtos.java`.
- Create `src/main/java/com/amidog/app/scheduling/AdminAvailabilityController.java`.

### Reservations

- Create `src/main/java/com/amidog/app/reservation/ReservationStatus.java`.
- Create `src/main/java/com/amidog/app/reservation/Reservation.java`.
- Create `src/main/java/com/amidog/app/reservation/ReservationItem.java`.
- Create `src/main/java/com/amidog/app/reservation/ReservationEvent.java`.
- Create `src/main/java/com/amidog/app/reservation/ReservationRepository.java`.
- Create `src/main/java/com/amidog/app/reservation/ReservationItemRepository.java`.
- Create `src/main/java/com/amidog/app/reservation/ReservationEventRepository.java`.
- Create `src/main/java/com/amidog/app/reservation/ReservationService.java`.
- Create `src/main/java/com/amidog/app/reservation/ReservationController.java`.
- Create `src/main/java/com/amidog/app/reservation/ReservationDtos.java`.

### Notifications

- Create `src/main/java/com/amidog/app/notification/Notification.java`.
- Create `src/main/java/com/amidog/app/notification/NotificationType.java`.
- Create `src/main/java/com/amidog/app/notification/NotificationRepository.java`.
- Create `src/main/java/com/amidog/app/notification/NotificationService.java`.
- Create `src/main/java/com/amidog/app/notification/NotificationController.java`.
- Create `src/main/java/com/amidog/app/notification/NotificationDtos.java`.

### Administrator

- Create `src/main/java/com/amidog/app/admin/AdminDashboardService.java`.
- Create `src/main/java/com/amidog/app/admin/AdminDashboardController.java`.
- Create `src/main/java/com/amidog/app/admin/AdminReservationController.java`.
- Create `src/main/java/com/amidog/app/admin/AdminClientController.java`.
- Create `src/main/java/com/amidog/app/admin/AdminPetController.java`.
- Create `src/main/java/com/amidog/app/admin/AdminDtos.java`.

### Database

- Treat applied foundation migrations V1 through V6 as immutable.
- Create `src/main/resources/db/migration/V7__scheduling_and_reservations.sql`.
- Create `src/main/resources/db/migration/V8__import_legacy_phase1.sql`.
- Create `src/main/resources/db/migration/V9__reservation_overlap_constraint.sql`.
- After Task 9's explicit backup/export and operator-approval checkpoint, create
  `src/main/resources/db/migration/V10__retire_legacy_domains.sql`.
- Create `src/test/java/com/amidog/app/support/TestRows.java`.
- Create `src/test/java/com/amidog/app/support/DomainTestFixtures.java`.

## Task 1: Scheduling and Reservation Schema

**Files:**

- Create: `src/main/resources/db/migration/V7__scheduling_and_reservations.sql`
- Create: `src/main/resources/db/migration/V8__import_legacy_phase1.sql`
- Create: `src/main/resources/db/migration/V9__reservation_overlap_constraint.sql`
- Do not modify: frozen V1 through V6
- Modify: `src/main/resources/application.yaml`
- Create: `src/test/java/com/amidog/app/migration/SchedulingMigrationIntegrationTests.java`

**Interfaces:**

- Produces: all scheduling, reservation, event, service, and notification tables plus the database overlap guarantee.

`TestRows.verifiedClient(JdbcClient)` inserts a unique verified `users` row and its `clients` row with `returning id`. `DomainTestFixtures` wraps repeatable service, pet, weekly-hours, block, and reservation builders used by later integration tests. Both live under `src/test/java/com/amidog/app/support` and never enter the production artifact.

- [ ] **Step 1: Write the failing database-constraint test**

```java
class SchedulingMigrationIntegrationTests extends PostgresIntegrationTest {
    @Autowired JdbcClient jdbc;

    @Test
    void rejectsTwoOccupiedReservationsThatOverlap() {
        long clientId = TestRows.verifiedClient(jdbc);
        jdbc.sql("""
            insert into reservations(client_id, scheduled_start, scheduled_end, status)
            values (:client, '2026-08-10T13:00:00Z', '2026-08-10T13:30:00Z', 'PENDING')
            """).param("client", clientId).update();

        assertThatThrownBy(() -> jdbc.sql("""
            insert into reservations(client_id, scheduled_start, scheduled_end, status)
            values (:client, '2026-08-10T13:15:00Z', '2026-08-10T13:45:00Z', 'CONFIRMED')
            """).param("client", clientId).update())
            .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void permitsAReplacementAfterCancellation() {
        long clientId = TestRows.verifiedClient(jdbc);
        long id = jdbc.sql("""
            insert into reservations(client_id, scheduled_start, scheduled_end, status)
            values (:client, '2026-08-10T13:00:00Z', '2026-08-10T13:30:00Z', 'PENDING')
            returning id
            """).param("client", clientId).query(Long.class).single();
        jdbc.sql("update reservations set status='CANCELLED' where id=:id")
            .param("id", id).update();

        assertThatCode(() -> jdbc.sql("""
            insert into reservations(client_id, scheduled_start, scheduled_end, status)
            values (:client, '2026-08-10T13:00:00Z', '2026-08-10T13:30:00Z', 'PENDING')
            """).param("client", clientId).update()).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run and verify missing-table failure**

Run:

```powershell
.\mvnw.cmd -Dtest=SchedulingMigrationIntegrationTests test
```

Expected: FAIL because `reservations` does not exist.

- [ ] **Step 3: Create V7 domain tables**

`V7__scheduling_and_reservations.sql` contains:

```sql
create table services (
    id bigint generated by default as identity primary key,
    code varchar(60) not null unique,
    name varchar(100) not null,
    description varchar(500),
    active boolean not null default true,
    display_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create table weekly_availability (
    id bigint generated by default as identity primary key,
    day_of_week smallint not null check (day_of_week between 1 and 7),
    local_start_time time not null,
    local_end_time time not null,
    active boolean not null default true,
    check (local_end_time > local_start_time),
    unique (day_of_week, local_start_time, local_end_time)
);

create table availability_blocks (
    id bigint generated by default as identity primary key,
    start_at timestamptz not null,
    end_at timestamptz not null,
    reason varchar(200),
    created_at timestamptz not null default now(),
    check (end_at > start_at)
);
create index availability_blocks_range_idx on availability_blocks(start_at, end_at);

create table reservations (
    id bigint generated by default as identity primary key,
    client_id bigint not null references clients(id),
    scheduled_start timestamptz not null,
    scheduled_end timestamptz not null,
    status varchar(20) not null check (
        status in ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    client_note varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    cancelled_at timestamptz,
    cancelled_by varchar(20),
    cancellation_reason varchar(300),
    version bigint not null default 0,
    check (scheduled_end = scheduled_start + interval '30 minutes')
);
create index reservations_client_start_idx on reservations(client_id, scheduled_start desc);
create index reservations_status_start_idx on reservations(status, scheduled_start);

create table reservation_items (
    id bigint generated by default as identity primary key,
    reservation_id bigint not null references reservations(id) on delete cascade,
    pet_id bigint not null references pets(id),
    service_id bigint not null references services(id),
    service_name_snapshot varchar(100) not null,
    unique (reservation_id, pet_id)
);

create table reservation_events (
    id bigint generated by default as identity primary key,
    reservation_id bigint not null references reservations(id) on delete cascade,
    event_type varchar(30) not null,
    actor_type varchar(20) not null,
    previous_status varchar(20),
    new_status varchar(20),
    previous_start timestamptz,
    new_start timestamptz,
    reason varchar(300),
    created_at timestamptz not null default now()
);
create index reservation_events_reservation_idx
    on reservation_events(reservation_id, created_at);

create table notifications (
    id bigint generated by default as identity primary key,
    recipient_user_id bigint not null references users(id) on delete cascade,
    type varchar(40) not null,
    title varchar(120) not null,
    body varchar(500) not null,
    reservation_id bigint references reservations(id),
    deduplication_key varchar(160) not null unique,
    read_at timestamptz,
    created_at timestamptz not null default now()
);
create index notifications_recipient_idx
    on notifications(recipient_user_id, read_at, created_at desc);
```

- [ ] **Step 4: Preserve legacy Phase 1 data and add the final constraint**

Keep Flyway baseline version `0` for a non-empty pre-Flyway development schema.
Frozen V1 already conditionally renames the old tables to `legacy_clients`,
`legacy_pets`, `legacy_reservations`, and `legacy_reservation_pets`; never edit
that applied migration.

`V8__import_legacy_phase1.sql` conditionally:

1. inserts disabled, unverified `CLIENT` users with `password_hash = null`;
2. copies clients and pets while retaining their legacy numeric IDs;
3. creates one active service row per distinct trimmed legacy service;
4. converts date/time through `AT TIME ZONE 'America/Santiago'`;
5. copies the earliest occupied reservation in each duplicate legacy block as `PENDING` or `CONFIRMED`;
6. copies later collisions as `CANCELLED` with `cancellation_reason = 'Conflicto detectado durante migración Phase 1'`;
7. copies reservation-pet rows into `reservation_items`;
8. advances identity sequences with `setval`.

`V9__reservation_overlap_constraint.sql` contains:

```sql
alter table reservations
    add constraint reservations_no_occupied_overlap
    exclude using gist (
        tstzrange(scheduled_start, scheduled_end, '[)') with &&
    )
    where (status in ('PENDING', 'CONFIRMED'));
```

- [ ] **Step 5: Pass migration tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=FlywayMigrationIntegrationTests,SchedulingMigrationIntegrationTests test
if (Test-Path .git) {
  git add src/main/resources src/test/java/com/amidog/app/migration
  git commit -m "feat: add scheduling and reservation schema"
}
```

Expected: PASS for a fresh schema and for a real upgrade that first applies the
frozen V1-V6 history, then V7-V9. The upgrade fixture begins with the real
Phase 1 tables/data and proves legacy import, duration checks, occupied overlap
rejection, cancelled-slot reuse, and unchanged V1-V6 Flyway checksums.

## Task 2: Service Catalog and Authenticated Pet Management

**Files:**

- Create: catalog files listed above
- Create: `src/main/java/com/amidog/app/client/CurrentClient.java`
- Create: `src/main/java/com/amidog/app/client/PetService.java`
- Create: `src/main/java/com/amidog/app/client/PetReservationGuard.java`
- Create: `src/main/java/com/amidog/app/client/ClientProfileService.java`
- Create: `src/main/java/com/amidog/app/client/ClientController.java`
- Create: `src/main/java/com/amidog/app/client/ClientDtos.java`
- Create: `src/test/java/com/amidog/app/catalog/ServiceCatalogIntegrationTests.java`
- Create: `src/test/java/com/amidog/app/client/ClientPetIntegrationTests.java`

**Interfaces:**

- Produces: public active services, administrator service CRUD/archive, and session-owned client profile/pet CRUD/archive.

- [ ] **Step 1: Write ownership and archive API tests**

```java
@Test
void clientCanOnlyReadAndEditOwnPets() throws Exception {
    SessionFixture ana = accounts.verifiedClient("ana@example.com");
    long anaPet = pets.create(ana.clientId(), "Milo", "Gato");
    long otherPet = pets.create(accounts.verifiedClient("bob@example.com").clientId(), "Luna", "Perro");

    mvc.perform(get("/api/v1/me/pets").session(ana.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(anaPet))
        .andExpect(jsonPath("$", hasSize(1)));

    mvc.perform(patch("/api/v1/me/pets/{id}", otherPet)
            .session(ana.session()).with(csrf())
            .contentType(APPLICATION_JSON)
            .content("""{"name":"Intruso","species":"Gato"}"""))
        .andExpect(status().isNotFound());
}

@Test
void archivedServiceDisappearsFromPublicCatalogButRemainsStored() throws Exception {
    long id = services.create("consulta-general", "Consulta general");
    mvc.perform(delete("/api/v1/admin/services/{id}", id)
            .session(accounts.adminSession()).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/services"))
        .andExpect(jsonPath("$", hasSize(0)));
    assertThat(serviceRepository.findById(id)).get()
        .extracting(ServiceOffering::isActive).isEqualTo(false);
}
```

- [ ] **Step 2: Run and verify `404`**

Run:

```powershell
.\mvnw.cmd -Dtest=ServiceCatalogIntegrationTests,ClientPetIntegrationTests test
```

Expected: FAIL because catalog and client controllers do not exist.

- [ ] **Step 3: Implement DTOs and services**

```java
public record ServiceResponse(Long id, String code, String name,
                              String description, boolean active, int displayOrder) {}
public record ServiceCreateRequest(
        @NotBlank @Size(max=60) String code,
        @NotBlank @Size(max=100) String name,
        @Size(max=500) String description,
        @Min(0) int displayOrder) {}
public record ServiceUpdateRequest(
        @NotBlank @Size(max=100) String name,
        @Size(max=500) String description,
        @Min(0) int displayOrder,
        boolean active) {}
```

```java
public record PetResponse(Long id, String name, String species,
                          String breed, LocalDate birthdate, boolean active) {}
public record PetWriteRequest(
        @NotBlank @Size(max=80) String name,
        @NotBlank @Size(max=40) String species,
        @Size(max=80) String breed,
        @PastOrPresent LocalDate birthdate) {}
```

`CurrentClient.require(AccountPrincipal)` resolves `ClientRepository.findByUserId(principal.userId())` or throws `NotFoundException`. `PetService` always queries by both pet ID and current client ID; foreign IDs deliberately return `404`.

Service codes are normalized to lowercase kebab-case and remain immutable after creation. Archive sets `active=false`; update can reactivate a service explicitly through `PATCH`.

- [ ] **Step 4: Implement controllers and archive guard**

Expose the approved endpoints. `PetReservationGuard` uses `JdbcClient` so this task does not depend on reservation JPA classes created later:

```java
public boolean hasFutureOccupied(long petId, Instant now) {
    return jdbc.sql("""
        select exists(
          select 1 from reservation_items ri
          join reservations r on r.id = ri.reservation_id
          where ri.pet_id = :petId
            and r.status in ('PENDING', 'CONFIRMED')
            and r.scheduled_start > :now
        )
        """)
        .param("petId", petId)
        .param("now", now)
        .query(Boolean.class)
        .single();
}
```

Before archiving:

```java
boolean hasFutureOccupied = petReservationGuard.hasFutureOccupied(petId, clock.instant());
if (hasFutureOccupied) {
    throw new ConflictException("PET_HAS_FUTURE_RESERVATION",
        "Cancela o resuelve primero las reservas futuras de esta mascota.");
}
pet.archive(clock.instant());
```

Run:

```powershell
.\mvnw.cmd -Dtest=ServiceCatalogIntegrationTests,ClientPetIntegrationTests test
```

Expected: PASS for validation, role enforcement, ownership, service archive/reactivation, and pet future-reservation guard.

- [ ] **Step 5: Checkpoint**

```powershell
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/catalog src/main/java/com/amidog/app/client src/test/java/com/amidog/app/catalog src/test/java/com/amidog/app/client
  git commit -m "feat: add service catalog and client pet management"
}
```

## Task 3: Chilean Availability and Blackout Administration

**Files:**

- Create: scheduling files listed above
- Create: `src/main/java/com/amidog/app/scheduling/OccupiedSlotReader.java`
- Create: `src/test/java/com/amidog/app/scheduling/ClinicTimeTests.java`
- Create: `src/test/java/com/amidog/app/scheduling/AvailabilityIntegrationTests.java`

**Interfaces:**

- Produces: `AvailabilityService.availableSlots(LocalDate, LocalDate)`, public `GET /api/v1/availability`, and administrator weekly/block endpoints.

- [ ] **Step 1: Write slot and timezone tests**

```java
@Test
void generatesThirtyMinuteSlotsInSantiagoAndRemovesABlackout() {
    weekly.save(WeeklyAvailability.active(MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)));
    blocks.save(AvailabilityBlock.create(
        instant("2026-08-10T09:30:00-04:00"),
        instant("2026-08-10T10:00:00-04:00"), "Cirugía", now));

    assertThat(service.availableSlots(date("2026-08-10"), date("2026-08-10")))
        .extracting(slot -> slot.startsAt().toLocalTime())
        .containsExactly(time("09:00"), time("10:00"), time("10:30"));
}

@Test
void usesTheOffsetDefinedBySantiagoRules() {
    assertThat(clinicTime.resolve(dateTime("2026-01-12T09:00")).getOffset())
        .isEqualTo(ZoneOffset.ofHours(-3));
    assertThat(clinicTime.resolve(dateTime("2026-07-13T09:00")).getOffset())
        .isEqualTo(ZoneOffset.ofHours(-4));
}
```

- [ ] **Step 2: Run and verify missing scheduling types**

Run:

```powershell
.\mvnw.cmd -Dtest=ClinicTimeTests,AvailabilityIntegrationTests test
```

Expected: compilation FAIL.

- [ ] **Step 3: Implement deterministic clinic-time policy**

```java
@Component
public class ClinicTime {
    private final ZoneId zone;

    public ClinicTime(AmidogProperties properties) {
        this.zone = properties.clinicZone();
    }

    public ZonedDateTime resolve(LocalDateTime local) {
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) {
            throw new ConflictException("NONEXISTENT_LOCAL_TIME",
                "La hora no existe por el cambio de horario en Chile.");
        }
        return ZonedDateTime.ofLocal(local, zone, offsets.getFirst());
    }

    public LocalDate localDate(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }
}
```

For an ambiguous local time, `offsets.getFirst()` deliberately chooses the earlier occurrence. Nonexistent local times are rejected. Add boundary tests using actual `ZoneRules` transitions rather than fixed transition dates.

- [ ] **Step 4: Implement slot generation and administrator mutations**

`AvailabilityService` reads occupied ranges through a JDBC projection so it remains independent from the reservation aggregate introduced in Task 4:

```java
@Repository
public class OccupiedSlotReader {
    private final JdbcClient jdbc;

    public OccupiedSlotReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean overlaps(Instant start, Instant end) {
        return jdbc.sql("""
            select exists(
              select 1 from reservations
              where status in ('PENDING', 'CONFIRMED')
                and scheduled_start < :end
                and scheduled_end > :start
            )
            """)
            .param("start", start)
            .param("end", end)
            .query(Boolean.class)
            .single();
    }
}
```

For each local date:

1. read active intervals for its ISO day number;
2. walk from local start while `start + 30 minutes <= local end`;
3. skip nonexistent local timestamps;
4. discard starts before `now + minimumNoticeHours`;
5. discard dates beyond `today + horizonDays`;
6. discard overlap with a block;
7. discard overlap with `PENDING`/`CONFIRMED` reservations.

Public response:

```java
public record AvailableSlotResponse(OffsetDateTime startsAt, OffsetDateTime endsAt) {}
```

Administrator writes use:

```java
public record WeeklyIntervalRequest(
        @Min(1) @Max(7) int dayOfWeek,
        @NotNull LocalTime start,
        @NotNull LocalTime end,
        boolean active) {}

public record AvailabilityBlockRequest(
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @Size(max=200) String reason) {}
```

Creating a block queries occupied overlaps first and returns `409` with conflict code `BLOCK_OVERLAPS_RESERVATIONS` and reservation IDs. Changing weekly hours does not modify reservations.

- [ ] **Step 5: Pass tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=ClinicTimeTests,AvailabilityIntegrationTests test
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/scheduling src/test/java/com/amidog/app/scheduling
  git commit -m "feat: add Chilean availability and blackout controls"
}
```

Expected: PASS for split shifts, min notice, horizon, partial/full-day block, existing reservations, and DST policy.

## Task 4: Multi-Pet Reservation Creation and Database Collision Handling

**Files:**

- Create: core reservation files listed above
- Create: `src/test/java/com/amidog/app/reservation/ReservationCreationIntegrationTests.java`
- Create: `src/test/java/com/amidog/app/reservation/ReservationConcurrencyIntegrationTests.java`

**Interfaces:**

- Produces: `POST /api/v1/me/reservations`, `GET /api/v1/me/reservations`, and a stable reservation DTO.

- [ ] **Step 1: Write multi-pet and collision tests**

```java
@Test
void createsOneThirtyMinuteReservationWithAServiceForEachPet() throws Exception {
    SessionFixture ana = accounts.verifiedClient("ana@example.com");
    long milo = pets.create(ana.clientId(), "Milo", "Gato");
    long luna = pets.create(ana.clientId(), "Luna", "Perro");
    long consult = services.create("consulta", "Consulta general");
    long vaccine = services.create("vacuna", "Vacunación");
    availability.openMonday("09:00", "12:00");

    mvc.perform(post("/api/v1/me/reservations")
            .session(ana.session()).with(csrf())
            .contentType(APPLICATION_JSON)
            .content("""
                {"startsAt":"2026-08-10T10:00:00-04:00",
                 "items":[
                   {"petId":%d,"serviceId":%d},
                   {"petId":%d,"serviceId":%d}
                 ],
                 "note":"Control anual"}
                """.formatted(milo, consult, luna, vaccine)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.items", hasSize(2)));
}

@Test
void concurrentRequestsProduceOneCreatedAndOneConflict() {
    List<Integer> statuses = concurrentBookSameSlot();
    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    assertThat(reservations.countOccupiedAt(slotStart)).isEqualTo(1);
}
```

- [ ] **Step 2: Run and verify missing reservation API**

Run:

```powershell
.\mvnw.cmd -Dtest=ReservationCreationIntegrationTests,ReservationConcurrencyIntegrationTests test
```

Expected: FAIL with `404`.

- [ ] **Step 3: Implement the aggregate and request contract**

```java
public enum ReservationStatus {
    PENDING, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW
}
```

```java
public record CreateReservationRequest(
        @NotNull OffsetDateTime startsAt,
        @NotEmpty @Size(max=10) List<@Valid ReservationItemRequest> items,
        @Size(max=500) String note) {}

public record ReservationItemRequest(@NotNull Long petId, @NotNull Long serviceId) {}

public record ReservationResponse(
        Long id, OffsetDateTime startsAt, OffsetDateTime endsAt,
        ReservationStatus status, String note,
        List<ReservationItemResponse> items) {}
```

The service rejects duplicate pet IDs, foreign/inactive pets, inactive/missing services, non-slot-aligned starts, starts absent from `AvailabilityService`, and requests beyond ten pets.

- [ ] **Step 4: Persist atomically and translate the constraint**

Inside one transaction:

```java
ReservationStatus initial = properties.booking().autoConfirm()
        ? ReservationStatus.CONFIRMED
        : ReservationStatus.PENDING;
Instant start = request.startsAt().toInstant();
Reservation reservation = reservations.saveAndFlush(
        Reservation.create(client, start, start.plus(30, MINUTES),
            initial, sanitize(request.note()), clock.instant()));
for (ReservationItemRequest item : request.items()) {
    ServiceOffering service = activeService(item.serviceId());
    items.save(ReservationItem.create(
        reservation, ownedActivePet(client.id(), item.petId()),
        service, service.getName()));
}
events.save(ReservationEvent.created(reservation, "CLIENT", clock.instant()));
```

Catch a root PostgreSQL SQL state `23P01` from `DataIntegrityViolationException` and throw:

```java
new ConflictException("SLOT_ALREADY_BOOKED",
    "Ese horario acaba de ser reservado. Elige otro bloque disponible.");
```

The endpoint returns `Location: /api/v1/me/reservations/{id}` and `201`.

- [ ] **Step 5: Pass both auto-confirm branches and concurrency tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ReservationCreationIntegrationTests,ReservationConcurrencyIntegrationTests test
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/reservation src/test/java/com/amidog/app/reservation
  git commit -m "feat: add collision-safe multi-pet reservations"
}
```

Expected: PASS with `BOOKING_AUTO_CONFIRM=false` and a nested test context overriding it to `true`.

## Task 5: Cancellation, Rescheduling, Status Transitions, and Audit History

**Files:**

- Modify: reservation domain/service/controller files
- Create: `src/test/java/com/amidog/app/reservation/ReservationLifecycleIntegrationTests.java`
- Create: `src/main/java/com/amidog/app/admin/AdminReservationController.java`

**Interfaces:**

- Produces: client cancel/reschedule and administrator status/reschedule operations with immutable event history.

- [ ] **Step 1: Write lifecycle matrix tests**

```java
@ParameterizedTest
@CsvSource({
    "PENDING,CONFIRMED,true",
    "PENDING,CANCELLED,true",
    "CONFIRMED,CANCELLED,true",
    "CONFIRMED,COMPLETED,true",
    "CONFIRMED,NO_SHOW,true",
    "PENDING,COMPLETED,false",
    "CANCELLED,CONFIRMED,false",
    "COMPLETED,CANCELLED,false"
})
void enforcesTheStatusMatrix(String from, String to, boolean allowed) {
    Reservation reservation = fixture.inStatus(valueOf(from));
    if (allowed) {
        assertThatCode(() -> reservation.transitionTo(valueOf(to), now)).doesNotThrowAnyException();
    } else {
        assertThatThrownBy(() -> reservation.transitionTo(valueOf(to), now))
            .isInstanceOf(ConflictException.class);
    }
}
```

Add API tests asserting cancellation retains the row, releases the slot, stores actor/reason/time, and appends one event.

- [ ] **Step 2: Run and verify missing transitions**

Run:

```powershell
.\mvnw.cmd -Dtest=ReservationLifecycleIntegrationTests test
```

Expected: FAIL because lifecycle methods/endpoints are absent.

- [ ] **Step 3: Implement explicit transition rules**

```java
private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED = Map.of(
    PENDING, Set.of(CONFIRMED, CANCELLED),
    CONFIRMED, Set.of(CANCELLED, COMPLETED, NO_SHOW),
    CANCELLED, Set.of(),
    COMPLETED, Set.of(),
    NO_SHOW, Set.of()
);
```

Client cancel accepts `{"reason":"..."}` and only acts on the current client's future `PENDING`/`CONFIRMED` reservation. Administrator status accepts:

```java
public record StatusChangeRequest(
        @NotNull ReservationStatus status,
        @Size(max=300) String reason) {}
```

Every successful mutation appends a `ReservationEvent` with actor `CLIENT` or `ADMIN`.

- [ ] **Step 4: Implement safe rescheduling**

```java
public record RescheduleRequest(@NotNull OffsetDateTime startsAt) {}
```

Client reschedule:

- reuses `AvailabilityService` validation;
- changes status to the configured initial status;
- records old/new start and previous/new status.

Administrator reschedule:

- reuses availability validation;
- preserves `CONFIRMED` if the old status is `CONFIRMED`;
- preserves `PENDING` otherwise.

Both flush inside the transaction so a database collision becomes `SLOT_ALREADY_BOOKED`.

- [ ] **Step 5: Pass tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=ReservationLifecycleIntegrationTests test
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/reservation src/main/java/com/amidog/app/admin src/test/java/com/amidog/app/reservation
  git commit -m "feat: add reservation lifecycle and audit history"
}
```

Expected: PASS for every allowed/rejected transition, both reschedule actors, cancellation retention, and conflict rollback.

## Task 6: In-App Operational Notifications

**Files:**

- Create: notification files listed above
- Modify: reservation services
- Create: `src/test/java/com/amidog/app/notification/NotificationIntegrationTests.java`

**Interfaces:**

- Produces: idempotent event notifications plus client/admin list/read APIs.

- [ ] **Step 1: Write trigger and read-state tests**

```java
@Test
void createsOnlyOneAdminNotificationForANewReservation() {
    Reservation reservation = booking.create(request);
    notificationService.create(
        adminUserId,
        NEW_RESERVATION,
        "Nueva reserva",
        "Reserva recibida",
        reservation.getId(),
        "NEW_RESERVATION:" + reservation.getId());

    assertThat(notifications.findAllByReservationId(reservation.getId()))
        .filteredOn(item -> item.getType() == NEW_RESERVATION)
        .singleElement();
}

@Test
void clientCannotMarkAnotherUsersNotificationRead() throws Exception {
    mvc.perform(patch("/api/v1/me/notifications/{id}/read", otherNotification)
            .session(ana.session()).with(csrf()))
        .andExpect(status().isNotFound());
}
```

- [ ] **Step 2: Run and verify missing notification implementation**

Run:

```powershell
.\mvnw.cmd -Dtest=NotificationIntegrationTests test
```

Expected: compilation FAIL.

- [ ] **Step 3: Implement notification creation and deduplication**

```java
public enum NotificationType {
    NEW_RESERVATION,
    CLIENT_CANCELLED,
    CLIENT_RESCHEDULED,
    RESERVATION_CONFIRMED,
    ADMIN_CANCELLED,
    ADMIN_RESCHEDULED,
    APPOINTMENT_REMINDER
}
```

`NotificationService.create(...)` uses a deterministic key such as:

```java
String key = "%s:%d:%d".formatted(type, reservationId, recipientUserId);
```

It returns the existing row when the unique key already exists. Insert notifications in the same business transaction as the event that caused them.

- [ ] **Step 4: Implement recipient-scoped list/read endpoints**

```java
public record NotificationResponse(
        Long id, NotificationType type, String title, String body,
        Long reservationId, Instant createdAt, boolean unread) {}
```

Clients resolve their own user ID from `AccountPrincipal`. Administrator routes use the current admin user ID. `read-all` updates only that recipient's unread rows.

- [ ] **Step 5: Pass tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=NotificationIntegrationTests test
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/notification src/main/java/com/amidog/app/reservation src/test/java/com/amidog/app/notification
  git commit -m "feat: add operational notifications"
}
```

Expected: PASS for all initial triggers, ownership, individual/read-all, and deduplication.

## Task 7: Administrator Dashboard, Client, Pet, Reservation, and Availability APIs

**Files:**

- Create: administrator files listed above
- Modify: `src/main/java/com/amidog/app/scheduling/AdminAvailabilityController.java`
- Create: `src/test/java/com/amidog/app/admin/AdminApiIntegrationTests.java`

**Interfaces:**

- Produces: the complete `/api/v1/admin/**` contract consumed by the attached dashboard.

- [ ] **Step 1: Write administrator projection and authorization tests**

```java
@Test
void dashboardReturnsTodayCountsAndUpcomingAgendaInClinicTime() throws Exception {
    fixtures.bookingAt("2026-08-10T09:00:00-04:00", CONFIRMED);

    mvc.perform(get("/api/v1/admin/dashboard")
            .session(accounts.adminSession()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats.todayAppointments").value(1))
        .andExpect(jsonPath("$.appointments[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$.appointments[0].items[0].petName").exists());
}

@Test
void clientRoleCannotOpenAdministratorRoutes() throws Exception {
    mvc.perform(get("/api/v1/admin/dashboard")
            .session(accounts.verifiedClient("ana@example.com").session()))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run and verify `404`**

Run:

```powershell
.\mvnw.cmd -Dtest=AdminApiIntegrationTests test
```

Expected: FAIL because the dashboard endpoints are absent.

- [ ] **Step 3: Implement explicit dashboard DTO projections**

```java
public record DashboardResponse(
        AdminProfile profile,
        DashboardStats stats,
        List<AdminReservationSummary> appointments,
        List<ServiceUsage> services,
        List<ChartSeries> chart,
        List<AgendaItem> schedule) {}

public record DashboardStats(long todayAppointments, long clients, long pets) {}
```

Repository projections count active clients/pets, group reservation items by service snapshot, and group reservations by clinic-local date. Never return JPA entities.

`AdminProfile` reads the display name from `AmidogProperties.admin().name()` and the email from the authenticated administrator account. No `clients` row exists for the administrator, so dashboard client counts remain accurate.

- [ ] **Step 4: Complete filterable administrator routes**

Implement:

- reservations filtered by `from`, `to`, `status`, and search text;
- client list/detail and active/name/phone edit;
- pet list/detail and name/species/breed/birthdate edit;
- service CRUD/archive from Task 2;
- weekly interval replacement and blackout CRUD from Task 3;
- notification routes from Task 6.

Pagination parameters are `page` (zero-based) and `size` (default 25, maximum 100). Invalid ranges return `400`.

- [ ] **Step 5: Pass tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=AdminApiIntegrationTests test
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/admin src/main/java/com/amidog/app/scheduling src/test/java/com/amidog/app/admin
  git commit -m "feat: add administrator management APIs"
}
```

Expected: PASS for all roles, filters, pagination, aggregate counts, Chilean date grouping, and response DTOs.

## Task 8: API Regression and Phase 1 Cleanup

**Files:**

- Delete: remaining Phase 1 reservation controller/service/DTO/tests
- Modify: `src/main/java/com/amidog/app/common/api/ApiExceptionHandler.java`
- Modify: `README.md`
- Create: `docs/api.md`

**Interfaces:**

- Produces: one coherent `/api/v1` backend with no anonymous booking or medical/ecommerce surface.

- [ ] **Step 1: Add final error-envelope regression assertions**

Assert:

```json
{
  "code": "SLOT_ALREADY_BOOKED",
  "message": "Ese horario acaba de ser reservado. Elige otro bloque disponible.",
  "errors": {}
}
```

Cover `400`, `401`, `403`, `404`, `409`, `429`, and unexpected `500` without exposing stack traces or SQL.

- [ ] **Step 2: Remove retired code and public Phase 1 route**

Delete `/reservation` and its request shape after the React booking flow has a tracked frontend task for `/api/v1/me/reservations`. Search:

```powershell
rg -n 'MedicalRecord|Consultation|ProcedureRecord|Cart|Purchase|Product|/reservation\b|confirmed\b' src/main src/test
```

Expected: no retired-domain hits outside Flyway legacy-import comments and documentation.

- [ ] **Step 3: Run the complete backend suite**

Run:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
```

Expected: all tests PASS and the production JAR builds.

- [ ] **Step 4: Document API payloads and configuration**

`docs/api.md` includes exact requests/responses for authentication, pets, services, availability, create/list/cancel/reschedule reservations, admin status changes, blackouts, and notifications. `README.md` links the API document and explains `BOOKING_AUTO_CONFIRM`.

- [ ] **Step 5: Checkpoint**

```powershell
if (Test-Path .git) {
  git add -A
  git commit -m "refactor: complete scheduling and administration backend"
}
```

## Task 9: Approved Legacy Database Retirement

**Files:**

- Create only after approval:
  `src/main/resources/db/migration/V10__retire_legacy_domains.sql`
- Create:
  `src/test/java/com/amidog/app/migration/LegacyDomainRetirementIntegrationTests.java`
- Modify: deployment/backup runbook

**Mandatory operator checkpoint (before V10 enters a deployed release):**

1. stop writes or take a transactionally consistent PostgreSQL backup;
2. export the legacy medical/ecommerce and Phase 1 booking tables separately;
3. validate V8 import row counts, representative client/pet/reservation records,
   timestamps, and reservation-item links in a staging restore;
4. record the backup location, restore-drill result, retention period, and the
   clinic owner's explicit approval;
5. only then include and deploy V10. There is no automatic or silent approval.

The retention policy is: new `users`, `clients`, `pets`, `reservations`, and
`reservation_items` rows become authoritative after validation. Old
`legacy_clients`, `legacy_pets`, `legacy_reservations`, and
`legacy_reservation_pets` tables, plus the out-of-scope medical/ecommerce graph,
remain in the approved backup/export, not in the live final schema.

- [ ] **Step 1: Write fresh-install and real Phase 1 upgrade tests**

The fresh-install test proves V10 is harmless when optional legacy tables are
absent. The upgrade test starts with the real Phase 1 schema and linked sample
data, applies frozen V1-V6 and V7-V9, validates imported data, then applies V10
and proves new booking data remains while every retired table is absent.

- [ ] **Step 2: Create the forward-only V10 migration**

Use explicit conditional drops in foreign-key-safe dependent-first order:

1. `procedure_records`;
2. `procedures`;
3. `consultations`;
4. `medical_records`;
5. purchase/cart join tables if present, then `purchases`, then `carts`;
6. product join/dependent tables if present, then `products`;
7. `legacy_reservation_pets`;
8. `legacy_reservations`;
9. `legacy_pets`;
10. `legacy_clients`.

Do not use `CASCADE`: enumerate known dependencies so an unexpected downstream
table fails the migration and protects data. Use PostgreSQL catalog checks for
optional legacy variants and document every supported Phase 1 table name.

- [ ] **Step 3: Run both migration paths**

```powershell
.\mvnw.cmd -Dtest=FlywayMigrationIntegrationTests,SchedulingMigrationIntegrationTests,LegacyDomainRetirementIntegrationTests test
```

Expected: PASS for fresh installation and the real Phase 1-to-final upgrade,
with V1-V6 checksums unchanged and backup/approval evidence recorded.
