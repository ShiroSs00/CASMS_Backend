package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {
    boolean existsByService_ServiceId(UUID serviceId);
    java.util.List<ServiceRequest> findByPassenger_PassengerId(UUID passengerId);
    java.util.List<ServiceRequest> findByPassenger_User_UserId(UUID userId);
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(r.quantity),0) from ServiceRequest r where r.service.serviceId=:id and r.status in :statuses")
    Long sumActiveQuantity(@org.springframework.data.repository.query.Param("id") UUID id,@org.springframework.data.repository.query.Param("statuses") java.util.Collection<com.casms.servicepackage.enums.ServiceRequestStatus> statuses);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from ServiceRequest r where r.serviceRequestId = :id")
    java.util.Optional<ServiceRequest> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    java.util.Optional<ServiceRequest> findByIdempotencyKey(String idempotencyKey);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(r.quantity),0) from ServiceRequest r where r.serviceSchedule.serviceScheduleId=:id and r.status in :statuses")
    Long sumActiveQuantityForSchedule(@org.springframework.data.repository.query.Param("id") UUID id,
                                      @org.springframework.data.repository.query.Param("statuses") java.util.Collection<com.casms.servicepackage.enums.ServiceRequestStatus> statuses);
}
