package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.trip.dto.response.TripDetailResponse;
import com.casms.trip.entity.IdempotencyRecord;
import com.casms.trip.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> T execute(String idempotencyKey, UUID actorId, String operation, Object payload, Supplier<T> action) {
        return execute(idempotencyKey, actorId, operation, payload, null, action);
    }

    @Transactional
    public <T> T execute(String idempotencyKey, UUID actorId, String operation, Object payload, Class<T> responseType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        String requestHash = computeHash(payload != null ? payload.toString() : "");

        Optional<IdempotencyRecord> existingOpt = repository.findByActorIdAndOperationAndIdempotencyKey(
                actorId, operation, idempotencyKey.trim());

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "Idempotency key '" + idempotencyKey + "' đã được sử dụng với nội dung payload khác.");
            }
            log.info("Idempotent replay detected for key: {} in operation: {}. Returning cached response without re-executing.",
                    idempotencyKey, operation);

            if (existing.getResponsePayload() != null && !existing.getResponsePayload().isBlank()) {
                try {
                    Class<T> targetClass = responseType != null ? responseType : inferResponseType(operation);
                    if (targetClass != null) {
                        return objectMapper.readValue(existing.getResponsePayload(), targetClass);
                    }
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached idempotency payload for key {}: {}", idempotencyKey, e.getMessage());
                }
            }
            // Fallback if no serialized payload available
            return action.get();
        }

        T result;
        try {
            result = action.get();
        } catch (Exception e) {
            throw e;
        }

        try {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setActorId(actorId);
            record.setOperation(operation);
            record.setIdempotencyKey(idempotencyKey.trim());
            record.setRequestHash(requestHash);

            if (result != null) {
                try {
                    record.setResponsePayload(objectMapper.writeValueAsString(result));
                } catch (Exception e) {
                    log.warn("Failed to serialize idempotency response payload: {}", e.getMessage());
                }

                if (result instanceof TripDetailResponse detail) {
                    record.setResultingId(detail.getTripId());
                }
            }

            repository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate request detected for idempotency key: {}", idempotencyKey);
            // Re-fetch record saved concurrently
            Optional<IdempotencyRecord> concurrentOpt = repository.findByActorIdAndOperationAndIdempotencyKey(
                    actorId, operation, idempotencyKey.trim());
            if (concurrentOpt.isPresent()) {
                IdempotencyRecord concurrent = concurrentOpt.get();
                if (!concurrent.getRequestHash().equals(requestHash)) {
                    throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                            "Idempotency key '" + idempotencyKey + "' đã được sử dụng với nội dung payload khác.");
                }
                if (concurrent.getResponsePayload() != null) {
                    try {
                        Class<T> targetClass = responseType != null ? responseType : inferResponseType(operation);
                        if (targetClass != null) {
                            return objectMapper.readValue(concurrent.getResponsePayload(), targetClass);
                        }
                    } catch (Exception ex) {
                        // ignore and continue
                    }
                }
            }
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Yêu cầu đồng thời với cùng Idempotency-Key đang được xử lý.");
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> inferResponseType(String operation) {
        if ("CREATE_TRIP".equals(operation) || "CLONE_TRIP".equals(operation)) {
            return (Class<T>) TripDetailResponse.class;
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
