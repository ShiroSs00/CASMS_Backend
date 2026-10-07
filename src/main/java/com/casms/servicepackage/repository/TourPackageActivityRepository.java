package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.TourPackageActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface TourPackageActivityRepository extends JpaRepository<TourPackageActivity, Serializable> {
}
