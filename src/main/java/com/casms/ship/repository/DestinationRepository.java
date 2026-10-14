package com.casms.ship.repository;

import com.casms.ship.entity.Destination;
import com.casms.ship.enums.DestinationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, UUID> {

    Optional<Destination> findByDestinationName(String destinationName);

    boolean existsByDestinationName(String destinationName);

    List<Destination> findByStatus(DestinationStatus status);

    @Query("SELECT d FROM Destination d WHERE d.status = 'ACTIVE'")
    List<Destination> findAllActiveDestinations();
}