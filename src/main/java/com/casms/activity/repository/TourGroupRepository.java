package com.casms.activity.repository;

import com.casms.activity.entity.TourGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TourGroupRepository extends JpaRepository<TourGroup, UUID> {
}
