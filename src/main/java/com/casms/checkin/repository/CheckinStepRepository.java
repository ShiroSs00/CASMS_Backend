package com.casms.checkin.repository;

import com.casms.checkin.entity.CheckinStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CheckinStepRepository extends JpaRepository<CheckinStep, UUID> {
}
