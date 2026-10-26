package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.trip.dto.response.TripDetailResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyReservationService reservationService;
    private final ObjectMapper objectMapper;

    @lombok.Setter
    private java.util.function.Function<UUID, Object> entityRecoveryFunction;

    public <T> T execute(String idempotencyKey, UUID actorId, String operation, Object payload, Supplier<T> action) {
        return execute(idempotencyKey, actorId, operation, payload, null, action);
    }

    public <T> T execute(String idempotencyKey, UUID actorId, String operation, Object payload, Class<T> responseType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        String trimmedKey = idempotencyKey.trim();
        String requestHash = computeHash(payload != null ? payload.toString() : "");

        IdempotencyReservationService.ReservationResult reservation =
                reservationService.reserveKey(actorId, operation, trimmedKey, requestHash);

        if (reservation.getStatus() == IdempotencyReservationService.ReservationStatus.CONFLICT) {
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Idempotency key '" + trimmedKey + "' đã được sử dụng với nội dung payload khác.");
        }

        if (reservation.getStatus() == IdempotencyReservationService.ReservationStatus.CACHED) {
            log.info("Idempotent replay detected for key: {} in operation: {}. Returning cached response without re-executing.",
                    trimmedKey, operation);
            return deserializeCached(reservation.getRecord(), responseType, operation, trimmedKey);
        }

        // Concurrent in-progress request: poll for completed cached response
        if (reservation.getStatus() == IdempotencyReservationService.ReservationStatus.IN_PROGRESS) {
            log.info("Concurrent request detected for idempotency key: {}. Awaiting completion of active operation.", trimmedKey);
            int maxAttempts = 15; // 15 * 200ms = 3000ms
            for (int i = 0; i < maxAttempts; i++) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                            "Quá trình chờ xử lý đồng thời cho Idempotency-Key bị gián đoạn.");
                }

                IdempotencyReservationService.ReservationResult polled =
                        reservationService.checkStatus(actorId, operation, trimmedKey, requestHash);

                if (polled.getStatus() == IdempotencyReservationService.ReservationStatus.CONFLICT) {
                    throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                            "Idempotency key '" + trimmedKey + "' đã được sử dụng với nội dung payload khác.");
                }
                if (polled.getStatus() == IdempotencyReservationService.ReservationStatus.CACHED) {
                    log.info("Concurrent request completed. Returning cached result for key: {}", trimmedKey);
                    return deserializeCached(polled.getRecord(), responseType, operation, trimmedKey);
                }
            }
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Yêu cầu đồng thời với cùng Idempotency-Key đang được xử lý bởi hệ thống.");
        }

        // We reserved the key! Execute the business action.
        UUID reservationId = reservation.getRecord() != null ? reservation.getRecord().getIdempotencyId() : null;
        T result;
        try {
            result = action.get();
        } catch (Exception e) {
            log.warn("Action execution failed for idempotency key {}: {}. Releasing reservation key.", trimmedKey, e.getMessage());
            reservationService.releaseKey(reservationId);
            throw e;
        }

        // Action succeeded, complete the reservation with serialized response
        try {
            String json = result != null ? objectMapper.writeValueAsString(result) : null;
            UUID resultingId = null;
            if (result instanceof TripDetailResponse detail) {
                resultingId = detail.getTripId();
            } else if (result instanceof com.casms.ship.dto.response.CruiseResponse cruiseRes) {
                resultingId = cruiseRes.getCruiseId();
            }
            reservationService.completeKey(reservationId, json, resultingId);
        } catch (Exception e) {
            log.warn("Failed to serialize or complete idempotency record for key {}: {}", trimmedKey, e.getMessage());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeCached(com.casms.trip.entity.IdempotencyRecord record, Class<T> responseType, String operation, String key) {
        String payload = record != null ? record.getResponsePayload() : null;
        if (payload == null || payload.isBlank()) {
            // Attempt recovery using resultingId and entityRecoveryFunction
            if (record != null && record.getResultingId() != null && entityRecoveryFunction != null) {
                try {
                    Object recovered = entityRecoveryFunction.apply(record.getResultingId());
                    if (recovered != null) {
                        log.info("Successfully recovered idempotency result for key {} from database resultingId: {}", key, record.getResultingId());
                        return (T) recovered;
                    }
                } catch (Exception ex) {
                    log.warn("Failed to recover entity via entityRecoveryFunction for resultingId {}: {}", record.getResultingId(), ex.getMessage());
                }
            }
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Dữ liệu phản hồi cached không tồn tại cho Idempotency-Key: " + key);
        }
        try {
            Class<T> targetClass = responseType != null ? responseType : inferResponseType(operation);
            if (targetClass != null) {
                return objectMapper.readValue(payload, targetClass);
            }
        } catch (Exception e) {
            log.error("Failed to deserialize cached idempotency payload for key {}: {}", key, e.getMessage());
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Không thể giải mã dữ liệu cached cho Idempotency-Key: " + key);
        }
        throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                "Không xác định được kiểu dữ liệu phản hồi cho Idempotency-Key: " + key);
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> inferResponseType(String operation) {
        if ("CREATE_TRIP".equals(operation) || "CLONE_TRIP".equals(operation)) {
            return (Class<T>) TripDetailResponse.class;
        } else if ("CREATE_CRUISE".equals(operation)) {
            return (Class<T>) com.casms.ship.dto.response.CruiseResponse.class;
        }
        return null;
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }
}
