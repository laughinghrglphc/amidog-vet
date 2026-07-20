package com.amidog.app.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findAllByActiveTrueOrderByDisplayOrderAscNameAscCodeAsc();

    List<ServiceOffering> findAllByOrderByDisplayOrderAscNameAscCodeAsc();

    boolean existsByCode(String code);

    /**
     * Shared service mutation protocol: reservation creation must lock the
     * referenced service rows before checking that they are active.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select service from ServiceOffering service where service.id = :id")
    Optional<ServiceOffering> findByIdForUpdate(@Param("id") Long id);
}
