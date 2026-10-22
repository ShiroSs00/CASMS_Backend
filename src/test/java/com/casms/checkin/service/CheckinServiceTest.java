package com.casms.checkin.service;

import com.casms.booking.entity.BookingPassenger;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {
    @Mock private CruiseCheckinRepository checkinRepository;
    @Mock private BookingPassengerRepository bookingPassengerRepository;
    @InjectMocks private CheckinService service;

    @Test
    void passengerCanStartOwnCheckin() {
        UUID userId = UUID.randomUUID();
        BookingPassenger bookingPassenger = bookingPassenger(userId);
        when(bookingPassengerRepository.findById(bookingPassenger.getBookingPassengerId())).thenReturn(Optional.of(bookingPassenger));
        when(checkinRepository.findByBookingPassenger_BookingPassengerId(bookingPassenger.getBookingPassengerId())).thenReturn(Optional.empty());
        when(checkinRepository.save(any(CruiseCheckin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CruiseCheckin result = service.startCheckin(bookingPassenger.getBookingPassengerId(), null, null, userId, false);

        assertSame(bookingPassenger, result.getBookingPassenger());
        verify(checkinRepository).save(any(CruiseCheckin.class));
    }

    @Test
    void passengerCannotStartAnotherPassengersCheckin() {
        BookingPassenger bookingPassenger = bookingPassenger(UUID.randomUUID());
        when(bookingPassengerRepository.findById(bookingPassenger.getBookingPassengerId())).thenReturn(Optional.of(bookingPassenger));

        assertThrows(AccessDeniedException.class, () -> service.startCheckin(
                bookingPassenger.getBookingPassengerId(), null, null, UUID.randomUUID(), false));
        verify(checkinRepository, never()).save(any());
    }

    @Test
    void authorizedStaffCanReadAnotherPassengersCheckin() {
        UUID userId = UUID.randomUUID();
        BookingPassenger bookingPassenger = bookingPassenger(UUID.randomUUID());
        CruiseCheckin record = new CruiseCheckin();
        record.setBookingPassenger(bookingPassenger);
        when(checkinRepository.findByBookingPassenger_BookingPassengerId(bookingPassenger.getBookingPassengerId())).thenReturn(Optional.of(record));

        assertSame(record, service.getStatus(bookingPassenger.getBookingPassengerId(), userId, true));
    }

    private BookingPassenger bookingPassenger(UUID ownerId) {
        User owner = new User();
        owner.setUserId(ownerId);
        Passenger passenger = new Passenger();
        passenger.setUser(owner);
        BookingPassenger bookingPassenger = new BookingPassenger();
        bookingPassenger.setBookingPassengerId(UUID.randomUUID());
        bookingPassenger.setPassenger(passenger);
        return bookingPassenger;
    }
}
