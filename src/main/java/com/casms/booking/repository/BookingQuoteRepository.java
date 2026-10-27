package com.casms.booking.repository;

import com.casms.booking.entity.BookingQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingQuoteRepository extends JpaRepository<BookingQuote, UUID> {
    Optional<BookingQuote> findByQuoteCode(String quoteCode);
    Optional<BookingQuote> findByQuoteIdAndCruise_CruiseId(UUID quoteId, UUID cruiseId);
}
