package com.casms.servicepackage.service;

import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.SupplierRepository;
import com.casms.servicepackage.entity.ServiceSchedule;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.servicepackage.repository.ServiceScheduleRepository;
import com.casms.ship.enums.CruiseStopStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStopRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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

    public ServiceSchedule create(UUID serviceId, UUID cruiseId, UUID cruiseStopId, UUID supplierId,
                                  Instant startsAt, Instant endsAt, String location, Integer capacity) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt))
            throw new IllegalArgumentException("Schedule end time must be after start time");
        if (location == null || location.isBlank() || location.length() > 250)
            throw new IllegalArgumentException("A schedule location is required");
        if (capacity == null || capacity < 1) throw new IllegalArgumentException("Schedule capacity must be positive");

        var service = services.findById(serviceId).orElseThrow(() -> missing("Service", serviceId));
        if (!service.isRequiresSchedule()) throw new IllegalStateException("Service is not configured for scheduled reservations");
        var cruise = cruiseId == null ? null : cruises.findById(cruiseId).orElseThrow(() -> missing("Cruise", cruiseId));
        var stop = cruiseStopId == null ? null : stops.findById(cruiseStopId).orElseThrow(() -> missing("Cruise stop", cruiseStopId));
        if (stop != null) {
            var stopCruise = stop.getItinerary().getCruise();
            if (cruise != null && !cruise.getCruiseId().equals(stopCruise.getCruiseId()))
                throw new IllegalArgumentException("Cruise stop does not belong to the selected cruise");
            if (startsAt.isBefore(stop.getPlannedArrival()) || endsAt.isAfter(stop.getPlannedDeparture()))
                throw new IllegalArgumentException("Shore service schedule must fit within the cruise stop window");
            if (stop.getStatus() == CruiseStopStatus.OMITTED
                    || stop.getStatus() == CruiseStopStatus.SUBSTITUTED)
                throw new IllegalStateException("Cannot schedule a service at an omitted or substituted cruise stop");
            cruise = stopCruise;
        }
        var supplier = supplierId == null ? null : suppliers.findById(supplierId).orElseThrow(() -> missing("Supplier", supplierId));
        if (supplier != null && (supplier.getStatus() != SupplierStatus.ACTIVE
                || supplier.getVerificationStatus() != SupplierVerificationStatus.VERIFIED))
            throw new IllegalStateException("Service schedule supplier must be active and verified");

        ServiceSchedule schedule = new ServiceSchedule();
        schedule.setScheduleCode("SS-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        schedule.setService(service);
        schedule.setCruise(cruise);
        schedule.setCruiseStop(stop);
        schedule.setSupplier(supplier);
        schedule.setStartsAt(startsAt);
        schedule.setEndsAt(endsAt);
        schedule.setLocation(location.trim());
        schedule.setCapacity(capacity);
        schedule.setStatus("OPEN");
        schedule.setCreatedAt(Instant.now());
        schedule.setUpdatedAt(Instant.now());
        return schedules.save(schedule);
    }

    private static EntityNotFoundException missing(String type, UUID id) {
        return new EntityNotFoundException(type + " not found: " + id);
    }
}
