package com.casms.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"RefundLine\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"refund_id", "invoice_line_id"}))
public class RefundLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "refund_line_id")
    private UUID refundLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_line_id", nullable = false)
    private InvoiceLine invoiceLine;

    @Column(name = "base_refund_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseRefundAmount;

    @Column(name = "tax_refund_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxRefundAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_charge_id")
    private Charge reversalCharge;
}
