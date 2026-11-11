package com.casms.payment.dto.response;

import com.casms.payment.enums.InvoiceDocumentType;
import com.casms.payment.enums.InvoiceStatus;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class InvoiceResponse {
    private UUID invoiceId;
    private UUID bookingId;
    private String bookingReference;
    private UUID onboardAccountId;
    private String invoiceReference;
    private InvoiceDocumentType documentType;
    private Integer versionNo;
    private Instant issuedAt;
    private Instant sourceCutoffAt;
    private String currency;
    private BigDecimal snapshotTotalAmount;
    private BigDecimal snapshotNetAmountDue;
    private InvoiceStatus status;
    private String documentReference;
    private List<InvoiceLineResponse> lines;
}

