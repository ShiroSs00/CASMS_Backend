package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.Service;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {
    boolean existsByServiceCodeAndServiceIdNot(String serviceCode, UUID serviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Service s where s.serviceId = :id")
    Optional<Service> findByIdForUpdate(@Param("id") UUID id);
}
