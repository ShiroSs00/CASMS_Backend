package com.casms.trip.service;

import com.casms.trip.entity.IdempotencyRecord;
import com.casms.trip.repository.IdempotencyRecordRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyReservationService {

    private final IdempotencyRecordRepository repository;

    public enum ReservationStatus {
        RESERVED,
        CACHED,
        IN_PROGRESS,
        CONFLICT
    }

    @Getter
    @AllArgsConstructor
    public static class ReservationResult {
        private final ReservationStatus status;
        private final IdempotencyRecord record;
    }

    public ReservationResult reserveKey(UUID actorId, String operation, String idempotencyKey, String requestHash) {
        ReservationResult existing = checkStatus(actorId, operation, idempotencyKey, requestHash);
        if (existing.getStatus() != ReservationStatus.RESERVED) {
            return existing;
        }

        try {
            IdempotencyRecord record = tryInsert(actorId, operation, idempotencyKey, requestHash);
            return new ReservationResult(ReservationStatus.RESERVED, record);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition: another transaction committed key first.
            // That transaction was rolled back, now read cleanly in a new transaction.
            return checkStatus(actorId, operation, idempotencyKey, requestHash);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord tryInsert(UUID actorId, String operation, String idempotencyKey, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setActorId(actorId);
        record.setOperation(operation);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setCreatedAt(Instant.now());
        return repository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ReservationResult checkStatus(UUID actorId, String operation, String idempotencyKey, String requestHash) {
        Optional<IdempotencyRecord> existingOpt = repository.findByActorIdAndOperationAndIdempotencyKey(
                actorId, operation, idempotencyKey);
        if (existingOpt.isEmpty()) {
            return new ReservationResult(ReservationStatus.RESERVED, null);
        }
        IdempotencyRecord existing = existingOpt.get();
        if (!existing.getRequestHash().equals(requestHash)) {
            return new ReservationResult(ReservationStatus.CONFLICT, existing);
        }
        if (existing.getResponsePayload() != null && !existing.getResponsePayload().isBlank()) {
            return new ReservationResult(ReservationStatus.CACHED, existing);
        }
        return new ReservationResult(ReservationStatus.IN_PROGRESS, existing);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeKey(UUID idempotencyId, String responsePayload, UUID resultingId) {
        if (idempotencyId == null) return;
        repository.findById(idempotencyId).ifPresent(record -> {
            record.setResponsePayload(responsePayload);
            record.setResultingId(resultingId);
            repository.saveAndFlush(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseKey(UUID idempotencyId) {
        if (idempotencyId != null) {
            try {
                repository.deleteById(idempotencyId);
                repository.flush();
            } catch (Exception e) {
                log.warn("Failed to release idempotency record {}: {}", idempotencyId, e.getMessage());
            }
        }
    }
}
