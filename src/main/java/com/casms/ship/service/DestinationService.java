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

    @org.springframework.beans.factory.annotation.Autowired
    private com.casms.audit.AuditService auditService;

    @Transactional
    public DestinationResponse create(DestinationCreateRequest request) {
        log.info("Creating new destination with name: {}", request.getDestinationName());

        if (destinationRepository.existsByDestinationName(request.getDestinationName())) {
            throw new BusinessException(
                    "DESTINATION_NAME_EXISTS",
                    "Destination name already exists: " + request.getDestinationName());
        }

        String countryCode = PortTimeZoneHelper.requireCountryCode(request.getCountryCode());
        Destination destination = new Destination();
        destination.setDestinationName(request.getDestinationName());
        destination.setCountryCode(countryCode);
        destination.setDescription(request.getDescription());
        destination.setDomesticRegion(
                validRegion(request.getDomesticRegion(), destination.getCountryCode()));
        destination.setStatus(
                request.getStatus() != null ? request.getStatus() : DestinationStatus.ACTIVE);

        destination = destinationRepository.saveAndFlush(destination);
        auditService.recordDirectoryMutation(
                "Destination",
                destination.getDestinationId(),
                "CREATE",
                java.util.Map.of(),
                snapshot(destination));
        return mapToResponse(destination);
    }

    @Transactional(readOnly = true)
    public Page<DestinationResponse> list(Pageable pageable) {
        return destinationRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<DestinationResponse> list(
            String search, String countryCode, DestinationStatus status, Pageable pageable) {
        return destinationRepository
                .findDirectory(
                        search == null || search.isBlank() ? null : search.trim(),
                        countryCode == null || countryCode.isBlank()
                                ? null
                                : countryCode.trim().toUpperCase(java.util.Locale.ROOT),
                        status,
                        pageable)
                .map(this::mapToResponse);
    }

    private String validRegion(String region, String country) {
        if (region == null || region.isBlank()) return null;
        if (!"VN".equals(country)
                || !java.util.Set.of("BAC_BO", "TRUNG_BO", "NAM_BO").contains(region))
            throw new BusinessException(
                    "INVALID_DOMESTIC_REGION", "Miền nội địa không hợp lệ với quốc gia.");
        return region;
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
        var before = snapshot(destination);

        PortTimeZoneHelper.requireCountryCode(destination.getCountryCode());
        if (request.getDestinationName() != null && request.getDestinationName().isBlank())
            throw new BusinessException(
                    "INVALID_DESTINATION_NAME", "Tên điểm đến không được để trống.");
        if (request.getDestinationName() != null
                && !request.getDestinationName().equals(destination.getDestinationName())) {
            if (destinationRepository.existsByDestinationName(request.getDestinationName())) {
                throw new BusinessException(
                        "DESTINATION_NAME_EXISTS",
                        "Destination name already exists: " + request.getDestinationName());
            }
            destination.setDestinationName(request.getDestinationName());
        }
        if (request.getDescription() != null) {
            destination.setDescription(request.getDescription());
        }

        if (request.getDomesticRegion() != null)
            destination.setDomesticRegion(
                    validRegion(request.getDomesticRegion(), destination.getCountryCode()));
        destination = destinationRepository.saveAndFlush(destination);
        auditService.recordDirectoryMutation(
                "Destination",
                destination.getDestinationId(),
                "UPDATE",
                before,
                snapshot(destination));
        return mapToResponse(destination);
    }

    @Transactional
    public void deleteUnused(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);
        var before = snapshot(destination);

        if (destinationRepository.hasDirectoryReferences(destinationId)) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "DESTINATION_IN_USE",
                    "Điểm đến đang được cảng hoặc lịch trình tham chiếu; không thể xóa.");
        }

        destinationRepository.deleteDirectoryRow(destinationId);
        auditService.recordDirectoryMutation(
                "Destination", destinationId, "DELETE_UNUSED", before, java.util.Map.of());
        log.info("Deleted unused destination: {}", destinationId);
    }

    @Transactional(readOnly = true)
    public List<PortSummaryResponse> listPortsByDestination(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);

        List<Port> ports =
                portRepository.findByDestinationDestinationIdOrderByPortNameAsc(destinationId);
        if (ports == null || ports.isEmpty()) {
            ports = destination.getPorts() != null ? destination.getPorts() : List.of();
        }

        return ports.stream().map(this::mapPortToSummary).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public void validateDestination(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);
        PortTimeZoneHelper.requireCountryCode(destination.getCountryCode());
    }

    @Transactional
    public DestinationResponse activate(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);
        var before = snapshot(destination);
        PortTimeZoneHelper.requireCountryCode(destination.getCountryCode());
        destination.setStatus(DestinationStatus.ACTIVE);
        destination = destinationRepository.saveAndFlush(destination);
        auditService.recordDirectoryMutation(
                "Destination",
                destination.getDestinationId(),
                "UPDATE",
                before,
                snapshot(destination));
        log.info("Activated destination: {}", destinationId);
        return mapToResponse(destination);
    }

    @Transactional
    public DestinationResponse deactivate(UUID destinationId) {
        Destination destination = getDestinationById(destinationId);
        var before = snapshot(destination);
        PortTimeZoneHelper.requireCountryCode(destination.getCountryCode());
        destination.setStatus(DestinationStatus.INACTIVE);
        destination = destinationRepository.saveAndFlush(destination);
        auditService.recordDirectoryMutation(
                "Destination",
                destination.getDestinationId(),
                "UPDATE",
                before,
                snapshot(destination));
        log.info("Deactivated destination: {}", destinationId);
        return mapToResponse(destination);
    }

    private Destination getDestinationById(UUID destinationId) {
        return destinationRepository
                .findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", destinationId));
    }

    private DestinationResponse mapToResponse(Destination destination) {
        DestinationResponse res = new DestinationResponse();
        res.setDestinationId(destination.getDestinationId());
        res.setDestinationName(destination.getDestinationName());
        res.setCountryCode(destination.getCountryCode());
        res.setDescription(destination.getDescription());
        res.setDomesticRegion(destination.getDomesticRegion());
        res.setStatus(destination.getStatus());
        res.setCreatedAt(destination.getCreatedAt());
        res.setUpdatedAt(destination.getUpdatedAt());

        // Map ports if available
        List<Port> ports = destination.getPorts();
        if (ports == null || ports.isEmpty()) {
            ports =
                    portRepository.findByDestinationDestinationIdOrderByPortNameAsc(
                            destination.getDestinationId());
        }
        if (ports != null) {
            res.setPorts(ports.stream().map(this::mapPortToSummary).collect(Collectors.toList()));
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

    private java.util.Map<String, Object> snapshot(Destination d) {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("destinationId", d.getDestinationId());
        fields.put("destinationName", d.getDestinationName());
        fields.put("countryCode", d.getCountryCode());
        fields.put("domesticRegion", d.getDomesticRegion());
        fields.put("description", d.getDescription());
        fields.put("status", d.getStatus());
        fields.put("createdAt", d.getCreatedAt());
        fields.put("updatedAt", d.getUpdatedAt());
        return fields;
    }
}
