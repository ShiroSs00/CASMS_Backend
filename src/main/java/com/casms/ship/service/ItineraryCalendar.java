package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.util.PortTimeZoneHelper;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.Objects;

/** Operational day numbers are calendar days in ship time, including both endpoints. */
public final class ItineraryCalendar {
    private ItineraryCalendar() {}
    public static LocalDate start(Cruise cruise) { return cruise.getDepartureDatetime().atZone(PortTimeZoneHelper.SHIP_TIME_ZONE).toLocalDate(); }
    public static LocalDate end(Cruise cruise) { return cruise.getReturnDatetime().atZone(PortTimeZoneHelper.SHIP_TIME_ZONE).toLocalDate(); }
    public static void validate(Cruise cruise, LocalDate start, LocalDate end) {
        if (!Objects.equals(start(cruise), start) || !Objects.equals(end(cruise), end))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"ITINERARY_CRUISE_DATES_MISMATCH",
                    "Ngày đầu phải là ngày khởi hành và ngày cuối phải là ngày kết thúc lần khởi hành theo giờ tàu Việt Nam.");
    }
    public static LocalDate day(Itinerary itinerary, Integer number, LocalDate requested) {
        if (number == null || number < 1) throw new BusinessException("INVALID_DAY_NUMBER", "Day number must be positive");
        LocalDate start = itinerary.getCruise() == null ? itinerary.getStartDate() : start(itinerary.getCruise());
        LocalDate end = itinerary.getCruise() == null ? itinerary.getEndDate() : end(itinerary.getCruise());
        LocalDate expected = start.plusDays(number - 1L);
        if (expected.isAfter(end) || requested != null && !requested.equals(expected))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"ITINERARY_DAY_DATE_MISMATCH",
                    "Ngày thứ N phải bằng ngày khởi hành cộng N−1 ngày và không vượt ngày kết thúc chuyến.");
        return expected;
    }
}
