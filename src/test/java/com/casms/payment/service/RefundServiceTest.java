package com.casms.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casms.booking.entity.Booking;
import com.casms.common.exception.BusinessException;
import com.casms.identity.repository.UserRepository;
import com.casms.payment.dto.request.RefundApproveRequest;
import com.casms.payment.dto.request.RefundCompleteRequest;
import com.casms.payment.dto.request.RefundCreateRequest;
import com.casms.payment.dto.response.RefundResponse;
import com.casms.payment.entity.*;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.RefundStatus;
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
class RefundServiceTest {

    @Mock private RefundRepository refundRepository;
    @Mock private RefundLineRepository refundLineRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private FinalSettlementRepository finalSettlementRepository;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private UserRepository userRepository;
    @Mock private InvoiceService invoiceService;

    @InjectMocks private RefundService refundService;

    private UUID paymentId;
    private Payment payment;
    private Booking booking;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setBookingReference("BK-REFUND-01");

        payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setBooking(booking);
        payment.setStatus(PaymentStatus.Confirmed);
        payment.setConfirmedAmount(new BigDecimal("1000.00"));
        payment.setCurrency("USD");
    }

    @Test
    @DisplayName("createRefund successfully requests refund within limit")
    void testCreateRefundSuccess() {
        RefundCreateRequest req = new RefundCreateRequest();
        req.setOriginalPaymentId(paymentId);
        req.setRequestedAmount(new BigDecimal("300.00"));
        req.setRefundReason("Khách hủy dịch vụ spa");
        req.setIdempotencyKey("KEY-REF-1");

        when(refundRepository.findByIdempotencyKey("KEY-REF-1")).thenReturn(Optional.empty());
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(refundRepository.findByOriginalPayment_PaymentIdOrderByRequestedAtDesc(paymentId))
                .thenReturn(List.of());

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(inv -> {
                    Refund r = inv.getArgument(0);
                    r.setRefundId(UUID.randomUUID());
                    return r;
                });

        RefundResponse response = refundService.createRefund(req);

        assertNotNull(response);
        assertEquals(RefundStatus.Requested, response.getStatus());
        assertEquals(new BigDecimal("300.00"), response.getRequestedAmount());
        assertEquals("USD", response.getCurrency());
    }

    @Test
    @DisplayName("createRefund throws exception when cumulative refunds exceed payment total")
    void testCreateRefundExceedsCumulativeLimit() {
        // Giả sử trước đó đã có một yêu cầu hoàn $800
        Refund existingRef = new Refund();
        existingRef.setRefundId(UUID.randomUUID());
        existingRef.setStatus(RefundStatus.Approved);
        existingRef.setApprovedAmount(new BigDecimal("800.00"));

        RefundCreateRequest req = new RefundCreateRequest();
        req.setOriginalPaymentId(paymentId);
        req.setRequestedAmount(new BigDecimal("300.00")); // 800 + 300 = 1100 > 1000
        req.setRefundReason("Yêu cầu hoàn thêm");
        req.setIdempotencyKey("KEY-REF-2");

        when(refundRepository.findByIdempotencyKey("KEY-REF-2")).thenReturn(Optional.empty());
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(refundRepository.findByOriginalPayment_PaymentIdOrderByRequestedAtDesc(paymentId))
                .thenReturn(List.of(existingRef));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> refundService.createRefund(req));
        assertEquals("REFUND_EXCEEDS_PAYMENT", ex.getCode());
    }

    @Test
    @DisplayName("approveRefund sets status to Approved and validates positive amount")
    void testApproveRefund() {
        UUID refId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setRefundId(refId);
        refund.setStatus(RefundStatus.Requested);
        refund.setRequestedAmount(new BigDecimal("300.00"));

        when(refundRepository.findById(refId)).thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundLineRepository.findByRefund_RefundId(refId)).thenReturn(List.of());

        RefundApproveRequest approveReq = new RefundApproveRequest();
        approveReq.setApprovedAmount(new BigDecimal("250.00"));

        RefundResponse response = refundService.approveRefund(refId, approveReq, null);

        assertNotNull(response);
        assertEquals(RefundStatus.Approved, response.getStatus());
        assertEquals(new BigDecimal("250.00"), response.getApprovedAmount());
    }

    @Test
    @DisplayName("approveRefund throws exception when approved amount exceeds requested amount")
    void testApproveRefundExceedsRequested() {
        UUID refId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setRefundId(refId);
        refund.setStatus(RefundStatus.Requested);
        refund.setRequestedAmount(new BigDecimal("300.00"));

        when(refundRepository.findById(refId)).thenReturn(Optional.of(refund));

        RefundApproveRequest approveReq = new RefundApproveRequest();
        approveReq.setApprovedAmount(new BigDecimal("350.00")); // > 300

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> refundService.approveRefund(refId, approveReq, null));
        assertEquals("APPROVED_AMOUNT_EXCEEDED", ex.getCode());
    }

    @Test
    @DisplayName("completeRefund transitions status to Completed and generates adjustment invoice")
    void testCompleteRefundSuccess() {
        UUID refId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setRefundId(refId);
        refund.setOriginalPayment(payment);
        refund.setStatus(RefundStatus.Approved);
        refund.setRequestedAmount(new BigDecimal("300.00"));
        refund.setApprovedAmount(new BigDecimal("300.00"));
        refund.setRefundReason("Hoàn cọc hủy phòng");

        when(refundRepository.findById(refId)).thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundLineRepository.findByRefund_RefundId(refId)).thenReturn(List.of());

        Invoice inv = new Invoice();
        inv.setInvoiceId(UUID.randomUUID());
        when(invoiceRepository.findFirstByBooking_BookingIdOrderByVersionNoDesc(booking.getBookingId()))
                .thenReturn(Optional.of(inv));

        RefundCompleteRequest compReq = new RefundCompleteRequest();
        compReq.setProcessorOrReceiptReference("BANK-REFUND-123");

        RefundResponse response = refundService.completeRefund(refId, compReq);

        assertNotNull(response);
        assertEquals(RefundStatus.Completed, response.getStatus());
        assertEquals("BANK-REFUND-123", response.getProcessorOrReceiptReference());
        verify(invoiceService, times(1))
                .generateAdjustmentInvoice(eq(inv.getInvoiceId()), eq(new BigDecimal("300.00")), anyString());
    }
}
