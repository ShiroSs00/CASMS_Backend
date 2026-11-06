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

    @Query(
            "SELECT d FROM Destination d WHERE (cast(:search as string) IS NULL OR"
                + " LOWER(d.destinationName) LIKE LOWER(CONCAT('%', cast(:search as string), '%')))"
                + " AND (cast(:countryCode as string) IS NULL OR d.countryCode = :countryCode) AND"
                + " (cast(:status as string) IS NULL OR d.status = :status)")
    org.springframework.data.domain.Page<Destination> findDirectory(
            @Param("search") String search,
            @Param("countryCode") String countryCode,
            @Param("status") DestinationStatus status,
            org.springframework.data.domain.Pageable pageable);

    Optional<Destination> findByDestinationName(String destinationName);

    boolean existsByDestinationName(String destinationName);

    List<Destination> findByStatus(DestinationStatus status);

    @Query("SELECT d FROM Destination d WHERE d.status = 'ACTIVE'")
    List<Destination> findAllActiveDestinations();

    @Query(
            value =
                    """
SELECT EXISTS (SELECT 1 FROM "DestinationVisit" WHERE destination_id=:id)
    OR EXISTS (SELECT 1 FROM "CruiseDestinationVisit" WHERE destination_id=:id)
    OR EXISTS (SELECT 1 FROM "Port" WHERE destination_id=:id)
    OR EXISTS (SELECT 1 FROM "CruiseStop" WHERE destination_id=:id)
    OR EXISTS (SELECT 1 FROM "TripPlanStop" WHERE destination_id=:id)
    OR EXISTS (SELECT 1 FROM "TripPlanEntry" WHERE destination_id=:id)
    OR EXISTS (SELECT 1 FROM "ShoreExcursion" WHERE destination_id=:id)
    OR EXISTS (SELECT 1 FROM "Trip" WHERE candidate_destinations LIKE '%' || CAST(:id AS text) || '%')
""",
            nativeQuery = true)
    boolean hasDirectoryReferences(@Param("id") UUID id);

    // Bulk delete deliberately bypasses Destination.ports cascade; FK constraints protect
    // concurrent references.
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE FROM \"Destination\" WHERE destination_id=:id", nativeQuery = true)
    int deleteDirectoryRow(@Param("id") UUID id);
}
