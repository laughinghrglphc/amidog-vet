package com.amidog.app.notification;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.auth.UserAccount;
import com.amidog.app.auth.UserAccountRepository;
import com.amidog.app.client.CurrentClient;
import com.amidog.app.common.api.NotFoundException;
import com.amidog.app.reservation.Reservation;
import com.amidog.app.reservation.ReservationEvent;
import com.amidog.app.reservation.ReservationRepository;
import com.amidog.app.reservation.ReservationStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static com.amidog.app.notification.NotificationDtos.NotificationResponse;
import static com.amidog.app.notification.NotificationDtos.ReadAllResponse;

@Service
public class NotificationService implements NotificationOperations {

    public static final String NOTIFICATION_NOT_FOUND =
            "No se encontr\u00f3 la notificaci\u00f3n.";
    private static final int LIST_LIMIT = 100;

    private final NotificationRepository notifications;
    private final UserAccountRepository users;
    private final ReservationRepository reservations;
    private final CurrentClient currentClient;
    private final Clock clock;
    private final NotificationReminderProperties reminderProperties;

    public NotificationService(
            NotificationRepository notifications,
            UserAccountRepository users,
            ReservationRepository reservations,
            CurrentClient currentClient,
            Clock clock,
            NotificationReminderProperties reminderProperties) {
        this.notifications = notifications;
        this.users = users;
        this.reservations = reservations;
        this.currentClient = currentClient;
        this.clock = clock;
        this.reminderProperties = reminderProperties;
    }

    @Override
    @Transactional
    public Notification onReservationCreated(Reservation reservation) {
        UserAccount administrator = requireSingleAdministrator();
        return create(
                administrator,
                NotificationType.NEW_RESERVATION,
                "Nueva reserva",
                "Se recibi\u00f3 una nueva reserva.",
                reservation,
                "NEW_RESERVATION:r:" + requiredId(reservation)
                        + ":u:" + requiredId(administrator)).notification();
    }

    @Override
    @Transactional
    public Notification onClientCancelled(Reservation reservation) {
        return createForAdministratorEvent(
                reservation,
                NotificationType.CLIENT_CANCELLED,
                "Reserva cancelada por cliente",
                "Un cliente cancel\u00f3 una reserva.");
    }

    @Override
    @Transactional
    public Notification onClientRescheduled(Reservation reservation) {
        return createForAdministratorEvent(
                reservation,
                NotificationType.CLIENT_RESCHEDULED,
                "Reserva reagendada por cliente",
                "Un cliente reagend\u00f3 una reserva.");
    }

    @Override
    @Transactional
    public Notification onReservationConfirmed(Reservation reservation) {
        return createForClientEvent(
                reservation,
                NotificationType.RESERVATION_CONFIRMED,
                "Reserva confirmada",
                "Tu reserva fue confirmada.");
    }

    @Override
    @Transactional
    public Notification onAdminCancelled(Reservation reservation) {
        return createForClientEvent(
                reservation,
                NotificationType.ADMIN_CANCELLED,
                "Reserva cancelada",
                "La cl\u00ednica cancel\u00f3 tu reserva.");
    }

    @Override
    @Transactional
    public Notification onAdminRescheduled(Reservation reservation) {
        return createForClientEvent(
                reservation,
                NotificationType.ADMIN_RESCHEDULED,
                "Reserva reagendada",
                "La cl\u00ednica reagend\u00f3 tu reserva.");
    }

