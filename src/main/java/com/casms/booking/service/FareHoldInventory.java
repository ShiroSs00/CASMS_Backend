package com.casms.booking.service;

import com.casms.booking.dto.response.QuoteLineItem;
import com.casms.booking.enums.CabinSelectionMode;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.cruisepackage.dto.CruisePackagePurchaseSelection;
import com.casms.cruisepackage.service.CruisePackagePurchaseSnapshotService;
import com.casms.ship.entity.Cruise;
import com.casms.ship.enums.CabinType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
class FareHoldInventory {
    private final BookingPassengerRepository passengers;
    private final CruisePackagePurchaseSnapshotService snapshots;
    private final JdbcTemplate jdbc;

    /** Caller locks the Cruise. Simulate placements only, leaving actual cabin assignment unchanged. */
    void verify(Cruise cruise, List<QuoteLineItem> incoming) {
        Map<String, Group> groups = new TreeMap<>();
        for (var hold : passengers.findActiveUnassignedFareHolds(cruise.getCruiseId())) {
            add(groups, hold.getBooking().getBookingId() + ":" + hold.getCabinGroupKey(), snapshots.intent(hold));
        }
        for (var line : incoming) {
            if (line.getCabinSelectionMode() == CabinSelectionMode.CATEGORY_HOLD)
                add(groups, "incoming:" + line.getCabinGroupKey(), line.getFareSelection());
        }
        if (groups.isEmpty()) return;

        Map<UUID, Integer> remaining = new HashMap<>();
        Map<UUID, CabinType> types = new HashMap<>();
        jdbc.query("""
                SELECT c.cabin_id, c.cabin_type::text, c.capacity - COALESCE(occupied.slots, 0) AS remaining
                FROM "Cabin" c
                LEFT JOIN (
                    SELECT ca.cabin_id, COUNT(*) AS slots
                    FROM "CabinAssignment" ca JOIN "BookingPassenger" p USING (booking_passenger_id)
                    JOIN "Booking" b USING (booking_id)
                    WHERE b.cruise_id = ? AND ca.valid_to IS NULL AND p.participation_status = 'Active'
                      AND (b.booking_status = 'Confirmed' OR (b.booking_status = 'Draft'
                        AND (b.hold_expires_at IS NULL OR b.hold_expires_at > CURRENT_TIMESTAMP)))
                    GROUP BY ca.cabin_id
                ) occupied USING (cabin_id)
                WHERE c.ship_id = ? AND c.status = 'AVAILABLE'
                """, rs -> {
            UUID id = rs.getObject("cabin_id", UUID.class);
            remaining.put(id, rs.getInt("remaining"));
            types.put(id, CabinType.valueOf(rs.getString("cabin_type")));
        }, cruise.getCruiseId(), cruise.getShip().getShipId());
        for (var line : incoming) {
            if (line.getCabinId() != null)
                remaining.computeIfPresent(line.getCabinId(), (id, slots) -> slots - 1);
        }
        groups.values().forEach(group -> group.cabins.removeIf(id -> types.get(id) != group.type));
        fitGroups(groups.values(), remaining);
    }

    private static void add(Map<String, Group> groups, String key, CruisePackagePurchaseSelection fare) {
        if (fare == null) throw exhausted();
        Group group = groups.computeIfAbsent(key, ignored -> new Group(fare.cabinType(), fare.physicalCabinIds()));
        if (group.type != fare.cabinType()) throw exhausted();
        group.cabins.retainAll(fare.physicalCabinIds());
        group.size++;
    }

    static void fitGroups(Collection<Group> groups, Map<UUID, Integer> remaining) {
        var ordered = groups.stream()
                .sorted(Comparator.comparingInt((Group group) -> group.cabins.size())
                        .thenComparing(Comparator.comparingInt((Group group) -> group.size).reversed()))
                .toList();
        for (Group group : ordered) {
            UUID cabin = group.cabins.stream()
                    .filter(id -> remaining.getOrDefault(id, 0) >= group.size)
                    .min(Comparator.comparingInt((UUID id) -> remaining.get(id)).thenComparing(Comparator.naturalOrder()))
                    .orElseThrow(FareHoldInventory::exhausted);
            remaining.compute(cabin, (id, slots) -> slots - group.size);
        }
    }

    static final class Group {
        final CabinType type;
        final Set<UUID> cabins;
        int size;

        Group(CabinType type, Collection<UUID> cabins) {
            this.type = type;
            this.cabins = new HashSet<>(cabins);
        }
    }

    private static BusinessException exhausted() {
        return new BusinessException(HttpStatus.CONFLICT, "FARE_CATEGORY_CAPACITY_UNAVAILABLE",
                "Không đủ chỗ theo hạng để giữ nguyên nhóm hành khách trong một cabin.");
    }
}
