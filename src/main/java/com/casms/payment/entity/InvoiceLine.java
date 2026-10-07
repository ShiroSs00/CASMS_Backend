package com.casms.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"InvoiceLine\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"invoice_id", "line_no"}))
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_line_id")
    private UUID invoiceLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_charge_id")
    private Charge sourceCharge;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "description_snapshot", nullable = false, columnDefinition = "text")
    private String descriptionSnapshot;

    @Column(name = "quantity_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantitySnapshot;

    @Column(name = "base_amount_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseAmountSnapshot;

    @Column(name = "tax_amount_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmountSnapshot;

    @Column(name = "total_amount_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmountSnapshot;

    @Column(name = "booking_fare_amount_snapshot", precision = 19, scale = 4)
    private BigDecimal bookingFareAmountSnapshot;
}
