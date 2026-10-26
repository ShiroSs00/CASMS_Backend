package com.casms.ship.repository;

import com.casms.ship.entity.CruiseImpactTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruiseImpactTaskRepository extends JpaRepository<CruiseImpactTask, UUID> {

    List<CruiseImpactTask> findByCruiseCruiseIdOrderByCreatedAtDesc(UUID cruiseId);

    List<CruiseImpactTask> findByCruiseCruiseIdAndStatusOrderByCreatedAtDesc(UUID cruiseId, String status);

    Optional<CruiseImpactTask> findByTaskCode(String taskCode);

    boolean existsByTaskCode(String taskCode);

    long countByCruiseCruiseIdAndStatus(UUID cruiseId, String status);

    void deleteByCruiseCruiseId(UUID cruiseId);
}
