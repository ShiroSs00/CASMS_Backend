package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.PortCreateRequest;
import com.casms.ship.dto.request.PortUpdateRequest;
import com.casms.ship.dto.response.PortResponse;
import com.casms.ship.dto.response.TimeZoneOptionResponse;
import com.casms.ship.entity.Destination;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.PortLocationType;
import com.casms.ship.enums.PortStatus;
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
public class PortService {

    private final PortRepository portRepository;
    private final DestinationRepository destinationRepository;

    @Transactional
    public PortResponse create(PortCreateRequest request) {
        log.info("Creating new port with code: {}", request.getPortCode());

        if (portRepository.existsByPortCode(request.getPortCode())) {
            throw new BusinessException("PORT_CODE_EXISTS", "Port code already exists: " + request.getPortCode());
        }

        Destination destination = null;
        if (request.getDestinationId() != null) {
            destination = destinationRepository.findById(request.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));
        }

        String rawCountryCode = request.getCountryCode() != null ? request.getCountryCode().trim().toUpperCase() : null;
        if (rawCountryCode == null && destination != null) {
            rawCountryCode = destination.getCountryCode();
        }
        final String effectiveCountryCode = rawCountryCode;

        String timeZoneId = request.getTimeZoneId();
        if (timeZoneId == null || timeZoneId.isBlank()) {
            timeZoneId = PortTimeZoneHelper.resolveDefaultTimeZoneByCountry(effectiveCountryCode)
                    .orElseThrow(() -> new BusinessException("TIME_ZONE_REQUIRED",
                            "Không thể tự động suy luận múi giờ cho quốc gia: " + effectiveCountryCode + ". Vui lòng nhập timeZoneId theo chuẩn IANA."));
            log.info("Auto-resolved timezone for port {} ({}) -> {}", request.getPortCode(), effectiveCountryCode, timeZoneId);
        } else if (!PortTimeZoneHelper.isValidTimeZoneId(timeZoneId)) {
            throw new BusinessException("INVALID_TIME_ZONE", "Múi giờ IANA không hợp lệ: " + timeZoneId);
        }

        Port port = new Port();
        port.setPortCode(request.getPortCode().toUpperCase());
        port.setPortName(request.getPortName());
        port.setCountryCode(effectiveCountryCode);
        port.setTimeZoneId(timeZoneId);
        port.setDestination(destination);
        port.setDescription(request.getDescription());
        port.setLatitude(request.getLatitude());
        port.setLongitude(request.getLongitude());
        port.setLocationType(request.getLocationType() != null ? request.getLocationType() : PortLocationType.PORT);
        port.setStatus(request.getStatus() != null ? request.getStatus() : PortStatus.ACTIVE);

