package com.casms.ship.repository;

import com.casms.ship.entity.Deck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeckRepository extends JpaRepository<Deck, UUID> {
    long countByShipShipId(UUID shipId);
    List<Deck> findByShipShipIdOrderByDeckNumber(UUID shipId);
    Page<Deck> findByShipShipId(UUID shipId, Pageable pageable);
    boolean existsByShipShipIdAndDeckNumber(UUID shipId, Integer deckNumber);
    Optional<Deck> findByShipShipIdAndDeckNumber(UUID shipId, Integer deckNumber);

    @Query("SELECT COALESCE(MAX(d.deckNumber), 0) FROM Deck d WHERE d.ship.shipId = :shipId")
    Integer findMaxDeckNumberByShipId(@Param("shipId") UUID shipId);
}
