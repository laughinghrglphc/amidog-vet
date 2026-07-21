package com.amidog.app.reservation;

import com.amidog.app.auth.UserAccount;
import com.amidog.app.catalog.ServiceOffering;
import com.amidog.app.client.Client;
import com.amidog.app.client.Pet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationDomainTests {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    @Test
    void oneAggregateKeepsMultiplePetsAndAlwaysLastsThirtyMinutes() throws Exception {
        Client client = client(10L);
        Pet milo = pet(client, 20L, "Milo");
        Pet luna = pet(client, 21L, "Luna");
        ServiceOffering consult = service(30L, "consulta", "Consulta general");
        ServiceOffering vaccine = service(31L, "vacuna", "Vacunaci\u00f3n");
        Instant start = Instant.parse("2026-08-10T14:00:00Z");

        Reservation reservation = Reservation.create(
                client, start, ReservationStatus.PENDING, "  Control anual  ", NOW);
        reservation.addItem(milo, consult);
        reservation.addItem(luna, vaccine);
        reservation.addCreationEvent(NOW);

        assertThat(Duration.between(
                reservation.getScheduledStart(), reservation.getScheduledEnd()))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(reservation.getClientNote()).isEqualTo("Control anual");
        assertThat(reservation.getItems())
                .extracting(ReservationItem::getServiceNameSnapshot)
                .containsExactly("Consulta general", "Vacunaci\u00f3n");
        assertThat(reservation.getEvents()).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("CREATED");
            assertThat(event.getActorType()).isEqualTo("CLIENT");
            assertThat(event.getNewStatus()).isEqualTo(ReservationStatus.PENDING);
            assertThat(event.getNewStart()).isEqualTo(start);
        });
    }

    @Test
    void blankNoteIsNormalizedToNull() {
        Reservation reservation = Reservation.create(
                clientWithoutId(), Instant.parse("2026-08-10T14:00:00Z"),
                ReservationStatus.CONFIRMED, "   ", NOW);

        assertThat(reservation.getClientNote()).isNull();
    }

    private static Client client(long id) throws Exception {
        Client client = clientWithoutId();
        setId(client, id);
        return client;
    }

    private static Client clientWithoutId() {
        UserAccount account = UserAccount.client(
                "ana@example.com", "{noop}password", NOW);
        return Client.create(account, "Ana", "+56911111111", NOW);
    }

    private static Pet pet(Client client, long id, String name) throws Exception {
        Pet pet = Pet.create(client, name, "Mascota", null, null, NOW);
        setId(pet, id);
        return pet;
    }

    private static ServiceOffering service(
            long id, String code, String name) throws Exception {
        ServiceOffering service = ServiceOffering.create(
                code, name, null, 0, NOW);
        setId(service, id);
        return service;
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
