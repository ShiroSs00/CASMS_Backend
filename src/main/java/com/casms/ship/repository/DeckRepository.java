package com.casms.ship.repository;

import com.casms.ship.entity.Deck;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeckRepository extends JpaRepository<Deck, UUID> {
    @Override
    @Query("SELECT d FROM Deck d WHERE d.ship.archivedAt IS NULL")
    Page<Deck> findAll(Pageable pageable);

    long countByShipShipId(UUID shipId);

    long countByShipShipIdAndServiceLevelFalse(UUID shipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deck d where d.deckId = :id")
    Optional<Deck> findByIdForUpdate(@Param("id") UUID id);

    List<Deck> findByShipShipIdOrderByDeckNumber(UUID shipId);

    Page<Deck> findByShipShipId(UUID shipId, Pageable pageable);

    @Query("""
            SELECT d FROM Deck d WHERE d.ship.archivedAt IS NULL AND EXISTS (SELECT crew.cruiseStaffId FROM CruiseStaff crew
                WHERE crew.cruise.ship=d.ship AND crew.user.userId=:user AND crew.status='ACTIVE'
                AND crew.roleInCruise='SHIP_MANAGER' AND crew.cruise.status NOT IN ('CANCELLED','COMPLETED'))
            """)
    Page<Deck> findManagedDecks(@Param("user") UUID user, Pageable pageable);

    boolean existsByShipShipIdAndDeckNumber(UUID shipId, Integer deckNumber);

    Optional<Deck> findByShipShipIdAndDeckNumber(UUID shipId, Integer deckNumber);

    @Query("SELECT COALESCE(MAX(d.deckNumber), 0) FROM Deck d WHERE d.ship.shipId = :shipId")
    Integer findMaxDeckNumberByShipId(@Param("shipId") UUID shipId);
}
