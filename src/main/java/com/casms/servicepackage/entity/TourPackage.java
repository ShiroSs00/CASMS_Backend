package com.casms.servicepackage.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.enums.TourPackageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"TourPackage\"")
public class TourPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tour_package_id")
    private UUID tourPackageId;

    @Column(name = "package_code", nullable = false, unique = true, length = 50)
    private String packageCode;

    @Column(name = "package_name", nullable = false, length = 200)
    private String packageName;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false)
    private TourPackageType packageType;

    @Column(name = "cabin_selection_policy", nullable = false, length = 30)
    private String cabinSelectionPolicy = "AUTO_ASSIGN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_package_id")
    private TourPackage parentPackage;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, columnDefinition = "char(3)")
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TourPackageStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name="owner_cruise_id")
    private UUID ownerCruiseId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
