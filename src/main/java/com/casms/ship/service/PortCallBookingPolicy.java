package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.CruiseStopStatus;
import com.casms.ship.repository.CruiseStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PortCallBookingPolicy {
    public enum State { EN_ROUTE, ARRIVAL_PENDING, IN_PORT, DEPARTURE_PENDING, DEPARTED }

    private final CruiseStopRepository stops;

    public void requireOnboardBooking(Cruise cruise, Instant now) {
        requireOnboardBooking(cruise, now, true);
    }

    /** Advisory GET projection. A later mutation must re-evaluate under its own locks. */
    public void requireOnboardBookingForProjection(Cruise cruise, Instant now) {
        requireOnboardBooking(cruise, now, false);
    }

    private void requireOnboardBooking(Cruise cruise, Instant now, boolean lock) {
        if (cruise.getStatus() == CruiseStatus.COMPLETED
                || (cruise.getReturnDatetime() != null && !now.isBefore(cruise.getReturnDatetime()))) {
            throw blocked("CRUISE_ENDED");
        }
        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) return;
        var calls = (lock
                ? stops.findByItineraryCruiseCruiseIdOrderByStopOrderAsc(cruise.getCruiseId())
                : stops.findBookingPolicyProjection(cruise.getCruiseId()))
                .stream().filter(stop -> stop.getStatus() != CruiseStopStatus.OMITTED).toList();
        for (int index = 0; index < calls.size(); index++) {
            CruiseStop call = calls.get(index);
            if (index == 0 && isDepartureStop(cruise, call)) continue;
            if (index == calls.size() - 1 && isReturnStop(cruise, call)) {
                var disembarkation = call.validDisembarkationStart();
                if (disembarkation != null
                        && occurred(disembarkation.plusSeconds(60L * call.getDisembarkationMinutes()), now)) {
                    throw blocked("FINAL_DISEMBARKATION_ENDED");
                }
                continue;
            }
            State state = stateAt(call, now);
            if (state != State.EN_ROUTE && state != State.DEPARTED) {
                throw blocked(state.name());
            }
        }
    }

    static State stateAt(CruiseStop call, Instant now) {
        if (occurred(call.getActualDeparture(), now)) return State.DEPARTED;
        if (occurred(call.getActualArrival(), now)) {
            return occurred(call.getPlannedDeparture(), now) ? State.DEPARTURE_PENDING : State.IN_PORT;
        }
        return occurred(call.getPlannedArrival(), now) ? State.ARRIVAL_PENDING : State.EN_ROUTE;
    }

    private static boolean isDepartureStop(Cruise cruise, CruiseStop call) {
        return cruise.getDeparturePort() != null && call.getPort() != null
                && cruise.getDeparturePort().getPortId().equals(call.getPort().getPortId())
                && cruise.getDepartureDatetime() != null && call.getPlannedDeparture() != null
                && !call.getPlannedDeparture().isAfter(cruise.getDepartureDatetime());
    }

    private static boolean isReturnStop(Cruise cruise, CruiseStop call) {
        return cruise.getReturnPort() != null && call.getPort() != null
                && cruise.getReturnPort().getPortId().equals(call.getPort().getPortId());
    }

    private static boolean occurred(Instant timestamp, Instant now) {
        return timestamp != null && !timestamp.isAfter(now);
    }

    private static BusinessException blocked(String phase) {
        return new BusinessException(HttpStatus.CONFLICT, "ONBOARD_BOOKING_CLOSED",
                "Onboard booking is closed during this phase", java.util.Map.of("phase", phase));
    }
}
