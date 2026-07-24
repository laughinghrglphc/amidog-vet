package com.amidog.app.admin;

import com.amidog.app.config.AmidogProperties;
import com.amidog.app.reservation.ReservationActor;
import com.amidog.app.reservation.ReservationEventType;
import com.amidog.app.reservation.ReservationStatus;
import com.amidog.app.scheduling.ClinicTime;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.amidog.app.admin.AdminDtos.AdminClientDetail;
import static com.amidog.app.admin.AdminDtos.AdminClientSummary;
import static com.amidog.app.admin.AdminDtos.AdminPetDetail;
import static com.amidog.app.admin.AdminDtos.AdminPetSummary;
import static com.amidog.app.admin.AdminDtos.AdminReservationDetail;
import static com.amidog.app.admin.AdminDtos.AdminReservationSummary;
import static com.amidog.app.admin.AdminDtos.ClientPetSummary;
import static com.amidog.app.admin.AdminDtos.CancellationActor;
import static com.amidog.app.admin.AdminDtos.DashboardStats;
import static com.amidog.app.admin.AdminDtos.ReservationCounts;
import static com.amidog.app.admin.AdminDtos.ReservationEventSummary;
import static com.amidog.app.admin.AdminDtos.ReservationItemSummary;

@Repository
public class JdbcAdminReadRepository
        implements AdminReadRepository {

    private static final String RESERVATION_COLUMNS = """
            r.id, r.client_id, c.name as client_name,
            u.email_normalized as client_email, c.phone as client_phone,
            r.scheduled_start, r.scheduled_end, r.status,
            r.client_note, r.created_at, r.updated_at,
            r.cancelled_at, r.cancelled_by, r.cancellation_reason
            """;
    private static final int CLIENT_DETAIL_PET_LIMIT = 100;
    private static final int CLIENT_DETAIL_UPCOMING_LIMIT = 10;
    private static final int PET_DETAIL_RESERVATION_LIMIT = 10;

    private final NamedParameterJdbcOperations jdbc;
    private final ClinicTime clinicTime;
    private final Clock clock;
    private final String clinicZone;

    public JdbcAdminReadRepository(
            NamedParameterJdbcOperations jdbc,
            ClinicTime clinicTime,
            Clock clock,
            AmidogProperties properties) {
        this.jdbc = jdbc;
        this.clinicTime = clinicTime;
        this.clock = clock;
        this.clinicZone = properties.clinicZone().getId();
    }

    @Override
    public DashboardStats dashboardStats(
            Instant todayStart,
            Instant tomorrowStart) {
        return jdbc.queryForObject("""
                select
                  (select count(*) from reservations r
                   where r.scheduled_start >= :from
                     and r.scheduled_start < :to
                     and r.status <> 'CANCELLED') as today_appointments,
                  (select count(*) from clients c
                   where c.active = true) as clients,
                  (select count(*) from pets p
                   where p.active = true) as pets
                """,
                Map.of("from", todayStart, "to", tomorrowStart),
                (result, row) -> new DashboardStats(
                        result.getLong("today_appointments"),
                        result.getLong("clients"),
                        result.getLong("pets")));
    }

    @Override
    public List<ReservationRecord> upcomingReservations(
            Instant fromInclusive,
            int limit) {
        List<ReservationHeader> headers = reservationHeaders("""
                select %s
                from reservations r
                join clients c on c.id = r.client_id
                join users u on u.id = c.user_id
                where r.scheduled_start >= :from
                  and r.status in ('PENDING', 'CONFIRMED')
                order by r.scheduled_start asc, r.id asc
                limit :limit
                """.formatted(RESERVATION_COLUMNS),
                Map.of("from", fromInclusive, "limit", limit));
        return records(headers);
    }

    @Override
    public List<ReservationRecord> reservationsBetween(
            Instant fromInclusive,
            Instant toExclusive,
            int limit) {
        List<ReservationHeader> headers = reservationHeaders("""
                select %s
                from reservations r
                join clients c on c.id = r.client_id
                join users u on u.id = c.user_id
                where r.scheduled_start >= :from
                  and r.scheduled_start < :to
                  and r.status in ('PENDING', 'CONFIRMED')
                order by r.scheduled_start asc, r.id asc
                limit :limit
                """.formatted(RESERVATION_COLUMNS),
                Map.of(
                        "from", fromInclusive,
                        "to", toExclusive,
                        "limit", limit));
        return records(headers);
    }

    @Override
    public List<ServiceUsageCount> serviceUsage(
            Instant fromInclusive,
            Instant toExclusive) {
        return jdbc.query("""
                select ri.service_name_snapshot as service_name,
                       count(*) as usage_count
                from reservation_items ri
                join reservations r on r.id = ri.reservation_id
                where r.scheduled_start >= :from
                  and r.scheduled_start < :to
                  and r.status <> 'CANCELLED'
                group by ri.service_name_snapshot
                order by count(*) desc,
                         lower(ri.service_name_snapshot) asc,
                         ri.service_name_snapshot asc
                limit 10
                """,
                Map.of("from", fromInclusive, "to", toExclusive),
                (result, row) -> new ServiceUsageCount(
                        result.getString("service_name"),
                        result.getLong("usage_count")));
    }

    @Override
    public List<DailyReservationCount> dailyReservationCounts(
            Instant fromInclusive,
            Instant toExclusive) {
        return jdbc.query("""
                select timezone(:zone, r.scheduled_start)::date
                           as clinic_date,
                       count(*) as reservation_count
                from reservations r
                where r.scheduled_start >= :from
                  and r.scheduled_start < :to
                  and r.status <> 'CANCELLED'
                group by clinic_date
                order by clinic_date asc
                """,
                Map.of(
                        "zone", clinicZone,
                        "from", fromInclusive,
                        "to", toExclusive),
                (result, row) -> new DailyReservationCount(
                        result.getObject(
                                "clinic_date", LocalDate.class),
                        result.getLong("reservation_count")));
    }

    @Override
    public PageResponse<AdminReservationSummary> reservations(
            ReservationQuery query) {
        SqlFilter filter = reservationFilter(query);
        long total = jdbc.queryForObject("""
                select count(*)
                from reservations r
                join clients c on c.id = r.client_id
                join users u on u.id = c.user_id
                %s
                """.formatted(filter.where()),
                filter.parameters(),
                Long.class);
        Map<String, Object> pageParameters =
                new HashMap<>(filter.parameters());
        pageParameters.put("limit", query.size());
        pageParameters.put(
                "offset",
                Math.multiplyExact(
                        (long) query.page(), query.size()));
        List<ReservationHeader> headers = reservationHeaders("""
                select %s
                from reservations r
                join clients c on c.id = r.client_id
                join users u on u.id = c.user_id
                %s
                order by r.scheduled_start desc, r.id desc
                limit :limit offset :offset
                """.formatted(
                        RESERVATION_COLUMNS, filter.where()),
                pageParameters);
        return PageResponse.of(
                records(headers).stream()
                        .map(this::summary)
                        .toList(),
                query.page(),
                query.size(),
                total);
    }

    @Override
    public Optional<AdminReservationDetail> reservation(long id) {
        List<ReservationHeader> headers = reservationHeaders("""
                select %s
                from reservations r
                join clients c on c.id = r.client_id
                join users u on u.id = c.user_id
                where r.id = :id
                """.formatted(RESERVATION_COLUMNS),
                Map.of("id", id));
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        ReservationHeader header = headers.getFirst();
        List<ReservationItemSummary> items =
                items(List.of(header.id()))
                        .getOrDefault(header.id(), List.of());
        List<ReservationEventSummary> events = jdbc.query("""
                select id, event_type, actor_type,
                       previous_status, new_status,
                       previous_start, new_start,
                       reason, created_at
                from reservation_events
                where reservation_id = :id
                order by created_at asc, id asc
                """,
                Map.of("id", id),
                (result, row) -> event(result));
        return Optional.of(new AdminReservationDetail(
                header.id(),
                header.clientId(),
                header.clientName(),
                header.clientEmail(),
                header.clientPhone(),
                clinicTime.toOffsetDateTime(header.startsAt()),
                clinicTime.toOffsetDateTime(header.endsAt()),
                header.status(),
                header.clientNote(),
                clinicTime.toOffsetDateTime(header.createdAt()),
                clinicTime.toOffsetDateTime(header.updatedAt()),
                offset(header.cancelledAt()),
                CancellationActor.fromStored(
                        header.cancelledBy()),
                header.cancellationReason(),
                items,
                events));
    }

    @Override
    public PageResponse<AdminClientSummary> clients(
            ClientQuery query) {
        SqlFilter filter = clientFilter(query);
        long total = jdbc.queryForObject("""
                select count(*)
                from clients c
                join users u on u.id = c.user_id
                %s
                """.formatted(filter.where()),
                filter.parameters(),
                Long.class);
        Map<String, Object> parameters =
                new HashMap<>(filter.parameters());
        parameters.put("limit", query.size());
        parameters.put(
                "offset",
                Math.multiplyExact(
                        (long) query.page(), query.size()));
        List<AdminClientSummary> content = jdbc.query("""
                select c.id, c.name, u.email_normalized as email,
                       c.phone, c.active, c.created_at, c.updated_at
                from clients c
                join users u on u.id = c.user_id
                %s
                order by lower(c.name) asc, c.id asc
                limit :limit offset :offset
                """.formatted(filter.where()),
                parameters,
                (result, row) -> clientSummary(result));
        return PageResponse.of(
                content, query.page(), query.size(), total);
    }

    @Override
    public Optional<AdminClientDetail> client(long id) {
        List<AdminClientSummary> roots = jdbc.query("""
                select c.id, c.name, u.email_normalized as email,
                       c.phone, c.active, c.created_at, c.updated_at
                from clients c
                join users u on u.id = c.user_id
                where c.id = :id
                """,
                Map.of("id", id),
                (result, row) -> clientSummary(result));
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        AdminClientSummary root = roots.getFirst();
        List<ClientPetSummary> pets = jdbc.query("""
                select p.id, p.name, p.species, p.breed,
                       p.birthdate, p.active
                from pets p
                where p.client_id = :id
                order by lower(p.name) asc, p.id asc
                limit :limit
                """,
                Map.of(
                        "id", id,
                        "limit", CLIENT_DETAIL_PET_LIMIT),
                (result, row) -> new ClientPetSummary(
                        result.getLong("id"),
                        result.getString("name"),
                        result.getString("species"),
                        result.getString("breed"),
                        result.getObject(
                                "birthdate", LocalDate.class),
                        result.getBoolean("active")));
        ReservationCounts counts = jdbc.queryForObject("""
                select count(*) as total,
                       count(*) filter (
                         where scheduled_start >= :now
                           and status in ('PENDING','CONFIRMED'))
                         as upcoming,
                       count(*) filter (where status = 'PENDING')
                         as pending,
                       count(*) filter (where status = 'CONFIRMED')
                         as confirmed,
                       count(*) filter (where status = 'CANCELLED')
                         as cancelled,
                       count(*) filter (where status = 'COMPLETED')
                         as completed,
                       count(*) filter (where status = 'NO_SHOW')
                         as no_show
                from reservations
                where client_id = :id
                """,
                Map.of("id", id, "now", clock.instant()),
                (result, row) -> new ReservationCounts(
                        result.getLong("total"),
                        result.getLong("upcoming"),
                        result.getLong("pending"),
                        result.getLong("confirmed"),
                        result.getLong("cancelled"),
                        result.getLong("completed"),
                        result.getLong("no_show")));
        List<ReservationHeader> upcoming = reservationHeaders("""
                select %s
                from reservations r
                join clients c on c.id = r.client_id
                join users u on u.id = c.user_id
                where r.client_id = :id
                  and r.scheduled_start >= :now
                  and r.status in ('PENDING','CONFIRMED')
                order by r.scheduled_start asc, r.id asc
                limit :limit
                """.formatted(RESERVATION_COLUMNS),
                Map.of(
                        "id", id,
                        "now", clock.instant(),
                        "limit", CLIENT_DETAIL_UPCOMING_LIMIT));
        return Optional.of(new AdminClientDetail(
                root.id(),
                root.name(),
                root.email(),
                root.phone(),
                root.active(),
                root.createdAt(),
                root.updatedAt(),
                pets,
                counts,
                records(upcoming).stream()
                        .map(this::summary)
                        .toList()));
    }

    @Override
    public PageResponse<AdminPetSummary> pets(PetQuery query) {
        SqlFilter filter = petFilter(query);
        long total = jdbc.queryForObject("""
                select count(*)
                from pets p
                join clients c on c.id = p.client_id
                join users u on u.id = c.user_id
                %s
                """.formatted(filter.where()),
                filter.parameters(),
                Long.class);
        Map<String, Object> parameters =
                new HashMap<>(filter.parameters());
        parameters.put("limit", query.size());
        parameters.put(
                "offset",
                Math.multiplyExact(
                        (long) query.page(), query.size()));
        List<AdminPetSummary> content = jdbc.query("""
                select p.id, p.client_id, c.name as owner_name,
                       p.name, p.species, p.breed, p.birthdate,
                       p.active, p.created_at, p.updated_at
                from pets p
                join clients c on c.id = p.client_id
                join users u on u.id = c.user_id
                %s
                order by lower(p.name) asc, p.id asc
                limit :limit offset :offset
                """.formatted(filter.where()),
                parameters,
                (result, row) -> petSummary(result));
        return PageResponse.of(
                content, query.page(), query.size(), total);
    }

    @Override
    public Optional<AdminPetDetail> pet(long id) {
        List<PetDetailRoot> roots = jdbc.query("""
                select p.id, p.client_id, c.name as owner_name,
                       u.email_normalized as owner_email,
                       p.name, p.species, p.breed, p.birthdate,
                       p.active, p.created_at, p.updated_at
                from pets p
                join clients c on c.id = p.client_id
                join users u on u.id = c.user_id
                where p.id = :id
                """,
                Map.of("id", id),
                (result, row) -> new PetDetailRoot(
                        petSummary(result),
                        result.getString("owner_email")));
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        PetDetailRoot root = roots.getFirst();
        List<ReservationHeader> recent = reservationHeaders("""
                select %s
                from reservations r
                join clients c on c.id = r.client_id
                join users u on u.id = c.user_id
                where exists (
                  select 1 from reservation_items selected_item
                  where selected_item.reservation_id = r.id
                    and selected_item.pet_id = :petId)
                order by r.scheduled_start desc, r.id desc
                limit :limit
                """.formatted(RESERVATION_COLUMNS),
                Map.of(
                        "petId", id,
                        "limit", PET_DETAIL_RESERVATION_LIMIT));
        AdminPetSummary pet = root.summary();
        return Optional.of(new AdminPetDetail(
                pet.id(),
                pet.clientId(),
                pet.ownerName(),
                root.ownerEmail(),
                pet.name(),
                pet.species(),
                pet.breed(),
                pet.birthdate(),
                pet.active(),
                pet.createdAt(),
                pet.updatedAt(),
                records(recent).stream()
                        .map(this::summary)
                        .toList()));
    }

    private SqlFilter reservationFilter(ReservationQuery query) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();
        if (query.fromInclusive() != null) {
            conditions.add("r.scheduled_start >= :from");
            parameters.put("from", query.fromInclusive());
        }
        if (query.toExclusive() != null) {
            conditions.add("r.scheduled_start < :to");
            parameters.put("to", query.toExclusive());
        }
        if (query.status() != null) {
            conditions.add("r.status = :status");
            parameters.put("status", query.status().name());
        }
        if (query.searchPattern() != null) {
            conditions.add("""
                    (lower(c.name) like :search escape '!'
                     or lower(u.email_normalized)
                            like :search escape '!'
                     or exists (
                       select 1
                       from reservation_items searched_item
                       join pets searched_pet
                         on searched_pet.id = searched_item.pet_id
                       where searched_item.reservation_id = r.id
                         and (
                           lower(searched_pet.name)
                             like :search escape '!'
                           or lower(
                             searched_item.service_name_snapshot)
                             like :search escape '!')))
                    """);
            parameters.put("search", query.searchPattern());
        }
        return SqlFilter.of(conditions, parameters);
    }

    private SqlFilter clientFilter(ClientQuery query) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();
        if (query.searchPattern() != null) {
            conditions.add("""
                    (lower(c.name) like :search escape '!'
                     or lower(u.email_normalized)
                          like :search escape '!'
                     or lower(c.phone) like :search escape '!')
                    """);
            parameters.put("search", query.searchPattern());
        }
        if (query.active() != null) {
            conditions.add("c.active = :active");
            parameters.put("active", query.active());
        }
        return SqlFilter.of(conditions, parameters);
    }

    private SqlFilter petFilter(PetQuery query) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();
        if (query.searchPattern() != null) {
            conditions.add("""
                    (lower(p.name) like :search escape '!'
                     or lower(p.species) like :search escape '!'
                     or lower(coalesce(p.breed, ''))
                          like :search escape '!'
                     or lower(c.name) like :search escape '!'
                     or lower(u.email_normalized)
                          like :search escape '!')
                    """);
            parameters.put("search", query.searchPattern());
        }
        if (query.active() != null) {
            conditions.add("p.active = :active");
            parameters.put("active", query.active());
        }
        if (query.clientId() != null) {
            conditions.add("p.client_id = :clientId");
            parameters.put("clientId", query.clientId());
        }
        return SqlFilter.of(conditions, parameters);
    }

    private List<ReservationHeader> reservationHeaders(
            String sql,
            Map<String, ?> parameters) {
        return jdbc.query(
                sql,
                parameters,
                (result, row) -> new ReservationHeader(
                        result.getLong("id"),
                        result.getLong("client_id"),
                        result.getString("client_name"),
                        result.getString("client_email"),
                        result.getString("client_phone"),
                        instant(result, "scheduled_start"),
                        instant(result, "scheduled_end"),
                        ReservationStatus.valueOf(
                                result.getString("status")),
                        result.getString("client_note"),
                        instant(result, "created_at"),
                        instant(result, "updated_at"),
                        nullableInstant(result, "cancelled_at"),
                        result.getString("cancelled_by"),
                        result.getString("cancellation_reason")));
    }

    private List<ReservationRecord> records(
            List<ReservationHeader> headers) {
        if (headers.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ReservationItemSummary>> items =
                items(headers.stream()
                        .map(ReservationHeader::id)
                        .toList());
        return headers.stream()
                .map(header -> new ReservationRecord(
                        header.id(),
                        header.clientId(),
                        header.clientName(),
                        header.clientEmail(),
                        header.clientPhone(),
                        header.startsAt(),
                        header.endsAt(),
                        header.status(),
                        header.clientNote(),
                        header.createdAt(),
                        header.updatedAt(),
                        items.getOrDefault(
                                header.id(), List.of())))
                .toList();
    }

    private Map<Long, List<ReservationItemSummary>> items(
            List<Long> reservationIds) {
        if (reservationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ReservationItemSummary>> byReservation =
                new LinkedHashMap<>();
        jdbc.query("""
                select ri.reservation_id, ri.pet_id,
                       p.name as pet_name, p.species, p.breed,
                       ri.service_id, ri.service_name_snapshot
                           as service_name
                from reservation_items ri
                join pets p on p.id = ri.pet_id
                where ri.reservation_id in (:ids)
                order by ri.reservation_id asc, ri.id asc
                """,
                Map.of("ids", reservationIds),
                result -> {
                    long reservationId =
                            result.getLong("reservation_id");
                    byReservation.computeIfAbsent(
                                    reservationId,
                                    ignored -> new ArrayList<>())
                            .add(new ReservationItemSummary(
                                    result.getLong("pet_id"),
                                    result.getString("pet_name"),
                                    result.getString("species"),
                                    result.getString("breed"),
                                    result.getLong("service_id"),
                                    result.getString("service_name")));
                });
        return byReservation;
    }

    private AdminReservationSummary summary(
            ReservationRecord value) {
        return new AdminReservationSummary(
                value.id(),
                value.clientId(),
                value.clientName(),
                value.clientEmail(),
                value.clientPhone(),
                clinicTime.toOffsetDateTime(value.startsAt()),
                clinicTime.toOffsetDateTime(value.endsAt()),
                value.status(),
                value.clientNote(),
                clinicTime.toOffsetDateTime(value.createdAt()),
                clinicTime.toOffsetDateTime(value.updatedAt()),
                value.items());
    }

    private AdminClientSummary clientSummary(ResultSet result)
            throws SQLException {
        return new AdminClientSummary(
                result.getLong("id"),
                result.getString("name"),
                result.getString("email"),
                result.getString("phone"),
                result.getBoolean("active"),
                clinicTime.toOffsetDateTime(
                        instant(result, "created_at")),
                clinicTime.toOffsetDateTime(
                        instant(result, "updated_at")));
    }

    private AdminPetSummary petSummary(ResultSet result)
            throws SQLException {
        return new AdminPetSummary(
                result.getLong("id"),
                result.getLong("client_id"),
                result.getString("owner_name"),
                result.getString("name"),
                result.getString("species"),
                result.getString("breed"),
                result.getObject("birthdate", LocalDate.class),
                result.getBoolean("active"),
                clinicTime.toOffsetDateTime(
                        instant(result, "created_at")),
                clinicTime.toOffsetDateTime(
                        instant(result, "updated_at")));
    }

    private ReservationEventSummary event(ResultSet result)
            throws SQLException {
        return new ReservationEventSummary(
                result.getLong("id"),
                ReservationEventType.valueOf(
                        result.getString("event_type")),
                ReservationActor.valueOf(
                        result.getString("actor_type")),
                enumValue(
                        ReservationStatus.class,
                        result.getString("previous_status")),
                enumValue(
                        ReservationStatus.class,
                        result.getString("new_status")),
                offset(nullableInstant(result, "previous_start")),
                offset(nullableInstant(result, "new_start")),
                result.getString("reason"),
                clinicTime.toOffsetDateTime(
                        instant(result, "created_at")));
    }

    private java.time.OffsetDateTime offset(Instant instant) {
        return instant == null
                ? null
                : clinicTime.toOffsetDateTime(instant);
    }

    private static Instant instant(
            ResultSet result,
            String column) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(
            ResultSet result,
            String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type,
            String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private record ReservationHeader(
            Long id,
            Long clientId,
            String clientName,
            String clientEmail,
            String clientPhone,
            Instant startsAt,
            Instant endsAt,
            ReservationStatus status,
            String clientNote,
            Instant createdAt,
            Instant updatedAt,
            Instant cancelledAt,
            String cancelledBy,
            String cancellationReason) {
    }

    private record PetDetailRoot(
            AdminPetSummary summary,
            String ownerEmail) {
    }

    private record SqlFilter(
            String where,
            Map<String, Object> parameters) {

        static SqlFilter of(
                List<String> conditions,
                Map<String, Object> parameters) {
            String where = conditions.isEmpty()
                    ? ""
                    : "where " + String.join(
                            "\nand ", conditions);
            return new SqlFilter(where, Map.copyOf(parameters));
        }
    }
}
