package com.casms.payment.service;

import com.casms.payment.entity.Payment;
import com.casms.payment.entity.Refund;
import com.casms.payment.enums.RefundStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

final class RefundBalance {
    private RefundBalance() {}

    static BigDecimal eligible(Payment payment) {
        if (payment.getCapturedAmount() != null) return payment.getCapturedAmount();
        if (payment.getConfirmedAmount() != null) return payment.getConfirmedAmount();
        return payment.getRequestedAmount();
    }

    static BigDecimal gatewayRefunded(Payment payment) {
        return Objects.requireNonNullElse(payment.getRefundedAmount(), BigDecimal.ZERO);
    }

    static BigDecimal remaining(Payment payment, List<Refund> manualRefunds) {
        // Preserve reservations for failed payouts until reconciliation can establish
        // whether they settled. Unmatched gateway and manual records are both counted.
        BigDecimal manualTotal = manualRefunds.stream()
                .filter(refund -> refund.getStatus() != RefundStatus.Rejected)
                .map(refund -> Objects.requireNonNullElse(refund.getApprovedAmount(), refund.getRequestedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return eligible(payment).subtract(gatewayRefunded(payment)).subtract(manualTotal).max(BigDecimal.ZERO);
    }
}
