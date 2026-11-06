package com.casms.servicepackage.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.entity.ServiceSchedule;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import com.casms.servicepackage.enums.ServiceStatus;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.servicepackage.repository.ServiceRequestRepository;
import com.casms.servicepackage.repository.ServiceScheduleRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional
public class ServiceRequestService {
    private final ServiceRequestRepository requests;
    private final ServiceRepository services;
    private final BookingPassengerRepository bookingPassengers;
    private final PassengerRepository passengers;
    private final UserRepository users;
    private final ServiceScheduleRepository schedules;
    private final com.casms.pos.PosLedgerService posLedger;

    private final ServiceUsageQuotaService usageQuotas;
    private final com.casms.servicepackage.repository.ServiceUsageRepository usages;
    private final com.casms.cruisepackage.service.CruisePackageEntitlementService fareEntitlements;
    private final ShipServiceAvailability shipServices;

    public List<ServiceRequest> list(UUID passengerId, boolean staff) {
        if (!staff) return requests.findByPassenger_User_UserId(passengerId);
        return requests.findAll();
    }

    public ServiceRequest get(UUID id, UUID userId, boolean staff) {
        ServiceRequest r = requests.findById(id).orElseThrow(() -> missing("Service request", id));
        assertOwner(r, userId, staff);
        return r;
    }

    public ServiceRequest byOperationKey(String key, UUID userId) {
        if (key == null || key.isBlank() || key.length() > 100)
            throw new IllegalArgumentException("Operation key must contain 1 to 100 characters");
        var request = requests.findByIdempotencyKey(key)
                .orElseThrow(() -> new EntityNotFoundException("Service request operation not found"));
        assertOwner(request, userId, false);
        return request;
    }

    public ServiceRequest create(
            UUID userId, UUID serviceId, UUID bookingPassengerId, Integer quantity, String note) {
        return create(userId, serviceId, bookingPassengerId, null, quantity, note, null);
    }

