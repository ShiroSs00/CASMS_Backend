package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.payment.dto.v1.InternationalPaymentDtos.*;
import com.casms.payment.entity.CurrencyExchangeRate;
import com.casms.payment.service.CurrencyExchangeService;
import com.casms.payment.service.CurrencyExchangeService.ConversionResult;
import com.casms.payment.service.CurrencyExchangeService.CurrencyMetadata;
import com.casms.payment.service.InternationalCruisePaymentService;
import com.casms.security.principal.CasmsPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(
        name = "International Cruise Payment & Settlement (v1)",
        description = "Multi-currency onboard cashless accounts, tokenized payment gateway, offline POS synchronization, and end-of-cruise settlement")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InternationalCruisePaymentController {

    private final InternationalCruisePaymentService paymentService;
    private final CurrencyExchangeService currencyExchangeService;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // 1. ONBOARD ACCOUNT ENDPOINTS
    // =========================================================================
    @Operation(summary = "Create or activate passenger onboard account for a cruise")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER') and hasAuthority('PAYMENT_PROCESS')"
            + " and !hasRole('PASSENGER') and principal.enabled and principal.accountNonLocked")
    @PostMapping("/onboard-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OnboardAccountResponse> createOnboardAccount(
            @RequestBody @Valid CreateOnboardAccountRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Onboard account created/activated successfully",
                paymentService.createOrActivateAccount(request, extractPrincipal(authentication)));
    }

    @Operation(summary = "Get onboard account details by ID")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/onboard-accounts/{id}")
    public ApiResponse<OnboardAccountResponse> getOnboardAccount(
            @PathVariable("id") UUID accountId,
            Authentication authentication) {
        return ApiResponse.success(
                paymentService.getAccount(accountId, extractPrincipal(authentication)));
    }

    @Operation(summary = "Get real-time onboard account balance and optional informational currency conversion")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/onboard-accounts/{id}/balance")
    public ApiResponse<AccountBalanceResponse> getOnboardAccountBalance(
            @PathVariable("id") UUID accountId,
            @RequestParam(required = false) String displayCurrency,
            Authentication authentication) {
        return ApiResponse.success(
                paymentService.getAccountBalance(accountId, displayCurrency, extractPrincipal(authentication)));
    }

    @Operation(summary = "Update onboard account spending limit")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER') and hasAuthority('PAYMENT_PROCESS')"
            + " and !hasRole('PASSENGER') and principal.enabled and principal.accountNonLocked")
    @PatchMapping("/onboard-accounts/{id}/spending-limit")
    public ApiResponse<OnboardAccountResponse> updateSpendingLimit(
            @PathVariable("id") UUID accountId,
            @RequestBody @Valid UpdateSpendingLimitRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Spending limit updated successfully",
                paymentService.updateSpendingLimit(accountId, request, extractPrincipal(authentication)));
    }

    // =========================================================================
    // 2. ONBOARD TRANSACTION ENDPOINTS
    // =========================================================================
    @Operation(summary = "Record an onboard purchase/charge or adjustment in cruise base currency")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> recordTransaction(
            @RequestBody @Valid CreateTransactionRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Onboard transaction posted successfully",
                paymentService.recordTransaction(request, extractPrincipal(authentication)));
    }

    @Operation(summary = "Get onboard transaction details by ID")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/transactions/{id}")
    public ApiResponse<TransactionResponse> getTransaction(
            @PathVariable("id") UUID transactionId,
            Authentication authentication) {
        return ApiResponse.success(
                paymentService.getTransaction(transactionId, extractPrincipal(authentication)));
    }

    @Operation(summary = "List all onboard transactions for an account")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/onboard-accounts/{id}/transactions")
    public ApiResponse<List<TransactionResponse>> getAccountTransactions(
            @PathVariable("id") UUID accountId,
            Authentication authentication) {
        return ApiResponse.success(
                paymentService.getAccountTransactions(accountId, extractPrincipal(authentication)));
    }

    @Operation(summary = "Refund a posted onboard transaction")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/transactions/{id}/refund")
    public ApiResponse<TransactionResponse> refundTransaction(
            @PathVariable("id") UUID transactionId,
            @RequestBody @Valid TransactionActionRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Onboard transaction refunded successfully",
                paymentService.refundTransaction(transactionId, request, extractPrincipal(authentication)));
    }

    @Operation(summary = "Void an onboard transaction and post an audit reversal")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/transactions/{id}/void")
    public ApiResponse<TransactionResponse> voidTransaction(
            @PathVariable("id") UUID transactionId,
            @RequestBody @Valid TransactionActionRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Onboard transaction voided successfully",
                paymentService.voidTransaction(transactionId, request, extractPrincipal(authentication)));
    }

    // =========================================================================
    // 3. INTERNATIONAL PAYMENT GATEWAY ENDPOINTS
    // =========================================================================
    @Operation(summary = "Pre-authorize a payment on a tokenized card")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/payments/authorize")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GatewayPaymentResponse> authorizePayment(
            @RequestBody @Valid AuthorizePaymentRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Payment pre-authorization processed",
                paymentService.authorizePayment(request, extractPrincipal(authentication)));
    }

    @Operation(summary = "Capture a previously authorized payment")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/payments/capture")
    public ApiResponse<GatewayPaymentResponse> capturePayment(
            @RequestBody @Valid CapturePaymentRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Authorized payment captured",
                paymentService.capturePayment(request, extractPrincipal(authentication)));
    }

    @Operation(summary = "Direct charge using a tokenized payment method")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/payments/charge")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GatewayPaymentResponse> chargePayment(
            @RequestBody @Valid DirectChargePaymentRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Direct payment processed",
                paymentService.chargePayment(request, extractPrincipal(authentication)));
    }

    @Operation(summary = "Refund a captured payment via the payment gateway")
    @PreAuthorize("hasRole('FINANCE_OFFICER') and hasAuthority('PAYMENT_REFUND')")
    @PostMapping("/payments/{id}/refund")
    public ApiResponse<GatewayPaymentResponse> refundPayment(
            @PathVariable("id") UUID paymentId,
            @RequestBody @Valid GatewayRefundRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Gateway payment refunded",
                paymentService.refundGatewayPayment(paymentId, request, extractPrincipal(authentication)));
    }

    @Operation(summary = "Get payment details and attempt history by ID")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/payments/{id}")
    public ApiResponse<GatewayPaymentResponse> getPayment(
            @PathVariable("id") UUID paymentId,
            Authentication authentication) {
        return ApiResponse.success(
                paymentService.getGatewayPayment(paymentId, extractPrincipal(authentication)));
    }

    @Operation(summary = "Query and synchronize live payment status from the external payment provider")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/payments/{id}/status")
    public ApiResponse<GatewayPaymentResponse> getPaymentStatus(
            @PathVariable("id") UUID paymentId,
            Authentication authentication) {
        return ApiResponse.success(
                paymentService.queryAndSyncPaymentStatus(paymentId, extractPrincipal(authentication)));
    }

    @Operation(summary = "Receive and verify HMAC-SHA256 signed webhook events from payment provider")
    @PostMapping("/payments/webhooks/{provider}")
    public ApiResponse<Map<String, Object>> handlePaymentWebhook(
            @PathVariable("provider") String provider,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody @Valid WebhookNotificationPayload payload) throws JsonProcessingException {
        String rawPayloadJson = objectMapper.writeValueAsString(payload);
        return ApiResponse.success(
                "Webhook processed",
                paymentService.processGatewayWebhook(provider, signature, rawPayloadJson, payload));
    }

    // =========================================================================
    // 4. TOKENIZED PAYMENT METHOD ENDPOINTS
    // =========================================================================
    @Operation(summary = "Register a tokenized payment method (never stores raw card PAN or CVV)")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisteredPaymentMethodResponse> registerPaymentMethod(
            @RequestBody @Valid RegisterPaymentMethodRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Tokenized payment method registered",
                paymentService.registerPaymentMethod(request, extractPrincipal(authentication)));
    }

    @Operation(summary = "List registered tokenized payment methods")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/payment-methods")
    public ApiResponse<List<RegisteredPaymentMethodResponse>> getPaymentMethods(
            @RequestParam(required = false) UUID passengerId,
            Authentication authentication) {
        return ApiResponse.success(
                paymentService.getPaymentMethods(passengerId, extractPrincipal(authentication)));
    }

    @Operation(summary = "Revoke / delete a registered payment method")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/payment-methods/{id}")
    public ApiResponse<Void> deletePaymentMethod(
            @PathVariable("id") UUID paymentMethodId,
            Authentication authentication) {
        paymentService.revokePaymentMethod(paymentMethodId, extractPrincipal(authentication));
        return ApiResponse.success("Payment method revoked successfully", null);
    }

    // =========================================================================
    // 5. END-OF-CRUISE SETTLEMENT ENDPOINTS
    // =========================================================================
    @Operation(summary = "Generate an end-of-cruise settlement statement after verifying offline sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER') and hasAuthority('SETTLEMENT_PROCESS')"
            + " and !hasRole('PASSENGER') and principal.enabled and principal.accountNonLocked")
    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SettlementStatementResponse> initiateSettlement(
            @RequestBody @Valid InitiateSettlementRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "End-of-cruise settlement statement generated",
                paymentService.initiateSettlement(request, extractPrincipal(authentication)));
    }

    @Operation(summary = "Get settlement statement and itemized folio by settlement ID")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/settlements/{id}")
    public ApiResponse<SettlementStatementResponse> getSettlement(
            @PathVariable("id") UUID settlementId,
            Authentication authentication) {
        return ApiResponse.success(
                paymentService.getSettlement(settlementId, extractPrincipal(authentication)));
    }

    @Operation(summary = "Get latest settlement statement for an onboard account")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/onboard-accounts/{id}/settlement")
    public ApiResponse<SettlementStatementResponse> getAccountSettlement(
            @PathVariable("id") UUID accountId,
            Authentication authentication) {
        return ApiResponse.success(
                paymentService.getAccountSettlement(accountId, extractPrincipal(authentication)));
    }

    @Operation(summary = "Process end-of-cruise settlement payment and close account upon full reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER') and hasAuthority('SETTLEMENT_PROCESS')"
            + " and !hasRole('PASSENGER') and principal.enabled and principal.accountNonLocked")
    @PostMapping("/settlements/{id}/process")
    public ApiResponse<SettlementStatementResponse> processSettlement(
            @PathVariable("id") UUID settlementId,
            @RequestBody @Valid ProcessSettlementRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Settlement processed",
                paymentService.processSettlement(settlementId, request, extractPrincipal(authentication)));
    }

    // =========================================================================
    // 6. MULTI-CURRENCY & FX RATE ENDPOINTS
    // =========================================================================
    @Operation(summary = "List supported ISO 4217 currencies and rounding rules")
    @GetMapping("/currencies")
    public ApiResponse<List<CurrencyMetadata>> getSupportedCurrencies() {
        return ApiResponse.success(currencyExchangeService.getSupportedCurrencies());
    }

    @Operation(summary = "Get configured exchange rates for a base currency")
    @GetMapping("/exchange-rates")
    public ApiResponse<List<CurrencyExchangeRate>> getExchangeRates(
            @RequestParam(defaultValue = "USD") String baseCurrency) {
        return ApiResponse.success(currencyExchangeService.getExchangeRates(baseCurrency));
    }

    @Operation(summary = "Register a new exchange rate snapshot")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/exchange-rates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CurrencyExchangeRate> registerExchangeRate(
            @RequestBody @Valid ExchangeRateUpsertRequest request) {
        return ApiResponse.success(
                "Exchange rate registered",
                currencyExchangeService.registerExchangeRate(
                        request.baseCurrency(),
                        request.targetCurrency(),
                        request.rate(),
                        request.source(),
                        request.effectiveFrom()));
    }

    @Operation(summary = "Convert an amount between currencies (informational display vs settlement DCC)")
    @GetMapping("/currency-conversion")
    public ApiResponse<ConversionResult> convertCurrency(
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "USD") String fromCurrency,
            @RequestParam String toCurrency,
            @RequestParam(defaultValue = "false") boolean forSettlement,
            @RequestParam(defaultValue = "false") boolean customerDccConsent) {
        ConversionResult result = forSettlement
                ? currencyExchangeService.convertForSettlement(amount, fromCurrency, toCurrency, customerDccConsent)
                : currencyExchangeService.convertForDisplay(amount, fromCurrency, toCurrency);
        return ApiResponse.success(result);
    }

    // =========================================================================
    // 7. OFFLINE POS TRANSACTION SYNCHRONIZATION ENDPOINTS
    // =========================================================================
    @Operation(summary = "Synchronize offline POS transactions with idempotency and conflict detection")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/offline-transactions/sync")
    public ApiResponse<OfflineBatchSyncResponse> syncOfflineTransactions(
            @RequestBody @Valid OfflineBatchSyncRequest request,
            Authentication authentication) {
        return ApiResponse.success(
                "Offline transaction batch processed",
                paymentService.syncOfflineTransactions(request, extractPrincipal(authentication)));
    }

    @Operation(summary = "Get offline synchronization status and conflict logs")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/offline-transactions/sync-status")
    public ApiResponse<Map<String, Object>> getOfflineSyncStatus(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID batchId) {
        return ApiResponse.success(paymentService.getOfflineSyncStatus(accountId, batchId));
    }

    private CasmsPrincipal extractPrincipal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CasmsPrincipal cp) {
            return cp;
        }
        return null;
    }
}

