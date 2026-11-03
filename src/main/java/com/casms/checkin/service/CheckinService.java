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
    private final com.casms.booking.repository.BookingRepository bookingRepository;
    private final com.casms.booking.repository.CabinAssignmentRepository cabinAssignments;
    private final com.casms.ship.repository.CruiseRepository cruises;
    private final com.casms.ship.repository.CabinRepository cabins;
    private final jakarta.persistence.EntityManager entityManager;
    private final com.casms.cruisepackage.service.CruisePackageCabinMappingService cabinMappings;
    private final com.casms.cruisepackage.service.CruisePackagePurchaseSnapshotService cruisePackagePurchases;
    private final com.casms.ship.service.CruiseStaffAccessPolicy cruiseStaffAccessPolicy;

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
        requirePurchasedFareCabin(checkin.getBookingPassenger());

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

    private void requirePurchasedFareCabin(BookingPassenger passenger) {
        if(passenger.getFareOfferId()==null)return; // No legacy mode conversion or historical backfill.
        bookingRepository.findByIdForUpdate(passenger.getBooking().getBookingId()).orElseThrow();
        var locked=bookingPassengerRepository.lockById(passenger.getBookingPassengerId()).orElseThrow();
        entityManager.refresh(locked);
        var cruise=cruises.findByIdForUpdate(locked.getBooking().getCruise().getCruiseId()).orElseThrow();
        if(locked.getCabinSelectionMode()==com.casms.booking.enums.CabinSelectionMode.CATEGORY_HOLD
                ||locked.getParticipationStatus()!=com.casms.booking.enums.BookingPassengerParticipationStatus.Active)
            throw new com.casms.common.exception.BusinessException("PHYSICAL_CABIN_ASSIGNMENT_REQUIRED","Phải phân cabin vật lý hợp lệ trước khi hoàn tất làm thủ tục/lên tàu.");
        var assignment=cabinAssignments.findByBookingPassengerBookingPassengerIdAndValidToIsNull(locked.getBookingPassengerId())
                .orElseThrow(()->new com.casms.common.exception.BusinessException("PHYSICAL_CABIN_ASSIGNMENT_REQUIRED","Hành khách chưa được phân cabin vật lý."));
        var cabin=cabins.findByIdForUpdate(assignment.getCabin().getCabinId()).orElseThrow();
        entityManager.refresh(cabin);
        if(cruise.getShip()==null||!cabin.getShip().getShipId().equals(cruise.getShip().getShipId())
                ||(cabin.getStatus()!=com.casms.ship.enums.CabinStatus.AVAILABLE&&cabin.getStatus()!=com.casms.ship.enums.CabinStatus.OCCUPIED)
                ||!cabinMappings.requirePurchasedCabins(cruisePackagePurchases.intent(locked)).contains(cabin.getCabinId())
                ||cabinAssignments.countOtherBookingAssignments(cabin.getCabinId(),cruise.getCruiseId(),locked.getBooking().getBookingId())>0
                ||cabinAssignments.countActiveAssignmentsByCabinAndCruise(cabin.getCabinId(),cruise.getCruiseId())>cabin.getCapacity())
            throw new com.casms.common.exception.BusinessException("PHYSICAL_CABIN_ASSIGNMENT_INVALID","Cabin đã phân không còn phù hợp với tàu, hạng đã mua hoặc sức chứa thực tế.");
    }

    @Transactional(readOnly = true)
    public CruiseCheckin getStatus(UUID bookingPassengerId, UUID userId, boolean staff) {
        BookingPassenger passenger = bookingPassengerRepository.findById(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking passenger not found: " + bookingPassengerId));
        assertOwner(passenger, userId, staff);
        CruiseCheckin checkin = checkinRepository.findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkin record not found for passenger: " + bookingPassengerId));
        return checkin;
    }

    private void assertOwner(BookingPassenger bookingPassenger, UUID userId, boolean staff) {
        if (staff) {
            cruiseStaffAccessPolicy.requireCheckinStaffScope(
                    bookingPassenger.getBooking().getCruise().getCruiseId(), userId);
        }
        if (!staff && (userId == null || bookingPassenger.getPassenger().getUser() == null
                || !userId.equals(bookingPassenger.getPassenger().getUser().getUserId()))) {
            throw new AccessDeniedException("You can only access your own check-in record");
        }
    }
}
