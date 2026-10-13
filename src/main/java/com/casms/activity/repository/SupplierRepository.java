package com.casms.activity.repository;

import com.casms.activity.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupplierRepository
        extends JpaRepository<Supplier, UUID> {

    boolean existsBySupplierNameIgnoreCase(String supplierName);

    boolean existsBySupplierNameIgnoreCaseAndSupplierIdNot(
            String supplierName,
            UUID supplierId
    );

}