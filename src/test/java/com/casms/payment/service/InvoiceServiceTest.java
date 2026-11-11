package com.casms.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.payment.dto.response.InvoiceResponse;
import com.casms.payment.entity.*;
import com.casms.payment.enums.InvoiceDocumentType;
import com.casms.payment.enums.InvoiceStatus;
import com.casms.payment.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private OnboardAccountRepository onboardAccountRepository;
    @Mock private ChargeRepository chargeRepository;

    @InjectMocks private InvoiceService invoiceService;

    private UUID bookingId;
    private Booking booking;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookingReference("BK-TEST-01");
        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setCurrency("USD");
        booking.setAgreedFareAmount(new BigDecimal("1500.00"));
    }

    @Test
    @DisplayName("generateBookingInvoice creates version 1 invoice with tour fare line item")
    void testGenerateBookingInvoice() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(invoiceRepository.findFirstByBooking_BookingIdOrderByVersionNoDesc(bookingId))
                .thenReturn(Optional.empty());

        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(inv -> {
                    Invoice i = inv.getArgument(0);
                    i.setInvoiceId(UUID.randomUUID());
                    return i;
                });
        when(invoiceLineRepository.save(any(InvoiceLine.class)))
                .thenAnswer(inv -> {
                    InvoiceLine l = inv.getArgument(0);
                    l.setInvoiceLineId(UUID.randomUUID());
                    return l;
                });

        InvoiceResponse response = invoiceService.generateBookingInvoice(bookingId);

        assertNotNull(response);
        assertEquals(bookingId, response.getBookingId());
        assertEquals("BK-TEST-01", response.getBookingReference());
        assertEquals(1, response.getVersionNo());
        assertEquals(InvoiceDocumentType.BookingInvoice, response.getDocumentType());
        assertEquals(new BigDecimal("1500.00"), response.getSnapshotTotalAmount());
        assertEquals(1, response.getLines().size());
        assertEquals(new BigDecimal("1500.00"), response.getLines().get(0).getTotalAmountSnapshot());
    }

    @Test
    @DisplayName("generateAdjustmentInvoice creates adjustment line item with negative amount")
    void testGenerateAdjustmentInvoice() {
        UUID origId = UUID.randomUUID();
        Invoice orig = new Invoice();
        orig.setInvoiceId(origId);
        orig.setBooking(booking);
        orig.setInvoiceReference("INV-BK-01-V1");
        orig.setDocumentType(InvoiceDocumentType.BookingInvoice);
        orig.setVersionNo(1);
        orig.setCurrency("USD");
        orig.setSnapshotTotalAmount(new BigDecimal("1500.00"));
        orig.setSnapshotNetAmountDue(new BigDecimal("1500.00"));
        orig.setStatus(InvoiceStatus.Issued);

        InvoiceLine oldLine = new InvoiceLine();
        oldLine.setInvoiceLineId(UUID.randomUUID());
        oldLine.setInvoice(orig);
        oldLine.setLineNo(1);
        oldLine.setDescriptionSnapshot("Cruise fare");
        oldLine.setQuantitySnapshot(BigDecimal.ONE);
        oldLine.setBaseAmountSnapshot(new BigDecimal("1500.00"));
        oldLine.setTotalAmountSnapshot(new BigDecimal("1500.00"));

        when(invoiceRepository.findById(origId)).thenReturn(Optional.of(orig));
        when(invoiceLineRepository.findByInvoice_InvoiceIdOrderByLineNoAsc(origId))
                .thenReturn(List.of(oldLine));
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(inv -> {
                    Invoice i = inv.getArgument(0);
                    if (i.getInvoiceId() == null) i.setInvoiceId(UUID.randomUUID());
                    return i;
                });
        when(invoiceLineRepository.save(any(InvoiceLine.class)))
                .thenAnswer(inv -> {
                    InvoiceLine l = inv.getArgument(0);
                    l.setInvoiceLineId(UUID.randomUUID());
                    return l;
                });

        InvoiceResponse response =
                invoiceService.generateAdjustmentInvoice(
                        origId, new BigDecimal("200.00"), "Hành khách hủy một dịch vụ");

        assertNotNull(response);
        assertEquals(2, response.getVersionNo());
        assertEquals(InvoiceDocumentType.AdjustmentStatement, response.getDocumentType());
        assertEquals(new BigDecimal("1300.00"), response.getSnapshotTotalAmount());
        // 1 old line copied + 1 adjustment line added = 2 lines
        assertEquals(2, response.getLines().size());
        assertEquals(new BigDecimal("-200.00"), response.getLines().get(1).getTotalAmountSnapshot());
    }

    @Test
    @DisplayName("getInvoicesForBooking auto-generates invoice when none exists")
    void testGetInvoicesForBookingAutoGenerate() {
        when(invoiceRepository.findByBooking_BookingIdOrderByIssuedAtDesc(bookingId))
                .thenReturn(List.of());
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(invoiceRepository.findFirstByBooking_BookingIdOrderByVersionNoDesc(bookingId))
                .thenReturn(Optional.empty());

        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(inv -> {
                    Invoice i = inv.getArgument(0);
                    i.setInvoiceId(UUID.randomUUID());
                    return i;
                });
        when(invoiceLineRepository.save(any(InvoiceLine.class)))
                .thenAnswer(inv -> {
                    InvoiceLine l = inv.getArgument(0);
                    l.setInvoiceLineId(UUID.randomUUID());
                    return l;
                });

        List<InvoiceResponse> list = invoiceService.getInvoicesForBooking(bookingId);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("BK-TEST-01", list.get(0).getBookingReference());
    }
}
