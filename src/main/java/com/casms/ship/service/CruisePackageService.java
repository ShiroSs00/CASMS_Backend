package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.repository.TourPackageRepository;
import com.casms.ship.dto.request.CruiseTourPackageRequest;
import com.casms.ship.dto.response.CruiseTourPackageResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseTourPackage;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseTourPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruisePackageService {

    private final CruiseTourPackageRepository cruiseTourPackageRepository;
    private final CruiseRepository cruiseRepository;
    private final TourPackageRepository tourPackageRepository;

    @Transactional
    public CruiseTourPackageResponse assignPackage(UUID cruiseId, CruiseTourPackageRequest request) {
        log.info("Assigning package {} to cruise {}", request.getTourPackageId(), cruiseId);

        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise not found with id: " + cruiseId));

        TourPackage tourPackage = tourPackageRepository.findById(request.getTourPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Tour package not found with id: " + request.getTourPackageId()));

        if (tourPackage.getStatus() != TourPackageStatus.ACTIVE) {
            throw new BusinessException("PACKAGE_INACTIVE", "Cannot assign inactive tour package to cruise");
        }

        if (cruiseTourPackageRepository.existsByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, tourPackage.getTourPackageId())) {
            throw new BusinessException("DUPLICATE_PACKAGE_ASSIGNMENT", "Package is already assigned to this cruise");
        }

        CruiseTourPackage ctp = new CruiseTourPackage();
        ctp.setCruise(cruise);
        ctp.setTourPackage(tourPackage);
        ctp.setActive(true);
        ctp.setCreatedAt(Instant.now());

        CruiseTourPackage saved = cruiseTourPackageRepository.save(ctp);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CruiseTourPackageResponse> listPackages(UUID cruiseId) {
        if (!cruiseRepository.existsById(cruiseId)) {
            throw new ResourceNotFoundException("Cruise not found with id: " + cruiseId);
        }
        return cruiseTourPackageRepository.findByCruise_CruiseId(cruiseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void removePackage(UUID cruiseId, UUID tourPackageId) {
        CruiseTourPackage ctp = cruiseTourPackageRepository
                .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, tourPackageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Package assignment not found for package %s on cruise %s", tourPackageId, cruiseId)));
        cruiseTourPackageRepository.delete(ctp);
        log.info("Removed tour package {} from cruise {}", tourPackageId, cruiseId);
    }

    private CruiseTourPackageResponse toResponse(CruiseTourPackage ctp) {
        return CruiseTourPackageResponse.builder()
                .cruiseTourPackageId(ctp.getCruiseTourPackageId())
                .cruiseId(ctp.getCruise().getCruiseId())
                .tourPackageId(ctp.getTourPackage().getTourPackageId())
                .packageCode(ctp.getTourPackage().getPackageCode())
                .packageName(ctp.getTourPackage().getPackageName())
                .defaultPrice(ctp.getTourPackage().getPrice())
                .active(ctp.isActive())
                .createdAt(ctp.getCreatedAt())
                .build();
    }
}
