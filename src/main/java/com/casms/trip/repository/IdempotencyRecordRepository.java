package com.casms.trip.repository;

import com.casms.trip.entity.IdempotencyRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO "IdempotencyRecord"
                (idempotency_id, actor_id, operation, idempotency_key, request_hash, created_at)
            VALUES (:id, :actor, 'CREATE_BOOKING', :key, :hash, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int reserveBooking(
            @Param("id") UUID id,
            @Param("actor") UUID actor,
            @Param("key") String key,
            @Param("hash") String hash);

    Optional<IdempotencyRecord> findByActorIdAndOperationAndIdempotencyKey(
            UUID actorId, String operation, String idempotencyKey);

    Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(
            String operation, String idempotencyKey);

    @Modifying
    @Query(
            value =
                    """
INSERT INTO "IdempotencyRecord" (idempotency_id, actor_id, operation, idempotency_key, request_hash, created_at)
VALUES (:id, :actor, 'CREATE_CRUISE', :key, :hash, now())
ON CONFLICT (actor_id, operation, idempotency_key) DO NOTHING
""",
            nativeQuery = true)
    int reserveCruise(
            @Param("id") UUID id,
            @Param("actor") UUID actor,
            @Param("key") String key,
            @Param("hash") String hash);
}
