package com.casms.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MockInternationalPaymentGateway implements PaymentGateway {

    public static final String PROVIDER_NAME = "MOCK_INTL_GATEWAY";

    @Value("${casms.payment.gateway.webhook-secret:casms-webhook-secret-key-2026}")
    private String webhookSecret = "casms-webhook-secret-key-2026";

    private final Map<String, GatewayResult> idempotencyStore = new ConcurrentHashMap<>();
    private final Map<String, GatewayResult> transactionStore = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> authorizedRemaining = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> capturedRemaining = new ConcurrentHashMap<>();

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public GatewayResult authorize(GatewayPaymentRequest request) {
        if (request.idempotencyKey() != null && idempotencyStore.containsKey("AUTH:" + request.idempotencyKey())) {
            return idempotencyStore.get("AUTH:" + request.idempotencyKey());
        }

        GatewayResult simulatedFailure = checkSimulatedTokenOutcome(request.paymentToken(), request.amount(), request.currency());
        if (simulatedFailure != null) {
            cacheResult("AUTH:" + request.idempotencyKey(), simulatedFailure);
            return simulatedFailure;
        }

        String txId = "GW-AUTH-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        GatewayResult result = new GatewayResult(
                true,
                false,
                "AUTHORIZED",
                txId,
                request.amount(),
                request.currency(),
                null,
                null,
                Instant.now());

        authorizedRemaining.put(txId, request.amount());
        cacheResult("AUTH:" + request.idempotencyKey(), result);
        return result;
    }

    @Override
    public GatewayResult capture(
            String providerAuthorizationReference,
            BigDecimal amount,
            String currency,
            String idempotencyKey) {
        if (idempotencyKey != null && idempotencyStore.containsKey("CAP:" + idempotencyKey)) {
            return idempotencyStore.get("CAP:" + idempotencyKey);
        }

        BigDecimal remainingAuth = authorizedRemaining.get(providerAuthorizationReference);
        if (remainingAuth != null && amount.compareTo(remainingAuth) > 0) {
            GatewayResult fail = new GatewayResult(
                    false,
                    false,
                    "FAILED",
                    providerAuthorizationReference,
                    BigDecimal.ZERO,
                    currency,
                    "CAPTURE_EXCEEDS_AUTHORIZATION",
                    "Capture amount " + amount + " exceeds remaining authorized amount " + remainingAuth,
                    Instant.now());
            cacheResult("CAP:" + idempotencyKey, fail);
            return fail;
        }

        String capTxId = "GW-CAP-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        if (remainingAuth != null) {
            authorizedRemaining.put(providerAuthorizationReference, remainingAuth.subtract(amount));
        }
        capturedRemaining.put(capTxId, amount);
        capturedRemaining.put(providerAuthorizationReference, amount);

        GatewayResult result = new GatewayResult(
                true,
                false,
                "CAPTURED",
                capTxId,
                amount,
                currency,
                null,
                null,
                Instant.now());

        cacheResult("CAP:" + idempotencyKey, result);
        return result;
    }

    @Override
    public GatewayResult charge(GatewayPaymentRequest request) {
        if (request.idempotencyKey() != null && idempotencyStore.containsKey("CHG:" + request.idempotencyKey())) {
            return idempotencyStore.get("CHG:" + request.idempotencyKey());
        }

        GatewayResult simulatedFailure = checkSimulatedTokenOutcome(request.paymentToken(), request.amount(), request.currency());
        if (simulatedFailure != null) {
            cacheResult("CHG:" + request.idempotencyKey(), simulatedFailure);
            return simulatedFailure;
        }

        String txId = "GW-CHG-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        capturedRemaining.put(txId, request.amount());

        GatewayResult result = new GatewayResult(
                true,
                false,
                "CAPTURED",
                txId,
                request.amount(),
                request.currency(),
                null,
                null,
                Instant.now());

        cacheResult("CHG:" + request.idempotencyKey(), result);
        return result;
    }

    @Override
    public GatewayResult refund(
            String providerTransactionReference,
            BigDecimal amount,
            String currency,
            String reason,
            String idempotencyKey) {
        if (idempotencyKey != null && idempotencyStore.containsKey("REF:" + idempotencyKey)) {
            return idempotencyStore.get("REF:" + idempotencyKey);
        }

        BigDecimal refundable = capturedRemaining.get(providerTransactionReference);
        if (refundable != null && amount.compareTo(refundable) > 0) {
            GatewayResult fail = new GatewayResult(
                    false,
                    false,
                    "FAILED",
                    providerTransactionReference,
                    BigDecimal.ZERO,
                    currency,
                    "REFUND_EXCEEDS_CAPTURED_AMOUNT",
                    "Refund amount " + amount + " exceeds refundable balance " + refundable,
                    Instant.now());
            cacheResult("REF:" + idempotencyKey, fail);
            return fail;
        }

        if (refundable != null) {
            capturedRemaining.put(providerTransactionReference, refundable.subtract(amount));
        }

        String refTxId = "GW-REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        GatewayResult result = new GatewayResult(
                true,
                false,
                "REFUNDED",
                refTxId,
                amount,
                currency,
                null,
                null,
                Instant.now());

        cacheResult("REF:" + idempotencyKey, result);
        return result;
    }

    @Override
    public GatewayResult getPaymentStatus(String providerTransactionReference) {
        GatewayResult existing = transactionStore.get(providerTransactionReference);
        if (existing != null) {
            return existing;
        }
        return new GatewayResult(
                true,
                false,
                providerTransactionReference != null && providerTransactionReference.startsWith("GW-AUTH-")
                        ? "AUTHORIZED"
                        : "CAPTURED",
                providerTransactionReference,
                BigDecimal.ZERO,
                "USD",
                null,
                null,
                Instant.now());
    }

    @Override
    public boolean verifyWebhookSignature(String payloadJson, String signatureHeader) {
        if (payloadJson == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String expected = computeHmacSha256(payloadJson, webhookSecret);
        String normalizedHeader = signatureHeader.startsWith("sha256=")
                ? signatureHeader.substring("sha256=".length()).trim()
                : signatureHeader.trim();
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                normalizedHeader.getBytes(StandardCharsets.UTF_8));
    }

    public String computeHmacSha256(String payloadJson, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 webhook signature", e);
        }
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    private GatewayResult checkSimulatedTokenOutcome(String token, BigDecimal amount, String currency) {
        if (token == null || token.isBlank()) {
            return new GatewayResult(
                    false, false, "FAILED", null, BigDecimal.ZERO, currency,
                    "INVALID_TOKEN", "Payment token is required", Instant.now());
        }
        String normalized = token.trim().toLowerCase();
        if (normalized.startsWith("tok_fail") || normalized.startsWith("tok_decline")) {
            return new GatewayResult(
                    false, false, "FAILED",
                    "GW-ERR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    BigDecimal.ZERO, currency,
                    "ISSUER_DECLINED", "Card issuer declined the transaction", Instant.now());
        }
        if (normalized.startsWith("tok_timeout") || normalized.startsWith("tok_uncertain")) {
            return new GatewayResult(
                    false, true, "PENDING_UNCERTAIN",
                    "GW-TIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    BigDecimal.ZERO, currency,
                    "GATEWAY_TIMEOUT", "Gateway response timed out; outcome uncertain, requires status reconciliation", Instant.now());
        }
        return null;
    }

    private void cacheResult(String key, GatewayResult result) {
        if (key != null) {
            idempotencyStore.put(key, result);
        }
        if (result.providerTransactionId() != null) {
            transactionStore.put(result.providerTransactionId(), result);
        }
    }
}

