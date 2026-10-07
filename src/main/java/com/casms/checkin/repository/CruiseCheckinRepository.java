package com.casms.checkin.repository;

import com.casms.checkin.entity.CruiseCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CruiseCheckinRepository extends JpaRepository<CruiseCheckin, UUID> {
}
