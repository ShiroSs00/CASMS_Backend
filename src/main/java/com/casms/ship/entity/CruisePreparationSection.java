package com.casms.ship.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CruisePreparationSection\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cruise_id", "section_code"}))
public class CruisePreparationSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "preparation_id")
    private UUID preparationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @Column(name = "section_code", nullable = false, length = 50)
    private String sectionCode;

    @Column(name = "section_name", nullable = false, length = 100)
    private String sectionName;

    @Column(name = "owner_role", nullable = false, length = 50)
    private String ownerRole;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "confirmed_by_user_id")
    private UUID confirmedByUserId;

    @Column(name = "confirmed_by_username", length = 100)
    private String confirmedByUsername;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "returned_by_user_id")
    private UUID returnedByUserId;

    @Column(name = "return_reason", columnDefinition = "TEXT")
    private String returnReason;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Version
    @Column(name = "revision", nullable = false)
    private Long revision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (revision == null) revision = 0L;
        if (status == null) status = "NOT_STARTED";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
