package com.casms.payment.repository;

import com.casms.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    @Query("select r from Refund r left join r.originalPayment.booking b left join r.originalPayment.installment i left join i.booking ib where b.bookingId = :bookingId or ib.bookingId = :bookingId")
    List<Refund> findForBooking(@Param("bookingId") UUID bookingId);
}
