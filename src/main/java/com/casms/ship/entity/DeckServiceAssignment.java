package com.casms.ship.entity;

import com.casms.servicepackage.entity.Service;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/** A manually assigned catalogue service at a physical deck; independent of model versions. */
@Getter @Setter
@Entity
@Table(name = "\"DeckServiceAssignment\"", uniqueConstraints =
        @UniqueConstraint(name = "uk_deck_service_assignment", columnNames = {"deck_id", "service_id"}))
public class DeckServiceAssignment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id")
    private UUID assignmentId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
