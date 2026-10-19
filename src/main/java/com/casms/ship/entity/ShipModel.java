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

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "file_uri", nullable = false, length = 1000)
    private String fileUri;

    @Column(name = "file_format", nullable = false, length = 10)
    private String fileFormat;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

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
