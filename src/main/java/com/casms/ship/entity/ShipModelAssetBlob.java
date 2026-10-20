package com.casms.ship.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Metadata for a PostgreSQL large object containing one model asset file. */
@Getter
@Setter
@Entity
@Table(name = "\"ShipModelAssetBlob\"", uniqueConstraints = @UniqueConstraint(
        name = "uk_ship_model_asset_blob_path", columnNames = {"model_key", "asset_version", "asset_path"}),
        indexes = @Index(name = "idx_ship_model_asset_blob_bundle", columnList = "model_key,asset_version"))
public class ShipModelAssetBlob {
    @Id
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "model_key", nullable = false, length = 80)
    private String modelKey;

    @Column(name = "asset_version", nullable = false, length = 80)
    private String assetVersion;

    @Column(name = "asset_path", nullable = false, length = 500)
    private String assetPath;

    @Column(name = "blob_oid", nullable = false, columnDefinition = "oid")
    private Long blobOid;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
