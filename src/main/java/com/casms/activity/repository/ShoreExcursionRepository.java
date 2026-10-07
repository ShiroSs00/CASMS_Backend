package com.casms.activity.repository;

import com.casms.activity.entity.ShoreExcursion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShoreExcursionRepository extends JpaRepository<ShoreExcursion, UUID> {
}
