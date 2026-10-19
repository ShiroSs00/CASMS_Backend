package com.casms.activity.repository;

import com.casms.activity.entity.Registration;
import com.casms.activity.enums.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface RegistrationRepository
        extends JpaRepository<Registration, UUID> {

    @Query("""
        SELECT COALESCE(SUM(r.quantity), 0)
        FROM Registration r
        WHERE r.shoreExcursion.shoreExcursionId = :excursionId
          AND r.status IN :statuses
        """)
    Long sumParticipants(
            @Param("excursionId") UUID excursionId,
            @Param("statuses") Collection<RegistrationStatus> statuses
    );

    boolean existsByShoreExcursion_ShoreExcursionId(UUID excursionId);

    boolean existsByPassenger_PassengerIdAndShoreExcursion_ShoreExcursionIdAndStatus(
            UUID passengerId,
            UUID shoreExcursionId,
            RegistrationStatus status
    );

    @Query("""
        SELECT r
        FROM Registration r
        JOIN FETCH r.passenger p
        WHERE r.shoreExcursion.shoreExcursionId = :excursionId
          AND r.status = :status
        """)
    java.util.List<Registration> findConfirmedRegistrationsByExcursion(
            @Param("excursionId") UUID excursionId,
            @Param("status") RegistrationStatus status
    );
}