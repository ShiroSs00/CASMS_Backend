package com.casms.ship.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Deck\"",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"ship_id", "deck_number"}),
           @UniqueConstraint(columnNames = {"deck_id", "ship_id"})
       })
public class Deck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "deck_id")
    private UUID deckId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @Column(name = "deck_number", nullable = false)
    private Integer deckNumber;

    @Column(name = "deck_name", length = 50)
    private String deckName;

    @Column(name = "model_position_x")
    private Double modelPositionX;
    @Column(name = "model_position_y")
    private Double modelPositionY;
    @Column(name = "model_position_z")
    private Double modelPositionZ;
    @Column(name = "model_rotation_x")
    private Double modelRotationX;
    @Column(name = "model_rotation_y")
    private Double modelRotationY;
    @Column(name = "model_rotation_z")
    private Double modelRotationZ;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
