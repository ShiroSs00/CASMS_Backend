package com.casms.activity.repository;

import com.casms.activity.entity.TourGroup;
import com.casms.activity.enums.TourGroupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TourGroupRepository extends JpaRepository<TourGroup, UUID> {

    boolean existsByShoreExcursion_ShoreExcursionIdAndGroupCodeIgnoreCase(
            UUID shoreExcursionId,
            String groupCode
    );

    boolean existsByShoreExcursion_ShoreExcursionIdAndGroupCodeIgnoreCaseAndTourGroupIdNot(
            UUID shoreExcursionId,
            String groupCode,
            UUID tourGroupId
    );

    List<TourGroup> findByShoreExcursion_ShoreExcursionId(
            UUID shoreExcursionId
    );

    List<TourGroup> findByStatus(TourGroupStatus status);

    List<TourGroup> findByGuideId(UUID guideId);

    List<TourGroup> findByShoreExcursion_ShoreExcursionIdAndStatus(
            UUID shoreExcursionId,
            TourGroupStatus status
    );

    List<TourGroup> findByShoreExcursion_ShoreExcursionIdAndGuideId(
            UUID shoreExcursionId,
            UUID guideId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT g
    FROM TourGroup g
    WHERE g.tourGroupId = :id
    """)
    Optional<TourGroup> findByIdForUpdate(@Param("id") UUID id);
}