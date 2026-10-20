package com.casms.identity.repository;

import com.casms.identity.entity.PassengerBleIdentity;
import com.casms.identity.enums.PassengerBleIdentityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PassengerBleIdentityRepository extends JpaRepository<PassengerBleIdentity, UUID> {
    Optional<PassengerBleIdentity> findByCruiseCruiseIdAndBookingPassengerBookingPassengerIdAndStatus(
            UUID cruiseId, UUID bookingPassengerId, PassengerBleIdentityStatus status);
    List<PassengerBleIdentity> findByCruiseCruiseIdAndBookingPassengerBookingPassengerIdOrderByAssignedAtDesc(
            UUID cruiseId, UUID bookingPassengerId);
    List<PassengerBleIdentity> findByCruiseCruiseIdAndStatusOrderByAssignedAtDesc(
            UUID cruiseId, PassengerBleIdentityStatus status);
    boolean existsByCruiseCruiseIdAndActiveBleIdentifier(UUID cruiseId, String bleIdentifier);
    Optional<PassengerBleIdentity> findByCruiseCruiseIdAndActiveBleIdentifier(UUID cruiseId, String bleIdentifier);
}
