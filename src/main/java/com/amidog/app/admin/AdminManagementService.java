package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.client.Pet;
import com.amidog.app.client.PetRepository;
import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import com.amidog.app.common.api.NotFoundException;
import com.amidog.app.scheduling.ClinicTime;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

import static com.amidog.app.admin.AdminDtos.AdminClientSummary;
import static com.amidog.app.admin.AdminDtos.AdminClientUpdateRequest;
import static com.amidog.app.admin.AdminDtos.AdminPetSummary;
import static com.amidog.app.admin.AdminDtos.AdminPetUpdateRequest;

@Service
public class AdminManagementService {

    private static final String CLIENT_NOT_FOUND =
            "No se encontr\u00f3 el cliente.";
    private static final String PET_NOT_FOUND =
            "No se encontr\u00f3 la mascota.";

    private final ClientRepository clients;
    private final PetRepository pets;
    private final ClinicTime clinicTime;
    private final Clock clock;

    public AdminManagementService(
            ClientRepository clients,
            PetRepository pets,
            ClinicTime clinicTime,
            Clock clock) {
        this.clients = clients;
        this.pets = pets;
        this.clinicTime = clinicTime;
        this.clock = clock;
    }

    @Transactional
    public AdminClientSummary updateClient(
            AccountPrincipal principal,
            long id,
            AdminClientUpdateRequest request) {
        requireAdministrator(principal);
        Client client = clients.findById(id)
                .orElseThrow(() ->
                        new NotFoundException(CLIENT_NOT_FOUND));
        client.updateByAdministrator(
                request.name(),
                request.phone(),
                request.active(),
                clock.instant());
        try {
            return clientSummary(clients.saveAndFlush(client));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConflictException(
                    ConflictType.CLIENT_CONCURRENT_UPDATE);
        }
    }

    @Transactional
    public AdminPetSummary updatePet(
            AccountPrincipal principal,
            long id,
            AdminPetUpdateRequest request) {
        requireAdministrator(principal);
        Pet pet = pets.findById(id)
                .orElseThrow(() ->
                        new NotFoundException(PET_NOT_FOUND));
        pet.update(
                request.name(),
                request.species(),
                request.breed(),
                request.birthdate(),
                clock.instant());
        try {
            return petSummary(pets.saveAndFlush(pet));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConflictException(
                    ConflictType.PET_CONCURRENT_UPDATE);
        }
    }

    private AdminClientSummary clientSummary(Client client) {
        return new AdminClientSummary(
                client.getId(),
                client.getName(),
                client.getUser().getEmailNormalized(),
                client.getPhone(),
                client.isActive(),
                clinicTime.toOffsetDateTime(client.getCreatedAt()),
                clinicTime.toOffsetDateTime(client.getUpdatedAt()));
    }

    private AdminPetSummary petSummary(Pet pet) {
        return new AdminPetSummary(
                pet.getId(),
                pet.getClient().getId(),
                pet.getClient().getName(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getBirthdate(),
                pet.isActive(),
                clinicTime.toOffsetDateTime(pet.getCreatedAt()),
                clinicTime.toOffsetDateTime(pet.getUpdatedAt()));
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
}
