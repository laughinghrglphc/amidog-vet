package com.amidog.app.repositories;

import com.amidog.app.models.Client;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;


@Repository
public interface ReservationRepository extends JpaRepository<Client, Long> {
}
