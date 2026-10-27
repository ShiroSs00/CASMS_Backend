package com.casms.ship.service;

import com.casms.booking.repository.BookingQuoteRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.CruisePricingPolicyRequest;
import com.casms.ship.dto.response.CruisePricingPolicyResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruisePricingPolicy;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.PricingPolicyStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruisePricingPolicyRepository;
import com.casms.ship.repository.CruiseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruisePricingPolicyService {

    private final CruisePricingPolicyRepository pricingPolicyRepository;
    private final CruiseRepository cruiseRepository;
    private final CabinRepository cabinRepository;
    private final BookingQuoteRepository bookingQuoteRepository;
    private final UserRepository userRepository;
    private final CruisePreparationService cruisePreparationService;

    @Transactional(readOnly = true)
    public CruisePricingPolicyResponse getPricingPolicy(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        CruisePricingPolicy policy = pricingPolicyRepository.findByCruise_CruiseId(cruiseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PRICING_POLICY_NOT_FOUND",
                        "Chính sách giá cho chuyến đi " + cruise.getCruiseCode() + " chưa được thiết lập"));

        return toResponse(policy);
    }

    @Transactional
    public CruisePricingPolicyResponse savePricingPolicy(UUID cruiseId, CruisePricingPolicyRequest request,
                                                         UUID actorId, boolean isApprover) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        CruisePricingPolicy policy = pricingPolicyRepository.findByCruise_CruiseId(cruiseId)
                .orElse(null);

        boolean isNew = (policy == null);
        if (isNew) {
            policy = new CruisePricingPolicy();
            policy.setCruise(cruise);
            policy.setStatus(PricingPolicyStatus.DRAFT);
            policy.setCreatedAt(Instant.now());
        }

        // Detect if prices or financial terms have changed on an already approved policy
        boolean pricesChanged = !isNew && (
                !Objects.equals(policy.getInteriorBaseFare(), request.getInteriorBaseFare())
                || !Objects.equals(policy.getOceanViewBaseFare(), request.getOceanViewBaseFare())
                || !Objects.equals(policy.getBalconyBaseFare(), request.getBalconyBaseFare())
                || !Objects.equals(policy.getSuiteBaseFare(), request.getSuiteBaseFare())
                || !Objects.equals(policy.getPortFeePerPassenger(), request.getPortFeePerPassenger())
                || !Objects.equals(policy.getTaxRate(), request.getTaxRate())
                || !Objects.equals(policy.getDepositPercentage(), request.getDepositPercentage())
                || !Objects.equals(policy.getCurrency(), request.getCurrency())
        );

        // Versioning rule: if approved policy changes prices, increment version and invalidate old active quotes
        if (pricesChanged && policy.getStatus() == PricingPolicyStatus.APPROVED) {
            String nextVersion = (request.getPolicyVersion() != null && !request.getPolicyVersion().isBlank())
                    ? request.getPolicyVersion()
                    : bumpVersion(policy.getPolicyVersion());
            policy.setPolicyVersion(nextVersion);
            int invalidated = bookingQuoteRepository.invalidateActiveQuotesByCruiseId(cruiseId);
            log.info("Pricing policy changed for cruise {}. New version: {}. Invalidated {} active quotes.",
                    cruiseId, nextVersion, invalidated);
        } else if (request.getPolicyVersion() != null && !request.getPolicyVersion().isBlank()) {
            policy.setPolicyVersion(request.getPolicyVersion());
        } else if (policy.getPolicyVersion() == null) {
            policy.setPolicyVersion("PRC-" + cruise.getCruiseCode() + "-V1");
        }

        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            policy.setCurrency(request.getCurrency().toUpperCase());
        }

        policy.setInteriorBaseFare(request.getInteriorBaseFare());
        policy.setOceanViewBaseFare(request.getOceanViewBaseFare());
        policy.setBalconyBaseFare(request.getBalconyBaseFare());
        policy.setSuiteBaseFare(request.getSuiteBaseFare());

        if (request.getPortFeePerPassenger() != null) {
            policy.setPortFeePerPassenger(request.getPortFeePerPassenger());
        }
        if (request.getTaxRate() != null) {
            policy.setTaxRate(request.getTaxRate());
        }
        if (request.getDepositPercentage() != null) {
            policy.setDepositPercentage(request.getDepositPercentage());
        }
        policy.setFinalPaymentDueDays(request.getFinalPaymentDueDays() > 0 ? request.getFinalPaymentDueDays() : 14);
        policy.setFullPaymentCutoffDays(request.getFullPaymentCutoffDays() > 0 ? request.getFullPaymentCutoffDays() : 7);
        policy.setCancellationPolicy(request.getCancellationPolicy());
        policy.setUpdatedAt(Instant.now());

        // Auto-approve if requested and actor has approval permission
        if (Boolean.TRUE.equals(request.getAutoApprove()) && isApprover) {
            validateFaresForApproval(cruise, policy);
            policy.setStatus(PricingPolicyStatus.APPROVED);
            policy.setApprovedAt(Instant.now());
            if (actorId != null) {
                userRepository.findById(actorId).ifPresent(policy::setApprovedBy);
            }
        }

        policy = pricingPolicyRepository.save(policy);
        cruisePreparationService.invalidateSignoffs(cruiseId, "Cập nhật chính sách giá: " + policy.getPolicyVersion());
        return toResponse(policy);
    }

    @Transactional
    public CruisePricingPolicyResponse approvePricingPolicy(UUID cruiseId, UUID actorId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        CruisePricingPolicy policy = pricingPolicyRepository.findByCruise_CruiseId(cruiseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICING_POLICY_MISSING",
                        "Chuyến đi chưa có chính sách giá được cấu hình để phê duyệt"));

        validateFaresForApproval(cruise, policy);

        policy.setStatus(PricingPolicyStatus.APPROVED);
        policy.setApprovedAt(Instant.now());
        if (actorId != null) {
            userRepository.findById(actorId).ifPresent(policy::setApprovedBy);
        }

        policy = pricingPolicyRepository.save(policy);
        cruisePreparationService.invalidateSignoffs(cruiseId, "Chính sách giá đã được phê duyệt: " + policy.getPolicyVersion());
        log.info("Pricing policy approved for cruise {} by user {}. Version: {}", cruiseId, actorId, policy.getPolicyVersion());
        return toResponse(policy);
    }

    private void validateFaresForApproval(Cruise cruise, CruisePricingPolicy policy) {
        // If ship is assigned, verify all cabin types on ship have configured (non-null) base fares
        if (cruise.getShip() != null) {
            UUID shipId = cruise.getShip().getShipId();
            for (CabinType type : CabinType.values()) {
                long count = cabinRepository.countByShipShipIdAndCabinType(shipId, type);
                if (count > 0 && policy.getBaseFareForCabinType(type) == null) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICING_POLICY_FARE_MISSING",
                            "Tàu có hạng phòng " + type + " nhưng chưa được cấu hình giá cơ sở trong chính sách");
                }
            }
        } else {
            // General validation: at least Interior base fare must be configured
            if (policy.getInteriorBaseFare() == null) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICING_POLICY_FARE_MISSING",
                        "Chính sách giá phải cấu hình ít nhất giá cơ sở phòng Interior");
            }
        }

        if (policy.getDepositPercentage() == null || policy.getDepositPercentage().compareTo(BigDecimal.ZERO) < 0
                || policy.getDepositPercentage().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DEPOSIT_PERCENTAGE",
                    "Tỷ lệ đặt cọc không hợp lệ (phải từ 0 đến 1.0)");
        }
    }

    private String bumpVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return "PRC-V2";
        }
        try {
            int lastV = currentVersion.lastIndexOf("-V");
            if (lastV != -1) {
                String numStr = currentVersion.substring(lastV + 2);
                int num = Integer.parseInt(numStr);
                return currentVersion.substring(0, lastV + 2) + (num + 1);
            }
        } catch (Exception ignored) {
        }
        return currentVersion + "-rev" + (System.currentTimeMillis() % 10000);
    }

    public CruisePricingPolicyResponse toResponse(CruisePricingPolicy policy) {
        Cruise cruise = policy.getCruise();
        User approver = policy.getApprovedBy();

        return CruisePricingPolicyResponse.builder()
                .pricingPolicyId(policy.getPricingPolicyId())
                .cruiseId(cruise != null ? cruise.getCruiseId() : null)
                .cruiseCode(cruise != null ? cruise.getCruiseCode() : null)
                .cruiseName(cruise != null ? cruise.getCruiseName() : null)
                .policyVersion(policy.getPolicyVersion())
                .status(policy.getStatus())
                .currency(policy.getCurrency())
                .interiorBaseFare(policy.getInteriorBaseFare())
                .oceanViewBaseFare(policy.getOceanViewBaseFare())
                .balconyBaseFare(policy.getBalconyBaseFare())
                .suiteBaseFare(policy.getSuiteBaseFare())
                .portFeePerPassenger(policy.getPortFeePerPassenger())
                .taxRate(policy.getTaxRate())
                .depositPercentage(policy.getDepositPercentage())
                .finalPaymentDueDays(policy.getFinalPaymentDueDays())
                .fullPaymentCutoffDays(policy.getFullPaymentCutoffDays())
                .cancellationPolicy(policy.getCancellationPolicy())
                .approvedBy(approver != null ? approver.getUserId() : null)
                .approvedByName(approver != null ? approver.getUsername() : null)
                .approvedAt(policy.getApprovedAt())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }
}
