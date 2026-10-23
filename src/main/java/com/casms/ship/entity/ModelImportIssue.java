package com.casms.ship.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ModelImportIssue\"", indexes = {
        @Index(name = "idx_model_import_issue_job", columnList = "model_import_job_id")
})
public class ModelImportIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "model_import_issue_id")
    private UUID modelImportIssueId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_import_job_id", nullable = false)
    private ModelImportJob job;

    @Column(name = "object_identifier", length = 200)
    private String objectIdentifier;

    @Column(name = "object_type", length = 30)
    private String objectType;

    @Column(name = "error_code", nullable = false, length = 80)
    private String errorCode;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
