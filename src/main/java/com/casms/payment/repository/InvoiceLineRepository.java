package com.casms.payment.repository;

import com.casms.payment.entity.InvoiceLine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {
    List<InvoiceLine> findByInvoice_InvoiceIdOrderByLineNoAsc(UUID invoiceId);

    void deleteByInvoice_InvoiceId(UUID invoiceId);
}
