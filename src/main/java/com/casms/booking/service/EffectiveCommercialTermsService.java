package com.casms.booking.service;

import com.casms.booking.enums.PaymentPlan;
import com.casms.common.exception.BusinessException;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruisePricingPolicy;
import com.casms.ship.enums.PricingPolicyStatus;
import com.casms.ship.service.CruiseMainflowService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Resolves Booking terms from approved sources without owning another policy lifecycle. */
final class EffectiveCommercialTermsService {
    private static final BigDecimal LEGACY_DEPOSIT = new BigDecimal("0.30");
    private static final int LEGACY_PAYMENT_DAYS = 7;

    private EffectiveCommercialTermsService() {}

    record SaleTerms(String version, String hash, BigDecimal portFee, BigDecimal taxRate) {}

    record Terms(String version, String currency, UUID pricingPolicyId, String pricingVersion,
            Long pricingRevision, String saleTermsVersion, String saleTermsHash, BigDecimal portFee,
            BigDecimal taxRate, BigDecimal depositRate, int cutoffDays, int dueDays,
            boolean compatibilityDefaults, String fingerprint) {}

    record Snapshot(int schemaVersion, Terms terms, UUID quoteId, String cancellationPolicyVersion,
            PaymentPlan paymentPlan, BigDecimal agreedAmount, BigDecimal depositAmount,
            String finalPaymentDueAt, String committedAt) {}

    static Terms resolve(Cruise cruise, SaleTerms sale, CruisePricingPolicy pricing,
            boolean requireApprovedPackagePaymentTerms) {
        String currency = cruise.getSaleCurrency();
        boolean pricingRequired = sale == null || requireApprovedPackagePaymentTerms;
        if (pricingRequired && (pricing == null || pricing.getStatus() != PricingPolicyStatus.APPROVED)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICING_POLICY_MISSING",
                    "Chuyến khởi hành chưa có chính sách giá được phê duyệt.");
        }
        if (pricing != null && (currency == null || pricing.getCurrency() == null
                || !currency.equalsIgnoreCase(pricing.getCurrency().strip()))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH",
                    "Đơn vị tiền tệ của chính sách giá không khớp với chuyến khởi hành.");
        }
        boolean useDefaultDeposit = pricing == null || pricing.getDepositPercentage() == null;
        boolean useDefaultCutoff = pricing == null || pricing.getFullPaymentCutoffDays() <= 0;
        boolean useDefaultDue = pricing == null || pricing.getFinalPaymentDueDays() <= 0;
        boolean usesDefaults = useDefaultDeposit || useDefaultCutoff || useDefaultDue;
        if (sale != null && requireApprovedPackagePaymentTerms && usesDefaults) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMERCIAL_TERMS_INCOMPLETE",
                    "Chính sách giá đã duyệt chưa có đủ điều kiện thanh toán.");
        }
        BigDecimal deposit = useDefaultDeposit ? LEGACY_DEPOSIT : pricing.getDepositPercentage();
        int cutoff = useDefaultCutoff ? LEGACY_PAYMENT_DAYS : pricing.getFullPaymentCutoffDays();
        int due = useDefaultDue ? LEGACY_PAYMENT_DAYS : pricing.getFinalPaymentDueDays();
        BigDecimal portFee = Objects.requireNonNullElse(
                sale == null ? pricing.getPortFeePerPassenger() : sale.portFee(), BigDecimal.ZERO);
        BigDecimal tax = Objects.requireNonNullElse(
                sale == null ? pricing.getTaxRate() : sale.taxRate(), BigDecimal.ZERO);
        String version = sale == null ? pricing.getPolicyVersion() : sale.version();
        String pricingVersion = pricing == null ? null : pricing.getPolicyVersion();
        Long pricingRevision = pricing == null ? null : pricing.getRevision();
        UUID policyId = pricing == null ? null : pricing.getPricingPolicyId();
        String saleVersion = sale == null ? null : sale.version();
        String saleHash = sale == null ? null : sale.hash();
        String fingerprint = CruiseMainflowService.hash(String.join("|", "booking-terms-v1",
                String.valueOf(cruise.getCruiseId()), String.valueOf(cruise.getDepartureDatetime()),
                String.valueOf(currency), String.valueOf(policyId), String.valueOf(pricingVersion),
                String.valueOf(pricingRevision), String.valueOf(saleVersion), String.valueOf(saleHash),
                portFee.stripTrailingZeros().toPlainString(), tax.stripTrailingZeros().toPlainString(),
                deposit.stripTrailingZeros().toPlainString(), Integer.toString(cutoff), Integer.toString(due),
                Boolean.toString(usesDefaults)));
        return new Terms(version, currency, policyId, pricingVersion, pricingRevision, saleVersion,
                saleHash, portFee, tax, deposit, cutoff, due, usesDefaults, fingerprint);
    }

    static Snapshot snapshot(Terms terms, UUID quoteId, PaymentPlan plan, BigDecimal total,
            BigDecimal deposit, Instant due, Instant committedAt) {
        return new Snapshot(1, terms, quoteId, null, plan, total, deposit, due.toString(), committedAt.toString());
    }
}
