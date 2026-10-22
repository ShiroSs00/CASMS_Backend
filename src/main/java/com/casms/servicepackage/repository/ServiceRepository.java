package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {
    boolean existsByServiceCodeAndServiceIdNot(String serviceCode, UUID serviceId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Service s where s.serviceId = :id")
    Optional<com.casms.servicepackage.entity.Service> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
