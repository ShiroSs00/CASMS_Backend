package com.casms.ship.service;

import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.enums.ServiceStatus;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.repository.TourPackageActivityRepository;
import com.casms.servicepackage.repository.TourPackageExcursionRepository;
import com.casms.servicepackage.repository.TourPackageRepository;
import com.casms.servicepackage.repository.TourPackageServiceRepository;
import com.casms.ship.dto.request.CruiseTourPackageRequest;
import com.casms.ship.dto.response.CruiseTourPackageResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseTourPackage;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseTourPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruisePackageService {

    private final CruiseTourPackageRepository cruiseTourPackageRepository;
    private final CruiseRepository cruiseRepository;
    private final TourPackageRepository tourPackageRepository;
    private final TourPackageServiceRepository tourPackageServiceRepository;
    private final TourPackageActivityRepository tourPackageActivityRepository;
    private final TourPackageExcursionRepository tourPackageExcursionRepository;
    private final BookingPassengerRepository bookingPassengerRepository;

    private final CruiseMainflowService mainflow;
    private final com.casms.servicepackage.service.ShipServiceAvailability shipServices;

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

        shipServices.requirePackage(cruiseId, tourPackage.getTourPackageId());

        if (cruiseTourPackageRepository.existsByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, tourPackage.getTourPackageId())) {
            throw new BusinessException("DUPLICATE_PACKAGE_ASSIGNMENT", "Package is already assigned to this cruise");
        }

        CruiseTourPackage ctp = new CruiseTourPackage();
        ctp.setCruise(cruise);
        ctp.setTourPackage(tourPackage);
        ctp.setActive(false); // BE-12: Configured in catalog, NOT active-for-sale until approved by OPS
        ctp.setMandatory(false);
        ctp.setEntitlementType("OPTIONAL_ADDON");
        ctp.setCreatedAt(Instant.now());

        CruiseTourPackage saved = cruiseTourPackageRepository.save(ctp);
        return toResponse(saved);
    }

    /**
     * BE-H14: Kích hoạt gói tour theo sale-scope gate đã duyệt
     * Revalidate toàn diện: status, price, currency, dates, suppliers, capacity, cruise stop status
     */
    @Transactional
    public CruiseTourPackageResponse activatePackage(UUID cruiseId, UUID tourPackageId) {
        mainflow.requireReady(cruiseId);
        if(!mainflow.commercialBlockers(cruiseId).isEmpty())throw new BusinessException(HttpStatus.CONFLICT,"PRICING_POLICY_APPROVAL_REQUIRED","Current USD pricing revision requires OPS approval");
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        // BE-H14: Không bật package nếu whole Cruise đang stop
        if (cruise.getStoppedAt() != null && (cruise.getStopScope() == null || "WHOLE_CRUISE".equalsIgnoreCase(cruise.getStopScope()) || "CRUISE".equalsIgnoreCase(cruise.getStopScope()))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_STOPPED",
                    "Không thể kích hoạt gói tour khi chuyến đi đang bị tạm dừng bán toàn bộ (whole cruise is stopped)");
        }

        CruiseTourPackage ctp = cruiseTourPackageRepository
                .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, tourPackageId)
                .orElseThrow(() -> new ResourceNotFoundException("CruiseTourPackage", tourPackageId));

        // Idempotent: nếu đã active thì trả về an toàn, không phát sinh thao tác lặp
        if (ctp.isActive()) {
            log.info("Package {} is already active on cruise {}, skipping duplicate activation", tourPackageId, cruiseId);
            return toResponse(ctp);
        }

        validatePackageSaleScopeGate(cruise, ctp);

        ctp.setActive(true);
        return toResponse(cruiseTourPackageRepository.save(ctp));
    }

    /**
     * BE-H14: Revalidate toàn diện điều kiện mở bán của gói tour đối với Cruise
     */
    @Transactional
    public void validatePackageSaleScopeGate(Cruise cruise, CruiseTourPackage ctp) {
        if (cruise.getStatus() == CruiseStatus.CANCELLED || cruise.getStatus() == CruiseStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_INVALID_STATUS",
                    "Không thể mở bán gói tour cho chuyến đi đã hủy hoặc đã hoàn tất");
        }

        TourPackage pkg = ctp.getTourPackage();
        if (pkg == null) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_NOT_FOUND", "Không tìm thấy thông tin gói tour");
        }
        shipServices.requirePackage(cruise.getCruiseId(), pkg.getTourPackageId());
        if (pkg.getStatus() != TourPackageStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_NOT_SALE_READY",
                    "Không thể mở bán lại gói tour đang không ở trạng thái ACTIVE (hiện tại: " + pkg.getStatus() + ")");
        }
        if (pkg.getPrice() == null || pkg.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_PRICE_INVALID",
                    "Gói tour chưa được cấu hình giá bán hợp lệ (> 0)");
        }
        if (pkg.getCurrencyCode() == null || pkg.getCurrencyCode().isBlank()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_CURRENCY_INVALID",
                    "Gói tour chưa được cấu hình đơn vị tiền tệ");
        }

        // Kiểm tra hiệu lực thời gian của gói so với chuyến đi
        if (cruise.getDepartureDatetime() != null && cruise.getReturnDatetime() != null) {
            LocalDate dep = cruise.getDepartureDatetime().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate ret = cruise.getReturnDatetime().atZone(ZoneOffset.UTC).toLocalDate();
            if ((pkg.getStartDate() != null && dep.isBefore(pkg.getStartDate())) ||
                (pkg.getEndDate() != null && ret.isAfter(pkg.getEndDate()))) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_DATE_MISMATCH",
                        "Gói tour không có hiệu lực trong toàn bộ khoảng thời gian của chuyến đi");
            }
        }

        // Revalidate các dịch vụ tiện ích thành phần (Services)
        var services = tourPackageServiceRepository.findByTourPackage_TourPackageId(pkg.getTourPackageId());
        for (var s : services) {
            if (s.getService() == null || s.getService().getStatus() != ServiceStatus.ACTIVE) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_SERVICE_UNAVAILABLE",
                        "Gói tour chứa dịch vụ tiện ích không khả dụng (chưa ACTIVE): " + (s.getService() != null ? s.getService().getServiceName() : "N/A"));
            }
        }

        // Revalidate các hoạt động trên tàu thành phần (Activities)
        var activities = tourPackageActivityRepository.findByTourPackage_TourPackageId(pkg.getTourPackageId());
        for (var a : activities) {
            if (a.getOnboardActivity() == null ||
                    (a.getOnboardActivity().getStatus() != OnboardActivityStatus.PLANNED && a.getOnboardActivity().getStatus() != OnboardActivityStatus.OPEN)) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_ACTIVITY_UNAVAILABLE",
                        "Gói tour chứa hoạt động trên tàu không sẵn sàng: " + (a.getOnboardActivity() != null ? a.getOnboardActivity().getActivityName() : "N/A"));
            }
        }

        // Revalidate các tour tham quan bờ thành phần (Excursions - supplier & capacity)
        var excursions = tourPackageExcursionRepository.findByTourPackage_TourPackageId(pkg.getTourPackageId());
        for (var e : excursions) {
            var exc = e.getShoreExcursion();
            if (exc == null ||
                    (exc.getStatus() != ShoreExcursionStatus.PLANNED && exc.getStatus() != ShoreExcursionStatus.OPEN)) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_EXCURSION_UNAVAILABLE",
                        "Gói tour chứa tour tham quan bờ không sẵn sàng: " + (exc != null ? exc.getExcursionName() : "N/A"));
            }
            if (exc.getMaximumCapacity() == null || exc.getMaximumCapacity() <= 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_EXCURSION_CAPACITY_INVALID",
                        "Tour bờ trong gói '" + exc.getExcursionName() + "' chưa cấu hình sức chứa hợp lệ");
            }
            var sup = exc.getSupplier();
            if (sup == null) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_EXCURSION_SUPPLIER_MISSING",
                        "Tour bờ trong gói '" + exc.getExcursionName() + "' chưa được phân công nhà cung cấp");
            }
            if (sup.getStatus() != SupplierStatus.ACTIVE || sup.getVerificationStatus() != SupplierVerificationStatus.VERIFIED) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_EXCURSION_SUPPLIER_UNVERIFIED",
                        "Nhà cung cấp '" + sup.getSupplierName() + "' cho tour bờ trong gói chưa được xác minh (VERIFIED) hoặc không ACTIVE");
            }
        }

        // Revalidate tải trọng tàu (Cruise Capacity)
        if (cruise.getShip() != null) {
            int physicalCapacity = cruise.getShip().getTotalCapacity();
            if (physicalCapacity <= 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SHIP_CAPACITY_INVALID",
                        "Sức chứa của con tàu chưa được cấu hình hợp lệ (> 0)");
            }
            long booked = bookingPassengerRepository.countConfirmedPassengersByCruiseId(cruise.getCruiseId());
            long held = bookingPassengerRepository.countHeldPassengersByCruiseId(cruise.getCruiseId());
            if ((physicalCapacity - booked - held) <= 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVENTORY_EXHAUSTED",
                        "Không còn chỗ trống trên tàu để kích hoạt/mở bán gói tour");
            }
        }
    }

    @Transactional
    public CruiseTourPackageResponse deactivatePackage(UUID cruiseId, UUID tourPackageId) {
        CruiseTourPackage ctp = cruiseTourPackageRepository
                .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, tourPackageId)
                .orElseThrow(() -> new ResourceNotFoundException("CruiseTourPackage", tourPackageId));
        ctp.setActive(false);
        return toResponse(cruiseTourPackageRepository.save(ctp));
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
                .mandatory(ctp.isMandatory())
                .entitlementType(ctp.getEntitlementType())
                .createdAt(ctp.getCreatedAt())
                .build();
    }
}
