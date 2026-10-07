package com.casms.checkin.repository;

import com.casms.checkin.entity.PassengerIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PassengerIdentifierRepository extends JpaRepository<PassengerIdentifier, UUID> {
}
