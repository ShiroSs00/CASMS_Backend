package com.casms.payment.entity;

import com.casms.booking.entity.Booking;
import com.casms.payment.enums.SepayTransactionStatus;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"SepayTransaction\"")
public class SepayTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "sepay_id", nullable = false, unique = true)
    private Long sepayId;

    @Column(name = "gateway", length = 100)
    private String gateway;

    @Column(name = "transaction_date")
    private Instant transactionDate;

    @Column(name = "account_number", length = 100)
    private String accountNumber;

    @Column(name = "transfer_type", length = 20)
    private String transferType;

    @Column(name = "transfer_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal transferAmount;

    @Column(name = "accumulated", precision = 19, scale = 4)
    private BigDecimal accumulated;

    @Column(name = "code", length = 100)
    private String code;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "reference_code", length = 255)
    private String referenceCode;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SepayTransactionStatus status;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
