package com.casms.ship.entity;

import com.casms.ship.enums.ModelImportStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ModelImportJob\"", indexes = {
        @Index(name = "idx_model_import_job_model_created", columnList = "ship_model_id,started_at")
})
public class ModelImportJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "model_import_job_id")
    private UUID modelImportJobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ship_model_id", nullable = false)
    private ShipModel shipModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ModelImportStatus status = ModelImportStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "requested_by", length = 120)
    private String requestedBy;

    @Column(name = "total_objects", nullable = false)
    private int totalObjects;

    @Column(name = "decks_imported", nullable = false)
    private int decksImported;

    @Column(name = "cabins_imported", nullable = false)
    private int cabinsImported;

    @Column(name = "objects_succeeded", nullable = false)
    private int objectsSucceeded;

    @Column(name = "objects_failed", nullable = false)
    private int objectsFailed;

    @Column(name = "objects_skipped", nullable = false)
    private int objectsSkipped;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = Instant.now();
    }
}
