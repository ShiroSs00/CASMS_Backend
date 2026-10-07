package com.casms.payment.entity;

import com.casms.booking.entity.Booking;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Invoice\"")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboard_account_id")
    private OnboardAccount onboardAccount;

    @Column(name = "invoice_reference", nullable = false, unique = true, length = 255)
    private String invoiceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private InvoiceDocumentType documentType;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "source_cutoff_at", nullable = false)
    private Instant sourceCutoffAt;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "snapshot_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal snapshotTotalAmount;

    @Column(name = "snapshot_net_amount_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal snapshotNetAmountDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    @Column(name = "document_reference", length = 255)
    private String documentReference;
}
