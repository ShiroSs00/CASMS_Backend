package com.casms.payment.dto.v1;

import com.casms.payment.enums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Clean DTO contracts for the International Cruise Payment Module (/api/v1/...).
 */
public final class InternationalPaymentDtos {

    private InternationalPaymentDtos() {}

    // =========================================================================
    // 1. ONBOARD ACCOUNT DTOs
    // =========================================================================
    public record CreateOnboardAccountRequest(
            @NotNull(message = "Cruise ID is required") UUID cruiseId,
            @NotNull(message = "Passenger ID is required") UUID passengerId,
            @DecimalMin(value = "0.00", message = "Spending limit cannot be negative") BigDecimal spendingLimit,
            String baseCurrency,
            Boolean activateImmediately
    ) {}

    public record UpdateSpendingLimitRequest(
            @NotNull(message = "New spending limit is required")
            @DecimalMin(value = "0.00", message = "Spending limit cannot be negative")
            BigDecimal newSpendingLimit,
            String reason,
            Boolean financeStaffOverride
    ) {}

    public record OnboardAccountResponse(
            UUID accountId,
            UUID passengerId,
            String passengerName,
            UUID cruiseId,
            String cruiseCode,
            String baseCurrency,
            BigDecimal spendingLimit,
            BigDecimal preAuthorizedAmount,
            BigDecimal totalCharges,
            BigDecimal totalRefunds,
            BigDecimal totalPayments,
            BigDecimal outstandingBalance,
            BigDecimal availableSpendingLimit,
            OnboardAccountStatus status,
            Instant activatedAt,
            Instant closedAt,
            Instant createdAt
    ) {}

    public record AccountBalanceResponse(
            UUID accountId,
            UUID passengerId,
            UUID cruiseId,
            String baseCurrency,
            BigDecimal spendingLimit,
            BigDecimal totalCharges,
            BigDecimal totalRefunds,
            BigDecimal totalPayments,
            BigDecimal outstandingBalance,
            BigDecimal availableSpendingLimit,
            OnboardAccountStatus accountStatus,
            // Optional informational display conversion
            String displayCurrency,
            BigDecimal convertedOutstandingBalance,
            BigDecimal convertedAvailableLimit,
            BigDecimal displayExchangeRate,
            String conversionNote
    ) {}

    // =========================================================================
    // 2. ONBOARD TRANSACTION DTOs
    // =========================================================================
    public record CreateTransactionRequest(
            @NotNull(message = "Account ID is required") UUID accountId,
            UUID passengerId,
            @NotBlank(message = "Service or product reference is required") String serviceReference,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be strictly positive")
            BigDecimal amount,
            @NotBlank(message = "Currency is required") String currency,
            TransactionType transactionType,
            UUID posTerminalId,
            String deviceReference,
            @NotBlank(message = "Idempotency key is required") String idempotencyKey,
            String description,
            Boolean staffLimitOverrideApproved
    ) {}

    public record TransactionActionRequest(
            @DecimalMin(value = "0.01", message = "Amount must be strictly positive") BigDecimal amount,
            @NotBlank(message = "Reason is required") String reason,
            @NotBlank(message = "Idempotency key is required") String idempotencyKey
    ) {}

    public record TransactionResponse(
            UUID transactionId,
            UUID accountId,
            UUID passengerId,
            String serviceReference,
            BigDecimal amount,
            String currency,
            TransactionType transactionType,
            TransactionStatus status,
            TransactionSyncStatus syncStatus,
            Instant transactionTimestamp,
            Instant syncedAt,
            UUID posTerminalId,
            String deviceReference,
            String idempotencyKey,
            UUID originalTransactionId,
            UUID createdByUserId,
            String description
    ) {}

    // =========================================================================
    // 3. TOKENIZED PAYMENT METHOD DTOs
    // =========================================================================
    public record RegisterPaymentMethodRequest(
            @NotNull(message = "Passenger ID is required") UUID passengerId,
            @NotBlank(message = "Provider is required") String provider,
            @NotBlank(message = "Payment token is required") String paymentToken,
            String providerCustomerReference,
            @NotBlank(message = "Card brand is required") String cardBrand,
            @NotBlank(message = "Card last 4 digits are required")
            @Pattern(regexp = "\\d{4}", message = "cardLast4 must be exactly 4 digits")
            String cardLast4,
            String cardHolderName,
            @NotNull @Min(1) @Max(12) Integer expiryMonth,
            @NotNull @Min(2025) @Max(2100) Integer expiryYear,
            String issuingCountry,
            String issuingCurrency,
            Boolean setAsDefault
    ) {}

    public record RegisteredPaymentMethodResponse(
            UUID paymentMethodId,
            UUID passengerId,
            String provider,
            String paymentToken,
            String cardBrand,
            String cardLast4,
            String cardHolderName,
            Integer expiryMonth,
            Integer expiryYear,
            String issuingCountry,
            String issuingCurrency,
            boolean isDefault,
            String status,
            Instant createdAt
    ) {}

    // =========================================================================
    // 4. INTERNATIONAL PAYMENT & GATEWAY DTOs
    // =========================================================================
    public record AuthorizePaymentRequest(
            UUID accountId,
            UUID bookingId,
            UUID registeredPaymentMethodId,
            String paymentToken,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency,
            String settlementCurrency,
            Boolean customerDccConsent,
            @NotBlank String idempotencyKey,
            String description
    ) {}

