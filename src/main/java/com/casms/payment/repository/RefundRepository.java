package com.casms.payment.repository;

import com.casms.payment.entity.Refund;
import com.casms.payment.enums.RefundStatus;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    interface ReportRow {
        String getCurrency();
        RefundStatus getStatus();
        long getCount();
        BigDecimal getRequestedAmount();
        BigDecimal getApprovedAmount();
    }

    @Query("""
            select r.currency as currency, r.status as status, count(r) as count,
                sum(r.requestedAmount) as requestedAmount, coalesce(sum(r.approvedAmount), 0) as approvedAmount
            from Refund r where r.requestedAt >= :from and r.requestedAt < :until
            group by r.currency, r.status order by r.currency, r.status
            """)
    List<ReportRow> totalsByCurrencyAndStatus(@Param("from") Instant from, @Param("until") Instant until);
    @Query(
            "select r from Refund r left join r.originalPayment.booking b left join"
                    + " r.originalPayment.installment i left join i.booking ib where b.bookingId ="
                    + " :bookingId or ib.bookingId = :bookingId")
    List<Refund> findForBooking(@Param("bookingId") UUID bookingId);

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.refundId = :refundId")
    Optional<Refund> findByIdForUpdate(@Param("refundId") UUID refundId);

    List<Refund> findByOriginalPayment_PaymentIdOrderByRequestedAtDesc(UUID paymentId);

    List<Refund> findByFinalSettlement_FinalSettlementIdOrderByRequestedAtDesc(
            UUID finalSettlementId);
}
