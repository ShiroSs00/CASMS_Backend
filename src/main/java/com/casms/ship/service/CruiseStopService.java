package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.CruiseStopCreateRequest;
import com.casms.ship.dto.request.CruiseStopUpdateRequest;
import com.casms.ship.dto.response.CruiseStopResponse;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Destination;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import com.casms.ship.enums.CruiseStopStatus;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.DestinationRepository;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.PortRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruiseStopService {

    private final CruiseStopRepository cruiseStopRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final PortRepository portRepository;
    private final DestinationRepository destinationRepository;

    @Transactional
    public CruiseStopResponse add(CruiseStopCreateRequest request) {
        log.info("Adding new cruise stop to itinerary: {}", request.getItineraryId());

        Itinerary itinerary = itineraryRepository.findById(request.getItineraryId())
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary", request.getItineraryId()));

        if (itinerary.getStatus() != ItineraryStatus.DRAFT && itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot add stops to itinerary with status: " + itinerary.getStatus());
        }

        ItineraryDay arrivalDay = itineraryDayRepository.findById(request.getArrivalItineraryDayId())
                .orElseThrow(() -> new ResourceNotFoundException("Arrival ItineraryDay", request.getArrivalItineraryDayId()));

        ItineraryDay departureDay = itineraryDayRepository.findById(request.getDepartureItineraryDayId())
                .orElseThrow(() -> new ResourceNotFoundException("Departure ItineraryDay", request.getDepartureItineraryDayId()));

        if (!arrivalDay.getItinerary().getItineraryId().equals(request.getItineraryId()) ||
            !departureDay.getItinerary().getItineraryId().equals(request.getItineraryId())) {
            throw new BusinessException("DAY_NOT_IN_ITINERARY", "Arrival or departure day does not belong to this itinerary");
        }

        if (arrivalDay.getDayNumber() > departureDay.getDayNumber()) {
            throw new BusinessException("INVALID_DAY_ORDER", "Arrival day must be before or same as departure day");
        }

        Port port = portRepository.findById(request.getPortId())
                .orElseThrow(() -> new ResourceNotFoundException("Port", request.getPortId()));

        if (request.getStopOrder() == null) {
            int maxOrder = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(request.getItineraryId())
                    .stream().mapToInt(CruiseStop::getStopOrder).max().orElse(0);
            request.setStopOrder(maxOrder + 1);
        } else if (cruiseStopRepository.existsByItineraryItineraryIdAndStopOrder(request.getItineraryId(), request.getStopOrder())) {
            throw new BusinessException("DUPLICATE_STOP_ORDER", "Stop order already exists in this itinerary");
        }

        if (request.getCallType() == null) {
            request.setCallType(CruiseStopCallType.NORMAL);
        }
        if (request.getArrivalMethod() == null) {
            request.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        }
        if (request.getAllAboardTime() == null && request.getPlannedDeparture() != null) {
            request.setAllAboardTime(request.getPlannedDeparture().minus(30, java.time.temporal.ChronoUnit.MINUTES));
        }

        if (request.getPlannedDeparture().isBefore(request.getPlannedArrival()) || 
            request.getPlannedDeparture().equals(request.getPlannedArrival())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Planned departure must be after planned arrival");
        }

        Destination destination = null;
        if (request.getDestinationId() != null) {
            destination = destinationRepository.findById(request.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));
        }

        Destination dest = null;
        if (request.getDestinationId() != null) {
            dest = destinationRepository.findById(request.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));
        }

        CruiseStop stop = new CruiseStop();
        stop.setItinerary(itineraryRepository.findById(request.getItineraryId()).get());
        stop.setArrivalItineraryDay(arrivalDay);
        stop.setDepartureItineraryDay(departureDay);
        stop.setPort(port);
        stop.setDestination(dest);
        stop.setStopOrder(request.getStopOrder());
        stop.setPlannedArrival(request.getPlannedArrival());
        stop.setPlannedDeparture(request.getPlannedDeparture());
        stop.setCallType(request.getCallType());
        stop.setArrivalMethod(request.getArrivalMethod());
        stop.setAllAboardTime(request.getAllAboardTime());
        stop.setTenderTransferMinutes(request.getTenderTransferMinutes());
        stop.setStatus(CruiseStopStatus.PLANNED);
        stop.setPortContactName(request.getPortContactName());
        stop.setPortContactRole(request.getPortContactRole());
        stop.setPortContactPhone(request.getPortContactPhone());
        stop.setPortContactEmail(request.getPortContactEmail());
        stop.setPortContactResponsibility(request.getPortContactResponsibility());
        stop.setNotes(request.getNotes());

        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional(readOnly = true)
    public List<CruiseStopResponse> listByItinerary(UUID itineraryId) {
        return cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itineraryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CruiseStopResponse detail(UUID cruiseStopId) {
        CruiseStop stop = getStopById(cruiseStopId);
        return mapToResponse(stop);
    }

    @Transactional
    public CruiseStopResponse update(UUID cruiseStopId, CruiseStopUpdateRequest request) {
        CruiseStop stop = getStopById(cruiseStopId);

        Itinerary itinerary = stop.getItinerary();
        if (itinerary.getStatus() != ItineraryStatus.DRAFT && itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot update stops in itinerary with status: " + itinerary.getStatus());
        }

        if (request.getItineraryId() != null && !request.getItineraryId().equals(stop.getItinerary().getItineraryId())) {
            throw new BusinessException("CANNOT_CHANGE_ITINERARY", "Cannot change itinerary of existing stop");
        }

        if (request.getStopOrder() != null && !request.getStopOrder().equals(stop.getStopOrder())) {
            if (cruiseStopRepository.existsByItineraryItineraryIdAndStopOrder(stop.getItinerary().getItineraryId(), request.getStopOrder())) {
                throw new BusinessException("DUPLICATE_STOP_ORDER", "Stop order already exists in this itinerary");
            }
            stop.setStopOrder(request.getStopOrder());
        }

        if (request.getArrivalItineraryDayId() != null) {
            ItineraryDay arrivalDay = itineraryDayRepository.findById(request.getArrivalItineraryDayId())
                    .orElseThrow(() -> new ResourceNotFoundException("Arrival ItineraryDay", request.getArrivalItineraryDayId()));
            stop.setArrivalItineraryDay(arrivalDay);
        }

        if (request.getDepartureItineraryDayId() != null) {
            ItineraryDay departureDay = itineraryDayRepository.findById(request.getDepartureItineraryDayId())
                    .orElseThrow(() -> new ResourceNotFoundException("Departure ItineraryDay", request.getDepartureItineraryDayId()));
            stop.setDepartureItineraryDay(departureDay);
        }

        if (request.getPortId() != null) {
            Port port = portRepository.findById(request.getPortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Port", request.getPortId()));
            stop.setPort(port);
        }

        if (request.getDestinationId() != null) {
            Destination destination = destinationRepository.findById(request.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));
            stop.setDestination(destination);
        }

        if (request.getStopOrder() != null && !request.getStopOrder().equals(stop.getStopOrder())) {
            if (cruiseStopRepository.existsByItineraryItineraryIdAndStopOrder(stop.getItinerary().getItineraryId(), request.getStopOrder())) {
                throw new BusinessException("DUPLICATE_STOP_ORDER", "Stop order already exists in this itinerary");
            }
            stop.setStopOrder(request.getStopOrder());
        }

        if (request.getPlannedArrival() != null) {
            stop.setPlannedArrival(request.getPlannedArrival());
        }
        if (request.getPlannedDeparture() != null) {
            stop.setPlannedDeparture(request.getPlannedDeparture());
        }
        if (request.getPlannedArrival() != null || request.getPlannedDeparture() != null) {
            Instant arrival = request.getPlannedArrival() != null ? request.getPlannedArrival() : stop.getPlannedArrival();
            Instant departure = request.getPlannedDeparture() != null ? request.getPlannedDeparture() : stop.getPlannedDeparture();
            if (departure.isBefore(arrival) || departure.equals(arrival)) {
                throw new BusinessException("INVALID_TIME_RANGE", "Planned departure must be after planned arrival");
            }
        }

        if (request.getCallType() != null) stop.setCallType(request.getCallType());
        if (request.getArrivalMethod() != null) stop.setArrivalMethod(request.getArrivalMethod());
        if (request.getAllAboardTime() != null) stop.setAllAboardTime(request.getAllAboardTime());
        if (request.getTenderTransferMinutes() != null) stop.setTenderTransferMinutes(request.getTenderTransferMinutes());
        if (request.getStatus() != null) stop.setStatus(request.getStatus());
        if (request.getCancellationReason() != null) stop.setCancellationReason(request.getCancellationReason());
        if (request.getAllAboardTime() != null) stop.setAllAboardTime(request.getAllAboardTime());
        if (request.getTenderTransferMinutes() != null) stop.setTenderTransferMinutes(request.getTenderTransferMinutes());
        if (request.getPortContactName() != null) stop.setPortContactName(request.getPortContactName());
        if (request.getPortContactRole() != null) stop.setPortContactRole(request.getPortContactRole());
        if (request.getPortContactPhone() != null) stop.setPortContactPhone(request.getPortContactPhone());
        if (request.getPortContactEmail() != null) stop.setPortContactEmail(request.getPortContactEmail());
        if (request.getPortContactResponsibility() != null) stop.setPortContactResponsibility(request.getPortContactResponsibility());
        if (request.getNotes() != null) stop.setNotes(request.getNotes());

        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional
    public void removeDraft(UUID cruiseStopId) {
        CruiseStop stop = getStopById(cruiseStopId);

        Itinerary itinerary = stop.getItinerary();
        if (itinerary.getStatus() != ItineraryStatus.DRAFT && itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot remove stops from itinerary with status: " + itinerary.getStatus());
        }

        cruiseStopRepository.delete(stop);
        log.info("Removed draft cruise stop: {}", cruiseStopId);
    }

    @Transactional
    public CruiseStopResponse reorder(UUID cruiseStopId, Integer newStopOrder) {
        CruiseStop stop = getStopById(cruiseStopId);

        Itinerary itinerary = stop.getItinerary();
        if (itinerary.getStatus() != ItineraryStatus.DRAFT && itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot reorder stops in itinerary with status: " + itinerary.getStatus());
        }

        if (newStopOrder != null && !newStopOrder.equals(stop.getStopOrder())) {
            if (cruiseStopRepository.existsByItineraryItineraryIdAndStopOrder(stop.getItinerary().getItineraryId(), newStopOrder)) {
                throw new BusinessException("DUPLICATE_STOP_ORDER", "Stop order already exists in this itinerary");
            }
            stop.setStopOrder(newStopOrder);
            stop = cruiseStopRepository.save(stop);
        }

        return mapToResponse(stop);
    }

    @Transactional
    public CruiseStopResponse setAllAboardTime(UUID cruiseStopId, Instant allAboardTime) {
        CruiseStop stop = getStopById(cruiseStopId);
        stop.setAllAboardTime(allAboardTime);
        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional
    public CruiseStopResponse recordActualArrival(UUID cruiseStopId, Instant actualArrival) {
        CruiseStop stop = getStopById(cruiseStopId);
        stop.setActualArrival(actualArrival);
        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional
    public CruiseStopResponse recordActualDeparture(UUID cruiseStopId, Instant actualDeparture) {
        CruiseStop stop = getStopById(cruiseStopId);
        stop.setActualDeparture(actualDeparture);
        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional
    public CruiseStopResponse omit(UUID cruiseStopId, String reason) {
        CruiseStop stop = getStopById(cruiseStopId);
        stop.setStatus(CruiseStopStatus.OMITTED);
        stop.setCancellationReason(reason);
        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional
    public CruiseStopResponse substitute(UUID cruiseStopId, UUID newStopId) {
        CruiseStop stop = getStopById(cruiseStopId);
        CruiseStop newStop = cruiseStopRepository.findById(newStopId)
                .orElseThrow(() -> new ResourceNotFoundException("New CruiseStop", newStopId));

        stop.setStatus(CruiseStopStatus.SUBSTITUTED);
        stop.setSubstitutedByStop(newStop);
        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional(readOnly = true)
    public void validateStop(UUID cruiseStopId) {
        CruiseStop stop = getStopById(cruiseStopId);
        
        if (!stop.getPlannedDeparture().isAfter(stop.getPlannedArrival())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Planned departure must be after planned arrival");
        }

        if (stop.getArrivalItineraryDay().getDayNumber() > stop.getDepartureItineraryDay().getDayNumber()) {
            throw new BusinessException("INVALID_DAY_ORDER", "Arrival day must be before or same as departure day");
        }
    }

    @Transactional
    public CruiseStopResponse setPlannedArrival(UUID cruiseStopId, Instant plannedArrival) {
        CruiseStop stop = getStopById(cruiseStopId);
        
        if (!plannedArrival.isBefore(stop.getPlannedDeparture())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Planned arrival must be before planned departure");
        }
        
        stop.setPlannedArrival(plannedArrival);
        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional
    public CruiseStopResponse setPlannedDeparture(UUID cruiseStopId, Instant plannedDeparture) {
        CruiseStop stop = getStopById(cruiseStopId);
        
        if (!plannedDeparture.isAfter(stop.getPlannedArrival())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Planned departure must be after planned arrival");
        }
        
        stop.setPlannedDeparture(plannedDeparture);
        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional(readOnly = true)
    public Long calculateStopDuration(UUID cruiseStopId) {
        CruiseStop stop = getStopById(cruiseStopId);
        
        Instant start = stop.getActualArrival() != null ? stop.getActualArrival() : stop.getPlannedArrival();
        Instant end = stop.getActualDeparture() != null ? stop.getActualDeparture() : stop.getPlannedDeparture();
        
        return java.time.Duration.between(start, end).toMinutes();
    }

    @Transactional(readOnly = true)
    public boolean checkPortAvailability(UUID portId, Instant start, Instant end, UUID excludeStopId) {
        if (excludeStopId != null) {
            return !cruiseStopRepository.existsByItineraryAndPortExcludingStopOrder(
                    UUID.fromString("00000000-0000-0000-0000-000000000000"), portId, 0);
        } else {
            List<CruiseStop> conflicts = cruiseStopRepository.findByPortPortIdAndPlannedArrivalBetween(portId, start, end);
            return conflicts.isEmpty();
        }
    }

    private CruiseStop getStopById(UUID cruiseStopId) {
        return cruiseStopRepository.findById(cruiseStopId)
                .orElseThrow(() -> new ResourceNotFoundException("CruiseStop", cruiseStopId));
    }

    private CruiseStopResponse mapToResponse(CruiseStop stop) {
        CruiseStopResponse res = new CruiseStopResponse();
        res.setCruiseStopId(stop.getCruiseStopId());
        res.setItineraryId(stop.getItinerary().getItineraryId());
        res.setArrivalItineraryDayId(stop.getArrivalItineraryDay().getItineraryDayId());
        res.setDepartureItineraryDayId(stop.getDepartureItineraryDay().getItineraryDayId());
        res.setPortId(stop.getPort().getPortId());
        res.setPortCode(stop.getPort().getPortCode());
        res.setPortName(stop.getPort().getPortName());

        if (stop.getPort() != null) {
            String portTz = stop.getPort().getTimeZoneId();
            res.setPortTimeZoneId(portTz);
            res.setPortUtcOffset(com.casms.ship.util.PortTimeZoneHelper.calculateUtcOffset(portTz));
            res.setFormattedPortTimeZone(com.casms.ship.util.PortTimeZoneHelper.formatTimeZoneDisplay(portTz));
        }

        if (stop.getArrivalItineraryDay() != null) {
            String shipTz = stop.getArrivalItineraryDay().getShipTimeZoneId();
            res.setShipTimeZoneId(shipTz);
            res.setShipUtcOffset(com.casms.ship.util.PortTimeZoneHelper.calculateUtcOffset(shipTz));
            res.setFormattedShipTimeZone(com.casms.ship.util.PortTimeZoneHelper.formatTimeZoneDisplay(shipTz));
        }
        
        if (stop.getDestination() != null) {
            res.setDestinationId(stop.getDestination().getDestinationId());
            res.setDestinationName(stop.getDestination().getDestinationName());
        }
        
        res.setStopOrder(stop.getStopOrder());
        res.setPlannedArrival(stop.getPlannedArrival());
        res.setPlannedDeparture(stop.getPlannedDeparture());
        res.setActualArrival(stop.getActualArrival());
        res.setActualDeparture(stop.getActualDeparture());
        res.setCallType(stop.getCallType());
        res.setArrivalMethod(stop.getArrivalMethod());
        res.setAllAboardTime(stop.getAllAboardTime());
        res.setTenderTransferMinutes(stop.getTenderTransferMinutes());
        res.setStatus(stop.getStatus());
        res.setCancellationReason(stop.getCancellationReason());
        res.setSubstitutedByStopId(stop.getSubstitutedByStop() != null ? stop.getSubstitutedByStop().getCruiseStopId() : null);
        res.setPortContactName(stop.getPortContactName());
        res.setPortContactRole(stop.getPortContactRole());
        res.setPortContactPhone(stop.getPortContactPhone());
        res.setPortContactEmail(stop.getPortContactEmail());
        res.setPortContactResponsibility(stop.getPortContactResponsibility());
        res.setNotes(stop.getNotes());
        return res;
    }
}