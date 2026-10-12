package com.casms.ship.entity;

import com.casms.ship.enums.ShipStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Ship\"")
public class Ship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ship_id")
    private UUID shipId;

    @Column(name = "ship_code", nullable = false, unique = true, length = 20)
    private String shipCode;

    @Column(name = "ship_name", nullable = false, length = 100)
    private String shipName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipStatus status;

    @Column(name = "total_capacity", nullable = false)
    private Integer totalCapacity;

    @Column(name = "total_decks", nullable = false)
    private Integer totalDecks;

    @Column(name = "commission_date")
    private LocalDate commissionDate;

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
