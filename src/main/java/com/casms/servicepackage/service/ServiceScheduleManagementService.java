package com.casms.servicepackage.service;

import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.SupplierRepository;
import com.casms.servicepackage.entity.ServiceSchedule;
import com.casms.servicepackage.enums.ServiceStatus;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.servicepackage.repository.ServiceScheduleRepository;
import com.casms.ship.enums.CruiseStopStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStopRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceScheduleManagementService {
    private final ServiceRepository services;
    private final ServiceScheduleRepository schedules;
    private final CruiseRepository cruises;
    private final CruiseStopRepository stops;
    private final SupplierRepository suppliers;
    private final JdbcTemplate jdbc;
    private final ShipServiceAvailability shipServices;

    public ServiceSchedule create(
            UUID serviceId,
            UUID cruiseId,
            UUID cruiseStopId,
            UUID supplierId,
            Instant startsAt,
            Instant endsAt,
            String location,
            Integer capacity) {
        return create(serviceId, cruiseId, cruiseStopId, supplierId, startsAt, endsAt, location, capacity, null, false);
    }

    public ServiceSchedule create(
            UUID serviceId, UUID cruiseId, UUID cruiseStopId, UUID supplierId, Instant startsAt,
            Instant endsAt, String location, Integer capacity, boolean stagedProgram) {
        return create(serviceId, cruiseId, cruiseStopId, supplierId, startsAt, endsAt, location, capacity, null, stagedProgram);
    }

    public ServiceSchedule create(
            UUID serviceId, UUID cruiseId, UUID cruiseStopId, UUID supplierId, Instant startsAt,
            Instant endsAt, String location, Integer capacity, Integer requiredStaffCount, boolean stagedProgram) {
        return configure(
                new ServiceSchedule(),
                serviceId,
                cruiseId,
                cruiseStopId,
                supplierId,
                startsAt,
                endsAt,
                location,
                capacity,
                requiredStaffCount,
                stagedProgram);
    }

    public ServiceSchedule update(
            UUID serviceId,
            UUID cruiseId,
            UUID cruiseStopId,
            UUID supplierId,
            Instant startsAt,
            Instant endsAt,
            String location,
            Integer capacity,
            UUID id) {
        return update(serviceId, cruiseId, cruiseStopId, supplierId, startsAt, endsAt, location, capacity, null, id, false);
    }

    public ServiceSchedule update(
            UUID serviceId, UUID cruiseId, UUID cruiseStopId, UUID supplierId, Instant startsAt,
            Instant endsAt, String location, Integer capacity, UUID id, boolean stagedProgram) {
        return update(serviceId, cruiseId, cruiseStopId, supplierId, startsAt, endsAt, location, capacity, null, id, stagedProgram);
    }

    public ServiceSchedule update(
            UUID serviceId, UUID cruiseId, UUID cruiseStopId, UUID supplierId, Instant startsAt,
            Instant endsAt, String location, Integer capacity, Integer requiredStaffCount, UUID id, boolean stagedProgram) {
        var schedule = editable(id, cruiseId);
        return configure(
                schedule,
                serviceId,
                cruiseId,
                cruiseStopId,
                supplierId,
                startsAt,
                endsAt,
                location,
                capacity,
                requiredStaffCount,
                stagedProgram);
    }

    public void remove(UUID id, UUID cruiseId) {
        schedules.delete(editable(id, cruiseId));
    }

    private ServiceSchedule editable(UUID id, UUID cruiseId) {
        var schedule = schedules.lockById(id).orElseThrow(() -> missing("Service schedule", id));
        if (schedule.getCruise() == null || !schedule.getCruise().getCruiseId().equals(cruiseId))
            throw new com.casms.common.exception.BusinessException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "CROSS_CRUISE_SCOPE",
                    "Schedule belongs to another Cruise");
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM \"ServiceRequest\" WHERE"
                            + " service_schedule_id=?)",
                        Boolean.class,
                        id)))
            throw new com.casms.common.exception.BusinessException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "SERVICE_WINDOW_HAS_REQUESTS",
                    "Khung phục vụ đã có yêu cầu dịch vụ, không thể sửa hoặc xóa trực tiếp.");
        return schedule;
    }

    private ServiceSchedule configure(
            ServiceSchedule schedule,
            UUID serviceId,
            UUID cruiseId,
            UUID cruiseStopId,
            UUID supplierId,
            Instant startsAt,
            Instant endsAt,
            String location,
            Integer capacity,
            Integer requiredStaffCount,
            boolean stagedProgram) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt))
            throw new IllegalArgumentException("Schedule end time must be after start time");
        if (location == null || location.isBlank() || location.length() > 250)
            throw new IllegalArgumentException("A schedule location is required");
        if (capacity == null || capacity < 1)
            throw new IllegalArgumentException("Schedule capacity must be positive");
        if (requiredStaffCount != null && requiredStaffCount < 1)
            throw new IllegalArgumentException("Số nhân viên phục vụ phải từ 1 trở lên.");

        var service = services.findById(serviceId).orElseThrow(() -> missing("Service", serviceId));
        if (!service.isRequiresSchedule())
            throw new IllegalStateException("Service is not configured for scheduled reservations");
        var cruise =
                cruiseId == null
                        ? null
                        : cruises.findById(cruiseId).orElseThrow(() -> missing("Cruise", cruiseId));
        if (cruise != null
                && (startsAt.isBefore(cruise.getDepartureDatetime())
                        || endsAt.isAfter(cruise.getReturnDatetime())))
            throw new IllegalArgumentException(
                    "Serving window must fit Cruise departure and return");
        var stop =
                cruiseStopId == null
                        ? null
                        : stops.findById(cruiseStopId)
                                .orElseThrow(() -> missing("Cruise stop", cruiseStopId));
        if (stop != null) {
            var stopCruise = stop.getItinerary().getCruise();
            if (cruise != null && !cruise.getCruiseId().equals(stopCruise.getCruiseId()))
                throw new IllegalArgumentException(
                        "Cruise stop does not belong to the selected cruise");
            if (startsAt.isBefore(stop.getPlannedArrival())
                    || endsAt.isAfter(stop.getPlannedDeparture()))
                throw new IllegalArgumentException(
                        "Shore service schedule must fit within the cruise stop window");
            if (stop.getStatus() == CruiseStopStatus.OMITTED
                    || stop.getStatus() == CruiseStopStatus.SUBSTITUTED)
                throw new IllegalStateException(
                        "Cannot schedule a service at an omitted or substituted cruise stop");
            cruise = stopCruise;
        }
        var supplier =
                supplierId == null
                        ? null
                        : suppliers
                                .findById(supplierId)
                                .orElseThrow(() -> missing("Supplier", supplierId));
        if (cruise == null)
            throw new IllegalArgumentException(
                    "A cruise or cruise stop is required to assign a service");
        if (stagedProgram) {
            if (cruise.getShip() == null || service.getStatus() != ServiceStatus.ACTIVE)
                throw new IllegalArgumentException("Staged programs require an assigned ship and an active service");
        } else {
            shipServices.requireService(cruise.getCruiseId(), serviceId);
        }
        if (supplier != null
                && (supplier.getStatus() != SupplierStatus.ACTIVE
                        || supplier.getVerificationStatus() != SupplierVerificationStatus.VERIFIED))
            throw new IllegalStateException(
                    "Service schedule supplier must be active and verified");

        if (schedule.getScheduleCode() == null)
            schedule.setScheduleCode(
                    "SS-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        schedule.setService(service);
        schedule.setCruise(cruise);
        schedule.setCruiseStop(stop);
        schedule.setSupplier(supplier);
        schedule.setStartsAt(startsAt);
        schedule.setEndsAt(endsAt);
        schedule.setLocation(location.trim());
        schedule.setCapacity(capacity);
        int effectiveStaff = requiredStaffCount != null
                ? requiredStaffCount
                : (schedule.getServiceScheduleId() != null && schedule.getRequiredStaffCount() != null && schedule.getRequiredStaffCount() >= 1
                        ? schedule.getRequiredStaffCount()
                        : (service.getDefaultRequiredStaff() != null && service.getDefaultRequiredStaff() >= 1 ? service.getDefaultRequiredStaff() : 1));
        schedule.setRequiredStaffCount(effectiveStaff);
        schedule.setStatus("OPEN");
        if (schedule.getCreatedAt() == null) schedule.setCreatedAt(Instant.now());
        schedule.setUpdatedAt(Instant.now());
        return schedules.save(schedule);
    }

    private static EntityNotFoundException missing(String type, UUID id) {
        return new EntityNotFoundException(type + " not found: " + id);
    }
}
