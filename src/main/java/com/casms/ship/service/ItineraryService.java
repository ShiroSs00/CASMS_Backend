package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.ItineraryCreateRequest;
import com.casms.ship.dto.request.ItineraryUpdateDraftRequest;
import com.casms.ship.dto.response.ItineraryResponse;
import com.casms.ship.dto.response.ItineraryDayResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.ItineraryType;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.repository.CruiseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryService {

    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final CruiseRepository cruiseRepository;
    private final UserRepository userRepository;

    @Transactional
    public ItineraryResponse create(ItineraryCreateRequest request, UUID creatorUserId) {
        log.info("Creating new itinerary for cruise: {}", request.getCruiseId());

        if (creatorUserId == null) {
            throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Creator user ID is required");
        }

        Cruise cruise = cruiseRepository.findById(request.getCruiseId())
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", request.getCruiseId()));

        if (itineraryRepository.existsByCruiseCruiseId(request.getCruiseId())) {
            throw new BusinessException("ITINERARY_ALREADY_EXISTS", "Itinerary already exists for this cruise");
        }

        User createdBy = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", creatorUserId));

        LocalDate startDate = request.getStartDate() != null ? request.getStartDate()
                : cruise.getDepartureDatetime().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate()
                : cruise.getReturnDatetime().atZone(java.time.ZoneOffset.UTC).toLocalDate();

        if (!endDate.isAfter(startDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", "End date must be after start date");
        }

        String name = (request.getItineraryName() != null && !request.getItineraryName().isBlank())
                ? request.getItineraryName().trim()
                : "Lịch trình " + cruise.getCruiseName();

        ItineraryType type = request.getItineraryType();
        if (type == null) {
            boolean samePort = cruise.getDeparturePort() != null && cruise.getReturnPort() != null
                    && cruise.getDeparturePort().getPortId().equals(cruise.getReturnPort().getPortId());
            type = samePort ? ItineraryType.ROUND_TRIP : ItineraryType.ONE_WAY;
        }

        Itinerary itinerary = new Itinerary();
        itinerary.setCruise(cruise);
        itinerary.setItineraryName(name);
        itinerary.setStartDate(startDate);
        itinerary.setEndDate(endDate);
        itinerary.setItineraryType(type);
        itinerary.setStatus(ItineraryStatus.DRAFT);
        itinerary.setCreatedByUser(createdBy);

        itinerary = itineraryRepository.save(itinerary);
        return mapToResponse(itinerary);
    }

    @Transactional(readOnly = true)
    public Page<ItineraryResponse> list(Pageable pageable) {
        return itineraryRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ItineraryResponse detail(UUID itineraryId) {
        Itinerary itinerary = getItineraryById(itineraryId);
        return mapToResponse(itinerary);
    }

    @Transactional
    public ItineraryResponse updateDraft(UUID itineraryId, ItineraryUpdateDraftRequest request) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_DRAFT", "Only draft itineraries can be modified");
        }

        if (request.getItineraryName() != null) {
            itinerary.setItineraryName(request.getItineraryName());
        }
        if (request.getStartDate() != null || request.getEndDate() != null) {
            LocalDate newStart = request.getStartDate() != null ? request.getStartDate() : itinerary.getStartDate();
            LocalDate newEnd = request.getEndDate() != null ? request.getEndDate() : itinerary.getEndDate();
            if (!newEnd.isAfter(newStart)) {
                throw new BusinessException("INVALID_DATE_RANGE", "End date must be after start date");
            }
            if (request.getStartDate() != null) itinerary.setStartDate(request.getStartDate());
            if (request.getEndDate() != null) itinerary.setEndDate(request.getEndDate());
        }
        if (request.getItineraryType() != null) {
            itinerary.setItineraryType(request.getItineraryType());
        }

        itinerary = itineraryRepository.save(itinerary);
        return mapToResponse(itinerary);
    }

    @Transactional
    public void deleteDraft(UUID itineraryId) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("ITINERARY_NOT_DRAFT", "Only draft itineraries can be deleted");
        }

        if (itinerary.getDays() != null && !itinerary.getDays().isEmpty()) {
            throw new BusinessException("ITINERARY_HAS_DAYS", "Cannot delete itinerary that has days");
        }

        itineraryRepository.delete(itinerary);
        log.info("Deleted draft itinerary: {}", itineraryId);
    }

    @Transactional(readOnly = true)
    public void validateFeasibility(UUID itineraryId) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (!itinerary.getEndDate().isAfter(itinerary.getStartDate())) {
            throw new BusinessException("INVALID_DATE_RANGE", "End date must be after start date");
        }

        List<ItineraryDay> days = itinerary.getDays();
        if (days != null && !days.isEmpty()) {
            days.sort((d1, d2) -> d1.getDayNumber().compareTo(d2.getDayNumber()));
            
            for (int i = 0; i < days.size(); i++) {
                if (days.get(i).getDayNumber() != i + 1) {
                    throw new BusinessException("INVALID_DAY_SEQUENCE", "Day numbers must be sequential starting from 1");
                }
                
                if (days.get(i).getDate().isBefore(itinerary.getStartDate()) || 
                    days.get(i).getDate().isAfter(itinerary.getEndDate())) {
                    throw new BusinessException("DAY_DATE_OUT_OF_RANGE", 
                        "Day " + days.get(i).getDayNumber() + " date is outside itinerary range");
                }
            }
        }

        if (itinerary.getCruise() != null) {
            Cruise cruise = itinerary.getCruise();
            LocalDate departureDate = cruise.getDepartureDatetime().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate returnDate = cruise.getReturnDatetime().atZone(ZoneId.systemDefault()).toLocalDate();
            if (itinerary.getStartDate().isBefore(departureDate) ||
                itinerary.getEndDate().isAfter(returnDate)) {
                throw new BusinessException("ITINERARY_OUTSIDE_CRUISE_RANGE", 
                    "Itinerary dates must be within cruise departure and return dates");
            }
        }
    }

    @Transactional
    public ItineraryResponse submit(UUID itineraryId) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException("INVALID_STATUS", "Only draft itineraries can be submitted");
        }

        validateFeasibility(itinerary.getItineraryId());

        itinerary.setStatus(ItineraryStatus.PENDING_APPROVAL);
        itinerary = itineraryRepository.save(itinerary);
        log.info("Submitted itinerary for approval: {}", itinerary.getItineraryId());
        return mapToResponse(itinerary);
    }

    @Transactional
    public ItineraryResponse approve(UUID itineraryId, UUID approverId) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Only pending approval itineraries can be approved");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", approverId));

        itinerary.setStatus(ItineraryStatus.APPROVED);
        itinerary.setApprovedByUser(approver);
        itinerary.setApprovedAt(Instant.now());

        itinerary = itineraryRepository.save(itinerary);
        log.info("Approved itinerary: {}", itinerary.getItineraryId());
        return mapToResponse(itinerary);
    }

    @Transactional
    public ItineraryResponse reject(UUID itineraryId, UUID approverId, String reason) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Only pending approval itineraries can be rejected");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", approverId));

        itinerary.setStatus(ItineraryStatus.REJECTED);
        itinerary.setApprovedByUser(approver);
        itinerary.setApprovedAt(Instant.now());

        itinerary = itineraryRepository.save(itinerary);
        log.info("Rejected itinerary: {}", itinerary.getItineraryId());
        return mapToResponse(itinerary);
    }

    @Transactional
    public ItineraryResponse applyChange(UUID itineraryId, String changeDescription) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (itinerary.getStatus() != ItineraryStatus.APPROVED && 
            itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE &&
            itinerary.getStatus() != ItineraryStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATUS", "Cannot apply changes to itinerary in current status");
        }

        log.info("Applied change to itinerary {}: {}", itineraryId, changeDescription);
        return mapToResponse(itinerary);
    }

    @Transactional(readOnly = true)
    public void validateStructure(UUID itineraryId) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (itinerary.getDays() == null || itinerary.getDays().isEmpty()) {
            throw new BusinessException("ITINERARY_EMPTY", "Itinerary must have at least one day");
        }

        List<ItineraryDay> days = itinerary.getDays().stream()
                .sorted((d1, d2) -> d1.getDayNumber().compareTo(d2.getDayNumber()))
                .collect(Collectors.toList());

        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).getDayNumber() != i + 1) {
                throw new BusinessException("INVALID_DAY_SEQUENCE", 
                    "Day numbers must be sequential starting from 1");
            }
        }

        for (int i = 1; i < days.size(); i++) {
            if (!days.get(i).getDate().equals(days.get(i-1).getDate().plusDays(1))) {
                throw new BusinessException("NON_CONTINUOUS_DATES", 
                    "Day " + days.get(i).getDayNumber() + " is not continuous with previous day");
            }
        }
    }

    @Transactional(readOnly = true)
    public void validateSequence(UUID itineraryId) {
        Itinerary itinerary = getItineraryById(itineraryId);
        
        List<ItineraryDay> days = itinerary.getDays();
        if (days != null && !days.isEmpty()) {
            days.sort((d1, d2) -> d1.getDayNumber().compareTo(d2.getDayNumber()));
            
            for (int i = 0; i < days.size(); i++) {
                if (days.get(i).getDayNumber() != i + 1) {
                    throw new BusinessException("INVALID_DAY_SEQUENCE", 
                        "Day numbers must be sequential starting from 1");
                }
            }
        }
    }

    @Transactional
    public ItineraryResponse publish(UUID itineraryId) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (itinerary.getStatus() != ItineraryStatus.APPROVED) {
            throw new BusinessException("INVALID_STATUS", "Only approved itineraries can be published");
        }

        validateStructure(itineraryId);

        itinerary.setStatus(ItineraryStatus.OPEN_FOR_SALE);
        itinerary = itineraryRepository.save(itinerary);
        log.info("Published itinerary: {}", itineraryId);
        return mapToResponse(itinerary);
    }

    @Transactional
    public ItineraryResponse cancel(UUID itineraryId) {
        Itinerary itinerary = getItineraryById(itineraryId);

        if (itinerary.getStatus() == ItineraryStatus.COMPLETED) {
            throw new BusinessException("CANNOT_CANCEL_COMPLETED", "Completed itineraries cannot be cancelled");
        }

        if (itinerary.getStatus() == ItineraryStatus.CANCELLED) {
            return mapToResponse(itinerary);
        }

        itinerary.setStatus(ItineraryStatus.CANCELLED);
        itinerary = itineraryRepository.save(itinerary);
        log.info("Cancelled itinerary: {}", itineraryId);
        return mapToResponse(itinerary);
    }

    @Transactional(readOnly = true)
    public List<ItineraryDayResponse> listDays(UUID itineraryId) {
        Itinerary itinerary = getItineraryById(itineraryId);
        return itinerary.getDays().stream()
                .sorted((d1, d2) -> d1.getDayNumber().compareTo(d2.getDayNumber()))
                .map(this::mapToDayResponse)
                .collect(Collectors.toList());
    }

    private Itinerary getItineraryById(UUID itineraryId) {
        return itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary", itineraryId));
    }

    private ItineraryResponse mapToResponse(Itinerary itinerary) {
        ItineraryResponse res = new ItineraryResponse();
        res.setItineraryId(itinerary.getItineraryId());
        res.setCruiseId(itinerary.getCruise() != null ? itinerary.getCruise().getCruiseId() : null);
        res.setCruiseCode(itinerary.getCruise() != null ? itinerary.getCruise().getCruiseCode() : null);
        res.setItineraryName(itinerary.getItineraryName());
        res.setStartDate(itinerary.getStartDate());
        res.setEndDate(itinerary.getEndDate());
        res.setItineraryType(itinerary.getItineraryType());
        res.setStatus(itinerary.getStatus());
        res.setApprovedByUserId(itinerary.getApprovedByUser() != null ? itinerary.getApprovedByUser().getUserId() : null);
        res.setApprovedAt(itinerary.getApprovedAt());
        res.setCreatedByUserId(itinerary.getCreatedByUser() != null ? itinerary.getCreatedByUser().getUserId() : null);
        res.setCreatedAt(itinerary.getCreatedAt());
        res.setUpdatedAt(itinerary.getUpdatedAt());
        
        if (itinerary.getDays() != null) {
            res.setDays(itinerary.getDays().stream()
                    .sorted((d1, d2) -> d1.getDayNumber().compareTo(d2.getDayNumber()))
                    .map(this::mapToDayResponse)
                    .collect(Collectors.toList()));
        }
        return res;
    }

    private ItineraryDayResponse mapToDayResponse(ItineraryDay day) {
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
