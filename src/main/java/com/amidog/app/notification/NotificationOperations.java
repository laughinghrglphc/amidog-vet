package com.amidog.app.notification;

import com.amidog.app.reservation.Reservation;

public interface NotificationOperations {

    Notification onReservationCreated(Reservation reservation);

    Notification onClientCancelled(Reservation reservation);

    Notification onClientRescheduled(Reservation reservation);

    Notification onReservationConfirmed(Reservation reservation);

    Notification onAdminCancelled(Reservation reservation);

    Notification onAdminRescheduled(Reservation reservation);
}
