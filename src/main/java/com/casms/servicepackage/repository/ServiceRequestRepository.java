package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("select coalesce(sum(r.quantity),0) from ServiceRequest r where r.service.serviceId=:id and r.status in :statuses")
    Long sumActiveQuantity(@Param("id") UUID id, @Param("statuses") Collection<ServiceRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.serviceRequestId = :id")
    Optional<ServiceRequest> lockById(@Param("id") UUID id);

    Optional<ServiceRequest> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(r.quantity),0) from ServiceRequest r where r.serviceSchedule.serviceScheduleId=:id and r.status in :statuses")
    Long sumActiveQuantityForSchedule(@Param("id") UUID id, @Param("statuses") Collection<ServiceRequestStatus> statuses);
}
