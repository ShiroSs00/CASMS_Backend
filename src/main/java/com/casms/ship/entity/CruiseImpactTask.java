package com.casms.ship.entity;

import com.casms.identity.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CruiseImpactTask\"")
public class CruiseImpactTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "task_id")
    private UUID taskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @Column(name = "task_code", nullable = false, unique = true, length = 50)
    private String taskCode;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "WARNING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

    @Column(name = "resolution_notes", columnDefinition = "text")
    private String resolutionNotes;

    @Column(name = "revision", nullable = false)
    private Long revision = 1L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (revision == null) {
            revision = 1L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        revision = (revision == null ? 1L : revision + 1L);
    }
}
