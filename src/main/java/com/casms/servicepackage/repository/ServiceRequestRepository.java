package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.enums.ServiceRequestStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {
    boolean existsByService_ServiceId(UUID serviceId);

    List<ServiceRequest> findByPassenger_PassengerId(UUID passengerId);

    List<ServiceRequest> findByPassenger_User_UserId(UUID userId);

    @Query(
            "select coalesce(sum(r.quantity),0) from ServiceRequest r where r.service.serviceId=:id"
                + " and r.status in :statuses")
    Long sumActiveQuantity(
            @Param("id") UUID id, @Param("statuses") Collection<ServiceRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.serviceRequestId = :id")
    Optional<ServiceRequest> lockById(@Param("id") UUID id);

    Optional<ServiceRequest> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
            SELECT request_hash FROM "IdempotencyRecord"
            WHERE actor_id=:actor AND operation='CREATE_SERVICE_REQUEST'
                AND idempotency_key=:key AND resulting_id=:resultId
            """, nativeQuery = true)
    Optional<String> findCreateHash(@Param("actor") UUID actor, @Param("key") String key,
                                    @Param("resultId") UUID resultId);

    @Modifying
    @Query(value = """
            INSERT INTO "IdempotencyRecord"(actor_id,operation,idempotency_key,request_hash,resulting_id)
            VALUES (:actor,'CREATE_SERVICE_REQUEST',:key,:hash,:resultId)
            """, nativeQuery = true)
    void recordCreateHash(@Param("actor") UUID actor, @Param("key") String key,
                          @Param("hash") String hash, @Param("resultId") UUID resultId);


    @Query(
            "select coalesce(sum(r.quantity),0) from ServiceRequest r where"
                + " r.serviceSchedule.serviceScheduleId=:id and r.status in :statuses")
    Long sumActiveQuantityForSchedule(
            @Param("id") UUID id, @Param("statuses") Collection<ServiceRequestStatus> statuses);
}
