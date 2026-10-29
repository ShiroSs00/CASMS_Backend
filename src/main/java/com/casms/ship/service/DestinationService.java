package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.DestinationCreateRequest;
import com.casms.ship.dto.request.DestinationUpdateRequest;
import com.casms.ship.dto.response.DestinationResponse;
import com.casms.ship.dto.response.PortSummaryResponse;
import com.casms.ship.entity.Destination;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.DestinationStatus;
import com.casms.ship.repository.DestinationRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.ship.util.PortTimeZoneHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final PortRepository portRepository;

    @Transactional
    public DestinationResponse create(DestinationCreateRequest request) {
        log.info("Creating new destination with name: {}", request.getDestinationName());

        if (destinationRepository.existsByDestinationName(request.getDestinationName())) {
            throw new BusinessException("DESTINATION_NAME_EXISTS", "Destination name already exists: " + request.getDestinationName());
        }

        Destination destination = new Destination();
        destination.setDestinationName(request.getDestinationName());
        destination.setCountryCode(request.getCountryCode().toUpperCase());
        destination.setDescription(request.getDescription());
        destination.setStatus(request.getStatus() != null ? request.getStatus() : DestinationStatus.ACTIVE);

        destination = destinationRepository.save(destination);
        return mapToResponse(destination);
    }

    @Transactional(readOnly = true)
    public Page<DestinationResponse> list(Pageable pageable) {
        return destinationRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<DestinationResponse> listAllActive() {
        return destinationRepository.findAllActiveDestinations().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DestinationResponse detail(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);
        return mapToResponse(destination);
    }

    @Transactional
    public DestinationResponse update(UUID destinationId, DestinationUpdateRequest request) {
        Destination destination = getDestinationById(destinationId);

        if (request.getDestinationName() != null && !request.getDestinationName().equals(destination.getDestinationName())) {
            if (destinationRepository.existsByDestinationName(request.getDestinationName())) {
                throw new BusinessException("DESTINATION_NAME_EXISTS", "Destination name already exists: " + request.getDestinationName());
            }
            destination.setDestinationName(request.getDestinationName());
        }
        if (request.getDescription() != null) {
            destination.setDescription(request.getDescription());
        }

        destination = destinationRepository.save(destination);
        return mapToResponse(destination);
    }

    @Transactional
    public void deleteUnused(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);

        // Check if destination is used in any cruise stops
        // For now, we allow deletion

        destinationRepository.delete(destination);
        log.info("Deleted unused destination: {}", destinationId);
    }

    @Transactional(readOnly = true)
    public List<PortSummaryResponse> listPortsByDestination(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);

        List<Port> ports = portRepository.findByDestinationDestinationIdOrderByPortNameAsc(destinationId);
        if (ports == null || ports.isEmpty()) {
            ports = destination.getPorts() != null ? destination.getPorts() : List.of();
        }

        return ports.stream()
                .map(this::mapPortToSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public void validateDestination(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);
        // Additional validation logic can be added here
    }

    @Transactional
    public DestinationResponse activate(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);
        destination.setStatus(DestinationStatus.ACTIVE);
        destination = destinationRepository.save(destination);
        log.info("Activated destination: {}", destinationId);
        return mapToResponse(destination);
    }

    @Transactional
    public DestinationResponse deactivate(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);
        destination.setStatus(DestinationStatus.INACTIVE);
        destination = destinationRepository.save(destination);
        log.info("Deactivated destination: {}", destinationId);
        return mapToResponse(destination);
    }

    private Destination getDestinationById(UUID destinationId) {
        return destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", destinationId));
    }

    private DestinationResponse mapToResponse(Destination destination) {
        DestinationResponse res = new DestinationResponse();
        res.setDestinationId(destination.getDestinationId());
        res.setDestinationName(destination.getDestinationName());
        res.setCountryCode(destination.getCountryCode());
        res.setDescription(destination.getDescription());
        res.setStatus(destination.getStatus());
        res.setCreatedAt(destination.getCreatedAt());
        res.setUpdatedAt(destination.getUpdatedAt());

        // Map ports if available
        List<Port> ports = destination.getPorts();
        if (ports == null || ports.isEmpty()) {
            ports = portRepository.findByDestinationDestinationIdOrderByPortNameAsc(destination.getDestinationId());
        }
        if (ports != null) {
            res.setPorts(ports.stream()
                    .map(this::mapPortToSummary)
                    .collect(Collectors.toList()));
        }
        return res;
    }

    private PortSummaryResponse mapPortToSummary(Port port) {
        PortSummaryResponse res = new PortSummaryResponse();
        res.setPortId(port.getPortId());
        res.setPortCode(port.getPortCode());
        res.setPortName(port.getPortName());
        res.setCountryCode(port.getCountryCode());
        res.setTimeZoneId(port.getTimeZoneId());
        res.setUtcOffset(PortTimeZoneHelper.calculateUtcOffset(port.getTimeZoneId()));
        res.setFormattedTimeZone(PortTimeZoneHelper.formatTimeZoneDisplay(port.getTimeZoneId()));
        res.setCurrentLocalTime(PortTimeZoneHelper.getCurrentLocalTimeIso(port.getTimeZoneId()));
        res.setLatitude(port.getLatitude());
        res.setLongitude(port.getLongitude());
        res.setStatus(port.getStatus());
        if (port.getDestination() != null) {
            res.setDestinationId(port.getDestination().getDestinationId());
            res.setDestinationName(port.getDestination().getDestinationName());
        }
        return res;
    }
}