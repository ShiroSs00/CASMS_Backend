package com.casms.payment.repository;

import com.casms.payment.entity.InvoiceLine;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InvoiceLine l where l.invoiceLineId = :lineId")
    Optional<InvoiceLine> findByIdForUpdate(@org.springframework.data.repository.query.Param("lineId") UUID lineId);

    List<InvoiceLine> findByInvoice_InvoiceIdOrderByLineNoAsc(UUID invoiceId);

    void deleteByInvoice_InvoiceId(UUID invoiceId);
}
