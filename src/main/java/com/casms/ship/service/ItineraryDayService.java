package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.ItineraryDayCreateRequest;
import com.casms.ship.dto.request.ItineraryDayUpdateRequest;
import com.casms.ship.dto.response.ItineraryDayResponse;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.repository.ItineraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

        if (itinerary.getStatus() != ItineraryStatus.DRAFT && itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot add days to itinerary with status: " + itinerary.getStatus());
        }

        Integer dayNumber = request.getDayNumber();
        if (dayNumber == null) {
            int maxDay = itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itinerary.getItineraryId())
                    .stream().mapToInt(ItineraryDay::getDayNumber).max().orElse(0);
            dayNumber = maxDay + 1;
        } else if (itineraryDayRepository.existsByItineraryItineraryIdAndDayNumber(itinerary.getItineraryId(), dayNumber)) {
            throw new BusinessException("DUPLICATE_DAY_NUMBER", "Day number already exists in this itinerary");
        }

        LocalDate date = request.getDate();
        if (date == null) {
            date = itinerary.getStartDate() != null ? itinerary.getStartDate().plusDays(dayNumber - 1) : LocalDate.now();
        }
        if (itineraryDayRepository.existsByItineraryItineraryIdAndDate(itinerary.getItineraryId(), date)) {
            throw new BusinessException("DUPLICATE_DATE", "Date already exists in this itinerary");
        }

        ItineraryDayType dayType = request.getDayType() == null ? ItineraryDayType.PORT_DAY : request.getDayType();

        String tz = request.getShipTimeZoneId();
        if (tz == null && itinerary.getCruise() != null && itinerary.getCruise().getDeparturePort() != null) {
            tz = itinerary.getCruise().getDeparturePort().getTimeZoneId();
        }

        ItineraryDay day = new ItineraryDay();
        day.setItinerary(itinerary);
        day.setDayNumber(dayNumber);
        day.setDate(date);
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
        if (itinerary.getStatus() != ItineraryStatus.DRAFT && itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot update days in itinerary with status: " + itinerary.getStatus());
        }

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

        if (request.getDayType() != null) {
            day.setDayType(request.getDayType());
        }
        if (request.getShipTimeZoneId() != null) {
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
        if (itinerary.getStatus() != ItineraryStatus.DRAFT && itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot delete days from itinerary with status: " + itinerary.getStatus());
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
        day.setDayType(dayType);
        day = itineraryDayRepository.save(day);
        return mapToResponse(day);
    }

    @Transactional
    public ItineraryDayResponse setShipTimeZone(UUID itineraryDayId, String shipTimeZoneId) {
        ItineraryDay day = getDayById(itineraryDayId);
        day.setShipTimeZoneId(shipTimeZoneId);
        day = itineraryDayRepository.save(day);
        return mapToResponse(day);
    }

    @Transactional
    public ItineraryDayResponse setDate(UUID itineraryDayId, java.time.LocalDate date) {
        ItineraryDay day = getDayById(itineraryDayId);
        day.setDate(date);
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
        res.setShipTimeZoneId(day.getShipTimeZoneId());
        res.setDescription(day.getDescription());
        return res;
    }
}