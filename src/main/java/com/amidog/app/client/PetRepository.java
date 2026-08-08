package com.amidog.app.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findAllByClientIdAndActiveTrueOrderByNameAscIdAsc(Long clientId);

    Optional<Pet> findByIdAndClientIdAndActiveTrue(Long id, Long clientId);

    /**
     * Shared pet mutation protocol: Task 4 reservation creation must acquire this
     * same row lock before validating/creating reservation items.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select pet from Pet pet
            where pet.id = :petId and pet.client.id = :clientId and pet.active = true
            """)
    Optional<Pet> findActiveOwnedForUpdate(
            @Param("petId") Long petId,
            @Param("clientId") Long clientId);
}
