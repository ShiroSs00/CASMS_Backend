package com.casms.servicepackage.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.casms.servicepackage.enums.ServiceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Service\"")
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "service_code", nullable = false, unique = true, length = 50)
    private String serviceCode;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_category_id")
    private ServiceCategory category;

    @Column(name = "unit_code", length = 30)
    private String unitCode;

    @Column(name = "can_sell_independently", nullable = false)
    private boolean canSellIndependently = true;

    @Column(name = "can_buy_pre_cruise", nullable = false)
    private boolean canBuyPreCruise = true;

    @Column(name = "can_buy_onboard", nullable = false)
    private boolean canBuyOnboard = true;

    @Column(name = "requires_schedule", nullable = false)
    private boolean requiresSchedule;

    @Column(name = "has_allowance", nullable = false)
    private boolean hasAllowance;

    @Column(name = "applicability_scope", nullable = false, length = 20)
    private String applicabilityScope = "PASSENGER";

    @Column(name = "requires_staff", nullable = false)
    private boolean requiresStaff;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "advance_notice_minutes")
    private Integer advanceNoticeMinutes;

    @Column(name = "minimum_age")
    private Integer minimumAge;

    @Column(name = "maximum_age")
    private Integer maximumAge;

    @Column(name = "usage_terms", columnDefinition = "text")
    private String usageTerms;

    @Column(name = "cancellation_terms", columnDefinition = "text")
    private String cancellationTerms;

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", columnDefinition = "char(3)")
    private String currencyCode;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ServiceStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
