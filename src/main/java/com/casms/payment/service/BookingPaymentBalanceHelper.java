package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.payment.entity.Installment;
import com.casms.payment.entity.Payment;
import com.casms.payment.entity.Refund;
import com.casms.payment.enums.InstallmentStatus;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single source of truth for booking payment balance, verified net collections (minus completed
 * evidenced refunds), hold-expiry check, and waterfall installment allocation. Mirrors
 * BookingService.verifiedNetPaid and BookingService.tryAutoConfirm.
 */
public final class BookingPaymentBalanceHelper {

    private BookingPaymentBalanceHelper() {}

    public static BigDecimal verifiedNetPaid(
            Booking booking, List<Payment> payments, List<Refund> refunds) {
        if (booking == null) {
            return BigDecimal.ZERO;
        }
        UUID bookingId = booking.getBookingId();
        String bookingCurrency =
                booking.getCurrency() != null ? booking.getCurrency().trim() : null;

        BigDecimal paid =
                (payments == null ? List.<Payment>of() : payments)
                        .stream()
                        .filter(
                                p ->
                                        p != null
                                                && (p.getStatus() == PaymentStatus.Confirmed
                                                        || p.getStatus() == PaymentStatus.Captured)
                                                && p.getConfirmedAt() != null
                                                && p.getConfirmedAmount() != null
                                                && p.getConfirmedAmount().signum() > 0
                                                && p.getOperationType()
                                                        == PaymentOperationType.Collect
                                                && belongsToBooking(p, bookingId)
                                                && hasSettlementEvidence(p)
                                                && matchesCurrency(p.getCurrency(), bookingCurrency))
                        .map(Payment::getConfirmedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal completedRefunds =
                (refunds == null ? List.<Refund>of() : refunds)
                        .stream()
                        .filter(
                                r ->
                                        r != null
                                                && r.getStatus() == RefundStatus.Completed
                                                && r.getCompletedAt() != null
                                                && r.getApprovedAmount() != null
                                                && r.getApprovedAmount().signum() > 0
                                                && matchesCurrency(r.getCurrency(), bookingCurrency)
                                                && r.getProcessorOrReceiptReference() != null
                                                && !r.getProcessorOrReceiptReference().isBlank())
                        .map(Refund::getApprovedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return paid.subtract(completedRefunds).max(BigDecimal.ZERO);
    }

    public static BigDecimal outstandingBalance(Booking booking, BigDecimal netPaid) {
        BigDecimal agreedFare =
                booking != null && booking.getAgreedFareAmount() != null
                        ? booking.getAgreedFareAmount()
                        : BigDecimal.ZERO;
        BigDecimal paid = netPaid != null ? netPaid : BigDecimal.ZERO;
        return agreedFare.subtract(paid).max(BigDecimal.ZERO);
    }

    public static Map<UUID, BigDecimal> allocateWaterfall(
            BigDecimal netPaid, List<Installment> installments) {
        Map<UUID, BigDecimal> allocated = new HashMap<>();
        if (installments == null || installments.isEmpty()) {
            return allocated;
        }
        BigDecimal remaining = netPaid != null ? netPaid.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        for (Installment inst : installments) {
            if (inst.getStatus() == InstallmentStatus.Cancelled) {
                allocated.put(inst.getInstallmentId(), BigDecimal.ZERO);
                continue;
            }
            BigDecimal required =
                    inst.getRequiredAmount() != null ? inst.getRequiredAmount() : BigDecimal.ZERO;
            BigDecimal alloc = remaining.min(required).max(BigDecimal.ZERO);
            allocated.put(inst.getInstallmentId(), alloc);
            remaining = remaining.subtract(alloc).max(BigDecimal.ZERO);
        }
        return allocated;
    }

    public static boolean isHoldExpired(Booking booking, Instant now) {
        if (booking == null) {
            return false;
        }
        return booking.getBookingStatus() == BookingStatus.Draft
                && booking.getHoldExpiresAt() != null
                && !booking.getHoldExpiresAt().isAfter(now);
    }

    private static boolean belongsToBooking(Payment payment, UUID bookingId) {
        if (bookingId == null) {
            return true;
        }
        if (payment.getBooking() != null && bookingId.equals(payment.getBooking().getBookingId())) {
            return true;
        }
        return payment.getInstallment() != null
                && payment.getInstallment().getBooking() != null
                && bookingId.equals(payment.getInstallment().getBooking().getBookingId());
    }

    private static boolean hasSettlementEvidence(Payment payment) {
        PaymentMethod method = payment.getPaymentMethod();
        if (method == null) {
            return false;
        }
        return switch (method) {
            case Cash ->
                    payment.getInternalReceiptReference() != null
                            && !payment.getInternalReceiptReference().isBlank();
            case GatewaySandbox, Sepay, CreditCard, TokenizedCard ->
                    payment.getProcessorReference() != null
                            && !payment.getProcessorReference().isBlank();
        };
    }

    private static boolean matchesCurrency(String candidate, String bookingCurrency) {
        if (bookingCurrency == null || bookingCurrency.isBlank()) {
            return true;
        }
        return candidate != null && bookingCurrency.equalsIgnoreCase(candidate.trim());
    }
}
