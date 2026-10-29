package com.casms.ship.repository;

import com.casms.ship.entity.DeckServiceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeckServiceAssignmentRepository extends JpaRepository<DeckServiceAssignment, UUID> {
    @EntityGraph(attributePaths = {"deck", "deck.ship", "service"})
    List<DeckServiceAssignment> findByDeckDeckIdOrderByCreatedAtAscAssignmentIdAsc(UUID deckId);
    Optional<DeckServiceAssignment> findByDeckDeckIdAndServiceServiceId(UUID deckId, UUID serviceId);
}
