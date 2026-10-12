package com.casms.ship.entity;

import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Cabin\"",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"ship_id", "cabin_number"}),
           @UniqueConstraint(columnNames = {"cabin_id", "ship_id"})
       })
public class Cabin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cabin_id")
    private UUID cabinId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    @Column(name = "cabin_number", nullable = false, length = 10)
    private String cabinNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "cabin_type", nullable = false)
    private CabinType cabinType;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CabinStatus status;

    @Column(name = "is_accessible", nullable = false)
    private Boolean isAccessible;

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

