package com.casms.payment.repository;

import com.casms.payment.entity.RefundLine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefundLineRepository extends JpaRepository<RefundLine, UUID> {
    List<RefundLine> findByRefund_RefundId(UUID refundId);

    @Query("select l from RefundLine l join fetch l.refund r where l.invoiceLine.invoiceLineId = :lineId")
    List<RefundLine> findAllocationsForLine(@Param("lineId") UUID lineId);

    void deleteByRefund_RefundId(UUID refundId);
}
