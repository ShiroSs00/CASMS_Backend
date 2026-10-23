package com.casms.checkin.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingContractRepository;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.checkin.entity.CruiseCheckin;
import com.casms.checkin.enums.CruiseCheckinStatus;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CruiseCheckinRepository checkinRepository;
    private final BookingPassengerRepository bookingPassengerRepository;
    private final BookingContractRepository bookingContractRepository;

    @Transactional
    public CruiseCheckin startCheckin(UUID bookingPassengerId, String emergencyName, String emergencyPhone,
                                      UUID userId, boolean staff) {
        BookingPassenger passenger = bookingPassengerRepository.findById(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking passenger not found: " + bookingPassengerId));
        assertOwner(passenger, userId, staff);

        CruiseCheckin checkin = checkinRepository.findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                .orElseGet(() -> {
                    CruiseCheckin c = new CruiseCheckin();
                    c.setBookingPassenger(passenger);
                    c.setLocationTrackingAllowed(true);
                    c.setLocationSharingAllowed(true);
                    c.setStartedAt(Instant.now());
                    return c;
                });

        checkin.setCheckinStatus(CruiseCheckinStatus.InProgress);
        if (checkin.getStartedAt() == null) {
            checkin.setStartedAt(Instant.now());
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
        CruiseCheckin checkin = checkinRepository.findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkin record not found for passenger: " + bookingPassengerId));
        assertOwner(checkin.getBookingPassenger(), userId, staff);

        // Idempotency: If already BoardingReady, return existing record
        if (checkin.getCheckinStatus() == CruiseCheckinStatus.BoardingReady) {
            log.info("Passenger {} check-in is already in BoardingReady status (idempotent)", bookingPassengerId);
            return checkin;
        }

        // Validate booking prerequisites
        var booking = checkin.getBookingPassenger().getBooking();
        if (booking.getBookingStatus() != BookingStatus.Confirmed) {
            throw new IllegalStateException("Cannot complete check-in: Booking status is " 
                    + booking.getBookingStatus() + ". Only Confirmed bookings are eligible for check-in.");
        }

        // Validate contract acceptance
        var contractOpt = bookingContractRepository.findByBookingBookingId(booking.getBookingId());
        if (contractOpt.isEmpty() || contractOpt.get().getStatus() != BookingContractStatus.Accepted) {
            throw new IllegalStateException("Cannot complete check-in: Booking contract has not been accepted.");
        }

        // Validate emergency contact details
        if (checkin.getEmergencyContactName() == null || checkin.getEmergencyContactName().isBlank()
                || checkin.getEmergencyContactPhone() == null || checkin.getEmergencyContactPhone().isBlank()) {
            throw new IllegalStateException("Cannot complete check-in: Emergency contact name and phone number must be provided.");
        }

        checkin.setCheckinStatus(CruiseCheckinStatus.BoardingReady);
        checkin.setCompletedAt(Instant.now());
        checkin.setBoardingReadyAt(Instant.now());
        log.info("Completed check-in for passenger {}, boarding ready.", bookingPassengerId);
        return checkinRepository.save(checkin);
    }

    @Transactional(readOnly = true)
    public CruiseCheckin getStatus(UUID bookingPassengerId, UUID userId, boolean staff) {
        CruiseCheckin checkin = checkinRepository.findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkin record not found for passenger: " + bookingPassengerId));
        assertOwner(checkin.getBookingPassenger(), userId, staff);
        return checkin;
    }

    private void assertOwner(BookingPassenger bookingPassenger, UUID userId, boolean staff) {
        if (!staff && (userId == null || bookingPassenger.getPassenger().getUser() == null
                || !userId.equals(bookingPassenger.getPassenger().getUser().getUserId()))) {
            throw new AccessDeniedException("You can only access your own check-in record");
        }
    }
}
