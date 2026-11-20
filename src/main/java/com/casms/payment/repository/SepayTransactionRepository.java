package com.casms.payment.repository;

import com.casms.payment.entity.SepayTransaction;
import com.casms.payment.enums.SepayReconciliationStatus;
import com.casms.payment.enums.SepayTransactionStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SepayTransactionRepository
        extends JpaRepository<SepayTransaction, UUID>,
                JpaSpecificationExecutor<SepayTransaction> {

    Optional<SepayTransaction> findBySepayId(Long sepayId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SepayTransaction t where t.id = :id")
    Optional<SepayTransaction> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SepayTransaction t where t.sepayId = :sepayId")
    Optional<SepayTransaction> findBySepayIdForUpdate(@Param("sepayId") Long sepayId);

    Optional<SepayTransaction> findByReferenceCode(String referenceCode);

    boolean existsBySepayId(Long sepayId);

    List<SepayTransaction> findByBookingBookingId(UUID bookingId);

    List<SepayTransaction> findByPaymentPaymentId(UUID paymentId);

    long countByReconciliationStatus(SepayReconciliationStatus reconciliationStatus);

    long countByStatus(SepayTransactionStatus status);
}
