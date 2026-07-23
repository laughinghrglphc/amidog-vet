package com.amidog.app.notification;

import com.amidog.app.auth.UserAccount;
import com.amidog.app.client.Client;
import com.amidog.app.reservation.Reservation;
import com.amidog.app.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationDomainTests {

    private static final Instant CREATED_AT =
            Instant.parse("2026-07-29T12:00:00Z");

    @Test
    void mapsTheFrozenNotificationShapeAndPreservesTheFirstReadTimestamp()
            throws Exception {
        Fixture fixture = fixture();
        Notification notification = Notification.create(
                fixture.recipient(),
                NotificationType.RESERVATION_CONFIRMED,
                "Reserva confirmada",
                "Tu reserva fue confirmada.",
                fixture.reservation(),
                "RESERVATION_CONFIRMED:e:41:u:10",
                CREATED_AT);
        setId(notification, 90L);

        Instant firstRead = CREATED_AT.plusSeconds(10);
        notification.markRead(firstRead);
        notification.markRead(firstRead.plusSeconds(20));

        assertThat(notification.getId()).isEqualTo(90L);
        assertThat(notification.getRecipient()).isSameAs(fixture.recipient());
        assertThat(notification.getReservation()).isSameAs(fixture.reservation());
        assertThat(notification.getType())
                .isEqualTo(NotificationType.RESERVATION_CONFIRMED);
        assertThat(notification.getTitle()).isEqualTo("Reserva confirmada");
        assertThat(notification.getBody()).isEqualTo("Tu reserva fue confirmada.");
        assertThat(notification.getDeduplicationKey())
                .isEqualTo("RESERVATION_CONFIRMED:e:41:u:10");
        assertThat(notification.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(notification.getReadAt()).isEqualTo(firstRead);
    }

    @Test
    void rejectsBlankOrFrozenColumnOverflowBeforePersistence()
            throws Exception {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> Notification.create(
                fixture.recipient(), NotificationType.NEW_RESERVATION,
                " ", "body", fixture.reservation(), "key", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.create(
                fixture.recipient(), NotificationType.NEW_RESERVATION,
                "x".repeat(121), "body", fixture.reservation(), "key",
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.create(
                fixture.recipient(), NotificationType.NEW_RESERVATION,
                "title", "x".repeat(501), fixture.reservation(), "key",
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.create(
                fixture.recipient(), NotificationType.NEW_RESERVATION,
                "title", "body", fixture.reservation(), "x".repeat(161),
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Fixture fixture() throws Exception {
        UserAccount recipient = UserAccount.client(
                "ana@example.com", "{noop}password", CREATED_AT);
        recipient.verify(CREATED_AT);
        setId(recipient, 10L);
        Client client = Client.create(
                recipient, "Ana", "+56911111111", CREATED_AT);
        setId(client, 11L);
        Reservation reservation = Reservation.create(
                client,
                Instant.parse("2026-08-10T14:00:00Z"),
                ReservationStatus.PENDING,
                null,
                CREATED_AT);
        setId(reservation, 20L);
        return new Fixture(recipient, reservation);
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private record Fixture(UserAccount recipient, Reservation reservation) {
    }
}
