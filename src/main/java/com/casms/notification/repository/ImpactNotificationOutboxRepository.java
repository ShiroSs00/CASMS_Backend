package com.casms.notification.repository;

import com.casms.notification.entity.ImpactNotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImpactNotificationOutboxRepository extends JpaRepository<ImpactNotificationOutbox, UUID> {

    List<ImpactNotificationOutbox> findByCruise_CruiseId(UUID cruiseId);

    List<ImpactNotificationOutbox> findByCruise_CruiseIdAndEventIdentity(UUID cruiseId, String eventIdentity);

    Optional<ImpactNotificationOutbox> findByEventIdentityAndBooking_BookingIdAndNotificationType(
            String eventIdentity, UUID bookingId, String notificationType);

    boolean existsByEventIdentityAndBooking_BookingIdAndNotificationType(
            String eventIdentity, UUID bookingId, String notificationType);

    long countByCruise_CruiseIdAndStatus(UUID cruiseId, String status);

    long countByCruise_CruiseId(UUID cruiseId);

    @Query("SELECT COUNT(DISTINCT o.booking.bookingId) FROM ImpactNotificationOutbox o WHERE o.cruise.cruiseId = :cruiseId")
    long countDistinctBookingsByCruiseId(@Param("cruiseId") UUID cruiseId);

    List<ImpactNotificationOutbox> findByRecipientUser_UserIdAndAcknowledgedAtIsNull(UUID userId);
}
