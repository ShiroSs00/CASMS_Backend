package com.casms.ship.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.Registration;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.RegistrationStatus;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.repository.ItineraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerItineraryServiceTest {

    @Mock BookingPassengerRepository bookingPassengerRepository;
    @Mock ItineraryRepository itineraryRepository;
    @Mock ItineraryDayRepository itineraryDayRepository;
    @Mock CruiseStopRepository cruiseStopRepository;
    @Mock RegistrationRepository registrationRepository;
    @InjectMocks PassengerItineraryService service;

    @Test
    void mineUsesAuthenticatedUserAndReturnsNoTripsWhenNoPassengerBookingsExist() {
        UUID authenticatedUserId = UUID.randomUUID();
        when(bookingPassengerRepository.findPassengerTrips(eq(authenticatedUserId), anyCollection()))
                .thenReturn(List.of());

        assertEquals(List.of(), service.mine(authenticatedUserId));
        verify(bookingPassengerRepository).findPassengerTrips(eq(authenticatedUserId), anyCollection());
    }

    @Test
    void mineSeparatesSharedCruiseScheduleFromThisPassengersRegistrations() {
        UUID userId = UUID.randomUUID();
        UUID bookingPassengerId = UUID.randomUUID();
        UUID cruiseId = UUID.randomUUID();
        UUID itineraryId = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-01T09:00:00Z");

        Cruise cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        cruise.setCruiseCode("CR-0001");
        cruise.setCruiseName("Halong Discovery");
        cruise.setStatus(CruiseStatus.IN_PROGRESS);

        Booking booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setBookingReference("BK-0001");
        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setCruise(cruise);

        BookingPassenger bookingPassenger = new BookingPassenger();
        bookingPassenger.setBookingPassengerId(bookingPassengerId);
        bookingPassenger.setBooking(booking);
        bookingPassenger.setParticipationStatus(BookingPassengerParticipationStatus.Active);

        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setCruise(cruise);
        itinerary.setStatus(ItineraryStatus.OPEN_FOR_SALE);
        itinerary.setStartDate(LocalDate.of(2026, 11, 1));
        itinerary.setEndDate(LocalDate.of(2026, 11, 3));

        ItineraryDay day = new ItineraryDay();
        day.setItineraryDayId(UUID.randomUUID());
        day.setItinerary(itinerary);
        day.setDayNumber(1);
        day.setDate(itinerary.getStartDate());
        day.setDayType(ItineraryDayType.SEA_DAY);

        OnboardActivity registered = activity(cruise, "Morning Yoga", UUID.randomUUID(), start);
        OnboardActivity unrelated = activity(new Cruise(), "Other cruise event", UUID.randomUUID(), start);
        unrelated.getCruise().setCruiseId(UUID.randomUUID());

        Registration ownRegistration = registration(bookingPassenger, registered, RegistrationStatus.CONFIRMED);
        Registration foreignRegistration = registration(bookingPassenger, unrelated, RegistrationStatus.CONFIRMED);

        when(bookingPassengerRepository.findPassengerTrips(eq(userId), anyCollection()))
                .thenReturn(List.of(bookingPassenger));
        when(itineraryRepository.findByCruiseCruiseIdInAndStatusIn(eq(List.of(cruiseId)), anyCollection()))
                .thenReturn(List.of(itinerary));
        when(cruiseStopRepository.findByItineraryItineraryIdInOrderByItineraryItineraryIdAscStopOrderAsc(eq(List.of(itineraryId))))
                .thenReturn(List.of());
        when(itineraryDayRepository.findByItineraryItineraryIdInOrderByItineraryItineraryIdAscDayNumberAsc(eq(List.of(itineraryId))))
                .thenReturn(List.of(day));
        when(registrationRepository.findTripRegistrations(eq(List.of(bookingPassengerId))))
                .thenReturn(List.of(ownRegistration, foreignRegistration));

        var result = service.mine(userId);

        assertEquals(1, result.size());
        assertEquals(ItineraryStatus.OPEN_FOR_SALE, result.get(0).itineraryStatus());
        assertEquals(1, result.get(0).cruiseSchedule().size());
        assertEquals(1, result.get(0).registeredActivities().size());
        assertEquals("Morning Yoga", result.get(0).registeredActivities().get(0).name());
        assertEquals(RegistrationStatus.CONFIRMED, result.get(0).registeredActivities().get(0).registrationStatus());
    }

    private static OnboardActivity activity(Cruise cruise, String name, UUID id, Instant start) {
        OnboardActivity activity = new OnboardActivity();
        activity.setOnboardActivityId(id);
        activity.setActivityName(name);
        activity.setStartTime(start);
        activity.setEndTime(start.plusSeconds(3600));
        activity.setLocation("Deck 3");
        activity.setStatus(OnboardActivityStatus.OPEN);
        activity.setCruise(cruise);
        return activity;
    }

    private static Registration registration(BookingPassenger bookingPassenger, OnboardActivity activity,
                                             RegistrationStatus status) {
        Registration registration = new Registration();
        registration.setRegistrationId(UUID.randomUUID());
        registration.setBookingPassenger(bookingPassenger);
        registration.setOnboardActivity(activity);
        registration.setStatus(status);
        registration.setQuantity(1);
        return registration;
    }
}
