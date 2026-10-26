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
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.PortStatus;
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.casms.activity.repository.ShoreExcursionRepository shoreExcursionRepository;

    public void setShoreExcursionRepository(com.casms.activity.repository.ShoreExcursionRepository repo) {
        this.shoreExcursionRepository = repo;
    }

    @Transactional
    public CruiseStopResponse add(CruiseStopCreateRequest request) {
        log.info("Adding new cruise stop to itinerary: {}", request.getItineraryId());

        Itinerary itinerary = itineraryRepository.findByIdForUpdate(request.getItineraryId())
                .or(() -> itineraryRepository.findById(request.getItineraryId()))
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary", request.getItineraryId()));

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot add stops to itinerary with status: " + itinerary.getStatus() + ". Only DRAFT itineraries can be modified.");
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

        if (port.getStatus() != PortStatus.ACTIVE) {
            throw new BusinessException("INACTIVE_PORT", "Port " + port.getPortCode() + " is not ACTIVE");
        }

        // GAP-1 / BE-11.2: country scope write-path validation
        validatePortCountryScope(port, itinerary);

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
            request.setAllAboardTime(request.getPlannedDeparture());
        }

        if (request.getPlannedDeparture().isBefore(request.getPlannedArrival()) || 
            request.getPlannedDeparture().equals(request.getPlannedArrival())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Planned departure must be after planned arrival");
        }

        List<CruiseStop> existingStops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(request.getItineraryId());
        validateCruiseStopInvariants(null, request.getStopOrder(), port.getPortId(), port.getPortName(),
                request.getPlannedArrival(), request.getPlannedDeparture(), existingStops);

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

        // Tự động gán múi giờ tàu cho ngày cập cảng theo múi giờ của cảng
        if (port.getTimeZoneId() != null) {
            if (arrivalDay.getDayType() == ItineraryDayType.PORT_DAY || arrivalDay.getShipTimeZoneId() == null) {
                arrivalDay.setShipTimeZoneId(port.getTimeZoneId());
                itineraryDayRepository.save(arrivalDay);
            }
            if (departureDay.getDayType() == ItineraryDayType.PORT_DAY || departureDay.getShipTimeZoneId() == null) {
                departureDay.setShipTimeZoneId(port.getTimeZoneId());
                itineraryDayRepository.save(departureDay);
            }
        }

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
        UUID itineraryId = stop.getItinerary().getItineraryId();

        Itinerary itinerary = itineraryRepository.findByIdForUpdate(itineraryId)
                .or(() -> itineraryRepository.findById(itineraryId))
                .orElse(stop.getItinerary());

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot update stops in itinerary with status: " + itinerary.getStatus() + ". Only DRAFT itineraries can be modified.");
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
            if (port.getStatus() != PortStatus.ACTIVE) {
                throw new BusinessException("INACTIVE_PORT", "Port " + port.getPortCode() + " is not ACTIVE");
            }
            // GAP-1 / BE-11.2: country scope on port change
            validatePortCountryScope(port, stop.getItinerary());
            stop.setPort(port);
        }

        if (request.getDestinationId() != null) {
            Destination destination = destinationRepository.findById(request.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));
            stop.setDestination(destination);
        }

        Instant arrival = request.getPlannedArrival() != null ? request.getPlannedArrival() : stop.getPlannedArrival();
        Instant departure = request.getPlannedDeparture() != null ? request.getPlannedDeparture() : stop.getPlannedDeparture();
        if (departure.isBefore(arrival) || departure.equals(arrival)) {
            throw new BusinessException("INVALID_TIME_RANGE", "Planned departure must be after planned arrival");
        }

        Integer effectiveStopOrder = request.getStopOrder() != null ? request.getStopOrder() : stop.getStopOrder();
        UUID effectivePortId = stop.getPort() != null ? stop.getPort().getPortId() : null;
        String effectivePortName = stop.getPort() != null ? stop.getPort().getPortName() : "Unknown";

        List<CruiseStop> existingStops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(stop.getItinerary().getItineraryId());
        validateCruiseStopInvariants(stop.getCruiseStopId(), effectiveStopOrder, effectivePortId, effectivePortName,
                arrival, departure, existingStops);

        // BE-12.3: Validate shore excursions under this stop to ensure shortening stop window does not violate excursion return time (E41)
        Instant effectiveAllAboard = request.getAllAboardTime() != null ? request.getAllAboardTime() : stop.getAllAboardTime();
        validateExcursionsUnderStop(stop, arrival, departure, effectiveAllAboard);

        if (request.getPlannedArrival() != null) {
            stop.setPlannedArrival(request.getPlannedArrival());
        }
        if (request.getPlannedDeparture() != null) {
            stop.setPlannedDeparture(request.getPlannedDeparture());
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
        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot remove stops from itinerary with status: " + itinerary.getStatus() + ". Only DRAFT itineraries can be modified.");
        }

        cruiseStopRepository.delete(stop);
        log.info("Removed draft cruise stop: {}", cruiseStopId);
    }

    @Transactional
    public CruiseStopResponse reorder(UUID cruiseStopId, Integer newStopOrder) {
        CruiseStop stop = getStopById(cruiseStopId);
        UUID itineraryId = stop.getItinerary().getItineraryId();

        Itinerary itinerary = itineraryRepository.findByIdForUpdate(itineraryId)
                .or(() -> itineraryRepository.findById(itineraryId))
                .orElse(stop.getItinerary());

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot reorder stops in itinerary with status: " + itinerary.getStatus() + ". Only DRAFT itineraries can be modified.");
        }

        if (newStopOrder != null && !newStopOrder.equals(stop.getStopOrder())) {
            if (cruiseStopRepository.existsByItineraryItineraryIdAndStopOrder(stop.getItinerary().getItineraryId(), newStopOrder)) {
                throw new BusinessException("DUPLICATE_STOP_ORDER", "Stop order already exists in this itinerary");
            }
            List<CruiseStop> existingStops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(stop.getItinerary().getItineraryId());
            validateCruiseStopInvariants(stop.getCruiseStopId(), newStopOrder,
                    stop.getPort() != null ? stop.getPort().getPortId() : null,
                    stop.getPort() != null ? stop.getPort().getPortName() : "Unknown",
                    stop.getPlannedArrival(), stop.getPlannedDeparture(), existingStops);
            stop.setStopOrder(newStopOrder);
            stop = cruiseStopRepository.save(stop);
        }

        return mapToResponse(stop);
    }

    @Transactional
    public CruiseStopResponse setAllAboardTime(UUID cruiseStopId, Instant allAboardTime) {
        CruiseStop stop = getStopById(cruiseStopId);
        UUID itineraryId = stop.getItinerary().getItineraryId();
        Itinerary itinerary = itineraryRepository.findByIdForUpdate(itineraryId)
                .or(() -> itineraryRepository.findById(itineraryId))
                .orElse(stop.getItinerary());

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot modify stop in itinerary with status: " + itinerary.getStatus());
        }

        if (allAboardTime != null) {
            if (stop.getPlannedArrival() != null && allAboardTime.isBefore(stop.getPlannedArrival())) {
                throw new BusinessException("INVALID_TIME_RANGE", "All aboard time cannot be earlier than planned arrival");
            }
            if (stop.getPlannedDeparture() != null && allAboardTime.isAfter(stop.getPlannedDeparture())) {
                throw new BusinessException("INVALID_TIME_RANGE", "All aboard time cannot be later than planned departure");
            }
            validateExcursionsUnderStop(stop, stop.getPlannedArrival(), stop.getPlannedDeparture(), allAboardTime);
        }

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
        UUID itineraryId = stop.getItinerary().getItineraryId();
        Itinerary itinerary = itineraryRepository.findByIdForUpdate(itineraryId)
                .or(() -> itineraryRepository.findById(itineraryId))
                .orElse(stop.getItinerary());

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot modify stop in itinerary with status: " + itinerary.getStatus());
        }
        
        if (!plannedArrival.isBefore(stop.getPlannedDeparture())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Planned arrival must be before planned departure");
        }

        List<CruiseStop> existingStops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId());
        validateCruiseStopInvariants(stop.getCruiseStopId(), stop.getStopOrder(),
                stop.getPort() != null ? stop.getPort().getPortId() : null,
                stop.getPort() != null ? stop.getPort().getPortName() : "Unknown",
                plannedArrival, stop.getPlannedDeparture(), existingStops);

        validateExcursionsUnderStop(stop, plannedArrival, stop.getPlannedDeparture(), stop.getAllAboardTime());
        
        stop.setPlannedArrival(plannedArrival);
        stop = cruiseStopRepository.save(stop);
        return mapToResponse(stop);
    }

    @Transactional
    public CruiseStopResponse setPlannedDeparture(UUID cruiseStopId, Instant plannedDeparture) {
        CruiseStop stop = getStopById(cruiseStopId);
        UUID itineraryId = stop.getItinerary().getItineraryId();
        Itinerary itinerary = itineraryRepository.findByIdForUpdate(itineraryId)
                .or(() -> itineraryRepository.findById(itineraryId))
                .orElse(stop.getItinerary());

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_EDITABLE", "Cannot modify stop in itinerary with status: " + itinerary.getStatus());
        }
        
        if (!plannedDeparture.isAfter(stop.getPlannedArrival())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Planned departure must be after planned arrival");
        }

        List<CruiseStop> existingStops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId());
        validateCruiseStopInvariants(stop.getCruiseStopId(), stop.getStopOrder(),
                stop.getPort() != null ? stop.getPort().getPortId() : null,
                stop.getPort() != null ? stop.getPort().getPortName() : "Unknown",
                stop.getPlannedArrival(), plannedDeparture, existingStops);

        validateExcursionsUnderStop(stop, stop.getPlannedArrival(), plannedDeparture, stop.getAllAboardTime());
        
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

    private void validateExcursionsUnderStop(CruiseStop stop, Instant arrival, Instant departure, Instant allAboard) {
        if (shoreExcursionRepository == null || stop == null || stop.getCruiseStopId() == null) {
            return;
        }
        Instant effectiveDeadline = allAboard != null ? allAboard : departure;
        List<com.casms.activity.entity.ShoreExcursion> excursions = shoreExcursionRepository.findByCruiseStop_CruiseStopId(stop.getCruiseStopId());
        for (com.casms.activity.entity.ShoreExcursion exc : excursions) {
            if (exc.getStartTime() != null && arrival != null && exc.getStartTime().isBefore(arrival)) {
                throw new BusinessException("EXCURSION_OUTSIDE_PORT_WINDOW",
                        String.format("Không thể điều chỉnh giờ cảng vì tour bờ '%s' (mã: %s) bắt đầu (%s) trước khi tàu cập cảng (%s).",
                                exc.getExcursionName(), exc.getExcursionCode(), exc.getStartTime(), arrival));
            }
            if (exc.getPlannedReturnTime() != null && effectiveDeadline != null && exc.getPlannedReturnTime().isAfter(effectiveDeadline)) {
                throw new BusinessException("EXCURSION_OUTSIDE_PORT_WINDOW",
                        String.format("Không thể điều chỉnh giờ cảng vì tour bờ '%s' (mã: %s) kết thúc (%s) sau hạn lên tàu / rời cảng (%s).",
                                exc.getExcursionName(), exc.getExcursionCode(), exc.getPlannedReturnTime(), effectiveDeadline));
            }
        }
    }

    /**
     * GAP-1 / BE-11.2: Validates that the given port's country code is within the allowed
     * country scope of the Cruise that owns this Itinerary (derived from sourceTrip via CountryScopeValidator).
     */
    private void validatePortCountryScope(Port port, Itinerary itinerary) {
        if (itinerary == null || itinerary.getCruise() == null) return;
        com.casms.ship.entity.Cruise cruise = itinerary.getCruise();
        if (cruise.getSourceTrip() == null) return; // no scope constraint

        com.casms.trip.service.CountryScopeValidator.validatePort(port, cruise.getSourceTrip(), "Cảng ghé");
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

    private void validateCruiseStopInvariants(UUID currentStopId, Integer stopOrder, UUID portId, String portName,
                                              Instant arrival, Instant departure, List<CruiseStop> existingStops) {
        if (arrival == null || departure == null) {
            return;
        }

        for (CruiseStop other : existingStops) {
            if (currentStopId != null && currentStopId.equals(other.getCruiseStopId())) {
                continue; // Skip self (E40)
            }
            Instant otherArr = other.getPlannedArrival();
            Instant otherDep = other.getPlannedDeparture();
            if (otherArr == null || otherDep == null) {
                continue;
            }

            // BE-12.1: Overlap check: arrA < depB && arrB < depA
            if (arrival.isBefore(otherDep) && otherArr.isBefore(departure)) {
                throw new BusinessException("PORT_CALL_OVERLAP",
                        String.format("Khoảng thời gian ghé cảng %s (%s - %s) bị chồng lấn với lần ghé cảng %s (%s - %s).",
                                portName, arrival, departure,
                                other.getPort() != null ? other.getPort().getPortName() : "Unknown", otherArr, otherDep));
            }

            // BE-12.1 & E37: Boundary check - if arrival == otherDep or otherArr == departure between distinct ports, transit time must be positive
            boolean isSamePort = portId != null && other.getPort() != null && portId.equals(other.getPort().getPortId());
            if (!isSamePort) {
                if (arrival.equals(otherDep) || otherArr.equals(departure)) {
                    throw new BusinessException("NON_POSITIVE_TRANSIT_TIME",
                            String.format("Thời gian di chuyển giữa hai cảng khác nhau (%s và %s) phải là số dương (lớn hơn 0).",
                                    portName, other.getPort() != null ? other.getPort().getPortName() : "Unknown"));
                }
            }

            // Sequence check with stopOrder
            if (stopOrder != null && other.getStopOrder() != null) {
                if (stopOrder < other.getStopOrder() && !departure.isBefore(otherArr)) {
                    throw new BusinessException("INVALID_STOP_SEQUENCE_TIME",
                            String.format("Lần ghé thứ %d (%s) phải kết thúc trước khi lần ghé thứ %d (%s) bắt đầu.",
                                    stopOrder, portName, other.getStopOrder(), other.getPort() != null ? other.getPort().getPortName() : "Unknown"));
                }
                if (stopOrder > other.getStopOrder() && !arrival.isAfter(otherDep)) {
                    throw new BusinessException("INVALID_STOP_SEQUENCE_TIME",
                            String.format("Lần ghé thứ %d (%s) phải bắt đầu sau khi lần ghé thứ %d (%s) kết thúc.",
                                    stopOrder, portName, other.getStopOrder(), other.getPort() != null ? other.getPort().getPortName() : "Unknown"));
                }
            }
        }
    }
}