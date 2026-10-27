package com.casms.ship.entity;

import com.casms.servicepackage.entity.TourPackage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CruiseTourPackage\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cruise_id", "tour_package_id"}))
public class CruiseTourPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cruise_tour_package_id")
    private UUID cruiseTourPackageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = false;

    @Column(name = "entitlement_type", nullable = false, length = 30)
    private String entitlementType = "OPTIONAL_ADDON";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
