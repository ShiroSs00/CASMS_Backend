package com.casms.booking.repository;

import com.casms.booking.entity.CabinAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CabinAssignmentRepository extends JpaRepository<CabinAssignment, UUID> {
    boolean existsByCabinCabinId(UUID cabinId);
    List<CabinAssignment> findByCabinCabinIdOrderByValidFromDesc(UUID cabinId);

    @Query("SELECT COUNT(ca) FROM CabinAssignment ca " +
           "WHERE ca.cabin.cabinId = :cabinId " +
           "AND ca.validTo IS NULL " +
           "AND ca.bookingPassenger.booking.cruise.cruiseId = :cruiseId " +
           "AND ca.bookingPassenger.booking.bookingStatus != 'Cancelled' " +
           "AND ca.bookingPassenger.participationStatus = 'Active'")
    long countActiveAssignmentsByCabinAndCruise(@Param("cabinId") UUID cabinId, @Param("cruiseId") UUID cruiseId);

    @Query("SELECT ca FROM CabinAssignment ca " +
           "WHERE ca.bookingPassenger.booking.cruise.cruiseId = :cruiseId " +
           "AND ca.validTo IS NULL " +
           "AND ca.bookingPassenger.booking.bookingStatus != 'Cancelled' " +
           "AND ca.bookingPassenger.participationStatus = 'Active'")
    List<CabinAssignment> findActiveAssignmentsByCruiseId(@Param("cruiseId") UUID cruiseId);

    Optional<CabinAssignment> findByBookingPassengerBookingPassengerIdAndValidToIsNull(UUID bookingPassengerId);
    List<CabinAssignment> findByBookingPassengerBookingPassengerId(UUID bookingPassengerId);
}