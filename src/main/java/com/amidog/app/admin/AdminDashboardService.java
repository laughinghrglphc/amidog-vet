package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.scheduling.ClinicTime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.amidog.app.admin.AdminDtos.AdminProfile;
import static com.amidog.app.admin.AdminDtos.AdminReservationSummary;
import static com.amidog.app.admin.AdminDtos.AgendaItem;
import static com.amidog.app.admin.AdminDtos.ChartSeries;
import static com.amidog.app.admin.AdminDtos.DashboardChart;
import static com.amidog.app.admin.AdminDtos.DashboardResponse;
import static com.amidog.app.admin.AdminDtos.ServiceUsage;

@Service
public class AdminDashboardService {

    private static final int UPCOMING_LIMIT = 10;
    private static final int TODAY_LIMIT = 100;
    private static final int SERVICE_USAGE_LIMIT = 10;
    private static final List<String> WEEKDAY_LABELS =
            List.of("Lun", "Mar", "Mi\u00e9", "Jue", "Vie", "S\u00e1b", "Dom");

    private final AdminReadRepository reads;
    private final ClinicTime clinicTime;
    private final AmidogProperties properties;
    private final Clock clock;

    public AdminDashboardService(
            AdminReadRepository reads,
            ClinicTime clinicTime,
            AmidogProperties properties,
            Clock clock) {
        this.reads = reads;
        this.clinicTime = clinicTime;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(AccountPrincipal principal) {
        requireAdministrator(principal);
        Instant now = clock.instant();
        LocalDate today = clinicTime.localDate(now);
        Instant todayStart =
                clinicTime.startOfDay(today).toInstant();
        Instant tomorrowStart =
                clinicTime.startOfDay(today.plusDays(1)).toInstant();

        List<AdminReservationSummary> appointments = reads
                .upcomingReservations(now, UPCOMING_LIMIT)
                .stream()
                .limit(UPCOMING_LIMIT)
                .map(this::summary)
                .toList();
        List<AdminReservationSummary> todayReservations = reads
                .reservationsBetween(
                        todayStart, tomorrowStart, TODAY_LIMIT)
                .stream()
                .limit(TODAY_LIMIT)
                .map(this::summary)
                .toList();

        return new DashboardResponse(
                new AdminProfile(
                        properties.admin().name(),
                        principal.getEmail(),
                        AccountType.ADMIN.name()),
                reads.dashboardStats(todayStart, tomorrowStart),
                appointments,
                serviceUsage(today),
                chart(today),
                todayReservations.stream()
                        .map(value -> new AgendaItem(
                                value.id(),
                                value.startsAt(),
                                value.endsAt(),
                                value.status(),
                                value.items()))
                        .toList());
    }

    private List<ServiceUsage> serviceUsage(LocalDate today) {
        Instant monthStart = clinicTime
                .startOfDay(today.withDayOfMonth(1))
                .toInstant();
        Instant nextMonthStart = clinicTime
                .startOfDay(today.withDayOfMonth(1)
                        .plusMonths(1))
                .toInstant();
        List<AdminReadRepository.ServiceUsageCount> counts =
                reads.serviceUsage(monthStart, nextMonthStart)
                        .stream()
                        .filter(value -> value.count() > 0)
                        .sorted(Comparator
                                .comparingLong(
                                        AdminReadRepository
                                                .ServiceUsageCount::count)
                                .reversed()
                                .thenComparing(
                                        AdminReadRepository
                                                .ServiceUsageCount::name,
                                        String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(
                                        AdminReadRepository
                                                .ServiceUsageCount::name))
                        .limit(SERVICE_USAGE_LIMIT)
                        .toList();
        if (counts.isEmpty()) {
            return List.of();
        }
        long total = counts.stream()
                .mapToLong(
                        AdminReadRepository.ServiceUsageCount::count)
                .sum();
        BigInteger totalBig = BigInteger.valueOf(total);
        List<UsageAllocation> allocations = new ArrayList<>();
        int assigned = 0;
        for (int index = 0; index < counts.size(); index++) {
            var count = counts.get(index);
            BigInteger[] division = BigInteger
                    .valueOf(count.count())
                    .multiply(BigInteger.valueOf(100))
                    .divideAndRemainder(totalBig);
            int percentage = division[0].intValueExact();
            assigned += percentage;
            allocations.add(new UsageAllocation(
                    index, count, percentage, division[1]));
        }
        allocations.stream()
                .sorted(Comparator
                        .comparing(UsageAllocation::remainder)
                        .reversed()
                        .thenComparingInt(UsageAllocation::index))
                .limit(100L - assigned)
                .forEach(UsageAllocation::addOne);
        return allocations.stream()
                .sorted(Comparator.comparingInt(
                        UsageAllocation::index))
                .map(value -> new ServiceUsage(
                        value.count().name(),
                        value.count().count(),
                        value.percentage()))
                .toList();
    }

    private DashboardChart chart(LocalDate today) {
        LocalDate currentWeekStart = today.with(
                TemporalAdjusters.previousOrSame(
                        DayOfWeek.MONDAY));
        LocalDate previousWeekStart =
                currentWeekStart.minusWeeks(1);
        LocalDate currentWeekEnd =
                currentWeekStart.plusWeeks(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);
        LocalDate queryStart = previousWeekStart.isBefore(monthStart)
                ? previousWeekStart : monthStart;
        LocalDate queryEnd = currentWeekEnd.isAfter(monthEnd)
                ? currentWeekEnd : monthEnd;

        Map<LocalDate, Long> counts = new HashMap<>();
        reads.dailyReservationCounts(
                        clinicTime.startOfDay(queryStart).toInstant(),
                        clinicTime.startOfDay(queryEnd).toInstant())
                .forEach(value -> counts.merge(
                        value.date(), value.count(), Long::sum));

        ChartSeries currentWeek = weekSeries(
                "Esta semana", currentWeekStart, counts);
        ChartSeries previousWeek = weekSeries(
                "Semana pasada", previousWeekStart, counts);
        int monthBuckets =
                (monthEnd.minusDays(1).getDayOfMonth() + 6) / 7;
        List<String> monthLabels = new ArrayList<>();
        List<Long> monthValues = new ArrayList<>();
        for (int index = 0; index < monthBuckets; index++) {
            monthLabels.add("Sem " + (index + 1));
            LocalDate start = monthStart.plusDays(index * 7L);
            LocalDate end = start.plusDays(7).isBefore(monthEnd)
                    ? start.plusDays(7) : monthEnd;
            long value = 0;
            for (LocalDate date = start;
                 date.isBefore(end);
                 date = date.plusDays(1)) {
                value += counts.getOrDefault(date, 0L);
            }
            monthValues.add(value);
        }
        return new DashboardChart(
                currentWeek,
                previousWeek,
                new ChartSeries(
                        "Este mes", monthLabels, monthValues));
    }

    private ChartSeries weekSeries(
            String label,
            LocalDate start,
            Map<LocalDate, Long> counts) {
        List<Long> values = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            values.add(counts.getOrDefault(
                    start.plusDays(index), 0L));
        }
        return new ChartSeries(label, WEEKDAY_LABELS, values);
    }

    private AdminReservationSummary summary(
            AdminReadRepository.ReservationRecord value) {
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

    private static void requireAdministrator(
            AccountPrincipal principal) {
        if (principal == null
                || principal.getAccountType() != AccountType.ADMIN
                || !principal.isEnabled()) {
            throw new AccessDeniedException(
                    "Administrator required");
        }
    }

    private static final class UsageAllocation {
        private final int index;
        private final AdminReadRepository.ServiceUsageCount count;
        private int percentage;
        private final BigInteger remainder;

        private UsageAllocation(
                int index,
                AdminReadRepository.ServiceUsageCount count,
                int percentage,
                BigInteger remainder) {
            this.index = index;
            this.count = count;
            this.percentage = percentage;
            this.remainder = remainder;
        }

        int index() {
            return index;
        }

        AdminReadRepository.ServiceUsageCount count() {
            return count;
        }

        int percentage() {
            return percentage;
        }

        BigInteger remainder() {
            return remainder;
        }

        void addOne() {
            percentage++;
        }
    }
}
