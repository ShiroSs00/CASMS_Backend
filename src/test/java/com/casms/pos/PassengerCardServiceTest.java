package com.casms.pos;

import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.checkin.entity.CruiseCheckin;
import com.casms.checkin.enums.CruiseCheckinStatus;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.Cruise;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PassengerCardServiceTest {
    private static final String CODE = "A".repeat(43);
    private static final String TOKEN = "pos-session";
    private final PosService sessions = mock(PosService.class);
    private final BookingPassengerRepository passengers = mock(BookingPassengerRepository.class);
    private final CruiseCheckinRepository checkins = mock(CruiseCheckinRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PassengerCardService service = new PassengerCardService(sessions, passengers, checkins, jdbc);
    private final CasmsPrincipal employee = mock(CasmsPrincipal.class);
    private final UUID cruiseId = UUID.randomUUID();
    private final UUID passengerId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @Test
    void claimRequiresOwnershipAndCompletedCheckin() {
        var passenger = fixturePassenger();
        when(passengers.lockById(passengerId)).thenReturn(Optional.of(passenger));

        assertThrows(AccessDeniedException.class, () -> service.issue(passengerId, UUID.randomUUID()));
        assertEquals("PASSENGER_CARD_CHECKIN_REQUIRED",
                assertThrows(BusinessException.class, () -> service.issue(passengerId, ownerId)).getCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void claimStoresDigestInsteadOfReturnedCode() {
        var passenger = fixturePassenger();
        when(passengers.lockById(passengerId)).thenReturn(Optional.of(passenger));
        completedCheckin();
        when(jdbc.queryForObject(contains("PassengerNfcCard"), eq(Boolean.class), eq(passengerId)))
                .thenReturn(false);

        var claim = service.issue(passengerId, ownerId);

        assertEquals(43, claim.code().length());
        verify(jdbc).update(contains("INSERT INTO \"PassengerCardClaim\""), eq(passengerId),
                argThat(digest -> digest instanceof String hash
                        && hash.matches("[0-9a-f]{64}") && !hash.equals(claim.code())),
                eq(Timestamp.from(claim.expiresAt())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void terminalOutsideClaimCruiseCannotResolvePassenger() {
        validSession();
        when(jdbc.query(contains("b.cruise_id = ?"), any(RowMapper.class), anyString(), eq(cruiseId)))
                .thenReturn(List.of());

        assertEquals("PASSENGER_CARD_CLAIM_INVALID",
                assertThrows(BusinessException.class, () -> service.preview(TOKEN, employee, CODE)).getCode());
        verifyNoInteractions(passengers, checkins);
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmationConsumesClaimAndStoresNormalizedCardWithAuditActor() {
        validSession();
        when(employee.getUserId()).thenReturn(employeeId);
        when(jdbc.query(contains("b.cruise_id = ?"), any(RowMapper.class), anyString(), eq(cruiseId)))
                .thenReturn(List.of(passengerId));
        when(passengers.lockById(passengerId)).thenReturn(Optional.of(fixturePassenger()));
        when(jdbc.queryForObject(contains("consumed_at IS NULL"), eq(Boolean.class),
                eq(passengerId), anyString(), any(Timestamp.class))).thenReturn(true);
        completedCheckin();
        when(jdbc.update(contains("SET consumed_at"), any(Timestamp.class),
                eq(passengerId), anyString(), any(Timestamp.class))).thenReturn(1, 0);
        when(jdbc.update(contains("INSERT INTO \"PassengerNfcCard\""), eq(passengerId),
                eq(cruiseId), eq("AABBCCDD"), eq(employeeId), any(Timestamp.class))).thenReturn(1);

        var confirmation = service.confirm(TOKEN, employee, CODE, "aabbccdd");

        assertEquals(passengerId, confirmation.bookingPassengerId());
        assertEquals("PASSENGER_CARD_CLAIM_INVALID", assertThrows(BusinessException.class,
                () -> service.confirm(TOKEN, employee, CODE, "aabbccdd")).getCode());
        verify(jdbc, times(1)).update(contains("INSERT INTO \"PassengerNfcCard\""), eq(passengerId),
                eq(cruiseId), eq("AABBCCDD"), eq(employeeId), eq(Timestamp.from(confirmation.confirmedAt())));
    }

    private void validSession() {
        var assignment = new PosDtos.AreaStaff(null, null, employeeId, null, cruiseId, true, null, null);
        when(sessions.access(TOKEN, employee)).thenReturn(new PosService.Access(null, null, employee, assignment));
    }

    private void completedCheckin() {
        var checkin = new CruiseCheckin();
        checkin.setCheckinStatus(CruiseCheckinStatus.BoardingReady);
        when(checkins.findByBookingPassenger_BookingPassengerId(passengerId)).thenReturn(Optional.of(checkin));
    }

    private BookingPassenger fixturePassenger() {
        var cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        var booking = new Booking();
        booking.setCruise(cruise);
        booking.setBookingStatus(BookingStatus.Confirmed);
        var user = new User();
        user.setUserId(ownerId);
        var passenger = new Passenger();
        passenger.setFullName("Synthetic Guest");
        passenger.setUser(user);
        var record = new BookingPassenger();
        record.setBookingPassengerId(passengerId);
        record.setBooking(booking);
        record.setPassenger(passenger);
        record.setParticipationStatus(BookingPassengerParticipationStatus.Active);
        return record;
    }
}