    public ServiceRequest create(
            UUID userId,
            UUID serviceId,
            UUID bookingPassengerId,
            UUID scheduleId,
            Integer quantity,
            String note,
            String idempotencyKey) {
        var replay = replayRequest(userId, serviceId, bookingPassengerId, scheduleId, quantity, note, idempotencyKey);
        if (replay != null) return replay;
        Passenger passenger =
                passengers
                        .findByUser_UserId(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Authenticated user has no passenger profile"));
        return reserve(
                passenger,
                serviceId,
                bookingPassengerId,
                scheduleId,
                quantity,
                note,
                idempotencyKey, false);
    }

    private ServiceRequest replayRequest(UUID userId, UUID serviceId, UUID bookingPassengerId,
            UUID scheduleId, Integer quantity, String note, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = requests.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                ServiceRequest request = existing.get();
                assertOwner(request, userId, false);
                var savedHash = requests.findCreateHash(userId, idempotencyKey, request.getServiceRequestId());
                if (savedHash.isPresent()) {
                    if (!savedHash.get().equals(createHash(serviceId, bookingPassengerId, scheduleId, quantity, note)))
                        throw replayConflict();
                    return request;
                }

                if (!request.getService().getServiceId().equals(serviceId)
                        || bookingPassengerId != null && (request.getBookingPassenger() == null
                            || !bookingPassengerId.equals(request.getBookingPassenger().getBookingPassengerId()))
                        || !Objects.equals(
                                request.getServiceSchedule() == null
                                        ? null
                                        : request.getServiceSchedule().getServiceScheduleId(),
                                scheduleId)
                        || request.getQuantity() != (quantity == null ? 1 : quantity)
                        || !Objects.equals(request.getNote(), note)) {
                    throw replayConflict();
                }
                return request;
            }
        }
        return null;
    }

    private static BusinessException replayConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                "Idempotency key was already used for a different service request");
    }

    private static String createHash(UUID serviceId, UUID bookingPassengerId, UUID scheduleId,
                                     Integer quantity, String note) {
        // Fixed-width IDs and scalar fields precede the final, nullable free-text field.
        String canonical = serviceId + "|" + bookingPassengerId + "|" + scheduleId + "|"
                + (quantity == null ? 1 : quantity) + "|" + (note == null ? "N" : "S" + note);
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * The POS caller must validate employee/device scope and spending membership before reserving.
     */
    public ServiceRequest createForPos(
            UUID bookingPassengerId, UUID serviceId, UUID scheduleId, Integer quantity) {
        var bp =
                bookingPassengers
                        .findById(bookingPassengerId)
                        .orElseThrow(() -> missing("Booking passenger", bookingPassengerId));
        return reserve(
                bp.getPassenger(), serviceId, bookingPassengerId, scheduleId, quantity, null, null, true);
    }

    private ServiceRequest reserve(
            Passenger passenger,
            UUID serviceId,
            UUID bookingPassengerId,
            UUID scheduleId,
            Integer quantity,
            String note,
            String idempotencyKey, boolean onboardSale) {
        // Serialize capacity checks for this service to prevent concurrent reservations exceeding
        // capacity.
        var s =
                services.findByIdForUpdate(serviceId)
                        .orElseThrow(() -> missing("Service", serviceId));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            // The initial lookup can miss a request committed while this transaction waits for the service lock.
            var replay = replayRequest(passenger.getUser().getUserId(), serviceId, bookingPassengerId,
                    scheduleId, quantity, note, idempotencyKey);
            if (replay != null) return replay;
        }
        if (s.getStatus() != ServiceStatus.ACTIVE)
            throw new IllegalStateException("Service is not available");
        if (s.getCurrencyCode() != null && !s.getCurrencyCode().isBlank())
            com.casms.common.util.NewWriteCurrency.requireUsd(s.getCurrencyCode());
        int effectiveQuantity = quantity == null ? 1 : quantity;
        if (effectiveQuantity < 1) throw new IllegalArgumentException("Quantity must be positive");
        if (scheduleId == null) assertCapacity(s, effectiveQuantity, 0);
        ServiceSchedule schedule = null;
        if (scheduleId != null) {
            schedule =
                    schedules
                            .lockById(scheduleId)
                            .orElseThrow(() -> missing("Service schedule", scheduleId));
            if (!schedule.getService().getServiceId().equals(serviceId))
                throw new IllegalArgumentException(
                        "Schedule does not belong to the requested service");
            if (!"OPEN".equals(schedule.getStatus()))
                throw new IllegalStateException("Service schedule is not open");
            long reserved =
                    requests.sumActiveQuantityForSchedule(
                            scheduleId,
                            List.of(
                                    ServiceRequestStatus.REQUESTED,
                                    ServiceRequestStatus.ACCEPTED,
                                    ServiceRequestStatus.IN_PROGRESS));
            if (reserved + effectiveQuantity > schedule.getCapacity())
                throw new IllegalStateException(
                        "Requested quantity exceeds remaining schedule capacity");
        } else if (s.isRequiresSchedule()) {
            throw new IllegalArgumentException("This service requires a schedule reservation");
        }
        BookingPassenger bp = null;
        if (bookingPassengerId != null) {
            bp =
                    bookingPassengers
                            .findById(bookingPassengerId)
                            .orElseThrow(() -> missing("Booking passenger", bookingPassengerId));
            assertSamePassenger(bp, passenger);
            if (bp.getBooking().getBookingStatus() != BookingStatus.Confirmed)
                throw new IllegalStateException("Booking is not confirmed");
        } else {
            var candidates =
                    bookingPassengers
                            .findByPassenger_PassengerId(passenger.getPassengerId())
                            .stream()
                            .filter(
                                    b ->
                                            b.getBooking().getBookingStatus()
                                                    == BookingStatus.Confirmed)
                            .toList();
            if (candidates.size() == 1) {
                bp = candidates.get(0);
            }
        }
        if (schedule != null
                && (bp == null
                        || schedule.getCruise() == null
                        || bp.getBooking().getCruise() == null
                        || !schedule.getCruise()
                                .getCruiseId()
                                .equals(bp.getBooking().getCruise().getCruiseId()))) {
            throw new com.casms.common.exception.BusinessException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "SERVICE_SCHEDULE_CRUISE_MISMATCH",
                    "Lịch phục vụ không thuộc chuyến khởi hành của hành khách.");
        }
        if (bp == null || bp.getBooking().getCruise() == null)
            throw new com.casms.common.exception.BusinessException("SERVICE_BOOKING_REQUIRED", "Select a confirmed booking passenger for the service");
        var cruise = bp.getBooking().getCruise();
        var now = Instant.now();
        if (cruise.getStatus() == com.casms.ship.enums.CruiseStatus.CANCELLED
                || cruise.getStatus() == com.casms.ship.enums.CruiseStatus.COMPLETED)
            throw new IllegalStateException("Service booking cruise is no longer active");
        shipServices.requireServiceForRequest(cruise.getCruiseId(), serviceId);
        if (onboardSale) {
            if (!s.isCanBuyOnboard() || !s.isCanSellIndependently()
                    || cruise.getStatus() != com.casms.ship.enums.CruiseStatus.IN_PROGRESS)
                throw new com.casms.common.exception.BusinessException("SERVICE_NOT_RETAIL_ONBOARD", "Service cannot be sold onboard this cruise");
            if (effectiveQuantity != 1)
                throw new IllegalArgumentException("Each onboard service sale records exactly one use");
        } else if (cruise.getDepartureDatetime() == null || now.isBefore(cruise.getDepartureDatetime())) {
            if (!s.isCanBuyPreCruise())
                throw new com.casms.common.exception.BusinessException("SERVICE_NOT_PRE_CRUISE", "Service does not allow pre-cruise registration");
        } else if (cruise.getStatus() != com.casms.ship.enums.CruiseStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cruise is not accepting service requests");
        }
        if (!onboardSale && s.getPreCruiseUsagePolicy() == com.casms.servicepackage.enums.ServiceUsagePolicy.PER_DAY && schedule == null)
            throw new IllegalArgumentException("PER_DAY prepaid services require a schedule to identify the usage day");
        ServiceRequest r = new ServiceRequest();
        r.setUsageMode(onboardSale ? "ONBOARD" : "PRE_CRUISE");
        r.setUsagePolicySnapshot(onboardSale ? com.casms.servicepackage.enums.ServiceUsagePolicy.UNLIMITED : s.getPreCruiseUsagePolicy());
        r.setUsageLimitSnapshot(onboardSale ? null : s.getPreCruiseUsageLimit());
        r.setUsageTimeZoneSnapshot(s.getUsageTimeZone());
        if (!onboardSale && bp.getFareOfferId() != null) {
            r.setFareBenefitSnapshotId(fareEntitlements.reserve(bp,
                    com.casms.cruisepackage.dto.CruisePackageContracts.ResourceType.SERVICE, serviceId, effectiveQuantity));
            if (schedule != null) fareEntitlements.validateServiceWindow(r.getFareBenefitSnapshotId(), schedule);
        }
        r.setPassenger(passenger);
        r.setService(s);
        r.setServiceSchedule(schedule);
        r.setUnitPriceSnapshot(s.getPrice());
        r.setCurrencySnapshot(s.getCurrencyCode());
        r.setIdempotencyKey(
                idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey);
        r.setBookingPassenger(bp);
        r.setBooking(bp == null ? null : bp.getBooking());
        r.setQuantity(effectiveQuantity);
        r.setNote(note);
        r.setRequestTime(Instant.now());
        r.setStatus(ServiceRequestStatus.REQUESTED);
        requireLegacyPrepaid(r);
        usageQuotas.requireAvailable(r, schedule == null ? now : schedule.getStartsAt());
        if (r.getIdempotencyKey() == null) return requests.save(r);
        // Commit the globally unique request and its original payload identity together.
        var saved = requests.saveAndFlush(r);
        requests.recordCreateHash(passenger.getUser().getUserId(), r.getIdempotencyKey(),
                createHash(serviceId, bookingPassengerId, scheduleId, quantity, note),
                saved.getServiceRequestId());
        return saved;
    }

    public ServiceRequest updatePending(
            UUID id, UUID userId, boolean staff, Integer quantity, String note) {
        ServiceRequest r = requests.lockById(id).orElseThrow(() -> missing("Service request", id));
        assertOwner(r, userId, staff);
        if (r.getStatus() != ServiceRequestStatus.REQUESTED)
            throw new IllegalStateException("Only requested service requests may be edited");
        if (posLedger.isPosRequest(id))
            throw new com.casms.common.exception.BusinessException(
                    "POS_CHARGE_IMMUTABLE",
                    "Cancel the POS request to restore its allowance, then record a new one;"
                        + " charged quantities cannot be edited");
        if (quantity == null || quantity < 1)
            throw new IllegalArgumentException("Quantity must be positive");
        var service =
                services.findByIdForUpdate(r.getService().getServiceId())
                        .orElseThrow(() -> missing("Service", r.getService().getServiceId()));
        if (r.getServiceSchedule() == null) {
            assertCapacity(service, quantity, r.getQuantity());
        } else {
            var schedule =
                    schedules
                            .lockById(r.getServiceSchedule().getServiceScheduleId())
                            .orElseThrow(
                                    () ->
                                            missing(
                                                    "Service schedule",
                                                    r.getServiceSchedule().getServiceScheduleId()));
            if (!schedule.getService().getServiceId().equals(service.getServiceId())
                    || !"OPEN".equals(schedule.getStatus()))
                throw new IllegalStateException("Service schedule is not open for this service");
            long other =
                    requests.sumActiveQuantityForSchedule(
                                    schedule.getServiceScheduleId(),
                                    List.of(
                                            ServiceRequestStatus.REQUESTED,
                                            ServiceRequestStatus.ACCEPTED,
                                            ServiceRequestStatus.IN_PROGRESS))
                            - r.getQuantity();
            if (other + quantity > schedule.getCapacity())
                throw new IllegalStateException(
                        "Requested quantity exceeds remaining schedule capacity");
        }
        r.setQuantity(quantity);
        requireLegacyPrepaid(r);
        usageQuotas.requireAvailable(r, r.getServiceSchedule() == null ? r.getRequestTime() : r.getServiceSchedule().getStartsAt());
        if (r.getFareBenefitSnapshotId() != null)
            fareEntitlements.validateServiceReservation(r.getFareBenefitSnapshotId(), r.getBookingPassenger(), service.getServiceId(), quantity, id);
        r.setNote(note);
        return r;
    }

    public ServiceRequest transition(UUID id, UUID userId, String action) {
        return transition(id, userId, action, false, false, null, null);
    }

    public ServiceRequest transitionFromRequestApi(UUID id, UUID userId, boolean staff, String action,
            Integer fulfilledQuantity, String unitCode) {
        return transition(id, userId, action, true, staff, fulfilledQuantity, unitCode);
    }

    private ServiceRequest transition(UUID id, UUID userId, String action, boolean requestApi,
            boolean staff, Integer fulfilledQuantity, String unitCode) {
        ServiceRequest r = requests.lockById(id).orElseThrow(() -> missing("Service request", id));
        if (requestApi && "complete".equals(action)) assertOwner(r, userId, staff);
        if ("complete".equals(action) && r.getStatus() == ServiceRequestStatus.COMPLETED
                && r.getAssignedUser() != null && r.getAssignedUser().getUserId().equals(userId)) {
            if (requestApi) validateFareFulfillment(r, fulfilledQuantity, unitCode);
            return r;
        }
        switch (action) {
            case "assign" -> {
                if (r.getStatus() != ServiceRequestStatus.REQUESTED) invalid();
                User user = users.findById(userId).orElseThrow(() -> missing("User", userId));
                r.setAssignedUser(user);
            }
            case "accept" -> {
                if (r.getStatus() != ServiceRequestStatus.REQUESTED) invalid();
                r.setStatus(ServiceRequestStatus.ACCEPTED);
            }
            case "reject" -> {
                if (r.getStatus() != ServiceRequestStatus.REQUESTED) invalid();
                r.setStatus(ServiceRequestStatus.REJECTED);
            }
            case "start" -> {
                if (r.getStatus() != ServiceRequestStatus.ACCEPTED
                        || r.getAssignedUser() == null
                        || !r.getAssignedUser().getUserId().equals(userId)) invalid();
                r.setStatus(ServiceRequestStatus.IN_PROGRESS);
            }
            case "complete" -> {
                if (r.getStatus() != ServiceRequestStatus.IN_PROGRESS
                        || r.getAssignedUser() == null
                        || !r.getAssignedUser().getUserId().equals(userId)) invalid();
                var serviceId = r.getService().getServiceId();
                services.findByIdForUpdate(serviceId).orElseThrow(() -> missing("Service", serviceId));
                if (requestApi) validateFareFulfillment(r, fulfilledQuantity, unitCode);
                recordUsage(r, userId);
                r.setStatus(ServiceRequestStatus.COMPLETED);
                r.setCompletedAt(Instant.now());
            }
            case "cancel" -> {
                if (r.getStatus() != ServiceRequestStatus.REQUESTED
                        && r.getStatus() != ServiceRequestStatus.ACCEPTED) invalid();
                r.setStatus(ServiceRequestStatus.CANCELLED);
            }
            default -> throw new IllegalArgumentException("Unsupported request action");
        }
        if (r.getStatus() == ServiceRequestStatus.REJECTED
                || r.getStatus() == ServiceRequestStatus.CANCELLED)
            posLedger.reverseForRequest(id, userId);
        return r;
    }

    private static void validateFareFulfillment(ServiceRequest request, Integer fulfilledQuantity, String unitCode) {
        if (request.getFareBenefitSnapshotId() != null
                && (fulfilledQuantity == null || unitCode == null
                        || !Objects.equals(fulfilledQuantity, request.getQuantity())
                        || !Objects.equals(unitCode, request.getFareFulfillmentUnitCode()))) {
            throw new BusinessException("BENEFIT_FULFILLMENT_QUANTITY_INVALID",
                    "Cần xác nhận đã phục vụ toàn bộ số lượng yêu cầu và đúng đơn vị.");
        }
    }

    private void requireLegacyPrepaid(ServiceRequest request) {
        if ("PRE_CRUISE".equals(request.getUsageMode()) && request.getFareBenefitSnapshotId() == null
                && request.getUnitPriceSnapshot() != null && request.getUnitPriceSnapshot().signum() > 0)
            usageQuotas.requireLegacyPrepaid(request);
    }

    private void recordUsage(ServiceRequest r, UUID userId) {
        if (r.getBooking() == null || r.getBooking().getBookingStatus() != BookingStatus.Confirmed
                || r.getBooking().getCruise().getStatus() != com.casms.ship.enums.CruiseStatus.IN_PROGRESS)
            throw new IllegalStateException("Usage can only be recorded on an in-progress confirmed cruise");
        Instant now = Instant.now();
        if (now.isBefore(r.getBooking().getCruise().getDepartureDatetime())
                || !now.isBefore(r.getBooking().getCruise().getReturnDatetime()))
            throw new IllegalStateException("Usage must be within the cruise dates");
        if (r.getServiceSchedule() != null && (now.isBefore(r.getServiceSchedule().getStartsAt())
                || now.isAfter(r.getServiceSchedule().getEndsAt())))
            throw new IllegalStateException("Usage must be within the service schedule window");
        String operation = "SERVICE_COMPLETE:" + r.getServiceRequestId();
        if (usages.findByOperationIdentity(operation).isPresent()) return;
        requireLegacyPrepaid(r);
        usageQuotas.requireAvailable(r, now);
        if (r.getFareBenefitSnapshotId() != null) {
            fareEntitlements.validateServiceWindow(r.getFareBenefitSnapshotId(), r.getServiceSchedule());
            fareEntitlements.fulfill(r.getFareBenefitSnapshotId(), r.getBookingPassenger(),
                    com.casms.cruisepackage.dto.CruisePackageContracts.ResourceType.SERVICE,
                    r.getService().getServiceId(), r.getQuantity(), r.getService().getUnitCode(), now);
        }
        var usage = new com.casms.servicepackage.entity.ServiceUsage();
        usage.setServiceRequest(r); usage.setQuantity(r.getQuantity()); usage.setUsedAt(now);
        usage.setRecordedBy(users.findById(userId).orElseThrow(() -> missing("User", userId)));
        usage.setFareBenefitSnapshotId(r.getFareBenefitSnapshotId());
        usage.setOperationIdentity(operation); usage.setUnitCodeSnapshot(r.getService().getUnitCode());
        usages.save(usage); r.getUsageRecords().add(usage);
    }

    public void assertOwner(ServiceRequest r, UUID userId, boolean staff) {
        if (staff) return;
        if (r.getPassenger().getUser() == null
                || !r.getPassenger().getUser().getUserId().equals(userId))
            throw new AccessDeniedException("Service request belongs to another passenger");
    }

    private void assertCapacity(Service service, long requestedQuantity, int existingQuantity) {
        if (service.getCapacity() == null) return;
        long used = activeQuantity(service.getServiceId()) - existingQuantity;
        if (used + requestedQuantity > service.getCapacity()) {
            throw new IllegalStateException(
                    "Requested quantity exceeds remaining service capacity");
        }
    }

    private long activeQuantity(UUID serviceId) {
        return requests.sumActiveQuantity(
                serviceId,
                List.of(
                        ServiceRequestStatus.REQUESTED,
                        ServiceRequestStatus.ACCEPTED,
                        ServiceRequestStatus.IN_PROGRESS));
    }

    private void assertSamePassenger(BookingPassenger bp, Passenger passenger) {
        if (!bp.getPassenger().getPassengerId().equals(passenger.getPassengerId()))
            throw new AccessDeniedException(
                    "Booking passenger does not belong to authenticated passenger");
    }

    private static void invalid() {
        throw new IllegalStateException("Invalid service request transition");
    }

    private static EntityNotFoundException missing(String what, UUID id) {
        return new EntityNotFoundException(what + " not found: " + id);
    }
}
