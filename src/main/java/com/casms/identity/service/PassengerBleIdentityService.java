package com.casms.identity.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.dto.PassengerBleIdentityResponse;
import com.casms.identity.entity.PassengerBleIdentity;
import com.casms.identity.enums.PassengerBleIdentityStatus;
import com.casms.identity.repository.PassengerBleIdentityRepository;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.CruiseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PassengerBleIdentityService {
    private final PassengerBleIdentityRepository identities;
    private final BookingPassengerRepository bookingPassengers;
    private final CruiseRepository cruises;

    @Transactional
    public PassengerBleIdentityResponse assign(UUID cruiseId, UUID bookingPassengerId, String bleIdentifier, String deviceName) {
        Cruise cruise = cruises.findById(cruiseId).orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        BookingPassenger bp = bookingPassengers.lockById(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking passenger", bookingPassengerId));
        if (bp.getBooking().getCruise() == null || !bp.getBooking().getCruise().getCruiseId().equals(cruiseId)) {
            throw new BusinessException("PASSENGER_NOT_ON_CRUISE", "Booking passenger is not booked on this cruise");
        }
        if (bp.getBooking().getBookingStatus() != BookingStatus.Confirmed
                || bp.getParticipationStatus() != BookingPassengerParticipationStatus.Active) {
            throw new BusinessException("PASSENGER_NOT_ELIGIBLE", "Only an active passenger on a confirmed booking may receive a BLE identity");
        }
        String identifier = normalizeIdentifier(bleIdentifier);
        String actor = currentActor();
        PassengerBleIdentity active = identities.findByCruiseCruiseIdAndBookingPassengerBookingPassengerIdAndStatus(
                cruiseId, bookingPassengerId, PassengerBleIdentityStatus.ACTIVE).orElse(null);
        if (active != null && active.getBleIdentifier().equals(identifier)) return response(active);
        if (identities.existsByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, identifier)) {
            throw new BusinessException("BLE_TAG_ALREADY_ASSIGNED", "This BLE identifier is already assigned to another passenger on this cruise");
        }
        if (active != null) revokeEntity(active, "Reassigned to a different BLE device", actor);

        PassengerBleIdentity entity = new PassengerBleIdentity();
        entity.setCruise(cruise);
        entity.setBookingPassenger(bp);
        entity.setBleIdentifier(identifier);
        entity.setDeviceName(deviceName == null || deviceName.isBlank() ? null : deviceName.trim());
        entity.setStatus(PassengerBleIdentityStatus.ACTIVE);
        entity.setAssignedBy(actor);
        try {
            return response(identities.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("BLE_TAG_ALREADY_ASSIGNED", "This BLE identifier or passenger already has an active assignment");
        }
    }

    @Transactional
    public PassengerBleIdentityResponse revoke(UUID cruiseId, UUID identityId, String reason) {
        PassengerBleIdentity identity = identities.findById(identityId)
                .filter(i -> i.getCruise().getCruiseId().equals(cruiseId))
                .orElseThrow(() -> new ResourceNotFoundException("Passenger BLE identity", identityId));
        if (identity.getStatus() == PassengerBleIdentityStatus.REVOKED) return response(identity);
        revokeEntity(identity, reason.trim(), currentActor());
        return response(identities.save(identity));
    }

    @Transactional(readOnly = true)
    public PassengerBleIdentityResponse active(UUID cruiseId, UUID bookingPassengerId) {
        return identities.findByCruiseCruiseIdAndBookingPassengerBookingPassengerIdAndStatus(
                        cruiseId, bookingPassengerId, PassengerBleIdentityStatus.ACTIVE)
                .map(PassengerBleIdentityService::response)
                .orElseThrow(() -> new ResourceNotFoundException("Active passenger BLE identity", bookingPassengerId));
    }

    @Transactional(readOnly = true)
    public List<PassengerBleIdentityResponse> history(UUID cruiseId, UUID bookingPassengerId) {
        if (!cruises.existsById(cruiseId)) throw new ResourceNotFoundException("Cruise", cruiseId);
        return identities.findByCruiseCruiseIdAndBookingPassengerBookingPassengerIdOrderByAssignedAtDesc(cruiseId, bookingPassengerId)
                .stream().map(PassengerBleIdentityService::response).toList();
    }

    @Transactional(readOnly = true)
    public List<PassengerBleIdentityResponse> activeForCruise(UUID cruiseId) {
        if (!cruises.existsById(cruiseId)) throw new ResourceNotFoundException("Cruise", cruiseId);
        return identities.findByCruiseCruiseIdAndStatusOrderByAssignedAtDesc(cruiseId, PassengerBleIdentityStatus.ACTIVE)
                .stream().map(PassengerBleIdentityService::response).toList();
    }

    private void revokeEntity(PassengerBleIdentity entity, String reason, String actor) {
        entity.setStatus(PassengerBleIdentityStatus.REVOKED);
        entity.setActiveBleIdentifier(null);
        entity.setActiveBookingPassengerId(null);
        entity.setRevokedAt(Instant.now());
        entity.setRevokedBy(actor);
        entity.setRevocationReason(reason);
        identities.saveAndFlush(entity);
    }

    private static String normalizeIdentifier(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 150) {
            throw new BusinessException("INVALID_BLE_IDENTIFIER", "BLE identifier is required and must be at most 150 characters");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    private static PassengerBleIdentityResponse response(PassengerBleIdentity i) {
        return new PassengerBleIdentityResponse(i.getPassengerBleIdentityId(), i.getCruise().getCruiseId(),
                i.getBookingPassenger().getBookingPassengerId(), i.getBookingPassenger().getPassenger().getPassengerId(),
                i.getBleIdentifier(), i.getDeviceName(), i.getStatus(), i.getAssignedAt(), i.getAssignedBy(),
                i.getRevokedAt(), i.getRevokedBy(), i.getRevocationReason());
    }
}
