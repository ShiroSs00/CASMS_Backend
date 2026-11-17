package com.casms.ship.entity;

import com.casms.ship.enums.ShipStatus;
import com.casms.ship.dto.request.ShipOperationalProfile;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "\"Ship\"")
public class Ship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ship_id")
    private UUID shipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_type_id")
    private ShipType shipType;

    @Column(name = "created_by_ship_manager_id")
    private UUID createdByShipManagerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operational_profile", columnDefinition = "jsonb")
    private ShipOperationalProfile operationalProfile;

    @Column(name = "design_model_key", length = 80)
    private String designModelKey;

    @Column(name = "design_asset_version", length = 80)
    private String designAssetVersion;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

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

    /** Highest passenger Deck ordinal allowed; may exceed count when numbering has gaps. */
    @Column(name = "deck_number_limit")
    private Integer deckNumberLimit;

    /** Source template identity; multiple physical ships may reuse the same uploaded model. */
    @Column(name = "manifest_model_id", length = 80)
    private String manifestModelId;

    @Column(name = "length_meters")
    private Double lengthMeters;

    @Column(name = "beam_meters")
    private Double beamMeters;

    @Column(name = "commission_date")
    private LocalDate commissionDate;

    @Column(name = "manufacturer_name", length = 150)
    private String manufacturerName;

    @Column(name = "origin", length = 100)
    private String origin;

    @Column(name = "production_year")
    private Integer productionYear;

    @Column(name = "engine_type", length = 100)
    private String engineType;

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
