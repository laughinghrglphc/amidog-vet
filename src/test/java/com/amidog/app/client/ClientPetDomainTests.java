package com.amidog.app.client;

import com.amidog.app.auth.UserAccount;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ClientPetDomainTests {

    @Test
    void profileAndPetMutationsTrimAndAdvanceTimestampsWithoutReactivation() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant changed = created.plusSeconds(60);
        UserAccount account = UserAccount.client("a@example.com", "{noop}password", created);
        Client client = Client.create(account, "  Ana  ", "  +56911111111 ", created);
        Pet pet = Pet.create(client, " Milo ", " Gato ", "  ", LocalDate.of(2020, 1, 1), created);

        client.updateProfile(" Ana Pérez ", " +56922222222 ", changed);
        pet.update(" Mili ", " Gato ", " Mestiza ", LocalDate.of(2021, 2, 2), changed);
        pet.archive(changed.plusSeconds(1));
        pet.update("Otro", "Gato", null, null, changed.plusSeconds(2));

        assertThat(client.getName()).isEqualTo("Ana Pérez");
        assertThat(client.getPhone()).isEqualTo("+56922222222");
        assertThat(client.getUpdatedAt()).isEqualTo(changed);
        assertThat(pet.getName()).isEqualTo("Otro");
        assertThat(pet.getBreed()).isNull();
        assertThat(pet.isActive()).isFalse();
        assertThat(pet.getUpdatedAt()).isEqualTo(changed.plusSeconds(2));
    }

    @Test
    void archiveRepositoryMethodDeclaresPessimisticWriteProtocol() throws Exception {
        Method method = PetRepository.class.getMethod(
                "findActiveOwnedForUpdate", Long.class, Long.class);

        assertThat(method.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
