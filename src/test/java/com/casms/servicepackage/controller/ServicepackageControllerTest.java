package com.casms.servicepackage.controller;

import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.service.ServiceRequestService;
import com.casms.servicepackage.service.ServicepackageService;
import com.casms.servicepackage.service.ServiceCatalogReadService;
import com.casms.servicepackage.service.ServiceScheduleManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicepackageControllerTest {
    @Mock ServicepackageService service;
    @Mock ServiceRequestService serviceRequests;
    @Mock ServiceCatalogReadService catalogReads;
    @Mock ServiceScheduleManagementService scheduleManagement;
    @Mock com.casms.servicepackage.repository.ServiceCategoryRepository categoryRepository;

    @Test
    void packageBenefits_doesNotExposeDraftPackageToPublic() {
        UUID packageId = UUID.randomUUID();
        TourPackage draft = new TourPackage();
        draft.setStatus(TourPackageStatus.DRAFT);
        when(service.tourPackage(packageId)).thenReturn(draft);

        ServicepackageController controller = new ServicepackageController(service, serviceRequests, catalogReads, scheduleManagement, categoryRepository);

        assertThrows(AccessDeniedException.class, () -> controller.packageBenefits(packageId, null));
        verify(service, never()).packageServices(packageId);
        verify(service, never()).packageActivities(packageId);
        verify(service, never()).packageExcursions(packageId);
    }
}
