package com.casms.checkin.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.checkin.entity.CruiseCheckin;
import com.casms.checkin.enums.CruiseCheckinStatus;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CruiseCheckinRepository checkinRepository;
    private final BookingPassengerRepository bookingPassengerRepository;

    @Transactional
    public CruiseCheckin startCheckin(UUID bookingPassengerId, String emergencyName, String emergencyPhone) {
        BookingPassenger passenger = bookingPassengerRepository.findById(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking passenger not found: " + bookingPassengerId));

        CruiseCheckin checkin = checkinRepository.findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                .orElseGet(() -> {
                    CruiseCheckin c = new CruiseCheckin();
                    c.setBookingPassenger(passenger);
                    c.setLocationTrackingAllowed(true);
                    return c;
                });

        checkin.setCheckinStatus(CruiseCheckinStatus.InProgress);
        checkin.setEmergencyContactName(emergencyName);
        checkin.setEmergencyContactPhone(emergencyPhone);
        return checkinRepository.save(checkin);
    }

    @Transactional
    public CruiseCheckin completeCheckin(UUID bookingPassengerId) {
        CruiseCheckin checkin = checkinRepository.findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkin record not found for passenger: " + bookingPassengerId));

        checkin.setCheckinStatus(CruiseCheckinStatus.BoardingReady);
        return checkinRepository.save(checkin);
    }

    @Transactional(readOnly = true)
    public CruiseCheckin getStatus(UUID bookingPassengerId) {
        return checkinRepository.findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkin record not found for passenger: " + bookingPassengerId));
    }
}
