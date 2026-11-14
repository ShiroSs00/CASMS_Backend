package com.casms.payment.entity;

import com.casms.identity.entity.Passenger;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"RegisteredPaymentMethod\"")
public class RegisteredPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "payment_token", nullable = false, unique = true, length = 255)
    private String paymentToken;

    @Column(name = "provider_customer_reference", length = 255)
    private String providerCustomerReference;

    @Column(name = "card_brand", nullable = false, length = 30)
    private String cardBrand;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_last4", nullable = false, columnDefinition = "char(4)")
    private String cardLast4;

    @Column(name = "card_holder_name", length = 150)
    private String cardHolderName;

    @Column(name = "expiry_month", nullable = false)
    private Integer expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private Integer expiryYear;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "issuing_country", columnDefinition = "char(2)")
    private String issuingCountry;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "issuing_currency", nullable = false, columnDefinition = "char(3)")
    private String issuingCurrency = "USD";

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, EXPIRED, REVOKED

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

