package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, UUID> {
    List<ServiceCategory> findAllByOrderByCategoryNameAsc();
}
