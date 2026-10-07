package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.TourPackageExcursion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface TourPackageExcursionRepository extends JpaRepository<TourPackageExcursion, Serializable> {
}
