package com.casms.ship.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.ItineraryChangeRequest;
import com.casms.ship.dto.response.ItineraryChangeHistoryResponse;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryChangeHistory;
import com.casms.ship.enums.ItineraryChangeType;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.ItineraryChangeHistoryRepository;
import com.casms.ship.repository.ItineraryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ItineraryChangeHistoryService {

    private final ItineraryChangeHistoryRepository historyRepository;
    private final ItineraryRepository itineraryRepository;
    private final CruiseStopRepository cruiseStopRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ItineraryChangeHistoryService(
            ItineraryChangeHistoryRepository historyRepository,
            ItineraryRepository itineraryRepository,
            CruiseStopRepository cruiseStopRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.historyRepository = historyRepository;
        this.itineraryRepository = itineraryRepository;
        this.cruiseStopRepository = cruiseStopRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // APPEND / RECORD ITINERARY CHANGE

    @Transactional
    public ItineraryChangeHistoryResponse append(
            ItineraryChangeRequest request
    ) {
        validateRequest(request);

        Itinerary itinerary = itineraryRepository
                .findById(request.itineraryId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Itinerary not found: " + request.itineraryId()
                ));

        CruiseStop cruiseStop = null;

        if (request.cruiseStopId() != null) {
            cruiseStop = cruiseStopRepository
                    .findById(request.cruiseStopId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Cruise stop not found: " + request.cruiseStopId()
                    ));

            if (!cruiseStop.getItinerary().getItineraryId()
                    .equals(itinerary.getItineraryId())) {
                throw new IllegalArgumentException(
                        "Cruise stop does not belong to the specified itinerary"
                );
            }
        }

        validateChangedEntity(
                request.changedEntityType(),
                request.changedEntityId(),
                itinerary,
                cruiseStop
        );

        User changedByUser = userRepository
                .findById(request.changedByUserId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + request.changedByUserId()
                ));

        validateJson(request.beforeData(), "beforeData");
        validateJson(request.afterData(), "afterData");

        ItineraryChangeHistory history = new ItineraryChangeHistory();
        history.setItinerary(itinerary);
        history.setCruiseStop(cruiseStop);
        history.setChangedEntityType(
                request.changedEntityType().trim()
        );
        history.setChangedEntityId(request.changedEntityId());
        history.setChangeType(request.changeType());
        history.setBeforeData(normalizeJson(request.beforeData()));
        history.setAfterData(normalizeJson(request.afterData()));
        history.setChangeSummary(request.changeSummary().trim());
        history.setReason(request.reason().trim());
        history.setChangedByUser(changedByUser);
        history.setChangedAt(Instant.now());

        return toResponse(historyRepository.save(history));
    }

    // BUSINESS ALIAS

    @Transactional
    public ItineraryChangeHistoryResponse recordItineraryChange(
            ItineraryChangeRequest request
    ) {
        return append(request);
    }

    // LIST WITH OPTIONAL FILTERS

    @Transactional(readOnly = true)
    public List<ItineraryChangeHistoryResponse> list(
            UUID itineraryId,
            UUID cruiseStopId,
            ItineraryChangeType changeType,
            UUID changedByUserId
    ) {
        if (itineraryId != null) {
            ensureItineraryExists(itineraryId);
        }

        if (cruiseStopId != null) {
            ensureCruiseStopExists(cruiseStopId);
        }

        return historyRepository.search(
                        itineraryId,
                        cruiseStopId,
                        changeType,
                        changedByUserId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // DETAIL

    @Transactional(readOnly = true)
    public ItineraryChangeHistoryResponse detail(UUID historyId) {
        if (historyId == null) {
            throw new IllegalArgumentException(
                    "Change history ID is required"
            );
        }

        ItineraryChangeHistory history = historyRepository
                .findById(historyId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Itinerary change history not found: " + historyId
                ));

        return toResponse(history);
    }

    // GET HISTORY OF ONE ITINERARY

    @Transactional(readOnly = true)
    public List<ItineraryChangeHistoryResponse> getChangeHistory(
            UUID itineraryId
    ) {
        ensureItineraryExists(itineraryId);

        return historyRepository
                .findByItinerary_ItineraryIdOrderByChangedAtDesc(
                        itineraryId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateRequest(ItineraryChangeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Change history request is required"
            );
        }

        requireText(request.changedEntityType(), "changedEntityType");
        requireText(request.changeSummary(), "changeSummary");
        requireText(request.reason(), "reason");

        if (request.itineraryId() == null
                || request.changedEntityId() == null
                || request.changeType() == null
                || request.changedByUserId() == null) {
            throw new IllegalArgumentException(
                    "Required change history fields must not be null"
            );
        }

        if (request.changedEntityType().trim().length() > 50) {
            throw new IllegalArgumentException(
                    "changedEntityType must not exceed 50 characters"
            );
        }

        if (request.changeSummary().trim().length() > 1000
                || request.reason().trim().length() > 1000) {
            throw new IllegalArgumentException(
                    "changeSummary and reason must not exceed 1000 characters"
            );
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }
    }

    private void validateJson(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(value);

            if (node == null) {
                throw new IllegalArgumentException(
                        fieldName + " must contain valid JSON"
                );
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    fieldName + " contains invalid JSON",
                    exception
            );
        }
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(value);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid JSON data",
                    exception
            );
        }
    }

    private void validateChangedEntity(
            String entityType,
            UUID entityId,
            Itinerary itinerary,
            CruiseStop cruiseStop
    ) {
        String normalizedType = entityType.trim();

        if ("Itinerary".equalsIgnoreCase(normalizedType)) {
            if (!itinerary.getItineraryId().equals(entityId)) {
                throw new IllegalArgumentException(
                        "changedEntityId does not match itineraryId"
                );
            }
            return;
        }

        if ("CruiseStop".equalsIgnoreCase(normalizedType)) {
            if (cruiseStop == null) {
                throw new IllegalArgumentException(
                        "cruiseStopId is required for CruiseStop changes"
                );
            }

            if (!cruiseStop.getCruiseStopId().equals(entityId)) {
                throw new IllegalArgumentException(
                        "changedEntityId does not match cruiseStopId"
                );
            }
        }
    }

    private void ensureItineraryExists(UUID itineraryId) {
        if (itineraryId == null) {
            throw new IllegalArgumentException(
                    "Itinerary ID is required"
            );
        }

        if (!itineraryRepository.existsById(itineraryId)) {
            throw new EntityNotFoundException(
                    "Itinerary not found: " + itineraryId
            );
        }
    }

    private void ensureCruiseStopExists(UUID cruiseStopId) {
        if (!cruiseStopRepository.existsById(cruiseStopId)) {
            throw new EntityNotFoundException(
                    "Cruise stop not found: " + cruiseStopId
            );
        }
    }

    private ItineraryChangeHistoryResponse toResponse(
            ItineraryChangeHistory history
    ) {
        return new ItineraryChangeHistoryResponse(
                history.getChangeHistoryId(),
                history.getItinerary().getItineraryId(),
                history.getCruiseStop() == null
                        ? null
                        : history.getCruiseStop().getCruiseStopId(),
                history.getChangedEntityType(),
                history.getChangedEntityId(),
                history.getChangeType(),
                history.getBeforeData(),
                history.getAfterData(),
                history.getChangeSummary(),
                history.getReason(),
                history.getChangedByUser().getUserId(),
                history.getChangedAt()
        );
    }
}