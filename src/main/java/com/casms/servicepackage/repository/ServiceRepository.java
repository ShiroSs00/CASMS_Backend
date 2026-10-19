package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {
    boolean existsByServiceCodeAndServiceIdNot(String serviceCode, UUID serviceId);
}
