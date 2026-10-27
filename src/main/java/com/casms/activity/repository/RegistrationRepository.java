package com.casms.activity.repository;

import com.casms.activity.entity.Registration;
import com.casms.activity.enums.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationRepository
        extends JpaRepository<Registration, UUID> {

    boolean existsByOnboardActivity_OnboardActivityId(UUID activityId);
    boolean existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(UUID bookingPassengerId, UUID activityId, Collection<RegistrationStatus> statuses);
    List<Registration> findByOnboardActivity_OnboardActivityId(UUID activityId);
    List<Registration> findByBookingPassenger_Passenger_User_UserId(UUID userId);

    @Query("""
        SELECT r FROM Registration r
        LEFT JOIN FETCH r.onboardActivity activity
        LEFT JOIN FETCH activity.cruise
        LEFT JOIN FETCH r.shoreExcursion excursion
        LEFT JOIN FETCH excursion.cruiseStop stop
        LEFT JOIN FETCH stop.itinerary
        LEFT JOIN FETCH stop.port
        WHERE r.bookingPassenger.bookingPassengerId IN :bookingPassengerIds
        ORDER BY r.registrationTime ASC
        """)
    List<Registration> findTripRegistrations(
            @Param("bookingPassengerIds") Collection<UUID> bookingPassengerIds);
    Optional<Registration> findByRegistrationId(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.registrationId = :id")
    Optional<Registration> lockById(@Param("id") UUID id);

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
    boolean existsByBookingPassenger_BookingPassengerIdAndShoreExcursion_ShoreExcursionIdAndStatusIn(
            UUID bookingPassengerId, UUID shoreExcursionId, Collection<RegistrationStatus> statuses);

    @Query("""
        SELECT r
        FROM Registration r
        JOIN FETCH r.passenger p
        WHERE r.shoreExcursion.shoreExcursionId = :excursionId
          AND r.status = :status
        """)
    List<Registration> findConfirmedRegistrationsByExcursion(
            @Param("excursionId") UUID excursionId,
            @Param("status") RegistrationStatus status
    );
}
