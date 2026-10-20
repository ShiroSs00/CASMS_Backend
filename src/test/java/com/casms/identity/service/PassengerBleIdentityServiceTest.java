package com.casms.identity.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.PassengerBleIdentity;
import com.casms.identity.entity.Passenger;
import com.casms.identity.enums.PassengerBleIdentityStatus;
import com.casms.identity.repository.PassengerBleIdentityRepository;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.CruiseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerBleIdentityServiceTest {
    @Mock PassengerBleIdentityRepository identities;
    @Mock BookingPassengerRepository bookingPassengers;
    @Mock CruiseRepository cruises;
    @InjectMocks PassengerBleIdentityService service;

    @Test
    void assign_acceptsConfirmedPassengerAndNormalizesIdentifier() {
        UUID cruiseId = UUID.randomUUID();
        UUID bookingPassengerId = UUID.randomUUID();
        Cruise cruise = new Cruise(); cruise.setCruiseId(cruiseId);
        Booking booking = new Booking(); booking.setCruise(cruise); booking.setBookingStatus(BookingStatus.Confirmed);
        BookingPassenger passenger = new BookingPassenger(); passenger.setBookingPassengerId(bookingPassengerId);
        passenger.setBooking(booking); passenger.setPassenger(new Passenger());
        passenger.setParticipationStatus(BookingPassengerParticipationStatus.Active);
        when(cruises.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(bookingPassengers.lockById(bookingPassengerId)).thenReturn(Optional.of(passenger));
        when(identities.findByCruiseCruiseIdAndBookingPassengerBookingPassengerIdAndStatus(
                cruiseId, bookingPassengerId, PassengerBleIdentityStatus.ACTIVE)).thenReturn(Optional.empty());
        when(identities.existsByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, "AA:BB:CC:DD:EE:FF")).thenReturn(false);
        when(identities.saveAndFlush(any(PassengerBleIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.assign(cruiseId, bookingPassengerId, " aa:bb:cc:dd:ee:ff ", "CASMS-P001");

        assertEquals("AA:BB:CC:DD:EE:FF", result.bleIdentifier());
        assertEquals(PassengerBleIdentityStatus.ACTIVE, result.status());
        verify(identities).saveAndFlush(argThat(identity -> "AA:BB:CC:DD:EE:FF".equals(identity.getBleIdentifier())
                && identity.getActiveBleIdentifier() == null));
    }

    @Test
    void assign_rejectsPassengerFromAnotherCruise() {
        UUID cruiseId = UUID.randomUUID();
        Cruise requestedCruise = new Cruise(); requestedCruise.setCruiseId(cruiseId);
        Cruise otherCruise = new Cruise(); otherCruise.setCruiseId(UUID.randomUUID());
        Booking booking = new Booking(); booking.setCruise(otherCruise); booking.setBookingStatus(BookingStatus.Confirmed);
        BookingPassenger passenger = new BookingPassenger(); passenger.setBooking(booking);
        when(cruises.findById(cruiseId)).thenReturn(Optional.of(requestedCruise));
        when(bookingPassengers.lockById(any())).thenReturn(Optional.of(passenger));

        assertThrows(BusinessException.class, () -> service.assign(cruiseId, UUID.randomUUID(), "tag-1", null));
        verify(identities, never()).saveAndFlush(any());
    }

    @Test
    void assign_rejectsUnconfirmedBooking() {
        UUID cruiseId = UUID.randomUUID();
        UUID bpId = UUID.randomUUID();
        Cruise cruise = new Cruise(); cruise.setCruiseId(cruiseId);
        Booking booking = new Booking(); booking.setCruise(cruise); booking.setBookingStatus(BookingStatus.Draft);
        BookingPassenger passenger = new BookingPassenger(); passenger.setBookingPassengerId(bpId);
        passenger.setBooking(booking); passenger.setParticipationStatus(BookingPassengerParticipationStatus.Active);
        when(cruises.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(bookingPassengers.lockById(bpId)).thenReturn(Optional.of(passenger));

        assertThrows(BusinessException.class, () -> service.assign(cruiseId, bpId, "tag-1", null));
        verify(identities, never()).saveAndFlush(any());
    }
}
