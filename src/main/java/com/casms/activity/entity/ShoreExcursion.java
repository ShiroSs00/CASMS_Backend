package com.casms.activity.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Port;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ShoreExcursion\"")
public class ShoreExcursion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "shore_excursion_id")
    private UUID shoreExcursionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_stop_id")
    private CruiseStop cruiseStop;

    /** Cảng biển nơi tour tham quan diễn ra (cho phép liên kết trực tiếp không cần chuyến khởi hành) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id")
    private Port port;

    @Column(name = "excursion_code", nullable = false, length = 50)
    private String excursionCode;

    @Column(name = "excursion_name", nullable = false, length = 250)
    private String excursionName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "planned_return_time", nullable = false)
    private Instant plannedReturnTime;

    @Column(name = "meeting_point", nullable = false, length = 250)
    private String meetingPoint;

    @Column(name = "minimum_participants")
    private Integer minimumParticipants;

    @Column(name = "maximum_capacity", nullable = false)
    private Integer maximumCapacity;

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", columnDefinition = "char(3)")
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShoreExcursionStatus status;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

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
