package com.casms.ship.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ShipType\"")
public class ShipType {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ship_type_id")
    private UUID shipTypeId;

    @Column(name = "type_code", nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "type_name", nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "model_key", nullable = false, length = 80)
    private String modelKey;

    @Column(name = "asset_version", nullable = false, length = 80)
    private String assetVersion;

    @Column(name = "manufacturer_name", nullable = false, length = 150)
    private String manufacturerName;

    @Column(name = "engine_type", nullable = false, length = 100)
    private String engineType;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "total_decks", nullable = false)
    private int totalDecks;

    @Column(name = "length_meters", nullable = false)
    private double lengthMeters;

    @Column(name = "beam_meters", nullable = false)
    private double beamMeters;
}
