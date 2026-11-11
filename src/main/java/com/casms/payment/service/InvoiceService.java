package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.payment.dto.response.InvoiceLineResponse;
import com.casms.payment.dto.response.InvoiceResponse;
import com.casms.payment.entity.*;
import com.casms.payment.enums.InvoiceDocumentType;
import com.casms.payment.enums.InvoiceStatus;
import com.casms.payment.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final BookingRepository bookingRepository;
    private final OnboardAccountRepository onboardAccountRepository;
    private final ChargeRepository chargeRepository;

    @Transactional
    public InvoiceResponse generateBookingInvoice(UUID bookingId) {
        Booking booking =
                bookingRepository
                        .findById(bookingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        Optional<Invoice> existing =
                invoiceRepository.findFirstByBooking_BookingIdOrderByVersionNoDesc(bookingId);
        int versionNo = existing.map(i -> i.getVersionNo() + 1).orElse(1);

        String reference =
                "INV-BK-"
                        + booking.getBookingReference()
                        + "-V"
                        + versionNo
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        Invoice invoice = new Invoice();
        invoice.setBooking(booking);
        invoice.setInvoiceReference(reference);
        invoice.setDocumentType(InvoiceDocumentType.BookingInvoice);
        invoice.setVersionNo(versionNo);
        invoice.setIssuedAt(Instant.now());
        invoice.setSourceCutoffAt(Instant.now());
        invoice.setCurrency(booking.getCurrency() != null ? booking.getCurrency() : "USD");
        invoice.setSnapshotTotalAmount(booking.getAgreedFareAmount());
        invoice.setSnapshotNetAmountDue(booking.getAgreedFareAmount());
        invoice.setStatus(InvoiceStatus.Issued);
        invoice.setDocumentReference("DOC-" + reference);

        Invoice savedInvoice = invoiceRepository.save(invoice);

        InvoiceLine line = new InvoiceLine();
        line.setInvoice(savedInvoice);
        line.setLineNo(1);
        line.setDescriptionSnapshot(
                "Giá vé du thuyền trọn gói (Cruise Tour Fare) - Mã đặt chỗ: "
                        + booking.getBookingReference());
        line.setQuantitySnapshot(BigDecimal.ONE);
        line.setBaseAmountSnapshot(booking.getAgreedFareAmount());
        line.setTaxAmountSnapshot(BigDecimal.ZERO);
        line.setTotalAmountSnapshot(booking.getAgreedFareAmount());
        line.setBookingFareAmountSnapshot(booking.getAgreedFareAmount());
        line.setSourceCharge(null);

        InvoiceLine savedLine = invoiceLineRepository.save(line);

        return toInvoiceResponse(savedInvoice, List.of(savedLine));
    }

    @Transactional
    public InvoiceResponse generateOnboardInvoice(
            UUID onboardAccountId, InvoiceDocumentType documentType) {
        if (documentType != InvoiceDocumentType.PreliminaryFolio
                && documentType != InvoiceDocumentType.FinalStatement) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DOCUMENT_TYPE",
                    "Document type must be PreliminaryFolio or FinalStatement");
        }

        OnboardAccount account =
                onboardAccountRepository
                        .findById(onboardAccountId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "OnboardAccount", onboardAccountId));

        List<Charge> charges =
                chargeRepository
                        .findByTransaction_OnboardAccount_OnboardAccountIdOrderByChargedAtAsc(
                                onboardAccountId);

        Optional<Invoice> existing =
                invoiceRepository.findFirstByOnboardAccount_OnboardAccountIdOrderByVersionNoDesc(
                        onboardAccountId);
        int versionNo = existing.map(i -> i.getVersionNo() + 1).orElse(1);

        String shortId = onboardAccountId.toString().substring(0, 8).toUpperCase();
        String reference =
                "INV-OB-"
                        + shortId
                        + "-V"
                        + versionNo
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Charge c : charges) {
            totalAmount = totalAmount.add(c.getNetAmount());
        }

        Invoice invoice = new Invoice();
        invoice.setOnboardAccount(account);
        invoice.setInvoiceReference(reference);
        invoice.setDocumentType(documentType);
        invoice.setVersionNo(versionNo);
        invoice.setIssuedAt(Instant.now());
        invoice.setSourceCutoffAt(Instant.now());
        invoice.setCurrency("USD");
        invoice.setSnapshotTotalAmount(totalAmount);
        invoice.setSnapshotNetAmountDue(totalAmount);
        invoice.setStatus(InvoiceStatus.Issued);
        invoice.setDocumentReference("DOC-" + reference);

        Invoice savedInvoice = invoiceRepository.save(invoice);

        List<InvoiceLine> savedLines = new ArrayList<>();
        int lineNo = 1;
        for (Charge c : charges) {
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(savedInvoice);
            line.setLineNo(lineNo++);
            String passengerName =
                    c.getPassenger() != null
                            ? (c.getPassenger().getFirstName()
                                    + " "
                                    + c.getPassenger().getLastName())
                            : "Khách hàng";
            line.setDescriptionSnapshot(
                    c.getSourceType()
                            + " - "
                            + passengerName
                            + " (Ref: "
                            + c.getSourceReferenceId()
                            + ")");
            line.setQuantitySnapshot(
                    c.getQuantity() != null && c.getQuantity().compareTo(BigDecimal.ZERO) > 0
                            ? c.getQuantity()
                            : BigDecimal.ONE);
            line.setBaseAmountSnapshot(
                    c.getGrossAmount() != null ? c.getGrossAmount() : c.getNetAmount());
            line.setTaxAmountSnapshot(BigDecimal.ZERO);
            line.setTotalAmountSnapshot(c.getNetAmount());
            line.setSourceCharge(c);
            line.setBookingFareAmountSnapshot(null);
            savedLines.add(invoiceLineRepository.save(line));
        }

        return toInvoiceResponse(savedInvoice, savedLines);
    }

    @Transactional
    public InvoiceResponse generateAdjustmentInvoice(
            UUID originalInvoiceId, BigDecimal adjustmentAmount, String reason) {
        Invoice original =
                invoiceRepository
                        .findById(originalInvoiceId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Invoice", originalInvoiceId));

        original.setStatus(InvoiceStatus.Superseded);
        invoiceRepository.save(original);

        int newVersion = original.getVersionNo() + 1;
        String reference =
                original.getInvoiceReference()
                        + "-ADJ-V"
                        + newVersion
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        BigDecimal newTotal = original.getSnapshotTotalAmount().subtract(adjustmentAmount);
        if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
            newTotal = BigDecimal.ZERO;
        }
        BigDecimal newNetDue = original.getSnapshotNetAmountDue().subtract(adjustmentAmount);
        if (newNetDue.compareTo(BigDecimal.ZERO) < 0) {
            newNetDue = BigDecimal.ZERO;
        }

        Invoice adjInvoice = new Invoice();
        adjInvoice.setBooking(original.getBooking());
        adjInvoice.setOnboardAccount(original.getOnboardAccount());
        adjInvoice.setInvoiceReference(reference);
        adjInvoice.setDocumentType(InvoiceDocumentType.AdjustmentStatement);
        adjInvoice.setVersionNo(newVersion);
        adjInvoice.setIssuedAt(Instant.now());
        adjInvoice.setSourceCutoffAt(Instant.now());
        adjInvoice.setCurrency(original.getCurrency());
        adjInvoice.setSnapshotTotalAmount(newTotal);
        adjInvoice.setSnapshotNetAmountDue(newNetDue);
        adjInvoice.setStatus(InvoiceStatus.Issued);
        adjInvoice.setDocumentReference("DOC-" + reference);

        Invoice saved = invoiceRepository.save(adjInvoice);

        // Copy existing lines
        List<InvoiceLine> oldLines =
                invoiceLineRepository.findByInvoice_InvoiceIdOrderByLineNoAsc(originalInvoiceId);
        List<InvoiceLine> newLines = new ArrayList<>();
        int lineNo = 1;
        for (InvoiceLine old : oldLines) {
            InvoiceLine newLine = new InvoiceLine();
            newLine.setInvoice(saved);
            newLine.setLineNo(lineNo++);
            newLine.setDescriptionSnapshot(old.getDescriptionSnapshot());
            newLine.setQuantitySnapshot(old.getQuantitySnapshot());
            newLine.setBaseAmountSnapshot(old.getBaseAmountSnapshot());
            newLine.setTaxAmountSnapshot(old.getTaxAmountSnapshot());
            newLine.setTotalAmountSnapshot(old.getTotalAmountSnapshot());
            newLine.setSourceCharge(old.getSourceCharge());
            newLine.setBookingFareAmountSnapshot(old.getBookingFareAmountSnapshot());
            newLines.add(invoiceLineRepository.save(newLine));
        }

        return toInvoiceResponse(saved, newLines);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceDetail(UUID invoiceId) {
        Invoice invoice =
                invoiceRepository
                        .findById(invoiceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        List<InvoiceLine> lines =
                invoiceLineRepository.findByInvoice_InvoiceIdOrderByLineNoAsc(invoiceId);
        return toInvoiceResponse(invoice, lines);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesForBooking(UUID bookingId) {
        List<Invoice> invoices =
                invoiceRepository.findByBooking_BookingIdOrderByIssuedAtDesc(bookingId);
        return invoices.stream()
                .map(
                        inv ->
                                toInvoiceResponse(
                                        inv,
                                        invoiceLineRepository
                                                .findByInvoice_InvoiceIdOrderByLineNoAsc(
                                                        inv.getInvoiceId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesForOnboardAccount(UUID onboardAccountId) {
        List<Invoice> invoices =
                invoiceRepository.findByOnboardAccount_OnboardAccountIdOrderByIssuedAtDesc(
                        onboardAccountId);
        return invoices.stream()
                .map(
                        inv ->
                                toInvoiceResponse(
                                        inv,
                                        invoiceLineRepository
                                                .findByInvoice_InvoiceIdOrderByLineNoAsc(
                                                        inv.getInvoiceId())))
                .toList();
    }

    private InvoiceResponse toInvoiceResponse(Invoice inv, List<InvoiceLine> lines) {
        List<InvoiceLineResponse> lineResponses =
                lines.stream()
                        .map(
                                l ->
                                        InvoiceLineResponse.builder()
                                                .invoiceLineId(l.getInvoiceLineId())
                                                .invoiceId(inv.getInvoiceId())
                                                .sourceChargeId(
                                                        l.getSourceCharge() != null
                                                                ? l.getSourceCharge().getChargeId()
                                                                : null)
                                                .lineNo(l.getLineNo())
                                                .descriptionSnapshot(l.getDescriptionSnapshot())
                                                .quantitySnapshot(l.getQuantitySnapshot())
                                                .baseAmountSnapshot(l.getBaseAmountSnapshot())
                                                .taxAmountSnapshot(l.getTaxAmountSnapshot())
                                                .totalAmountSnapshot(l.getTotalAmountSnapshot())
                                                .bookingFareAmountSnapshot(
                                                        l.getBookingFareAmountSnapshot())
                                                .build())
                        .toList();

        return InvoiceResponse.builder()
                .invoiceId(inv.getInvoiceId())
                .bookingId(inv.getBooking() != null ? inv.getBooking().getBookingId() : null)
                .bookingReference(
                        inv.getBooking() != null ? inv.getBooking().getBookingReference() : null)
                .onboardAccountId(
                        inv.getOnboardAccount() != null
                                ? inv.getOnboardAccount().getOnboardAccountId()
                                : null)
                .invoiceReference(inv.getInvoiceReference())
                .documentType(inv.getDocumentType())
                .versionNo(inv.getVersionNo())
                .issuedAt(inv.getIssuedAt())
                .sourceCutoffAt(inv.getSourceCutoffAt())
                .currency(inv.getCurrency())
                .snapshotTotalAmount(inv.getSnapshotTotalAmount())
                .snapshotNetAmountDue(inv.getSnapshotNetAmountDue())
                .status(inv.getStatus())
                .documentReference(inv.getDocumentReference())
                .lines(lineResponses)
                .build();
    }
}
