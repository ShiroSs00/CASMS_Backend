package com.casms.checkin.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.repository.BookingContractRepository;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.checkin.entity.CruiseCheckin;
import com.casms.checkin.enums.CruiseCheckinStatus;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final Duration ONLINE_CHECKIN_LEAD = Duration.ofHours(3);

    private final CruiseCheckinRepository checkinRepository;
    private final BookingPassengerRepository bookingPassengerRepository;
    private final BookingContractRepository bookingContractRepository;

    @Transactional
    public CruiseCheckin startCheckin(
            UUID bookingPassengerId,
            String emergencyName,
            String emergencyPhone,
            UUID userId,
            boolean staff) {
        BookingPassenger passenger = lockPassenger(bookingPassengerId);
        assertOwner(passenger, userId, staff);
        Instant now = Instant.now();
        if (!staff) {
            requireOnlineWindow(passenger, now);
            requireActiveConfirmedPassenger(passenger);
        }

        CruiseCheckin checkin =
                checkinRepository
                        .findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                        .orElseGet(
                                () -> {
                                    CruiseCheckin c = new CruiseCheckin();
                                    c.setBookingPassenger(passenger);
                                    c.setLocationTrackingAllowed(true);
                                    c.setLocationSharingAllowed(true);
                                    c.setStartedAt(now);
                                    return c;
                                });

        if (!staff && checkin.getCheckinStatus() == CruiseCheckinStatus.BoardingReady) {
            return checkin;
        }
        checkin.setCheckinStatus(CruiseCheckinStatus.InProgress);
        if (checkin.getStartedAt() == null) {
            checkin.setStartedAt(now);
        }
        if (emergencyName != null && !emergencyName.isBlank()) {
            checkin.setEmergencyContactName(emergencyName.trim());
        }
        if (emergencyPhone != null && !emergencyPhone.isBlank()) {
            checkin.setEmergencyContactPhone(emergencyPhone.trim());
        }
        return checkinRepository.save(checkin);
    }

    @Transactional
    public CruiseCheckin completeCheckin(UUID bookingPassengerId, UUID userId, boolean staff) {
        BookingPassenger passenger = lockPassenger(bookingPassengerId);
        assertOwner(passenger, userId, staff);
        CruiseCheckin checkin =
                checkinRepository
                        .findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Checkin record not found for passenger: "
                                                        + bookingPassengerId));
        if (!staff) {
            requireActiveConfirmedPassenger(passenger);
        }
        // Idempotency: If already BoardingReady, return existing record
        if (checkin.getCheckinStatus() == CruiseCheckinStatus.BoardingReady) {
            log.info(
                    "Passenger {} check-in is already in BoardingReady status (idempotent)",
                    bookingPassengerId);
            return checkin;
        }

        Instant now = Instant.now();
        if (!staff) {
            requireOnlineWindow(passenger, now);
        }

        // Validate booking prerequisites
        var booking = passenger.getBooking();
        if (booking.getBookingStatus() != BookingStatus.Confirmed) {
            throw new IllegalStateException(
                    "Cannot complete check-in: Booking status is "
                            + booking.getBookingStatus()
                            + ". Only Confirmed bookings are eligible for check-in.");
        }

        // Validate contract acceptance
        var contractOpt = bookingContractRepository.findByBookingBookingId(booking.getBookingId());
        if (contractOpt.isEmpty()
                || contractOpt.get().getStatus() != BookingContractStatus.Accepted) {
            throw new IllegalStateException(
                    "Cannot complete check-in: Booking contract has not been accepted.");
        }

        // Validate emergency contact details
        if (checkin.getEmergencyContactName() == null
                || checkin.getEmergencyContactName().isBlank()
                || checkin.getEmergencyContactPhone() == null
                || checkin.getEmergencyContactPhone().isBlank()) {
            throw new IllegalStateException(
                    "Cannot complete check-in: Emergency contact name and phone number must be"
                        + " provided.");
        }

        checkin.setCheckinStatus(CruiseCheckinStatus.BoardingReady);
        checkin.setCompletedAt(now);
        checkin.setBoardingReadyAt(now);
        log.info("Completed check-in for passenger {}, boarding ready.", bookingPassengerId);
        return checkinRepository.save(checkin);
    }

    @Transactional(readOnly = true)
    public CruiseCheckin getStatus(UUID bookingPassengerId, UUID userId, boolean staff) {
        CruiseCheckin checkin =
                checkinRepository
                        .findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Checkin record not found for passenger: "
                                                        + bookingPassengerId));
        assertOwner(checkin.getBookingPassenger(), userId, staff);
        return checkin;
    }

    private BookingPassenger lockPassenger(UUID id) {
        return bookingPassengerRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking passenger", id));
    }

    private static void requireOnlineWindow(BookingPassenger passenger, Instant now) {
        var cruise = passenger.getBooking().getCruise();
        var departure = cruise == null ? null : cruise.getDepartureDatetime();
        if (departure == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_SCHEDULE_UNAVAILABLE",
                    "Scheduled departure is unavailable");
        }
        Instant opensAt = departure.minus(ONLINE_CHECKIN_LEAD);
        if (now.isBefore(opensAt) || !now.isBefore(departure)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_WINDOW_CLOSED",
                    "Online check-in is available from three hours before scheduled departure until departure",
                    java.util.Map.of("opensAt", opensAt, "closesAt", departure));
        }
    }

    private static void requireActiveConfirmedPassenger(BookingPassenger passenger) {
        if (passenger.getBooking().getBookingStatus() != BookingStatus.Confirmed
                || passenger.getParticipationStatus() != BookingPassengerParticipationStatus.Active) {
            throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_PASSENGER_INELIGIBLE",
                    "Online check-in requires an active passenger on a confirmed booking");
        }
    }

    private void assertOwner(BookingPassenger bookingPassenger, UUID userId, boolean staff) {
        if (!staff
                && (userId == null
                        || bookingPassenger.getPassenger().getUser() == null
                        || !userId.equals(bookingPassenger.getPassenger().getUser().getUserId()))) {
            throw new AccessDeniedException("You can only access your own check-in record");
        }
    }
}
