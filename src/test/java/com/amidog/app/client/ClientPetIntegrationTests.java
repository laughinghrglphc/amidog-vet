package com.amidog.app.client;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ClientPetIntegrationTests extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from reservation_items").update();
        jdbc.sql("delete from reservation_events").update();
        jdbc.sql("delete from notifications").update();
        jdbc.sql("delete from reservations").update();
        jdbc.sql("delete from pets").update();
        jdbc.sql("delete from clients").update();
        jdbc.sql("delete from email_delivery_jobs").update();
        jdbc.sql("delete from email_verification_tokens").update();
        jdbc.sql("delete from password_reset_tokens").update();
        jdbc.sql("delete from user_external_identities").update();
        jdbc.sql("delete from users").update();
        jdbc.sql("delete from services").update();
    }

    @Test
    void profileAndPetsAlwaysResolveFromPrincipalUserIdNotSpoofedClientId() throws Exception {
        Fixture ana = client("ana@example.com", "Ana", "+56911111111");
        Fixture bob = client("bob@example.com", "Bob", "+56922222222");
        long anaPet = pet(ana.clientId(), "Milo", true);
        long bobPet = pet(bob.clientId(), "Luna", true);
        pet(ana.clientId(), "Archivada", false);

        AccountPrincipal spoofed = principal(
                ana.userId(), bob.clientId(), "ana@example.com", "Ana");
        mvc.perform(get("/api/v1/me/profile").with(user(spoofed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.clientId()))
                .andExpect(jsonPath("$.email").value("ana@example.com"));
        mvc.perform(patch("/api/v1/me/profile")
                        .with(user(spoofed)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Ana actualizada \",\"phone\":\" +56933333333 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.clientId()))
                .andExpect(jsonPath("$.name").value("Ana actualizada"))
                .andExpect(jsonPath("$.phone").value("+56933333333"));
        mvc.perform(get("/api/v1/me/pets").with(user(spoofed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(anaPet));

        mvc.perform(patch("/api/v1/me/pets/{id}", bobPet)
                        .with(user(spoofed)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Intruso","species":"Gato"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCanCreateTrimUpdateAndArchivePetWithoutHardDelete() throws Exception {
        Fixture ana = client("ana@example.com", "Ana", "+56911111111");
        AccountPrincipal principal = principal(ana.userId(), ana.clientId(), ana.email(), "Ana");

        mvc.perform(post("/api/v1/me/pets")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Milo ","species":" Gato ","breed":" ",
                                 "birthdate":"2020-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Milo"))
                .andExpect(jsonPath("$.breed").doesNotExist());
        long petId = jdbc.sql("select id from pets").query(Long.class).single();

        mvc.perform(patch("/api/v1/me/pets/{id}", petId)
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mili","species":"Gato","breed":"Mestiza"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mili"));
        mvc.perform(delete("/api/v1/me/pets/{id}", petId)
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql("select active from pets where id=:id")
                .param("id", petId).query(Boolean.class).single()).isFalse();
        mvc.perform(patch("/api/v1/me/pets/{id}", petId)
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Otra\",\"species\":\"Gato\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/pets/{id}", petId)
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void futurePendingOrConfirmedReservationsPreventArchiveButHistoryAndPastDoNot() throws Exception {
        Fixture ana = client("ana@example.com", "Ana", "+56911111111");
        long petId = pet(ana.clientId(), "Milo", true);
        long serviceId = service();
        long reservationId = reservation(ana.clientId(), Instant.now().plusSeconds(3600), "PENDING");
        reservationItem(reservationId, petId, serviceId);
        AccountPrincipal principal = principal(ana.userId(), ana.clientId(), ana.email(), "Ana");

        mvc.perform(delete("/api/v1/me/pets/{id}", petId)
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PET_HAS_FUTURE_RESERVATION"))
                .andExpect(jsonPath("$.message").value(
                        "Cancela o resuelve primero las reservas futuras de esta mascota."));

        jdbc.sql("update reservations set status='CANCELLED' where id=:id")
                .param("id", reservationId).update();
        mvc.perform(delete("/api/v1/me/pets/{id}", petId)
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.sql("select count(*) from reservation_items where pet_id=:id")
                .param("id", petId).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void cancelledCompletedNoShowPastAndExactNowReservationsDoNotBlockArchive() throws Exception {
        Fixture ana = client("ana@example.com", "Ana", "+56911111111");
        long serviceId = service();
        AccountPrincipal principal = principal(ana.userId(), ana.clientId(), ana.email(), "Ana");
        String[] statuses = {"CANCELLED", "COMPLETED", "NO_SHOW", "PENDING", "CONFIRMED"};

        for (int index = 0; index < statuses.length; index++) {
            long petId = pet(ana.clientId(), "Milo-" + index, true);
            Instant start = index == statuses.length - 1
                    ? Instant.now()
                    : (index == statuses.length - 2
                        ? Instant.now().minusSeconds(3600)
                        : Instant.now().plusSeconds(3600));
            long reservationId = reservation(ana.clientId(), start, statuses[index]);
            reservationItem(reservationId, petId, serviceId);

            mvc.perform(delete("/api/v1/me/pets/{id}", petId)
                            .with(user(principal)).with(csrf()))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void validationRolesAndCsrfAreEnforced() throws Exception {
        Fixture ana = client("ana@example.com", "Ana", "+56911111111");
        AccountPrincipal principal = principal(ana.userId(), ana.clientId(), ana.email(), "Ana");

        mvc.perform(post("/api/v1/me/pets")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"species\":\"Gato\",\"birthdate\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/me/pets")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Milo\",\"species\":\"Gato\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/pets").with(user(admin())))
                .andExpect(status().isForbidden());
    }

    private Fixture client(String email, String name, String phone) {
        long userId = jdbc.sql("""
                insert into users(email_normalized,password_hash,email_verified_at,account_type,enabled)
                values (:email,'{noop}password',now(),'CLIENT',true) returning id
                """).param("email", email).query(Long.class).single();
        long clientId = jdbc.sql("""
                insert into clients(user_id,name,phone) values (:userId,:name,:phone) returning id
                """).param("userId", userId).param("name", name).param("phone", phone)
                .query(Long.class).single();
        return new Fixture(userId, clientId, email);
    }

    private long pet(long clientId, String name, boolean active) {
        return jdbc.sql("""
                insert into pets(client_id,name,species,active)
                values (:clientId,:name,'Gato',:active) returning id
                """).param("clientId", clientId).param("name", name).param("active", active)
                .query(Long.class).single();
    }

    private long service() {
        return jdbc.sql("""
                insert into services(code,name) values ('consulta','Consulta') returning id
                """).query(Long.class).single();
    }

    private long reservation(long clientId, Instant start, String status) {
        return jdbc.sql("""
                insert into reservations(client_id,scheduled_start,scheduled_end,status)
                values (:clientId,:start,:end,:status) returning id
                """).param("clientId", clientId).param("start", start)
                .param("end", start.plusSeconds(1800)).param("status", status)
                .query(Long.class).single();
    }

    private void reservationItem(long reservationId, long petId, long serviceId) {
        jdbc.sql("""
                insert into reservation_items(reservation_id,pet_id,service_id,service_name_snapshot)
                values (:reservationId,:petId,:serviceId,'Consulta')
                """).param("reservationId", reservationId).param("petId", petId)
                .param("serviceId", serviceId).update();
    }

    private AccountPrincipal principal(long userId, long clientId, String email, String name) {
        return new AccountPrincipal(userId, clientId, email, name,
                AccountType.CLIENT, "{noop}password", true, true);
    }

    private AccountPrincipal admin() {
        return new AccountPrincipal(999L, null, "admin@example.com", "Admin",
                AccountType.ADMIN, "{noop}password", true, true);
    }

    private record Fixture(long userId, long clientId, String email) {
    }
}
