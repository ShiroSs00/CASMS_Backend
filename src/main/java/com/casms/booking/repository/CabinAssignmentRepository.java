package com.casms.booking.repository;

import com.casms.booking.entity.CabinAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CabinAssignmentRepository extends JpaRepository<CabinAssignment, UUID> {
    boolean existsByCabinCabinId(UUID cabinId);
    List<CabinAssignment> findByCabinCabinIdOrderByValidFromDesc(UUID cabinId);
}