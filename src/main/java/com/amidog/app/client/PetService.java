package com.amidog.app.client;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import com.amidog.app.common.api.NotFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

import static com.amidog.app.client.ClientDtos.PetResponse;
import static com.amidog.app.client.ClientDtos.PetWriteRequest;

@Service
public class PetService {

    static final String NOT_FOUND = "No se encontró la mascota.";

    private final CurrentClient currentClient;
    private final PetRepository pets;
    private final PetReservationGuard reservationGuard;
    private final Clock clock;

    public PetService(
            CurrentClient currentClient,
            PetRepository pets,
            PetReservationGuard reservationGuard,
            Clock clock) {
        this.currentClient = currentClient;
        this.pets = pets;
        this.reservationGuard = reservationGuard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PetResponse> list(AccountPrincipal principal) {
        long clientId = currentClient.require(principal).getId();
        return pets.findAllByClientIdAndActiveTrueOrderByNameAscIdAsc(clientId)
                .stream().map(PetResponse::from).toList();
    }

    @Transactional
    public PetResponse create(AccountPrincipal principal, PetWriteRequest request) {
        Client client = currentClient.require(principal);
        Pet pet = Pet.create(
                client, request.name(), request.species(), request.breed(),
                request.birthdate(), clock.instant());
        return PetResponse.from(pets.saveAndFlush(pet));
    }

    @Transactional
    public PetResponse update(AccountPrincipal principal, long petId, PetWriteRequest request) {
        long clientId = currentClient.require(principal).getId();
        Pet pet = pets.findActiveOwnedForUpdate(petId, clientId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        pet.update(
                request.name(), request.species(), request.breed(),
                request.birthdate(), clock.instant());
        try {
            return PetResponse.from(pets.saveAndFlush(pet));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConflictException(ConflictType.PET_CONCURRENT_UPDATE);
        }
    }

    @Transactional
    public void archive(AccountPrincipal principal, long petId) {
        long clientId = currentClient.require(principal).getId();
        // The lock must precede the guard query. Reservation creation shares this
        // lock protocol, closing the create/archive time-of-check/time-of-use gap.
        Pet pet = pets.findActiveOwnedForUpdate(petId, clientId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        if (reservationGuard.hasFutureOccupied(petId, clock.instant())) {
            throw new ConflictException(ConflictType.PET_HAS_FUTURE_RESERVATION);
        }
        pet.archive(clock.instant());
        pets.saveAndFlush(pet);
    }
}
