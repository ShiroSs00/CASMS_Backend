package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.TourPackageService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface TourPackageServiceRepository extends JpaRepository<TourPackageService, Serializable> {
}
