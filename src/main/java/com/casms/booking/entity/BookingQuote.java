package com.casms.booking.entity;

import com.casms.booking.enums.PaymentPlan;
import com.casms.identity.entity.User;
import com.casms.ship.entity.Cruise;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "\"BookingQuote\"")
public class BookingQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "quote_code", nullable = false, unique = true, length = 50)
    private String quoteCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAmount;

    @Builder.Default
    @Column(name = "taxes_and_fees_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxesAndFeesAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "deposit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_plan", nullable = false, length = 30)
    private PaymentPlan paymentPlan;

    @Column(name = "final_payment_due_at", nullable = false)
    private Instant finalPaymentDueAt;

    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @Column(name = "quote_payload_json", columnDefinition = "TEXT")
    private String quotePayloadJson;

    @Column(name = "guest_token", length = 128)
    private String guestToken;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, COMMITTED, EXPIRED

    @Builder.Default
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt) || !"ACTIVE".equalsIgnoreCase(status);
    }
}