    public record CapturePaymentRequest(
            @NotNull(message = "Payment ID is required") UUID paymentId,
            @NotNull @DecimalMin("0.01") BigDecimal captureAmount,
            @NotBlank String idempotencyKey
    ) {}

    public record DirectChargePaymentRequest(
            UUID accountId,
            UUID bookingId,
            UUID registeredPaymentMethodId,
            String paymentToken,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency,
            String settlementCurrency,
            Boolean customerDccConsent,
            @NotBlank String idempotencyKey,
            String description
    ) {}

    public record GatewayRefundRequest(
            @NotNull @DecimalMin("0.01") BigDecimal refundAmount,
            @NotBlank String reason,
            @NotBlank String idempotencyKey
    ) {}

    public record GatewayPaymentResponse(
            UUID paymentId,
            UUID accountId,
            UUID bookingId,
            UUID registeredPaymentMethodId,
            String provider,
            String processorReference,
            PaymentStatus status,
            BigDecimal requestedAmount,
            BigDecimal authorizedAmount,
            BigDecimal capturedAmount,
            BigDecimal refundedAmount,
            String baseCurrency,
            BigDecimal convertedAmount,
            String settlementCurrency,
            BigDecimal exchangeRateApplied,
            String exchangeRateSource,
            String idempotencyKey,
            String failureReason,
            Instant requestedAt,
            Instant authorizedAt,
            Instant capturedAt,
            List<PaymentAttemptDto> attempts
    ) {}

    public record PaymentAttemptDto(
            UUID attemptId,
            int attemptNo,
            String operation,
            String provider,
            String providerTransactionId,
            BigDecimal amount,
            String currency,
            String status,
            String errorCode,
            String errorMessage,
            Instant requestedAt,
            Instant completedAt
    ) {}

    public record WebhookNotificationPayload(
            @NotBlank String externalEventId,
            @NotBlank String eventType, // PAYMENT_AUTHORIZED, PAYMENT_CAPTURED, PAYMENT_FAILED, PAYMENT_REFUNDED
            UUID paymentId,
            String processorReference,
            BigDecimal amount,
            String currency,
            String failureReason
    ) {}

    // =========================================================================
    // 5. OFFLINE SYNCHRONIZATION DTOs
    // =========================================================================
    public record OfflineTransactionItemRequest(
            UUID transactionId,
            @NotNull UUID accountId,
            UUID passengerId,
            @NotBlank String serviceReference,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency,
            TransactionType transactionType,
            UUID posTerminalId,
            @NotBlank String deviceReference,
            @NotBlank String idempotencyKey,
            @NotNull Instant occurrenceTime,
            String description
    ) {}

    public record OfflineBatchSyncRequest(
            UUID batchId,
            UUID posTerminalId,
            @NotBlank String deviceReference,
            @NotEmpty List<OfflineTransactionItemRequest> transactions
    ) {}

    public record OfflineSyncItemResult(
            UUID transactionId,
            String idempotencyKey,
            String syncOutcome, // SYNCED, DUPLICATE_IGNORED, CONFLICT, SYNC_FAILED
            TransactionSyncStatus transactionSyncStatus,
            String conflictCode,
            String errorDetail,
            Instant occurrenceTime,
            Instant syncedAt
    ) {}

    public record OfflineBatchSyncResponse(
            UUID batchId,
            String deviceReference,
            int totalSubmitted,
            int syncedCount,
            int duplicateIgnoredCount,
            int conflictCount,
            int failedCount,
            Instant processedAt,
            List<OfflineSyncItemResult> results
    ) {}

    // =========================================================================
    // 6. END-OF-CRUISE SETTLEMENT DTOs
    // =========================================================================
    public record InitiateSettlementRequest(
            @NotNull(message = "Onboard account ID is required") UUID accountId,
            Boolean forceWithDispute
    ) {}

    public record ProcessSettlementRequest(
            UUID registeredPaymentMethodId,
            UUID existingAuthorizedPaymentId,
            BigDecimal paymentAmount,
            Boolean allowPartialPayment,
            String settlementCurrency,
            Boolean customerDccConsent,
            @NotBlank(message = "Idempotency key is required") String idempotencyKey,
            Boolean authorizedExceptionClose
    ) {}

    public record SettlementItemDto(
            UUID settlementItemId,
            UUID sourceTransactionId,
            UUID sourcePaymentId,
            String itemType,
            String description,
            BigDecimal amount,
            String currency,
            Instant occurredAt
    ) {}

    public record SettlementStatementResponse(
            UUID settlementId,
            UUID accountId,
            String baseCurrency,
            BigDecimal totalCharges,
            BigDecimal totalRefunds,
            BigDecimal totalPayments,
            BigDecimal outstandingBalance,
            BigDecimal refundDue,
            FinalSettlementStatus settlementStatus,
            OnboardAccountStatus accountStatus,
            Instant startedAt,
            Instant completedAt,
            String notes,
            List<SettlementItemDto> items
    ) {}

    public record ExchangeRateUpsertRequest(
            @NotBlank String baseCurrency,
            @NotBlank String targetCurrency,
            @NotNull @DecimalMin("0.000001") BigDecimal rate,
            String source,
            Instant effectiveFrom
    ) {}
}
