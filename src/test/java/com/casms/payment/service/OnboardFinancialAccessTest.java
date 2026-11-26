package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.identity.entity.Passenger;
import com.casms.identity.enums.Role;
import com.casms.payment.entity.OnboardAccount;
import com.casms.payment.entity.OnboardAccountMember;
import com.casms.payment.enums.AccountMemberStatus;
import com.casms.payment.repository.OnboardAccountMemberRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.Cruise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnboardFinancialAccessTest {
    private final BookingPassengerRepository passengers = mock(BookingPassengerRepository.class);
    private final OnboardAccountMemberRepository members = mock(OnboardAccountMemberRepository.class);
    private final OnboardFinancialAccess access =
            new OnboardFinancialAccess(mock(BookingRepository.class), passengers, members);
    private final Booking booking = new Booking();
    private final OnboardAccount account = new OnboardAccount();
    private final CasmsPrincipal actor = mock(CasmsPrincipal.class);
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        when(actor.isEnabled()).thenReturn(true);
        when(actor.isAccountNonLocked()).thenReturn(true);
        when(actor.hasRole(Role.PASSENGER)).thenReturn(true);
        when(actor.getUserId()).thenReturn(userId);
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        booking.setBookingId(UUID.randomUUID());
        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setCruise(cruise);
        account.setOnboardAccountId(UUID.randomUUID());
        account.setCruise(cruise);
        booking.setSettlementOnboardAccount(account);
    }

    @Test
    void passengerCannotCombineAnAccountWithAnotherBooking() {
        var other = new OnboardAccount();
        other.setOnboardAccountId(UUID.randomUUID());
        other.setCruise(account.getCruise());
        assertThrows(AccessDeniedException.class, () -> access.readFolio(actor, other, booking));
    }

    @Test
    void passengerCannotReadAnAccountWithoutAnExactBookingPassenger() {
        assertThrows(AccessDeniedException.class, () -> access.readFolio(actor, account, booking));
        verifyNoInteractions(members);
    }

    @Test
    void exactMembershipYieldsPassengerScopeAndExpiryRevokesIt() {
        var passenger = new Passenger();
        passenger.setPassengerId(UUID.randomUUID());
        var bookingPassenger = new BookingPassenger();
        bookingPassenger.setBooking(booking);
        bookingPassenger.setPassenger(passenger);
        bookingPassenger.setParticipationStatus(BookingPassengerParticipationStatus.Active);
        var member = new OnboardAccountMember();
        member.setPassenger(passenger);
        member.setStatus(AccountMemberStatus.ACTIVE);
        when(passengers.findByPassenger_User_UserId(userId)).thenReturn(List.of(bookingPassenger));
        when(members.findByOnboardAccount_OnboardAccountId(account.getOnboardAccountId()))
                .thenReturn(List.of(member));

        var scope = access.readFolio(actor, account, booking);
        assertEquals(passenger.getPassengerId(), scope.passengerId());
        assertFalse(scope.accountWide());

        member.setEffectiveTo(Instant.now().minusSeconds(1));
        assertThrows(AccessDeniedException.class, () -> access.readFolio(actor, account, booking));
    }

    @Test
    void missingPrincipalIsDeniedBeforeScopeResolution() {
        assertThrows(AccessDeniedException.class, () -> access.readFolio(null, account, booking));
        verifyNoInteractions(passengers, members);
    }
}
