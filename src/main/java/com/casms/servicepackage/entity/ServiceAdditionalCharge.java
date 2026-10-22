package com.casms.servicepackage.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.casms.booking.entity.BookingPassenger;
import com.casms.payment.entity.Charge;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "\"ServiceAdditionalCharge\"")
public class ServiceAdditionalCharge {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "service_additional_charge_id")
    private UUID serviceAdditionalChargeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_passenger_id")
    private BookingPassenger bookingPassenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    private Charge charge;

    @Column(name = "charge_code", nullable = false, unique = true, length = 50)
    private String chargeCode;

    @Column(name = "reason", nullable = false, length = 30)
    private String reason;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
