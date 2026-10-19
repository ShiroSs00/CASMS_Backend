package com.casms.identity.repository;

import com.casms.identity.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, UUID> {
    java.util.Optional<Passenger> findByUser_UserId(UUID userId);
}
