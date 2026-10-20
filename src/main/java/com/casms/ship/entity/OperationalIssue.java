
package com.casms.ship.entity;

import com.casms.identity.entity.User;
import com.casms.ship.enums.OperationalIssueSeverity;
import com.casms.ship.enums.OperationalIssueStatus;
import com.casms.ship.enums.OperationalIssueType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"OperationalIssue\"")
public class OperationalIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "issue_id")
    private UUID issueId;

    @Column(name = "issue_code", nullable = false, unique = true, length = 30)
    private String issueCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id")
    private Cruise cruise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabin_id")
    private Cabin cabin;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, columnDefinition = "\"OperationalIssue_issue_type\"")
    private OperationalIssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, columnDefinition = "\"OperationalIssue_severity\"")
    private OperationalIssueSeverity severity;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "\"OperationalIssue_status\"")
    private OperationalIssueStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by", nullable = false)
    private User reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolution", columnDefinition = "text")
    private String resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (reportedAt == null) {
            reportedAt = now;
        }
        if (status == null) {
            status = OperationalIssueStatus.OPEN;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
