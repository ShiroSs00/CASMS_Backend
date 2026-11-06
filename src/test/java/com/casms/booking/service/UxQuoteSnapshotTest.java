package com.casms.booking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casms.booking.dto.request.BookingCreateRequest;
import com.casms.booking.dto.request.BookingQuoteRequest;
import com.casms.booking.dto.request.PassengerQuoteItem;
import com.casms.booking.entity.BookingQuote;
import com.casms.booking.repository.BookingQuoteRepository;
import com.casms.common.exception.BusinessException;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruisePricingPolicy;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.PricingPolicyStatus;
import com.casms.ship.repository.CruisePricingPolicyRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.service.CruiseMainflowService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class UxQuoteSnapshotTest {
    @Mock BookingQuoteRepository bookingQuoteRepository;
    @Mock CruisePricingPolicyRepository cruisePricingPolicyRepository;
    @Mock CruiseRepository cruiseRepository;
    @Mock ItineraryRepository itineraryRepository;
    @Mock CruiseMainflowService mainflow;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks BookingService service;

    @Test
    void newQuoteAndBookingRejectForeignRequestCurrencyBeforeAnyWrite() {
        var quote = new BookingQuoteRequest();
        quote.setCurrency("VND");
        assertEquals(
                "CURRENCY_USD_REQUIRED",
                assertThrows(BusinessException.class, () -> service.createQuote(quote, null))
                        .getCode());
        var booking = new BookingCreateRequest();
        booking.setCurrency("VND");
        assertEquals(
                "CURRENCY_USD_REQUIRED",
                assertThrows(BusinessException.class, () -> service.createBooking(booking, null))
                        .getCode());
        verifyNoInteractions(
                bookingQuoteRepository,
                cruiseRepository,
                itineraryRepository,
                cruisePricingPolicyRepository);
    }

    @Test
    void legacyPolicyCannotIssueNewQuoteInForeignCurrency() {
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setStatus(CruiseStatus.PUBLISHED);
        cruise.setOpenedForSaleAt(Instant.now());
        cruise.setDepartureDatetime(Instant.now().plusSeconds(86400));
        cruise.setShip(new Ship());
        var itinerary = new Itinerary();
        itinerary.setStatus(ItineraryStatus.OPEN_FOR_SALE);
        var policy = new CruisePricingPolicy();
        policy.setStatus(PricingPolicyStatus.APPROVED);
        policy.setCurrency("VND");
        when(cruiseRepository.findById(cruise.getCruiseId())).thenReturn(Optional.of(cruise));
        when(itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()))
                .thenReturn(Optional.of(itinerary));
        when(cruisePricingPolicyRepository.findByCruise_CruiseId(cruise.getCruiseId()))
                .thenReturn(Optional.of(policy));
        var person = new PassengerQuoteItem();
        person.setCabinId(UUID.randomUUID());
        var request = new BookingQuoteRequest();
        request.setCruiseId(cruise.getCruiseId());
        request.setPassengers(List.of(person));
        assertEquals(
                "CURRENCY_USD_REQUIRED",
                assertThrows(BusinessException.class, () -> service.createQuote(request, null))
                        .getCode());
        verify(bookingQuoteRepository, never()).save(any());
    }

    @Test
    void readingExistingQuoteUsesPersistedPricesAfterSharedDeckChanges() {
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        var deck = new Deck();
        deck.setPriceMultiplier(new BigDecimal("1.15"));
        var q =
                BookingQuote.builder()
                        .quoteId(UUID.randomUUID())
                        .cruise(cruise)
                        .guestToken("test-only-guest")
                        .currency("USD")
                        .subtotalAmount(new BigDecimal("115.00"))
                        .totalAmount(new BigDecimal("115.00"))
                        .depositAmount(new BigDecimal("25.00"))
                        .quotePayloadJson(
                                "[{\"baseFare\":100.00,\"priceMultiplier\":1.15,\"cabinPrice\":115.00,\"packagePrice\":0,\"itemTotal\":115.00}]")
                        .build();
        when(bookingQuoteRepository.findById(q.getQuoteId())).thenReturn(Optional.of(q));
        deck.setPriceMultiplier(new BigDecimal("1.40"));
        var result = service.getQuote(q.getQuoteId(), null, "test-only-guest");
        assertEquals(new BigDecimal("115.00"), result.getTotalAmount());
        assertEquals(new BigDecimal("1.15"), result.getLineItems().getFirst().getPriceMultiplier());
        assertEquals(new BigDecimal("115.00"), result.getLineItems().getFirst().getCabinPrice());
        verifyNoInteractions(cruisePricingPolicyRepository);
        verify(bookingQuoteRepository, never()).save(any());
    }
}
