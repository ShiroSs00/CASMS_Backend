package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.ItineraryDayCreateRequest;
import com.casms.ship.dto.request.ItineraryDayUpdateRequest;
import com.casms.ship.dto.response.ItineraryDayResponse;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryDayRole;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.util.PortTimeZoneHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryDayService {

    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryRepository itineraryRepository;

    @Transactional
    public ItineraryDayResponse create(ItineraryDayCreateRequest request) {
        log.info("Creating new itinerary day for itinerary: {}", request.getItineraryId());

        Itinerary itinerary = itineraryRepository.findById(request.getItineraryId())
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary", request.getItineraryId()));

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot add days to itinerary with status: " + itinerary.getStatus() + ". Only DRAFT itineraries can be modified.");
        }

        Integer dayNumber = request.getDayNumber();
        if (dayNumber == null) {
            int maxDay = itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itinerary.getItineraryId())
                    .stream().mapToInt(ItineraryDay::getDayNumber).max().orElse(0);
            dayNumber = maxDay + 1;
        } else if (itineraryDayRepository.existsByItineraryItineraryIdAndDayNumber(itinerary.getItineraryId(), dayNumber)) {
            throw new BusinessException("DUPLICATE_DAY_NUMBER", "Day number already exists in this itinerary");
        }

        LocalDate date = ItineraryCalendar.day(itinerary, dayNumber, request.getDate());
        if (itineraryDayRepository.existsByItineraryItineraryIdAndDate(itinerary.getItineraryId(), date)) {
            throw new BusinessException("DUPLICATE_DATE", "Date already exists in this itinerary");
        }

        ItineraryDayType dayType = request.getDayType() == null ? ItineraryDayType.PORT_DAY : request.getDayType();

        String tz = request.getShipTimeZoneId();
        if (tz != null && !tz.isBlank()) {
            if (!PortTimeZoneHelper.isValidTimeZoneId(tz)) {
                throw new BusinessException("INVALID_TIME_ZONE", "Múi giờ tàu không hợp lệ: " + tz);
            }
        } else if (itinerary.getCruise() != null && itinerary.getCruise().getDeparturePort() != null) {
            tz = itinerary.getCruise().getDeparturePort().getTimeZoneId();
        }

        ItineraryDay day = new ItineraryDay();
        day.setItinerary(itinerary);
        day.setDayNumber(dayNumber);
        day.setDate(date);
        requireDayType(day, dayType);
        day.setDayType(dayType);
        day.setShipTimeZoneId(tz);
        day.setDescription(request.getDescription());

        day = itineraryDayRepository.save(day);
        return mapToResponse(day);
    }

    @Transactional(readOnly = true)
    public List<ItineraryDayResponse> listByItinerary(UUID itineraryId) {
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary", itineraryId));

        return itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itineraryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ItineraryDayResponse detail(UUID itineraryDayId) {
        ItineraryDay day = getDayById(itineraryDayId);
        return mapToResponse(day);
    }

    @Transactional
    public ItineraryDayResponse update(UUID itineraryDayId, ItineraryDayUpdateRequest request) {
        ItineraryDay day = getDayById(itineraryDayId);

        Itinerary itinerary = day.getItinerary();
        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot update days in itinerary with status: " + itinerary.getStatus() + ". Only DRAFT itineraries can be modified.");
        }

        ItineraryCalendar.day(itinerary, request.getDayNumber() == null ? day.getDayNumber() : request.getDayNumber(),
                request.getDate() == null ? day.getDate() : request.getDate());

        if (request.getDayNumber() != null && !request.getDayNumber().equals(day.getDayNumber())) {
            if (itineraryDayRepository.existsByItineraryItineraryIdAndDayNumber(itinerary.getItineraryId(), request.getDayNumber())) {
                throw new BusinessException("DUPLICATE_DAY_NUMBER", "Day number already exists in this itinerary");
            }
            day.setDayNumber(request.getDayNumber());
        }

        if (request.getDate() != null && !request.getDate().equals(day.getDate())) {
            if (itineraryDayRepository.existsByItineraryItineraryIdAndDate(itinerary.getItineraryId(), request.getDate())) {
                throw new BusinessException("DUPLICATE_DATE", "Date already exists in this itinerary");
            }
            day.setDate(request.getDate());
        }

        requireDayType(day, request.getDayType() == null ? day.getDayType() : request.getDayType());
        if (request.getDayType() != null) {
            day.setDayType(request.getDayType());
        }
        if (request.getShipTimeZoneId() != null) {
            if (!PortTimeZoneHelper.isValidTimeZoneId(request.getShipTimeZoneId())) {
                throw new BusinessException("INVALID_TIME_ZONE", "Múi giờ tàu không hợp lệ: " + request.getShipTimeZoneId());
            }
            day.setShipTimeZoneId(request.getShipTimeZoneId());
        }
        if (request.getDescription() != null) {
            day.setDescription(request.getDescription());
        }

        day = itineraryDayRepository.save(day);
        return mapToResponse(day);
    }

    @Transactional
    public void deleteUnused(UUID itineraryDayId) {
        ItineraryDay day = getDayById(itineraryDayId);

        Itinerary itinerary = day.getItinerary();
        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot delete days from itinerary with status: " + itinerary.getStatus() + ". Only DRAFT itineraries can be modified.");
        }

        // Check if day has cruise stops (arrival or departure)
        if ((day.getArrivalStops() != null && !day.getArrivalStops().isEmpty()) ||
            (day.getDepartureStops() != null && !day.getDepartureStops().isEmpty())) {
            throw new BusinessException("DAY_HAS_STOPS", "Cannot delete day that has cruise stops");
        }

        itineraryDayRepository.delete(day);
        log.info("Deleted unused itinerary day: {}", itineraryDayId);
    }

    @Transactional
    public ItineraryDayResponse setDayType(UUID itineraryDayId, ItineraryDayType dayType) {
        ItineraryDay day = getDayById(itineraryDayId);
        if (day.getItinerary().getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot modify day type in itinerary with status: " + day.getItinerary().getStatus());
        }
        requireDayType(day, dayType);
        day.setDayType(dayType);
        day = itineraryDayRepository.save(day);
        return mapToResponse(day);
    }

    @Transactional
    public ItineraryDayResponse setShipTimeZone(UUID itineraryDayId, String shipTimeZoneId) {
        if (shipTimeZoneId != null && !shipTimeZoneId.isBlank() && !PortTimeZoneHelper.isValidTimeZoneId(shipTimeZoneId)) {
            throw new BusinessException("INVALID_TIME_ZONE", "Múi giờ tàu không hợp lệ: " + shipTimeZoneId);
        }
        ItineraryDay day = getDayById(itineraryDayId);
        if (day.getItinerary().getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot modify ship timezone in itinerary with status: " + day.getItinerary().getStatus());
        }
        day.setShipTimeZoneId(shipTimeZoneId);
        day = itineraryDayRepository.save(day);
        return mapToResponse(day);
    }

    @Transactional
    public ItineraryDayResponse setDate(UUID itineraryDayId, LocalDate date) {
        ItineraryDay day = getDayById(itineraryDayId);
        if (day.getItinerary().getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot modify date in itinerary with status: " + day.getItinerary().getStatus());
        }
        day.setDate(ItineraryCalendar.day(day.getItinerary(), day.getDayNumber(), date));
        day = itineraryDayRepository.save(day);
        return mapToResponse(day);
    }

    @Transactional(readOnly = true)
    public void validateSequence(UUID itineraryId) {
        List<ItineraryDay> days = itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itineraryId);
        
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).getDayNumber() != i + 1) {
                throw new BusinessException("INVALID_DAY_SEQUENCE", "Day numbers must be sequential starting from 1");
            }
        }
    }

    @Transactional(readOnly = true)
    public void validateDay(UUID itineraryDayId) {
        getDayById(itineraryDayId);
    }

    /**
     * Tự động đồng bộ và xác định múi giờ trên tàu (Ship Time Zone) cho toàn bộ các ngày
     * trong hải trình đa quốc gia dựa trên các điểm dừng cảng và ngày trên biển.
     */
    @Transactional
    public List<ItineraryDayResponse> syncShipTimeZonesFromStops(UUID itineraryId) {
        log.info("Syncing ship timezones for multi-country itinerary: {}", itineraryId);
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary", itineraryId));

        if (itinerary.getStatus() != ItineraryStatus.DRAFT && itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot sync timezones for itinerary with status: " + itinerary.getStatus());
        }

        List<ItineraryDay> days = itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itineraryId);
        if (days.isEmpty()) {
            return Collections.emptyList();
        }

        String defaultTz = (itinerary.getCruise() != null && itinerary.getCruise().getDeparturePort() != null)
                ? itinerary.getCruise().getDeparturePort().getTimeZoneId()
                : "UTC";

        String currentShipTz = defaultTz;

        for (ItineraryDay day : days) {
            CruiseStop stop = null;
            if (day.getArrivalStops() != null && !day.getArrivalStops().isEmpty()) {
                stop = day.getArrivalStops().get(0);
            } else if (day.getDepartureStops() != null && !day.getDepartureStops().isEmpty()) {
                stop = day.getDepartureStops().get(0);
            }

            if (stop != null && stop.getPort() != null && stop.getPort().getTimeZoneId() != null) {
                // Ngày tàu cập cảng tại quốc gia: Áp dụng múi giờ của Cảng đó
                currentShipTz = stop.getPort().getTimeZoneId();
                day.setDayType(ItineraryDayType.PORT_DAY);
                day.setShipTimeZoneId(currentShipTz);
            } else {
                // Ngày tàu di chuyển trên biển giữa các nước: Duy trì múi giờ tàu hiện tại
                day.setDayType(day.getDayRole().isEnding() ? ItineraryDayType.PORT_DAY : ItineraryDayType.SEA_DAY);
                if (day.getShipTimeZoneId() == null || !PortTimeZoneHelper.isValidTimeZoneId(day.getShipTimeZoneId())) {
                    day.setShipTimeZoneId(currentShipTz);
                } else {
                    currentShipTz = day.getShipTimeZoneId();
                }
            }
        }

        days = itineraryDayRepository.saveAll(days);
        return days.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private void requireDayType(ItineraryDay day, ItineraryDayType type) {
        if (day.getDayRole().isEnding() && type != ItineraryDayType.PORT_DAY)
            throw new BusinessException("END_DAY_REQUIRES_PORT", "Ngày kết thúc phải chọn cảng trả khách.");
    }

    private ItineraryDay getDayById(UUID itineraryDayId) {
        return itineraryDayRepository.findById(itineraryDayId)
                .orElseThrow(() -> new ResourceNotFoundException("ItineraryDay", itineraryDayId));
    }

    private ItineraryDayResponse mapToResponse(ItineraryDay day) {
        ItineraryDayResponse res = new ItineraryDayResponse();
        res.setItineraryDayId(day.getItineraryDayId());
        res.setItineraryId(day.getItinerary().getItineraryId());
        res.setDayNumber(day.getDayNumber());
        res.setDate(day.getDate());
        res.setDayType(day.getDayType());
        res.setDayRole(day.getDayRole());
        res.setShipTimeZoneId(day.getShipTimeZoneId());
        if (day.getShipTimeZoneId() != null) {
            res.setShipUtcOffset(PortTimeZoneHelper.calculateUtcOffset(day.getShipTimeZoneId()));
            res.setFormattedShipTimeZone(PortTimeZoneHelper.formatTimeZoneDisplay(day.getShipTimeZoneId()));
        }
        res.setDescription(day.getDescription());
        return res;
    }
}
