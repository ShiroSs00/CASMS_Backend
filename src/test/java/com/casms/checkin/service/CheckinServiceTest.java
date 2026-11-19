package com.casms.checkin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingContract;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingContractRepository;
import com.casms.checkin.enums.CruiseCheckinStatus;
import com.casms.common.exception.BusinessException;
import com.casms.ship.entity.Cruise;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.checkin.entity.CruiseCheckin;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {
    @Mock private CruiseCheckinRepository checkinRepository;
    @Mock private BookingPassengerRepository bookingPassengerRepository;
    @Mock private BookingContractRepository bookingContractRepository;
    @InjectMocks private CheckinService service;

    @Test
    void passengerCanStartOwnCheckin() {
        UUID userId = UUID.randomUUID();
        BookingPassenger bookingPassenger = bookingPassenger(userId);
        when(bookingPassengerRepository.lockById(bookingPassenger.getBookingPassengerId()))
                .thenReturn(Optional.of(bookingPassenger));
        when(checkinRepository.findByBookingPassenger_BookingPassengerId(
                        bookingPassenger.getBookingPassengerId()))
                .thenReturn(Optional.empty());
        when(checkinRepository.save(any(CruiseCheckin.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CruiseCheckin result =
                service.startCheckin(
                        bookingPassenger.getBookingPassengerId(), null, null, userId, false);

        assertSame(bookingPassenger, result.getBookingPassenger());
        verify(checkinRepository).save(any(CruiseCheckin.class));
    }

    @Test
    void passengerCannotStartAnotherPassengersCheckin() {
        BookingPassenger bookingPassenger = bookingPassenger(UUID.randomUUID());
        when(bookingPassengerRepository.lockById(bookingPassenger.getBookingPassengerId()))
                .thenReturn(Optional.of(bookingPassenger));

        UUID callerId = UUID.randomUUID();
        assertThrows(
                AccessDeniedException.class,
                () ->
                        service.startCheckin(
                                bookingPassenger.getBookingPassengerId(),
                                null,
                                null,
                                callerId,
                                false));
        verify(checkinRepository, never()).save(any());
    }

    @Test
    void authorizedStaffCanReadAnotherPassengersCheckin() {
        UUID userId = UUID.randomUUID();
        BookingPassenger bookingPassenger = bookingPassenger(UUID.randomUUID());
        CruiseCheckin checkinRecord = new CruiseCheckin();
        checkinRecord.setBookingPassenger(bookingPassenger);
        when(checkinRepository.findByBookingPassenger_BookingPassengerId(
                        bookingPassenger.getBookingPassengerId()))
                .thenReturn(Optional.of(checkinRecord));

        assertSame(
                checkinRecord, service.getStatus(bookingPassenger.getBookingPassengerId(), userId, true));
    }

    @ParameterizedTest
    @ValueSource(longs = {10801, 10800, 10799, 0})
    void onlineStartAndCompletionRespectDepartureWindow(long secondsBeforeDeparture) {
        UUID owner = UUID.randomUUID();
        var passenger = bookingPassenger(owner);
        UUID id = passenger.getBookingPassengerId();
        Instant now = Instant.parse("2030-02-01T09:00:00Z");
        passenger.getBooking().getCruise().setDepartureDatetime(now.plusSeconds(secondsBeforeDeparture));
        when(bookingPassengerRepository.lockById(id)).thenReturn(Optional.of(passenger));
        var checkin = new CruiseCheckin();
        checkin.setBookingPassenger(passenger);
        checkin.setCheckinStatus(CruiseCheckinStatus.InProgress);
        checkin.setEmergencyContactName("Test contact");
        checkin.setEmergencyContactPhone("0900000000");
        when(checkinRepository.findByBookingPassenger_BookingPassengerId(id))
                .thenReturn(Optional.of(checkin));

        try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(now);
            if (secondsBeforeDeparture > 10800 || secondsBeforeDeparture == 0) {
                var start = assertThrows(BusinessException.class,
                        () -> service.startCheckin(id, null, null, owner, false));
                var complete = assertThrows(BusinessException.class,
                        () -> service.completeCheckin(id, owner, false));
                assertEquals("CHECKIN_WINDOW_CLOSED", start.getCode());
                assertEquals("CHECKIN_WINDOW_CLOSED", complete.getCode());
                verify(checkinRepository, never()).save(any());
                verifyNoInteractions(bookingContractRepository);
            } else {
                var contract = new BookingContract();
                contract.setStatus(BookingContractStatus.Accepted);
                when(bookingContractRepository.findByBookingBookingId(passenger.getBooking().getBookingId()))
                        .thenReturn(Optional.of(contract));
                when(checkinRepository.save(checkin)).thenReturn(checkin);

                assertEquals(CruiseCheckinStatus.InProgress,
                        service.startCheckin(id, null, null, owner, false).getCheckinStatus());
                assertEquals(CruiseCheckinStatus.BoardingReady,
                        service.completeCheckin(id, owner, false).getCheckinStatus());
                assertEquals(now, checkin.getCompletedAt());
            }
        }
    }

    @Test
    void inactivePassengerCannotStartOrReuseCompletedCheckin() {
        UUID owner = UUID.randomUUID();
        var passenger = bookingPassenger(owner);
        passenger.setParticipationStatus(BookingPassengerParticipationStatus.Cancelled);
        UUID id = passenger.getBookingPassengerId();
        when(bookingPassengerRepository.lockById(id)).thenReturn(Optional.of(passenger));
        var checkin = new CruiseCheckin();
        checkin.setCheckinStatus(CruiseCheckinStatus.BoardingReady);
        when(checkinRepository.findByBookingPassenger_BookingPassengerId(id)).thenReturn(Optional.of(checkin));

        assertEquals("CHECKIN_PASSENGER_INELIGIBLE", assertThrows(BusinessException.class,
                () -> service.startCheckin(id, null, null, owner, false)).getCode());
        assertEquals("CHECKIN_PASSENGER_INELIGIBLE", assertThrows(BusinessException.class,
                () -> service.completeCheckin(id, owner, false)).getCode());
        verify(checkinRepository, never()).save(any());
    }

    private BookingPassenger bookingPassenger(UUID ownerId) {
        User owner = new User();
        owner.setUserId(ownerId);
        Passenger passenger = new Passenger();
        passenger.setUser(owner);
        BookingPassenger bookingPassenger = new BookingPassenger();
        bookingPassenger.setBookingPassengerId(UUID.randomUUID());
        bookingPassenger.setPassenger(passenger);
        bookingPassenger.setParticipationStatus(BookingPassengerParticipationStatus.Active);
        var cruise = new Cruise();
        cruise.setDepartureDatetime(Instant.now().plusSeconds(3600));
        var booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setCruise(cruise);
        bookingPassenger.setBooking(booking);
        return bookingPassenger;
    }
}