        port = portRepository.save(port);
        return mapToResponse(port);
    }

    @Transactional(readOnly = true)
    public Page<PortResponse> list(Pageable pageable) {
        return portRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<PortResponse> listAllActive() {
        return portRepository.findAllActivePorts().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PortResponse detail(UUID portId) {
        Port port = getPortById(portId);
        return mapToResponse(port);
    }

    @Transactional(readOnly = true)
    public List<TimeZoneOptionResponse> listSupportedTimeZones(String search, String countryCode) {
        return PortTimeZoneHelper.getSupportedTimeZoneOptions(search, countryCode);
    }

    @Transactional(readOnly = true)
    public TimeZoneOptionResponse resolveTimeZoneByCountry(String countryCode) {
        String tzId = PortTimeZoneHelper.resolveDefaultTimeZoneByCountry(countryCode)
                .orElseThrow(() -> new BusinessException("TIME_ZONE_NOT_FOUND",
                        "Không tìm thấy múi giờ mặc định cho quốc gia: " + countryCode));
        String offset = PortTimeZoneHelper.calculateUtcOffset(tzId);
        String display = PortTimeZoneHelper.formatTimeZoneDisplay(tzId);
        String localTime = PortTimeZoneHelper.getCurrentLocalTimeIso(tzId);

        return TimeZoneOptionResponse.builder()
                .timeZoneId(tzId)
                .displayName(display)
                .countryCode(countryCode != null ? countryCode.trim().toUpperCase() : null)
                .utcOffset(offset)
                .currentLocalTime(localTime)
                .isRecommended(true)
                .build();
    }

    @Transactional
    public PortResponse update(UUID portId, PortUpdateRequest request) {
        Port port = getPortById(portId);

        if (request.getPortName() != null) {
            port.setPortName(request.getPortName());
        }
        if (request.getDescription() != null) {
            port.setDescription(request.getDescription());
        }
        if (request.getDestinationId() != null) {
            Destination destination = destinationRepository.findById(request.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));
            port.setDestination(destination);
        }
        if (request.getLatitude() != null) {
            port.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            port.setLongitude(request.getLongitude());
        }
        if (request.getTimeZoneId() != null) {
            if (!PortTimeZoneHelper.isValidTimeZoneId(request.getTimeZoneId())) {
                throw new BusinessException("INVALID_TIME_ZONE", "Múi giờ IANA không hợp lệ: " + request.getTimeZoneId());
            }
            port.setTimeZoneId(request.getTimeZoneId());
        }
        if (request.getLocationType() != null) {
            port.setLocationType(request.getLocationType());
        }

        port = portRepository.save(port);
        return mapToResponse(port);
    }

    @Transactional
    public void deleteUnused(UUID portId) {
        Port port = getPortById(portId);

        // Check if port is used in any cruise stops
        // This would require checking CruiseStopRepository
        // For now, we allow deletion

        portRepository.delete(port);
        log.info("Deleted unused port: {}", portId);
    }

    @Transactional
    public PortResponse activate(UUID portId) {
        Port port = getPortById(portId);

        if (port.getStatus() == PortStatus.ACTIVE) {
            return mapToResponse(port);
        }

        port.setStatus(PortStatus.ACTIVE);
        port = portRepository.save(port);
        log.info("Activated port: {}", portId);
        return mapToResponse(port);
    }

    @Transactional
    public PortResponse deactivate(UUID portId) {
        Port port = getPortById(portId);

        if (port.getStatus() == PortStatus.INACTIVE) {
            return mapToResponse(port);
        }

        // Check if port is used in any active cruise stops
        // For now, allow deactivation

        port.setStatus(PortStatus.INACTIVE);
        port = portRepository.save(port);
        log.info("Deactivated port: {}", portId);
        return mapToResponse(port);
    }

    @Transactional(readOnly = true)
    public void validatePort(UUID portId) {
        Port port = getPortById(portId);
        // Additional validation logic can be added here
    }

    @Transactional(readOnly = true)
    public boolean checkOperationalAvailability(UUID portId) {
        Port port = getPortById(portId);
        return port.getStatus() == PortStatus.ACTIVE;
    }

    private Port getPortById(UUID portId) {
        return portRepository.findById(portId)
                .orElseThrow(() -> new ResourceNotFoundException("Port", portId));
    }

    private PortResponse mapToResponse(Port port) {
        PortResponse res = new PortResponse();
        res.setPortId(port.getPortId());
        res.setPortCode(port.getPortCode());
        res.setPortName(port.getPortName());
        res.setCountryCode(port.getCountryCode());
        res.setDescription(port.getDescription());
        res.setTimeZoneId(port.getTimeZoneId());
        res.setUtcOffset(PortTimeZoneHelper.calculateUtcOffset(port.getTimeZoneId()));
        res.setFormattedTimeZone(PortTimeZoneHelper.formatTimeZoneDisplay(port.getTimeZoneId()));
        res.setCurrentLocalTime(PortTimeZoneHelper.getCurrentLocalTimeIso(port.getTimeZoneId()));
        res.setLatitude(port.getLatitude());
        res.setLongitude(port.getLongitude());
        res.setLocationType(port.getLocationType() != null ? port.getLocationType() : PortLocationType.PORT);
        res.setStatus(port.getStatus());
        if (port.getDestination() != null) {
            res.setDestinationId(port.getDestination().getDestinationId());
            res.setDestinationName(port.getDestination().getDestinationName());
        }
        res.setCreatedAt(port.getCreatedAt());
        res.setUpdatedAt(port.getUpdatedAt());
        return res;
    }
}