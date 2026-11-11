package com.casms.booking.service;

import com.casms.booking.entity.*;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.*;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.payment.entity.Payment;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.repository.*;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.repository.BookingPassengerPackageRepository;
import com.casms.ship.entity.Cruise;
import com.casms.trip.entity.IdempotencyRecord;
import com.casms.trip.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingRecoveryTest {
    @Mock IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock BookingRepository bookingRepository;
    @Mock BookingContractRepository bookingContractRepository;
    @Mock BookingPassengerRepository bookingPassengerRepository;
    @Mock BookingPassengerPackageRepository bookingPassengerPackageRepository;
    @Mock InstallmentRepository installmentRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock RefundRepository refundRepository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks BookingService service;

    @Test void keyAloneCannotReadCachedResult() {
        assertEquals("RECOVERY_OWNERSHIP_REQUIRED", assertThrows(BusinessException.class,
                () -> service.recoverBookingByIdempotencyKey("known-key", null)).getCode());
        verifyNoInteractions(idempotencyRecordRepository, bookingRepository);
    }

    @Test void authenticatedRecoveryIsScopedToActorEvenWithBookingRead() {
        CasmsPrincipal principal = mock(CasmsPrincipal.class);
        UUID actor = UUID.randomUUID();
        when(principal.getUserId()).thenReturn(actor);
        assertEquals("RECOVERY_NOT_FOUND", assertThrows(BusinessException.class,
                () -> service.recoverBookingByIdempotencyKey("key", principal)).getCode());
        verify(idempotencyRecordRepository).findByActorIdAndOperationAndIdempotencyKey(actor, "CREATE_BOOKING", "key");
        verify(idempotencyRecordRepository, never()).findByOperationAndIdempotencyKey(any(), any());
    }

    @Test void wrongGuestTokenCannotReadResultingIdOrCachedPayload() {
        Booking booking = storedGuestBooking();
        assertEquals("FORBIDDEN_RECOVERY", assertThrows(BusinessException.class,
                () -> service.recoverBookingByIdempotencyKey("key", null, "wrong-token")).getCode());
        verifyNoInteractions(paymentRepository);
    }

    @Test void validGuestProofReconstructsCurrentStateInsteadOfCachedDto() {
        Booking booking = storedGuestBooking();
        var response = service.recoverBookingByIdempotencyKey("key", null, "original-token");
        assertEquals(booking.getBookingId(), response.getBookingId());
        assertEquals(new BigDecimal("100"), response.getOutstandingBalance());
        assertFalse(response.isFullyPaid());
    }

    @Test void legacyGuestRecordWithoutQuoteFailsClosed() {
        Booking booking = storedGuestBooking();
        booking.setQuote(null);
        assertEquals("FORBIDDEN_RECOVERY", assertThrows(BusinessException.class,
                () -> service.recoverBookingByIdempotencyKey("key", null, "original-token")).getCode());
    }

    @Test void originalGuestProofStillWorksAfterSigningIn() {
        Booking booking = storedGuestBooking();
        CasmsPrincipal principal = mock(CasmsPrincipal.class);
        UUID actor = UUID.randomUUID();
        when(principal.getUserId()).thenReturn(actor);
        doReturn(Optional.empty()).when(idempotencyRecordRepository)
                .findByActorIdAndOperationAndIdempotencyKey(actor, "CREATE_BOOKING", "key");
        assertEquals(booking.getBookingId(), service.recoverBookingByIdempotencyKey(
                "key", principal, "original-token").getBookingId());
    }

    @Test void financialProjectionIgnoresUnverifiedCollectionsAndIncludesCompletedRefunds() {
        Booking booking = storedGuestBooking(); booking.setCurrency("USD");
        var confirmed = payment(booking, "80", "bank-reference");
        var unverified = payment(booking, "500", null);
        var wrongCurrency = payment(booking, "1000", "other-reference"); wrongCurrency.setCurrency("VND");
        when(paymentRepository.findForBooking(booking.getBookingId())).thenReturn(List.of(confirmed, unverified, wrongCurrency));
        var refund = new com.casms.payment.entity.Refund();
        refund.setStatus(com.casms.payment.enums.RefundStatus.Completed);
        refund.setCompletedAt(java.time.Instant.now()); refund.setApprovedAmount(new BigDecimal("20"));
        refund.setCurrency("USD"); refund.setProcessorOrReceiptReference("refund-reference");
        when(refundRepository.findForBooking(booking.getBookingId())).thenReturn(List.of(refund));
        var response = service.recoverBookingByIdempotencyKey("key", null, "original-token");
        assertEquals(new BigDecimal("60"), response.getTotalPaidAmount());
        assertEquals(new BigDecimal("40"), response.getOutstandingBalance());
        assertFalse(response.isFullyPaid());
    }

    @Test void fullyPaidProjectionIsSeparateFromBookingConfirmation() {
        Booking booking = storedGuestBooking(); booking.setCurrency("USD");
        booking.setBookingStatus(com.casms.booking.enums.BookingStatus.Draft);
        when(paymentRepository.findForBooking(booking.getBookingId())).thenReturn(List.of(payment(booking, "100", "settled")));
        var response = service.recoverBookingByIdempotencyKey("key", null, "original-token");
        assertTrue(response.isFullyPaid());
        assertEquals(BookingStatus.Draft, response.getBookingStatus());
    }

    private Payment payment(Booking booking, String amount, String reference) {
        var payment = new Payment(); payment.setBooking(booking);
        payment.setStatus(PaymentStatus.Confirmed);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.Sepay);
        payment.setConfirmedAt(Instant.now()); payment.setConfirmedAmount(new BigDecimal(amount));
        payment.setCurrency("USD"); payment.setProcessorReference(reference); return payment;
    }

    private Booking storedGuestBooking() {
        Booking booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setAgreedFareAmount(new BigDecimal("100"));
        User owner = new User(); owner.setUserId(UUID.randomUUID()); booking.setBookedByUser(owner);
        Cruise cruise = new Cruise(); booking.setCruise(cruise);
        BookingQuote quote = new BookingQuote(); quote.setGuestToken("original-token"); booking.setQuote(quote);
        IdempotencyRecord record = new IdempotencyRecord(); record.setResultingId(booking.getBookingId());
        record.setResponsePayload("{\"bookingReference\":\"STALE\"}");
        when(idempotencyRecordRepository.findByActorIdAndOperationAndIdempotencyKey(null, "CREATE_BOOKING", "key"))
                .thenReturn(Optional.of(record));
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        return booking;
    }
}
