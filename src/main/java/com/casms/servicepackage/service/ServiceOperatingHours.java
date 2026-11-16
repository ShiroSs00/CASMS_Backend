package com.casms.servicepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.servicepackage.enums.ServiceOperatingPeriod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.util.List;
import java.util.Set;

@Component
public class ServiceOperatingHours {
    private final LocalTime day;
    private final LocalTime evening;
    private final LocalTime night;

    public ServiceOperatingHours(
            @Value("${casms.services.hours.daytime:06:00}") String day,
            @Value("${casms.services.hours.evening:18:00}") String evening,
            @Value("${casms.services.hours.nighttime:22:00}") String night) {
        this.day = LocalTime.parse(day);
        this.evening = LocalTime.parse(evening);
        this.night = LocalTime.parse(night);
        if (!this.day.isBefore(this.evening) || !this.evening.isBefore(this.night)) {
            throw new IllegalArgumentException("Service hours must be in daytime, evening, nighttime order");
        }
    }

    public void requireOpen(Set<ServiceOperatingPeriod> periods, Instant start, Instant end, ZoneId zone) {
        if (periods == null || periods.isEmpty() || periods.size() == ServiceOperatingPeriod.values().length) return;
        Instant cursor = start;
        do {
            if (!periods.contains(periodAt(cursor.atZone(zone).toLocalTime()))) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SERVICE_OUTSIDE_OPERATING_HOURS",
                        "Service usage must be within its configured operating hours");
            }
            if (end == null || !cursor.isBefore(end)) return;
            cursor = nextBoundary(cursor, zone);
        } while (cursor.isBefore(end));
    }

    private ServiceOperatingPeriod periodAt(LocalTime time) {
        if (time.isBefore(day) || !time.isBefore(night)) return ServiceOperatingPeriod.NIGHTTIME;
        return time.isBefore(evening) ? ServiceOperatingPeriod.DAYTIME : ServiceOperatingPeriod.EVENING;
    }

    private Instant nextBoundary(Instant cursor, ZoneId zone) {
        var date = cursor.atZone(zone).toLocalDate();
        Instant next = date.plusDays(1).atTime(day).atZone(zone).toInstant();
        for (var time : List.of(day, evening, night)) {
            var local = date.atTime(time);
            for (var offset : zone.getRules().getValidOffsets(local)) {
                Instant candidate = local.toInstant(offset);
                if (candidate.isAfter(cursor) && candidate.isBefore(next)) next = candidate;
            }
        }
        ZoneOffsetTransition transition = zone.getRules().nextTransition(cursor);
        if (transition != null && transition.getInstant().isBefore(next)) return transition.getInstant();
        return next;
    }
}
