package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.response.PublicCruiseItineraryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicCruiseItineraryService {
    private final CruiseService cruises;
    private final ScheduleMaterializationService schedules;

    @Transactional(readOnly = true)
    public PublicCruiseItineraryResponse get(UUID cruiseId) {
        cruises.requirePublicSaleVisible(cruiseId);
        var schedule = schedules.getCruiseSchedule(cruiseId);
        if (!Boolean.TRUE.equals(schedule.getMaterialized())) {
            throw new BusinessException(HttpStatus.CONFLICT, "PUBLIC_ITINERARY_UNAVAILABLE",
                    "Lịch trình thực tế chưa sẵn sàng để hiển thị.");
        }
        var days = schedule.getDays().stream().map(day ->
                new PublicCruiseItineraryResponse.Day(day.getDayNumber(), day.getCalendarDate(),
                        day.getShipTimeZoneId(), day.getStops().stream().map(stop ->
                        new PublicCruiseItineraryResponse.PortCall(stop.getStopOrder(), stop.getPortName(),
                                stop.getCountryCode(), stop.getPortTimeZoneId(), stop.getPlannedArrival(),
                                stop.getPlannedDeparture(), stop.getVisits().stream().map(visit ->
                                new PublicCruiseItineraryResponse.Visit(visit.sequence(), visit.destinationName()))
                                .toList(), stop.getIsDisembarkation())).toList())).toList();
        return new PublicCruiseItineraryResponse(cruiseId, schedule.getDepartureDatetime(),
                schedule.getReturnDatetime(), schedule.getDurationDays(), schedule.getDurationNights(), days);
    }
}
