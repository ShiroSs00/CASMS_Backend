package com.casms.payment.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class InvoiceLineResponse {
    private UUID invoiceLineId;
    private UUID invoiceId;
    private UUID sourceChargeId;
    private Integer lineNo;
    private String descriptionSnapshot;
    private BigDecimal quantitySnapshot;
    private BigDecimal baseAmountSnapshot;
    private BigDecimal taxAmountSnapshot;
    private BigDecimal totalAmountSnapshot;
    private BigDecimal bookingFareAmountSnapshot;
}

