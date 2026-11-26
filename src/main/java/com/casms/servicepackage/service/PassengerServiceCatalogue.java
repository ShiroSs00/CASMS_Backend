package com.casms.servicepackage.service;

import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.service.PortCallBookingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PassengerServiceCatalogue {
    private final BookingPassengerRepository passengers;
    private final JdbcTemplate jdbc;
    private final PortCallBookingPolicy bookingPolicy;

    @Transactional(readOnly = true)
    public Catalogue list(UUID bookingPassengerId, UUID userId) {
        var bp = passengers.findById(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking passenger", bookingPassengerId));
        var passengerUser = bp.getPassenger().getUser();
        if (passengerUser == null || !passengerUser.getUserId().equals(userId))
            throw new AccessDeniedException("Service catalogue belongs to another booking passenger");
        var cruise = bp.getBooking().getCruise();
        UUID cruiseId = cruise.getCruiseId();
        if (bp.getParticipationStatus() != BookingPassengerParticipationStatus.Active
                || bp.getBooking().getBookingStatus() != BookingStatus.Confirmed
                || cruise.getShip() == null
                || (cruise.getStatus() != CruiseStatus.PUBLISHED && cruise.getStatus() != CruiseStatus.IN_PROGRESS))
            return new Catalogue(cruiseId, List.of());
        Instant now = Instant.now();
        bookingPolicy.requireOnboardBooking(cruise, now);

        UUID shipId = cruise.getShip().getShipId();
        var placements = jdbc.query("""
                SELECT a.service_id, d.deck_id, d.deck_name, area.ship_model_object_id, area.display_name
                FROM "DeckServiceAssignment" a JOIN "Deck" d USING (deck_id)
                LEFT JOIN LATERAL (
                    SELECT o.ship_model_object_id, o.display_name
                    FROM "AreaServiceAssignment" assignment
                    JOIN "ShipModelObject" o ON o.ship_model_object_id = assignment.area_object_id
                    JOIN "ShipModel" m ON m.ship_model_id = o.ship_model_id
                    WHERE assignment.service_id = a.service_id AND assignment.enabled
                      AND o.deck_id = d.deck_id AND m.ship_id = d.ship_id AND m.status = 'READY'
                      AND o.object_type = 'FACILITY' AND o.cabin_id IS NULL
                ) area ON true
                WHERE d.ship_id = ?
                ORDER BY d.deck_number, area.display_name, area.ship_model_object_id
                """, (r, row) -> new Placement(
                        r.getObject(1, UUID.class), r.getObject(2, UUID.class),
                        r.getString(3), r.getObject(4, UUID.class), r.getString(5)), shipId)
                .stream().collect(Collectors.groupingBy(Placement::serviceId));
        var schedules = jdbc.query("""
                SELECT ss.service_id, ss.service_schedule_id, ss.location, ss.starts_at, ss.ends_at,
                       greatest(ss.capacity - coalesce(sum(r.quantity), 0), 0) AS remaining
                FROM "ServiceSchedule" ss
                LEFT JOIN "ServiceRequest" r ON r.service_schedule_id = ss.service_schedule_id
                    AND r.status IN ('REQUESTED','ACCEPTED','IN_PROGRESS')
                WHERE ss.cruise_id = ? AND ss.status = 'OPEN' AND ss.ends_at > now()
                GROUP BY ss.service_schedule_id
                ORDER BY ss.starts_at, ss.service_schedule_id
                """, (r, row) -> new Schedule(
                        r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3),
                        r.getTimestamp(4).toInstant(), r.getTimestamp(5).toInstant(), r.getLong(6)), cruiseId)
                .stream().collect(Collectors.groupingBy(Schedule::serviceId));
        var entries = jdbc.query("""
                SELECT s.service_id, s.service_name, s.description, s.unit_code, s.price, s.currency_code,
                       s.can_buy_pre_cruise, s.can_buy_onboard, s.can_sell_independently,
                       s.requires_schedule, CASE WHEN s.capacity IS NULL THEN NULL
                           ELSE greatest(s.capacity - coalesce(sum(r.quantity), 0), 0) END AS remaining
                FROM "Service" s
                LEFT JOIN "ServiceRequest" r ON r.service_id = s.service_id
                    AND r.status IN ('REQUESTED','ACCEPTED','IN_PROGRESS')
                WHERE s.status = 'ACTIVE' AND EXISTS (
                    SELECT 1 FROM "DeckServiceAssignment" a JOIN "Deck" d USING (deck_id)
                    WHERE d.ship_id = ? AND a.service_id = s.service_id
                )
                GROUP BY s.service_id
                ORDER BY s.service_name, s.service_id
                """, (r, row) -> {
            UUID serviceId = r.getObject(1, UUID.class);
            return new Entry(serviceId, r.getString(2), r.getString(3), r.getString(4),
                    r.getBigDecimal(5), r.getString(6), r.getBoolean(7), r.getBoolean(8),
                    r.getBoolean(9), r.getBoolean(10), r.getObject(11, Long.class),
                    placements.getOrDefault(serviceId, List.of()), schedules.getOrDefault(serviceId, List.of()));
        }, shipId);
        return new Catalogue(cruiseId, entries);
    }

    public record Catalogue(UUID cruiseId, List<Entry> services) {}

    public record Entry(
            UUID serviceId, String serviceName, String description, String unitCode,
            BigDecimal unitPrice, String currency, boolean canBuyPreCruise, boolean canBuyOnboard,
            boolean canSellIndependently, boolean requiresSchedule, Long remainingCapacity,
            List<Placement> placements, List<Schedule> schedules) {}

    public record Placement(UUID serviceId, UUID deckId, String deckName, UUID areaId, String areaName) {}

    public record Schedule(
            UUID serviceId, UUID scheduleId, String location, Instant startsAt, Instant endsAt,
            long remainingCapacity) {}
}
