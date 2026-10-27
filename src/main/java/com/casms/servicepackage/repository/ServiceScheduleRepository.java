package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.ServiceSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceScheduleRepository extends JpaRepository<ServiceSchedule, UUID> {
    List<ServiceSchedule> findByService_ServiceIdAndStatusOrderByStartsAtAsc(UUID serviceId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServiceSchedule s where s.serviceScheduleId = :id")
    Optional<ServiceSchedule> lockById(@Param("id") UUID id);
}
