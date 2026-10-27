package com.casms.servicepackage.service;

import com.casms.servicepackage.entity.PackageEntitlement;
import com.casms.servicepackage.entity.ServiceCategory;
import com.casms.servicepackage.entity.ServiceSchedule;
import com.casms.servicepackage.repository.ServiceCategoryRepository;
import com.casms.servicepackage.repository.ServiceScheduleRepository;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.servicepackage.repository.PackageEntitlementRepository;
import com.casms.servicepackage.repository.ServiceRequestRepository;
import com.casms.servicepackage.repository.TourPackageRepository;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceCatalogReadService {
    private final ServiceCategoryRepository categoryRepository;
    private final ServiceScheduleRepository scheduleRepository;
    private final ServiceRepository serviceRepository;
    private final PackageEntitlementRepository entitlementRepository;
    private final ServiceRequestRepository requestRepository;
    private final TourPackageRepository packageRepository;

    public List<ServiceCategory> categories() {
        return categoryRepository.findAllByOrderByCategoryNameAsc();
    }

    public List<ServiceSchedule> openSchedules(UUID serviceId) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new EntityNotFoundException("Service not found: " + serviceId);
        }
        return scheduleRepository.findByService_ServiceIdAndStatusOrderByStartsAtAsc(serviceId, "OPEN");
    }

    public List<PackageEntitlement> entitlements(UUID packageId) {
        Map<UUID, PackageEntitlement> inherited = new LinkedHashMap<>();
        Set<UUID> seen = new HashSet<>();
        var current = packageRepository.findById(packageId)
                .orElseThrow(() -> new EntityNotFoundException("Tour package not found: " + packageId));
        while (current != null) {
            if (!seen.add(current.getTourPackageId())) throw new IllegalStateException("Package inheritance cycle detected");
            for (var entitlement : entitlementRepository.findByTourPackage_TourPackageId(current.getTourPackageId()))
                inherited.putIfAbsent(entitlement.getServiceCategory().getServiceCategoryId(), entitlement);
            current = current.getParentPackage();
        }
        return List.copyOf(inherited.values());
    }

    public int remainingCapacity(ServiceSchedule schedule) {
        long reserved = requestRepository.sumActiveQuantityForSchedule(schedule.getServiceScheduleId(),
                List.of(ServiceRequestStatus.REQUESTED, ServiceRequestStatus.ACCEPTED,
                        ServiceRequestStatus.IN_PROGRESS));
        return (int) Math.max(0, schedule.getCapacity() - reserved);
    }
}
