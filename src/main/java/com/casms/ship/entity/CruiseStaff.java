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
@Table(name = "\"CruiseStaff\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cruise_id", "user_id"}))
public class CruiseStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cruise_staff_id")
    private UUID cruiseStaffId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role_in_cruise", nullable = false, length = 100)
    private String roleInCruise;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
