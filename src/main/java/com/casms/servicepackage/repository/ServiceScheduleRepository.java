package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.ServiceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ServiceScheduleRepository extends JpaRepository<ServiceSchedule, UUID> {
    List<ServiceSchedule> findByService_ServiceIdAndStatusOrderByStartsAtAsc(UUID serviceId, String status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from ServiceSchedule s where s.serviceScheduleId = :id")
    java.util.Optional<ServiceSchedule> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
