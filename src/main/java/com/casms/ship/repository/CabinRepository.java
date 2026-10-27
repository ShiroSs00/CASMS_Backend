package com.casms.ship.repository;

import com.casms.ship.entity.Cabin;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@Repository
public interface CabinRepository extends JpaRepository<Cabin, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cabin c where c.cabinId = :cabinId")
    Optional<Cabin> findByIdForUpdate(@Param("cabinId") UUID cabinId);
    List<Cabin> findByShipShipIdOrderByDeckDeckNumberAscCabinNumberAsc(UUID shipId);
    Page<Cabin> findByShipShipId(UUID shipId, Pageable pageable);
    Page<Cabin> findByDeckDeckId(UUID deckId, Pageable pageable);
    long countByShipShipId(UUID shipId);
    long countByShipShipIdAndStatus(UUID shipId, CabinStatus status);
    long countByShipShipIdAndCabinType(UUID shipId, CabinType cabinType);
    long countByShipShipIdAndCabinTypeAndStatus(UUID shipId, CabinType cabinType, CabinStatus status);
    long countByShipShipIdAndCabinTypeAndIsAccessibleTrue(UUID shipId, CabinType cabinType);
    long countByShipShipIdAndCabinTypeAndIsFamilyCabinTrue(UUID shipId, CabinType cabinType);
    long countByShipShipIdAndCabinTypeAndHasConnectingDoorTrue(UUID shipId, CabinType cabinType);

    @Query("SELECT COALESCE(SUM(c.capacity), 0) FROM Cabin c WHERE c.ship.shipId = :shipId AND c.cabinType = :cabinType")
    Long sumCapacityByShipIdAndCabinType(@Param("shipId") UUID shipId, @Param("cabinType") CabinType cabinType);

    @Query("SELECT COALESCE(SUM(c.capacity), 0) FROM Cabin c WHERE c.ship.shipId = :shipId")
    Long sumCapacityByShipId(@Param("shipId") UUID shipId);
    boolean existsByDeckDeckId(UUID deckId);
    List<Cabin> findByDeckDeckId(UUID deckId);
    long countByDeckDeckId(UUID deckId);
    long countByDeckDeckIdAndStatus(UUID deckId, CabinStatus status);
    
    @Query("SELECT SUM(c.capacity) FROM Cabin c WHERE c.deck.deckId = :deckId")
    Long sumCapacityByDeckId(@Param("deckId") UUID deckId);

    boolean existsByShipShipIdAndCabinNumber(UUID shipId, String cabinNumber);
    Optional<Cabin> findByShipShipIdAndCabinNumber(UUID shipId, String cabinNumber);

    @Query("SELECT c FROM Cabin c WHERE c.status = 'AVAILABLE' " +
           "AND (cast(:shipId as uuid) IS NULL OR c.ship.shipId = :shipId) " +
           "AND (cast(:deckId as uuid) IS NULL OR c.deck.deckId = :deckId) " +
           "AND (cast(:cabinType as string) IS NULL OR c.cabinType = :cabinType) " +
           "AND (cast(:minCapacity as integer) IS NULL OR c.capacity >= :minCapacity) " +
           "AND (cast(:isAccessible as boolean) IS NULL OR c.isAccessible = :isAccessible)")
    List<Cabin> searchAvailable(@Param("shipId") UUID shipId,
                                @Param("deckId") UUID deckId,
                                @Param("cabinType") CabinType cabinType,
                                @Param("minCapacity") Integer minCapacity,
                                @Param("isAccessible") Boolean isAccessible);
}
