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

    boolean existsByOnboardActivity_OnboardActivityId(UUID activityId);
    boolean existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(UUID bookingPassengerId, UUID activityId, Collection<RegistrationStatus> statuses);
    java.util.List<Registration> findByOnboardActivity_OnboardActivityId(UUID activityId);
    java.util.List<Registration> findByBookingPassenger_Passenger_User_UserId(UUID userId);
    java.util.Optional<Registration> findByRegistrationId(UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Registration r where r.registrationId = :id")
    java.util.Optional<Registration> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM Registration r WHERE r.onboardActivity.onboardActivityId = :activityId AND r.status IN :statuses")
    Long sumOnboardParticipants(@Param("activityId") UUID activityId, @Param("statuses") Collection<RegistrationStatus> statuses);

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
<<<<<<< HEAD
    boolean existsByBookingPassenger_BookingPassengerIdAndShoreExcursion_ShoreExcursionIdAndStatusIn(
            UUID bookingPassengerId, UUID shoreExcursionId, Collection<RegistrationStatus> statuses);
}
=======

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
>>>>>>> main
