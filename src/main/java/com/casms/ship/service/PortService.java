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

    @org.springframework.beans.factory.annotation.Autowired
    private com.casms.audit.AuditService auditService;

    @Transactional
    public PortResponse create(PortCreateRequest request) {
        log.info("Creating new port with code: {}", request.getPortCode());

        String portCode = request.getPortCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (portRepository.existsByPortCode(portCode)) {
            throw new BusinessException("PORT_CODE_EXISTS", "Port code already exists: " + request.getPortCode());
        }

        Destination destination = null;
        if (request.getDestinationId() != null) {
            destination = destinationRepository.findById(request.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));
        }

        String effectiveCountryCode = PortTimeZoneHelper.requireCountryCode(request.getCountryCode());
        String timeZoneId = PortTimeZoneHelper.requirePortTimeZone(request.getTimeZoneId());

        Port port = new Port();
        port.setPortCode(portCode);
        port.setPortName(request.getPortName().trim());
        port.setCountryCode(effectiveCountryCode);
        port.setTimeZoneId(timeZoneId);
        port.setDestination(destination);
        port.setDescription(request.getDescription());
        port.setLatitude(request.getLatitude());
        port.setLongitude(request.getLongitude());
        validateCoordinates(request.getLatitude(), request.getLongitude());
        port.setCoordinateSource(newCoordinateSource(request.getCoordinateSource()));
        port.setLocationType(request.getLocationType() != null ? request.getLocationType() : PortLocationType.PORT);
        port.setStatus(request.getStatus() != null ? request.getStatus() : PortStatus.ACTIVE);

        port = portRepository.saveAndFlush(port);
        auditService.recordDirectoryMutation("Port", port.getPortId(), "CREATE", java.util.Map.of(), snapshot(port));
        return mapToResponse(port);
    }

    @Transactional(readOnly = true)
    public Page<PortResponse> list(Pageable pageable) {
        return list(null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PortResponse> list(List<String> countryCodes, PortStatus status, String search, Pageable pageable) {
        List<String> normalizedCountries = (countryCodes != null && !countryCodes.isEmpty())
                ? countryCodes.stream().filter(c -> c != null && !c.isBlank()).map(c -> c.trim().toUpperCase()).toList()
                : null;
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return portRepository.findPortsWithFilters(normalizedCountries, status, normalizedSearch, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<PortResponse> listAllActive() {
        return listAllActive(null, null);
    }

    @Transactional(readOnly = true)
    public List<PortResponse> listAllActive(List<String> countryCodes, String search) {
        List<String> normalizedCountries = (countryCodes != null && !countryCodes.isEmpty())
                ? countryCodes.stream().filter(c -> c != null && !c.isBlank()).map(c -> c.trim().toUpperCase()).toList()
                : null;
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return portRepository.findActivePortsWithFilters(normalizedCountries, normalizedSearch).stream()
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
        var before = snapshot(port);
        boolean geographic = request.getCoordinateSource() != null
                || (request.getLatitude() != null && !sameNumber(request.getLatitude(), port.getLatitude()))
                || (request.getLongitude() != null && !sameNumber(request.getLongitude(), port.getLongitude()));
        if (geographic) {
            validateCoordinates(request.getLatitude(), request.getLongitude());
            var selectedSource = newCoordinateSource(request.getCoordinateSource());
            port.setLatitude(request.getLatitude());
            port.setLongitude(request.getLongitude());
            port.setCoordinateSource(selectedSource);
        }
        // An unrelated PATCH (including unchanged legacy coordinates) keeps existing provenance verbatim.
        PortTimeZoneHelper.requireCountryCode(port.getCountryCode());
        PortTimeZoneHelper.requirePortTimeZone(request.getTimeZoneId() == null ? port.getTimeZoneId() : request.getTimeZoneId());

        if (request.getPortName() != null) {
            if (request.getPortName().isBlank()) throw new BusinessException("INVALID_PORT_NAME", "Tên cảng không được để trống.");
            port.setPortName(request.getPortName().trim());
        }
        if (request.getDescription() != null) {
            port.setDescription(request.getDescription());
        }
        if (request.getDestinationId() != null) {
            Destination destination = destinationRepository.findById(request.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));
            port.setDestination(destination);
        }
        if (request.getTimeZoneId() != null) port.setTimeZoneId(PortTimeZoneHelper.requirePortTimeZone(request.getTimeZoneId()));
        if (request.getLocationType() != null) {
            port.setLocationType(request.getLocationType());
        }

        port = portRepository.saveAndFlush(port);
        auditService.recordDirectoryMutation("Port", port.getPortId(), "UPDATE", before, snapshot(port));
        return mapToResponse(port);
    }

    @Transactional
    public void deleteUnused(UUID portId) {
        Port port = getPortById(portId);

        if (portRepository.hasDirectoryReferences(portId)) {
            throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT, "PORT_IN_USE", "Cảng đang được lịch trình hoặc chuyến khởi hành tham chiếu; không thể xóa.");
        }

        var before = snapshot(port);
        try {
            portRepository.delete(port);
            // Historical storage keeps its foreign keys; never cascade-delete archived evidence.
            portRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException referenced) {
            throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT, "PORT_IN_USE",
                    "Cảng đang được dữ liệu lịch sử tham chiếu; không thể xóa.");
        }
        auditService.recordDirectoryMutation("Port", portId, "DELETE_UNUSED", before, java.util.Map.of());
        log.info("Deleted unused port: {}", portId);
    }

    @Transactional
    public PortResponse activate(UUID portId) {
        Port port = getPortById(portId);
        var before = snapshot(port);

        if (port.getStatus() == PortStatus.ACTIVE) {
            return mapToResponse(port);
        }

        PortTimeZoneHelper.requireCountryCode(port.getCountryCode());
        PortTimeZoneHelper.requirePortTimeZone(port.getTimeZoneId());
        port.setStatus(PortStatus.ACTIVE);
        port = portRepository.saveAndFlush(port);
        auditService.recordDirectoryMutation("Port", port.getPortId(), "ACTIVATE", before, snapshot(port));
        log.info("Activated port: {}", portId);
        return mapToResponse(port);
    }

    @Transactional
    public PortResponse deactivate(UUID portId) {
        Port port = getPortById(portId);
        var before = snapshot(port);

        if (port.getStatus() == PortStatus.INACTIVE) {
            return mapToResponse(port);
        }

        // Check if port is used in any active cruise stops
        // For now, allow deactivation

        PortTimeZoneHelper.requireCountryCode(port.getCountryCode());
        PortTimeZoneHelper.requirePortTimeZone(port.getTimeZoneId());
        port.setStatus(PortStatus.INACTIVE);
        port = portRepository.saveAndFlush(port);
        auditService.recordDirectoryMutation("Port", port.getPortId(), "DEACTIVATE", before, snapshot(port));
        log.info("Deactivated port: {}", portId);
        return mapToResponse(port);
    }

    @Transactional(readOnly = true)
    public void validatePort(UUID portId) {
        Port port = getPortById(portId);
        PortTimeZoneHelper.requireCountryCode(port.getCountryCode());
        PortTimeZoneHelper.requirePortTimeZone(port.getTimeZoneId());
    }

    @Transactional(readOnly = true)
    public boolean checkOperationalAvailability(UUID portId) {
        Port port = getPortById(portId);
        return port.getStatus() == PortStatus.ACTIVE;
    }

    private boolean sameNumber(java.math.BigDecimal first, java.math.BigDecimal second) {
        return first != null && second != null && first.compareTo(second) == 0;
    }

    private void validateCoordinates(java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
        if (latitude == null || longitude == null || latitude.abs().compareTo(java.math.BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(java.math.BigDecimal.valueOf(180)) > 0) {
            throw new BusinessException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_PORT_COORDINATES", "Chọn kết quả Geocoding có đủ tọa độ hợp lệ.");
        }
    }

    private com.casms.ship.dto.PortCoordinateSource newCoordinateSource(com.casms.ship.dto.PortCoordinateSource input) {
        if (input == null || !"OPEN_METEO_GEOCODING".equals(input.getProvider()) || !"GEONAMES".equals(input.getDataset())
                || !"GEOCODED_LOCALITY".equals(input.getCoordinateReferenceType()) || input.getExternalLocationId() == null
                || input.getExternalLocationId() < 1 || input.getExternalLocationId() > 9007199254740991L
                || input.getLocationName() == null || input.getLocationName().isBlank()) {
            throw new BusinessException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "PORT_COORDINATE_SOURCE_REQUIRED", "Tạo cảng hoặc đổi tọa độ cần chọn kết quả GeoNames qua Open-Meteo hợp lệ.");
        }
        var source = new com.casms.ship.dto.PortCoordinateSource();
        source.setProvider(input.getProvider());
        source.setDataset(input.getDataset());
        source.setExternalLocationId(input.getExternalLocationId());
        source.setLocationName(input.getLocationName());
        source.setCoordinateReferenceType(input.getCoordinateReferenceType());
        source.setCountryCode(input.getCountryCode());
        source.setFeatureCode(input.getFeatureCode());
        source.setTimezone(input.getTimezone());
        source.setAdmin1(input.getAdmin1());
        source.setRecordedAt(java.time.Instant.now()); // Never trust a client timestamp.
        return source;
    }

    private java.util.Map<String,Object> snapshot(Port port) {
        var fields = new java.util.LinkedHashMap<String,Object>();
        fields.put("portId", port.getPortId());
        fields.put("portCode", port.getPortCode());
        fields.put("portName", port.getPortName());
        fields.put("countryCode", port.getCountryCode());
        fields.put("timeZoneId", port.getTimeZoneId());
        fields.put("latitude", port.getLatitude());
        fields.put("longitude", port.getLongitude());
        fields.put("coordinateSource", port.getCoordinateSource());
        fields.put("description", port.getDescription());
        fields.put("locationType", port.getLocationType());
        fields.put("destinationId", port.getDestination() == null ? null : port.getDestination().getDestinationId());
        fields.put("status", port.getStatus());
        fields.put("createdAt", port.getCreatedAt());
        fields.put("updatedAt", port.getUpdatedAt());
        return fields;
    }

    private Port getPortById(UUID portId) {
        return (org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? portRepository.findById(portId) : portRepository.lockDirectory(portId))
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
        res.setCoordinateSource(port.getCoordinateSource());
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