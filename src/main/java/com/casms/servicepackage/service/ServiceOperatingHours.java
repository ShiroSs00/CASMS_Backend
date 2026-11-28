package com.casms.servicepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.servicepackage.dto.ServiceOperatingWindow;
import com.casms.servicepackage.enums.ServiceOperatingPeriod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
        if (!isRestricted(null, periods)) return;
        requireOpen(start, end, zone, time -> periods.contains(periodAt(time)), List.of(day, evening, night));
    }

    public void requireOpen(List<ServiceOperatingWindow> windows, Set<ServiceOperatingPeriod> periods,
            Instant start, Instant end, ZoneId zone) {
        if (windows == null) {
            requireOpen(periods, start, end, zone);
            return;
        }
        if (windows.isEmpty()) return;
        List<LocalTime> boundaries = windows.stream()
                .flatMap(window -> Stream.of(window.startsAt(), window.endsAt()))
                .distinct().toList();
        requireOpen(start, end, zone,
                time -> windows.stream().anyMatch(window -> window.contains(time)), boundaries);
    }

    static boolean isRestricted(List<ServiceOperatingWindow> windows, Set<ServiceOperatingPeriod> periods) {
        return windows != null ? !windows.isEmpty()
                : periods != null && !periods.isEmpty() && periods.size() < ServiceOperatingPeriod.values().length;
    }

    private void requireOpen(Instant start, Instant end, ZoneId zone, Predicate<LocalTime> open,
            List<LocalTime> boundaries) {
        Instant cursor = start;
        do {
            if (!open.test(cursor.atZone(zone).toLocalTime())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SERVICE_OUTSIDE_OPERATING_HOURS",
                        "Service usage must be within its configured operating hours");
            }
            if (end == null || !cursor.isBefore(end)) return;
            cursor = nextBoundary(cursor, zone, boundaries);
        } while (cursor.isBefore(end));
    }

    private ServiceOperatingPeriod periodAt(LocalTime time) {
        if (time.isBefore(day) || !time.isBefore(night)) return ServiceOperatingPeriod.NIGHTTIME;
        return time.isBefore(evening) ? ServiceOperatingPeriod.DAYTIME : ServiceOperatingPeriod.EVENING;
    }

    private Instant nextBoundary(Instant cursor, ZoneId zone, List<LocalTime> boundaries) {
        LocalDate date = cursor.atZone(zone).toLocalDate();
        var rules = zone.getRules();
        ZoneOffsetTransition transition = rules.nextTransition(cursor);
        Instant next = transition == null ? Instant.MAX : transition.getInstant();
        for (var localDate : List.of(date, date.plusDays(1))) {
            for (LocalTime time : boundaries) {
                var local = localDate.atTime(time);
                for (var offset : rules.getValidOffsets(local)) {
                    Instant candidate = local.toInstant(offset);
                    if (candidate.isAfter(cursor) && candidate.isBefore(next)) next = candidate;
                }
            }
        }
        return next;
    }
}
