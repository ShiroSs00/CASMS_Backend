package com.casms.activity.repository;

import com.casms.activity.entity.TourGroupPassenger;
import com.casms.activity.enums.TourGroupPassengerAssignmentStatus;
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
public interface TourGroupPassengerRepository
        extends JpaRepository<TourGroupPassenger, UUID> {

    @Query("""
        SELECT tp
        FROM TourGroupPassenger tp
        WHERE (:groupId IS NULL OR tp.tourGroup.tourGroupId = :groupId)
          AND (:passengerId IS NULL OR tp.passenger.passengerId = :passengerId)
          AND (:status IS NULL OR tp.assignmentStatus = :status)
        ORDER BY tp.createdAt DESC
        """)
    List<TourGroupPassenger> search(
            @Param("groupId") UUID groupId,
            @Param("passengerId") UUID passengerId,
            @Param("status") TourGroupPassengerAssignmentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT tp
        FROM TourGroupPassenger tp
        WHERE tp.tourGroupPassengerId = :id
        """)
    Optional<TourGroupPassenger> findByIdForUpdate(
            @Param("id") UUID id
    );

    boolean existsByTourGroup_TourGroupIdAndPassenger_PassengerIdAndAssignmentStatusNot(
            UUID tourGroupId,
            UUID passengerId,
            TourGroupPassengerAssignmentStatus excludedStatus
    );

    @Query("""
        SELECT COUNT(tp)
        FROM TourGroupPassenger tp
        WHERE tp.tourGroup.tourGroupId = :groupId
          AND tp.assignmentStatus <> :cancelled
        """)
    long countActiveAssignments(
            @Param("groupId") UUID groupId,
            @Param("cancelled") TourGroupPassengerAssignmentStatus cancelled
    );

    @Query("""
        SELECT tgp
        FROM TourGroupPassenger tgp
        JOIN FETCH tgp.tourGroup tg
        JOIN FETCH tgp.passenger p
        WHERE tgp.tourGroupPassengerId = :assignmentId
        """)
    Optional<TourGroupPassenger> findDetailById(
            @Param("assignmentId") UUID assignmentId
    );

    boolean existsByPassenger_PassengerIdAndTourGroup_ShoreExcursion_ShoreExcursionIdAndAssignmentStatusIn(
            UUID passengerId,
            UUID shoreExcursionId,
            List<TourGroupPassengerAssignmentStatus> statuses
    );

    List<TourGroupPassenger> findByTourGroup_TourGroupId(UUID tourGroupId);

    @Query("""
    SELECT COUNT(tp)
    FROM TourGroupPassenger tp
    WHERE tp.tourGroup.tourGroupId = :groupId
      AND tp.assignmentStatus IN :statuses
    """)
    long countAssignedPassengers(
            @Param("groupId") UUID groupId,
            @Param("statuses") List<TourGroupPassengerAssignmentStatus> statuses
    );

    @Query("""
    SELECT COUNT(tp)
    FROM TourGroupPassenger tp
    WHERE tp.tourGroup.shoreExcursion.shoreExcursionId = :excursionId
      AND tp.assignmentStatus = :status
    """)
    long countActiveAssignmentsByExcursion(
            @Param("excursionId") UUID excursionId,
            @Param("status") TourGroupPassengerAssignmentStatus status
    );
}