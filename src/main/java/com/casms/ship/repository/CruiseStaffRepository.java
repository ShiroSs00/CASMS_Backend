package com.casms.ship.repository;

import com.casms.ship.entity.CruiseStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruiseStaffRepository extends JpaRepository<CruiseStaff, UUID> {

    String ACTIVE_ONBOARD_SQL = """
        SELECT EXISTS(SELECT 1 FROM "CruiseStaff" cs JOIN "User" u ON u.user_id=cs.user_id
          JOIN "UserRole" ur ON ur.user_id=u.user_id
          WHERE cs.cruise_id=:cruiseId AND cs.status='ACTIVE' AND cs.role_in_cruise='ONBOARD_STAFF'
          AND u.status='ACTIVE' AND ur.role='ONBOARD_STAFF')
        """;
    @Query(value=ACTIVE_ONBOARD_SQL, nativeQuery=true)
    boolean existsActiveOnboardStaff(@Param("cruiseId") UUID cruiseId);

    List<CruiseStaff> findByCruise_CruiseId(UUID cruiseId);

    Optional<CruiseStaff> findByCruise_CruiseIdAndUser_UserId(UUID cruiseId, UUID userId);

    boolean existsByCruise_CruiseIdAndUser_UserId(UUID cruiseId, UUID userId);

    boolean existsByCruise_CruiseIdAndUser_UserIdAndStatus(UUID cruiseId, UUID userId, String status);

    boolean existsByCruise_CruiseIdAndRoleInCruiseAndStatus(UUID cruiseId, String roleInCruise, String status);

    Optional<CruiseStaff> findFirstByCruise_CruiseIdAndUser_UserIdAndStatusOrderByAssignedAtDesc(
            UUID cruiseId, UUID userId, String status);

    long countByCruise_CruiseIdAndStatus(UUID cruiseId, String status);

    void deleteByCruise_CruiseIdAndUser_UserId(UUID cruiseId, UUID userId);

    void deleteByCruise_CruiseId(UUID cruiseId);

    @Query("""
        SELECT cs FROM CruiseStaff cs
        JOIN cs.cruise c
        WHERE cs.user.userId = :userId
          AND cs.status = 'ACTIVE'
          AND (:excludeCruiseId IS NULL OR c.cruiseId != :excludeCruiseId)
          AND c.status NOT IN (com.casms.ship.enums.CruiseStatus.CANCELLED, com.casms.ship.enums.CruiseStatus.COMPLETED)
          AND c.departureDatetime < :returnTime
          AND c.returnDatetime > :departureTime
    """)
    List<CruiseStaff> findConflictingAssignments(
            @Param("userId") UUID userId,
            @Param("departureTime") Instant departureTime,
            @Param("returnTime") Instant returnTime,
            @Param("excludeCruiseId") UUID excludeCruiseId
    );
}
