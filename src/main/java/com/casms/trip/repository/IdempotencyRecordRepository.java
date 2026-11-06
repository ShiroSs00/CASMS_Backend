package com.casms.trip.repository;

import com.casms.trip.entity.IdempotencyRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByActorIdAndOperationAndIdempotencyKey(
            UUID actorId, String operation, String idempotencyKey);

    Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(
            String operation, String idempotencyKey);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value =
                    """
INSERT INTO "IdempotencyRecord" (idempotency_id, actor_id, operation, idempotency_key, request_hash, created_at)
VALUES (:id, :actor, 'CREATE_CRUISE', :key, :hash, now())
ON CONFLICT (actor_id, operation, idempotency_key) DO NOTHING
""",
            nativeQuery = true)
    int reserveCruise(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("actor") UUID actor,
            @org.springframework.data.repository.query.Param("key") String key,
            @org.springframework.data.repository.query.Param("hash") String hash);
}
