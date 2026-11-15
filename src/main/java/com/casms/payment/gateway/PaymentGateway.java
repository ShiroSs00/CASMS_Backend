package com.casms.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Provider-agnostic abstraction for International Cruise Payment Gateways
 * (e.g., Stripe, Adyen, Cybersource, MockSandbox).
 * Never accepts or persists raw PAN or CVV—operates strictly on tokenized instruments.
 */
public interface PaymentGateway {

    String getProviderName();

    GatewayResult authorize(GatewayPaymentRequest request);

    GatewayResult capture(
            String providerAuthorizationReference,
            BigDecimal amount,
            String currency,
            String idempotencyKey);

    GatewayResult charge(GatewayPaymentRequest request);

    GatewayResult refund(
            String providerTransactionReference,
            BigDecimal amount,
            String currency,
            String reason,
            String idempotencyKey);

    GatewayResult getPaymentStatus(String providerTransactionReference);

    boolean verifyWebhookSignature(String payloadJson, String signatureHeader);

    record GatewayPaymentRequest(
            String paymentToken,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String description,
            String customerReference) {}

    record GatewayResult(
            boolean successful,
            boolean uncertainOrTimeout,
            String gatewayStatus, // AUTHORIZED, CAPTURED, FAILED, REFUNDED, PENDING_UNCERTAIN
            String providerTransactionId,
            BigDecimal processedAmount,
            String currency,
            String errorCode,
            String errorMessage,
            Instant processedAt) {}
}

