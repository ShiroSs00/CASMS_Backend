package com.casms.ship.entity;

import com.casms.ship.enums.ShipModelStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ShipModel\"", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ship_model_version", columnNames = {"ship_id", "version"})
}, indexes = {
        @Index(name = "idx_ship_model_ship_status", columnList = "ship_id,status")
})
public class ShipModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ship_model_id")
    private UUID shipModelId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @Column(name = "version", nullable = false, length = 161)
    private String version;

    /** Optional identity for a versioned, server-hosted model asset bundle. */
    @Column(name = "model_key", length = 80)
    private String modelKey;

    @Column(name = "asset_revision", length = 160)
    private String assetRevision;

    @Column(name = "asset_version", length = 80)
    private String assetVersion;

    /** Vessel builder/manufacturer. This is separate from the model asset author/provider. */
    @Column(name = "ship_manufacturer_name", length = 200)
    private String shipManufacturerName;

    /** Organization or person that supplied/authored this 3D asset. */
    @Column(name = "model_provider", length = 200)
    private String modelProvider;

    @Column(name = "file_uri", nullable = false, length = 1000)
    private String fileUri;

    @Column(name = "file_format", nullable = false, length = 10)
    private String fileFormat;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    /** Coordinate frame used by the GLB nodes and configuration transforms. */
    @Column(name = "coordinate_system", nullable = false, length = 30)
    private String coordinateSystem = "MODEL_LOCAL";

    /** GLB values are model units; the demo does not establish a physical unit. */
    @Column(name = "coordinate_unit", nullable = false, length = 30)
    private String coordinateUnit = "model_unit";

    @Column(name = "model_units_per_meter", nullable = false)
    private Double modelUnitsPerMeter = 1.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipModelStatus status = ShipModelStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
