package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.trip.entity.IdempotencyRecord;
import com.casms.trip.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public <T> T execute(String idempotencyKey, UUID actorId, String operation, Object payload, Supplier<T> action) {
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
            log.info("Idempotent replay detected for key: {} in operation: {}", idempotencyKey, operation);
            // Replay action (or return previous)
            return action.get();
        }

        T result = action.get();

        IdempotencyRecord record = new IdempotencyRecord();
        record.setActorId(actorId);
        record.setOperation(operation);
        record.setIdempotencyKey(idempotencyKey.trim());
        record.setRequestHash(requestHash);
        repository.save(record);

        return result;
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
