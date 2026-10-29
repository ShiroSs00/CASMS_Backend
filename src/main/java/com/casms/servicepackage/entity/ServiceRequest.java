package com.casms.servicepackage.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ServiceRequest\"")
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "service_request_id")
    private UUID serviceRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "request_time", nullable = false)
    private Instant requestTime;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ServiceRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_passenger_id")
    private BookingPassenger bookingPassenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_schedule_id")
    private ServiceSchedule serviceSchedule;

    @Column(name = "unit_price_snapshot", precision = 19, scale = 4)
    private BigDecimal unitPriceSnapshot;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_snapshot", columnDefinition = "char(3)")
    private String currencySnapshot;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

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
