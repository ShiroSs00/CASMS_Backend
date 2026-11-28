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
import com.casms.payment.entity.Installment;
import com.casms.payment.entity.Payment;
import com.casms.payment.enums.InstallmentStatus;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.BookingAgentCruiseScope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final Duration ONLINE_CHECKIN_LEAD = Duration.ofHours(3);

    private final CruiseCheckinRepository checkinRepository;
    private final BookingPassengerRepository bookingPassengerRepository;
    private final BookingContractRepository bookingContractRepository;
    private final InstallmentRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final CruiseRepository cruises;

    @Transactional
    public CruiseCheckin startCheckin(
            UUID bookingPassengerId,
            String emergencyName,
            String emergencyPhone,
            UUID userId,
            boolean staff) {
        return startCheckin(bookingPassengerId, emergencyName, emergencyPhone, userId, staff, null);
    }

    @Transactional
    public CruiseCheckin startCheckin(UUID bookingPassengerId, String emergencyName,
            String emergencyPhone, UUID userId, boolean staff, UUID assignedAgentId) {
        BookingPassenger passenger = lockPassenger(bookingPassengerId);
        requireAgentAssignment(passenger, assignedAgentId);
        assertOwner(passenger, userId, staff);
        requireActiveConfirmedPassenger(passenger);
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

        if (checkin.getCheckinStatus() == CruiseCheckinStatus.BoardingReady) {
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
            checkin.setEmergencyContactPhone(normalizeAndValidateE164Phone(emergencyPhone));
        }
        return checkinRepository.save(checkin);
    }

    @Transactional
    public CruiseCheckin completeCheckin(UUID bookingPassengerId, UUID userId, boolean staff) {
        return completeCheckin(bookingPassengerId, userId, staff, null);
    }

    @Transactional
    public CruiseCheckin completeCheckin(UUID bookingPassengerId, UUID userId, boolean staff,
            UUID assignedAgentId) {
        BookingPassenger passenger = lockPassenger(bookingPassengerId);
        requireAgentAssignment(passenger, assignedAgentId);
        assertOwner(passenger, userId, staff);
        CruiseCheckin checkin =
                checkinRepository
                        .findByBookingPassenger_BookingPassengerId(bookingPassengerId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Checkin record not found for passenger: "
                                                        + bookingPassengerId));
        // RG-08 (CASMS-CHECKIN-003): Both staff and self-service require Active participation on Confirmed booking
        requireActiveConfirmedPassenger(passenger);
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
            throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_CONTRACT_REQUIRED",
                    "Cannot complete check-in: Booking contract has not been accepted.");
        }

        // RG-08 (CASMS-CHECKIN-003): Verify pre-departure payable balance is settled (excludes onboard charges)
        requirePreDeparturePaymentSettled(booking.getBookingId(), booking.getAgreedFareAmount());

        // Validate emergency contact details
        if (checkin.getEmergencyContactName() == null
                || checkin.getEmergencyContactName().isBlank()
                || checkin.getEmergencyContactPhone() == null
                || checkin.getEmergencyContactPhone().isBlank()) {
            throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_EMERGENCY_CONTACT_REQUIRED",
                    "Cannot complete check-in: Emergency contact name and phone number must be"
                        + " provided.");
        }
        // RG-08 (CASMS-CHECKIN-001): Re-verify E.164 phone format before BoardingReady
        checkin.setEmergencyContactPhone(
                normalizeAndValidateE164Phone(checkin.getEmergencyContactPhone()));

        checkin.setCheckinStatus(CruiseCheckinStatus.BoardingReady);
        checkin.setCompletedAt(now);
        checkin.setBoardingReadyAt(now);
        log.info("Completed check-in for passenger {}, boarding ready.", bookingPassengerId);
        return checkinRepository.save(checkin);
    }

    @Transactional(readOnly = true)
    public CruiseCheckin getStatus(UUID bookingPassengerId, UUID userId, boolean staff) {
        return getStatus(bookingPassengerId, userId, staff, null);
    }

    @Transactional(readOnly = true)
    public CruiseCheckin getStatus(UUID bookingPassengerId, UUID userId, boolean staff,
            UUID assignedAgentId) {
        if (assignedAgentId != null) {
            var passenger = bookingPassengerRepository.findById(bookingPassengerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking passenger", bookingPassengerId));
            requireAgentAssignment(passenger, assignedAgentId);
        }
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

    private void requireAgentAssignment(BookingPassenger passenger, UUID assignedAgentId) {
        if (assignedAgentId != null) BookingAgentCruiseScope.requireAssigned(
                cruises, passenger.getBooking().getCruise().getCruiseId(), assignedAgentId);
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

    private void requirePreDeparturePaymentSettled(UUID bookingId, BigDecimal agreedFareAmount) {
        if (installmentRepository == null || paymentRepository == null) {
            return;
        }
        List<Installment> scheduledInstallments =
                installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId).stream()
                        .filter(i -> i.getStatus() == InstallmentStatus.Scheduled)
                        .toList();

        BigDecimal preDepartureRequired =
                scheduledInstallments.isEmpty()
                        ? (agreedFareAmount != null ? agreedFareAmount : BigDecimal.ZERO)
                        : scheduledInstallments.stream()
                                .map(Installment::getRequiredAmount)
                                .filter(a -> a != null)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (preDepartureRequired.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal paidPreDeparture =
                paymentRepository.findForBookingReadOnly(bookingId).stream()
                        .filter(p -> p.getOnboardAccount() == null)
                        .filter(
                                p ->
                                        p.getStatus() == PaymentStatus.Captured
                                                || p.getStatus() == PaymentStatus.Confirmed)
                        .map(
                                p ->
                                        p.getConfirmedAmount() != null
                                                ? p.getConfirmedAmount()
                                                : p.getRequestedAmount())
                        .filter(a -> a != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (paidPreDeparture.compareTo(preDepartureRequired) < 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "PRE_DEPARTURE_PAYMENT_INCOMPLETE",
                    "Hành khách chưa thanh toán đủ khoản phải thu trước khởi hành để sẵn sàng lên"
                        + " tàu.");
        }
    }

    private static void requireActiveConfirmedPassenger(BookingPassenger passenger) {
        if (passenger.getBooking().getBookingStatus() != BookingStatus.Confirmed
                || passenger.getParticipationStatus() != BookingPassengerParticipationStatus.Active) {
            throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_PASSENGER_INELIGIBLE",
                    "Online check-in requires an active passenger on a confirmed booking");
        }
    }

    /**
     * RG-08 (CASMS-CHECKIN-001): Normalize and validate emergency contact phone per ITU E.164
     * (max 15 digits, leading '+', country code starting with 1-9).
     */
    public static String normalizeAndValidateE164Phone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PHONE_NUMBER",
                    "Số điện thoại liên hệ khẩn cấp không được để trống.");
        }
        String cleaned = rawPhone.trim().replaceAll("[\\s\\-\\.\\(\\)]+", "");
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2);
        } else if (cleaned.matches("^0[1-9]\\d{8,9}$")) {
            // Normalize common Vietnam domestic mobile/landline numbers (e.g. 0912345678 -> +84912345678)
            cleaned = "+84" + cleaned.substring(1);
        }
        if (!cleaned.matches("^\\+[1-9]\\d{6,14}$")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PHONE_NUMBER",
                    "Số điện thoại liên hệ khẩn cấp không hợp lệ. Vui lòng nhập theo chuẩn quốc"
                        + " tế E.164 (VD: +84912345678).");
        }
        return cleaned;
    }

    /**
     * RG-08 (CASMS-CHECKIN-002): Allow staff, the passenger themselves, OR the primary booker
     * of the same reservation to manage companion check-in.
     */
    private void assertOwner(BookingPassenger bookingPassenger, UUID userId, boolean staff) {
        if (staff) {
            return;
        }
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to access check-in record");
        }
        boolean isPassengerSelf =
                bookingPassenger.getPassenger() != null
                        && bookingPassenger.getPassenger().getUser() != null
                        && userId.equals(bookingPassenger.getPassenger().getUser().getUserId());
        boolean isPrimaryBooker =
                bookingPassenger.getBooking() != null
                        && bookingPassenger.getBooking().getBookedByUser() != null
                        && userId.equals(
                                bookingPassenger.getBooking().getBookedByUser().getUserId());
        if (!isPassengerSelf && !isPrimaryBooker) {
            throw new AccessDeniedException(
                    "You can only access check-in records for yourself or companions on your"
                        + " booking");
        }
    }
}
