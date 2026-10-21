package com.casms.activity.repository;

import com.casms.activity.entity.OnboardActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.Instant;
import java.util.Collection;
import com.casms.activity.enums.OnboardActivityStatus;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OnboardActivityRepository extends JpaRepository<OnboardActivity, UUID> {
    Optional<OnboardActivity> findByActivityCode(String activityCode);
    boolean existsByActivityCode(String activityCode);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from OnboardActivity a where a.onboardActivityId = :id")
    Optional<OnboardActivity> lockActivity(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Query("select count(a) from OnboardActivity a where a.cruise.cruiseId=:cruiseId and lower(a.location)=lower(:location) and a.startTime < :endTime and a.endTime > :startTime and a.status in :statuses and (:excludeId is null or a.onboardActivityId<>:excludeId)")
    long countScheduleConflicts(@org.springframework.data.repository.query.Param("cruiseId") UUID cruiseId,@org.springframework.data.repository.query.Param("location") String location,@org.springframework.data.repository.query.Param("startTime") Instant startTime,@org.springframework.data.repository.query.Param("endTime") Instant endTime,@org.springframework.data.repository.query.Param("statuses") Collection<OnboardActivityStatus> statuses,@org.springframework.data.repository.query.Param("excludeId") UUID excludeId);
}
