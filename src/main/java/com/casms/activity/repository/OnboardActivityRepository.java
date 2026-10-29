package com.casms.activity.repository;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.enums.OnboardActivityStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnboardActivityRepository extends JpaRepository<OnboardActivity, UUID> {
    Optional<OnboardActivity> findByActivityCode(String activityCode);
    boolean existsByActivityCode(String activityCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OnboardActivity a where a.onboardActivityId = :id")
    Optional<OnboardActivity> lockActivity(@Param("id") UUID id);
    @Query("select count(a) from OnboardActivity a where a.cruise.cruiseId=:cruiseId and lower(a.location)=lower(:location) and a.startTime < :endTime and a.endTime > :startTime and a.status in :statuses and (:excludeId is null or a.onboardActivityId<>:excludeId)")
    long countScheduleConflicts(@Param("cruiseId") UUID cruiseId,@Param("location") String location,@Param("startTime") Instant startTime,@Param("endTime") Instant endTime,@Param("statuses") Collection<OnboardActivityStatus> statuses,@Param("excludeId") UUID excludeId);

    long countByCruise_CruiseId(UUID cruiseId);
    long countByCruise_CruiseIdAndDeckIsNotNull(UUID cruiseId);
    List<OnboardActivity> findByCruise_CruiseId(UUID cruiseId);
    void deleteByCruise_CruiseId(UUID cruiseId);
}
