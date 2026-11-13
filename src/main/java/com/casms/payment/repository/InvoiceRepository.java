package com.casms.payment.repository;

import com.casms.payment.entity.Invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByBooking_BookingIdOrderByIssuedAtDesc(UUID bookingId);

    List<Invoice> findByOnboardAccount_OnboardAccountIdOrderByIssuedAtDesc(UUID onboardAccountId);

    Optional<Invoice> findByInvoiceReference(String invoiceReference);

    Optional<Invoice> findFirstByBooking_BookingIdOrderByVersionNoDesc(UUID bookingId);

    Optional<Invoice> findFirstByOnboardAccount_OnboardAccountIdOrderByVersionNoDesc(
            UUID onboardAccountId);
}