    @Transactional
    public int createAppointmentReminders() {
        if (!reminderProperties.enabled()) {
            return 0;
        }
        Instant now = clock.instant();
        Instant from = now.plus(Duration.ofHours(
                reminderProperties.windowStartHours()));
        Instant to = now.plus(Duration.ofHours(
                reminderProperties.windowEndHours()));
        List<ReservationRepository.ReminderCandidate> candidates =
                reservations.findConfirmedReminderCandidates(
                        from,
                        to,
                        PageRequest.of(
                                0, reminderProperties.batchSize()));
        int created = 0;
        for (ReservationRepository.ReminderCandidate candidate
                : candidates) {
            Reservation reservation = reservations
                    .findReminderForUpdate(candidate.getReservationId())
                    .orElse(null);
            if (!isStillReminderEligible(
                    reservation, candidate, now, from, to)) {
                continue;
            }
            UserAccount recipient = reservation.getClient().getUser();
            String key = reminderKey(reservation, recipient);
            if (create(
                    recipient,
                    NotificationType.APPOINTMENT_REMINDER,
                    "Recordatorio de reserva",
                    reminderBody(),
                    reservation,
                    key).inserted()) {
                created++;
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listClient(
            AccountPrincipal principal) {
        return list(requireActiveClientRecipientUserId(principal));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listAdmin(
            AccountPrincipal principal) {
        requirePrincipal(principal, AccountType.ADMIN);
        return list(principal.getUserId());
    }

    @Transactional
    public NotificationResponse markClientRead(
            AccountPrincipal principal, long notificationId) {
        return markRead(
                requireActiveClientRecipientUserId(principal),
                notificationId);
    }

    @Transactional
    public NotificationResponse markAdminRead(
            AccountPrincipal principal, long notificationId) {
        requirePrincipal(principal, AccountType.ADMIN);
        return markRead(principal.getUserId(), notificationId);
    }

    @Transactional
    public ReadAllResponse markAllClientRead(
            AccountPrincipal principal) {
        return markAllRead(
                requireActiveClientRecipientUserId(principal));
    }

    @Transactional
    public ReadAllResponse markAllAdminRead(
            AccountPrincipal principal) {
        requirePrincipal(principal, AccountType.ADMIN);
        return markAllRead(principal.getUserId());
    }

    private Notification createForAdministratorEvent(
            Reservation reservation,
            NotificationType type,
            String title,
            String body) {
        UserAccount administrator = requireSingleAdministrator();
        return create(
                administrator,
                type,
                title,
                body,
                reservation,
                eventKey(type, reservation, administrator)).notification();
    }

    private Notification createForClientEvent(
            Reservation reservation,
            NotificationType type,
            String title,
            String body) {
        UserAccount recipient = reservation.getClient().getUser();
        return create(
                recipient,
                type,
                title,
                body,
                reservation,
                eventKey(type, reservation, recipient)).notification();
    }

    private Creation create(
            UserAccount recipient,
            NotificationType type,
            String title,
            String body,
            Reservation reservation,
            String deduplicationKey) {
        Instant createdAt = clock.instant();
        Notification candidate = Notification.create(
                recipient,
                type,
                title,
                body,
                reservation,
                deduplicationKey,
                createdAt);
        int inserted = notifications.insertIgnoringDeduplicationConflict(
                requiredId(recipient),
                candidate.getType().name(),
                candidate.getTitle(),
                candidate.getBody(),
                reservation == null ? null : requiredId(reservation),
                candidate.getDeduplicationKey(),
                candidate.getCreatedAt());
        Notification persisted = notifications
                .findByDeduplicationKey(deduplicationKey)
                .orElseThrow(NotificationInvariantException::new);
        return new Creation(persisted, inserted == 1);
    }

    private List<NotificationResponse> list(long recipientUserId) {
        return notifications
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(
                        recipientUserId,
                        PageRequest.of(0, LIST_LIMIT))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    private NotificationResponse markRead(
            long recipientUserId, long notificationId) {
        notifications.markOneUnread(
                notificationId,
                recipientUserId,
                clock.instant());
        Notification notification = notifications
                .findByIdAndRecipientId(
                        notificationId, recipientUserId)
                .orElseThrow(this::notFound);
        return NotificationResponse.from(notification);
    }

    private ReadAllResponse markAllRead(long recipientUserId) {
        return new ReadAllResponse(notifications.markAllUnread(
                recipientUserId, clock.instant()));
    }

    private long requireActiveClientRecipientUserId(
            AccountPrincipal principal) {
        requirePrincipal(principal, AccountType.CLIENT);
        return requiredId(currentClient.require(principal).getUser());
    }

    private String reminderBody() {
        int leadTimeHours =
                reminderProperties.approximateLeadTimeHours();
        String unit = leadTimeHours == 1 ? "hora" : "horas";
        return "Tienes una reserva programada para dentro de "
                + "aproximadamente " + leadTimeHours + " " + unit + ".";
    }

    private UserAccount requireSingleAdministrator() {
        List<UserAccount> administrators =
                users.findAllByAccountType(AccountType.ADMIN);
        if (administrators.size() != 1) {
            throw new NotificationInvariantException();
        }
        return administrators.get(0);
    }

    private static String eventKey(
            NotificationType type,
            Reservation reservation,
            UserAccount recipient) {
        ReservationEvent event = latestPersistedEvent(reservation);
        return type.name() + ":e:" + event.getId()
                + ":u:" + requiredId(recipient);
    }

    private static String reminderKey(
            Reservation reservation, UserAccount recipient) {
        Instant start = reservation.getScheduledStart();
        return NotificationType.APPOINTMENT_REMINDER.name()
                + ":r:" + requiredId(reservation)
                + ":u:" + requiredId(recipient)
                + ":s:" + start.getEpochSecond()
                + "-" + start.getNano();
    }

    private static boolean isStillReminderEligible(
            Reservation reservation,
            ReservationRepository.ReminderCandidate candidate,
            Instant now,
            Instant from,
            Instant to) {
        if (reservation == null
                || reservation.getStatus()
                        != ReservationStatus.CONFIRMED
                || !reservation.getScheduledStart()
                        .equals(candidate.getScheduledStart())
                || !reservation.getScheduledStart().isAfter(now)
                || reservation.getScheduledStart().isBefore(from)
                || !reservation.getScheduledStart().isBefore(to)) {
            return false;
        }
        UserAccount recipient = reservation.getClient().getUser();
        return recipient.getId() != null
                && recipient.getId()
                        .equals(candidate.getRecipientUserId());
    }

    private static ReservationEvent latestPersistedEvent(
            Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        List<ReservationEvent> events = reservation.getEvents();
        if (events.isEmpty()) {
            throw new NotificationInvariantException();
        }
        ReservationEvent event = events.get(events.size() - 1);
        if (event.getId() == null) {
            throw new NotificationInvariantException();
        }
        return event;
    }

    private static long requiredId(Object entity) {
        Long id;
        if (entity instanceof UserAccount user) {
            id = user.getId();
        } else if (entity instanceof Reservation reservation) {
            id = reservation.getId();
        } else {
            throw new NotificationInvariantException();
        }
        if (id == null) {
            throw new NotificationInvariantException();
        }
        return id;
    }

    private static void requirePrincipal(
            AccountPrincipal principal, AccountType requiredType) {
        if (principal == null
                || principal.getAccountType() != requiredType
                || !principal.isEnabled()) {
            throw new AccessDeniedException("Notification access denied");
        }
    }

    private NotFoundException notFound() {
        return new NotFoundException(NOTIFICATION_NOT_FOUND);
    }

    private record Creation(
            Notification notification, boolean inserted) {
    }
}
