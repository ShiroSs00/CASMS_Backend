package com.casms.ship.repository;

import com.casms.ship.entity.Cabin;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CabinRepository extends JpaRepository<Cabin, UUID> {
    long countByShipShipId(UUID shipId);
    long countByShipShipIdAndStatus(UUID shipId, CabinStatus status);

    @Query("SELECT COALESCE(SUM(c.capacity), 0) FROM Cabin c WHERE c.ship.shipId = :shipId")
    Long sumCapacityByShipId(@Param("shipId") UUID shipId);
    boolean existsByDeckDeckId(UUID deckId);
    List<Cabin> findByDeckDeckId(UUID deckId);
    long countByDeckDeckId(UUID deckId);
    long countByDeckDeckIdAndStatus(UUID deckId, CabinStatus status);
    
    @Query("SELECT SUM(c.capacity) FROM Cabin c WHERE c.deck.deckId = :deckId")
    Long sumCapacityByDeckId(@Param("deckId") UUID deckId);

    boolean existsByShipShipIdAndCabinNumber(UUID shipId, String cabinNumber);

    @Query("SELECT c FROM Cabin c WHERE c.status = 'AVAILABLE' " +
           "AND (:shipId IS NULL OR c.ship.shipId = :shipId) " +
           "AND (:deckId IS NULL OR c.deck.deckId = :deckId) " +
           "AND (:cabinType IS NULL OR c.cabinType = :cabinType) " +
           "AND (:minCapacity IS NULL OR c.capacity >= :minCapacity) " +
           "AND (:isAccessible IS NULL OR c.isAccessible = :isAccessible)")
    List<Cabin> searchAvailable(@Param("shipId") UUID shipId,
                                @Param("deckId") UUID deckId,
                                @Param("cabinType") CabinType cabinType,
                                @Param("minCapacity") Integer minCapacity,
                                @Param("isAccessible") Boolean isAccessible);
}