package com.casms.ship.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.response.OperationalProgramSyncResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.trip.entity.TripPlanEntry;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripPlanEntryKind;
import com.casms.trip.repository.TripPlanEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalProgramMappingService {

    private final CruiseRepository cruiseRepository;
    private final TripPlanEntryRepository tripPlanEntryRepository;
    private final ItineraryRepository itineraryRepository;
    private final CruiseStopRepository cruiseStopRepository;
    private final OnboardActivityRepository onboardActivityRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final SupplierRepository supplierRepository;

    @Transactional
    public OperationalProgramSyncResponse syncProgramsForCruise(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (cruise.getSourceVersion() == null) {
            log.info("Cruise {} has no source TripScheduleVersion, skipping program mapping", cruiseId);
            return OperationalProgramSyncResponse.builder()
                    .cruiseId(cruiseId)
                    .onboardActivitiesCount(0)
                    .shoreExcursionsCount(0)
                    .warnings(List.of("Chuyến đi không có phiên bản nguồn (sourceVersion)"))
                    .syncedAt(Instant.now())
                    .build();
        }

        return mapOperationalPrograms(cruise, cruise.getSourceVersion());
    }

    @Transactional
    public OperationalProgramSyncResponse mapOperationalPrograms(Cruise cruise, TripScheduleVersion sourceVersion) {
        List<TripPlanEntry> entries = tripPlanEntryRepository
                .findByVersion_VersionIdOrderByStartDayNumberAscPlannedStartTimeAsc(sourceVersion.getVersionId());

        if (cruise.getDepartureDatetime() == null) {
            log.warn("Cruise {} has no departureDatetime, skipping program mapping", cruise.getCruiseId());
            return OperationalProgramSyncResponse.builder()
                    .cruiseId(cruise.getCruiseId())
                    .onboardActivitiesCount(0)
                    .shoreExcursionsCount(0)
                    .warnings(List.of("Chuyến đi chưa được thiết lập thời điểm khởi hành (departureDatetime)"))
                    .syncedAt(Instant.now())
                    .build();
        }

        List<String> warnings = new ArrayList<>();
        int activitiesCount = 0;
        int excursionsCount = 0;

        LocalDate baseDate = cruise.getDepartureDatetime().atZone(ZoneOffset.UTC).toLocalDate();

        // Load cruise itinerary and stops for shore excursion linking
        Optional<Itinerary> itineraryOpt = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId());
        List<CruiseStop> stops = itineraryOpt.isPresent()
                ? cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itineraryOpt.get().getItineraryId())
                : List.of();

        for (TripPlanEntry entry : entries) {
            LocalDate startDate = baseDate.plusDays(Math.max(0, entry.getStartDayNumber() != null ? entry.getStartDayNumber() - 1 : 0));
            LocalDate endDate = baseDate.plusDays(Math.max(0, entry.getEndDayNumber() != null ? entry.getEndDayNumber() - 1 : 0));
            java.time.LocalTime startTimeVal = entry.getPlannedStartTime() != null ? entry.getPlannedStartTime() : java.time.LocalTime.of(8, 0);
            java.time.LocalTime endTimeVal = entry.getPlannedEndTime() != null ? entry.getPlannedEndTime() : startTimeVal.plusHours(2);
            Instant startTime = startDate.atTime(startTimeVal).atZone(ZoneOffset.UTC).toInstant();
            Instant endTime = endDate.atTime(endTimeVal).atZone(ZoneOffset.UTC).toInstant();

            if (entry.getKind() == TripPlanEntryKind.ONBOARD_ACTIVITY || entry.getKind() == TripPlanEntryKind.SERVING_WINDOW) {
                String rawCode = "OA-" + cruise.getCruiseCode() + "-" + entry.getClientKey();
                String activityCode = rawCode.length() > 50 ? rawCode.substring(0, 50) : rawCode;

                Optional<OnboardActivity> existingOpt = onboardActivityRepository.findByActivityCode(activityCode);
                OnboardActivity activity = existingOpt.orElseGet(OnboardActivity::new);

                activity.setCruise(cruise);
                activity.setActivityCode(activityCode);
                activity.setActivityName(entry.getTitle());
                activity.setCategory(entry.getCategory() != null ? entry.getCategory() : OnboardActivityCategory.ENTERTAINMENT);
                activity.setDescription(entry.getNotes());
                activity.setStartTime(startTime);
                activity.setEndTime(endTime);
                activity.setLocation(entry.getLocationText() != null ? entry.getLocationText() :
                        (entry.getDeck() != null ? "Deck " + entry.getDeck().getDeckNumber() : "Onboard"));

                // Link deck and cabin if belonging to assigned ship
                if (cruise.getShip() != null && entry.getDeck() != null && entry.getDeck().getShip() != null &&
                        entry.getDeck().getShip().getShipId().equals(cruise.getShip().getShipId())) {
                    activity.setDeck(entry.getDeck());
                } else {
                    activity.setDeck(null);
                }

                if (cruise.getShip() != null && entry.getCabin() != null && entry.getCabin().getShip() != null &&
                        entry.getCabin().getShip().getShipId().equals(cruise.getShip().getShipId())) {
                    activity.setCabin(entry.getCabin());
                } else {
                    activity.setCabin(null);
                }

                if (activity.getCapacity() == null) {
                    activity.setCapacity(50);
                }
                if (activity.getPrice() == null) {
                    activity.setPrice(BigDecimal.ZERO);
                }
                if (activity.getCurrencyCode() == null) {
                    activity.setCurrencyCode("VND");
                }
                if (activity.getStatus() == null) {
                    activity.setStatus(OnboardActivityStatus.PLANNED);
                }

                onboardActivityRepository.save(activity);
                activitiesCount++;

            } else if (entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                // Find matching CruiseStop
                CruiseStop matchedStop = null;
                if (entry.getStop() != null) {
                    int targetOrder = entry.getStop().getStopOrder();
                    matchedStop = stops.stream()
                            .filter(s -> s.getStopOrder() == targetOrder)
                            .findFirst()
                            .orElse(null);
                }

                if (matchedStop == null && !stops.isEmpty()) {
                    matchedStop = stops.get(0);
                }

                if (matchedStop == null) {
                    warnings.add("Không tìm thấy điểm dừng hải trình tương ứng cho tour: " + entry.getTitle());
                    continue;
                }

                String rawCode = "EX-" + cruise.getCruiseCode() + "-" + entry.getClientKey();
                String excursionCode = rawCode.length() > 50 ? rawCode.substring(0, 50) : rawCode;

                Optional<ShoreExcursion> existingOpt = shoreExcursionRepository.findByExcursionCode(excursionCode);
                ShoreExcursion excursion = existingOpt.orElseGet(ShoreExcursion::new);

                excursion.setCruiseStop(matchedStop);
                excursion.setPort(matchedStop.getPort());
                excursion.setExcursionCode(excursionCode);
                excursion.setExcursionName(entry.getTitle());
                excursion.setStartTime(startTime);
                excursion.setPlannedReturnTime(endTime);
                excursion.setMeetingPoint(entry.getMeetingPoint() != null ? entry.getMeetingPoint() : "Port Terminal Gate");

                if (excursion.getSupplier() == null) {
                    Supplier supplier = getOrCreateActiveSupplier();
                    excursion.setSupplier(supplier);
                }

                if (excursion.getMinimumParticipants() == null) {
                    excursion.setMinimumParticipants(5);
                }
                if (excursion.getMaximumCapacity() == null) {
                    excursion.setMaximumCapacity(50);
                }
                if (excursion.getPrice() == null) {
                    excursion.setPrice(BigDecimal.ZERO);
                }
                if (excursion.getCurrencyCode() == null) {
                    excursion.setCurrencyCode("VND");
                }
                if (excursion.getStatus() == null) {
                    excursion.setStatus(ShoreExcursionStatus.PLANNED);
                }

                shoreExcursionRepository.save(excursion);
                excursionsCount++;
            }
        }

        log.info("Mapped operational programs for cruise {}: {} activities, {} excursions",
                cruise.getCruiseId(), activitiesCount, excursionsCount);

        return OperationalProgramSyncResponse.builder()
                .cruiseId(cruise.getCruiseId())
                .onboardActivitiesCount(activitiesCount)
                .shoreExcursionsCount(excursionsCount)
                .warnings(warnings)
                .syncedAt(Instant.now())
                .build();
    }

    private Supplier getOrCreateActiveSupplier() {
        return supplierRepository.findAll().stream()
                .filter(s -> s.getStatus() == SupplierStatus.ACTIVE)
                .findFirst()
                .orElseGet(() -> {
                    Supplier s = new Supplier();
                    s.setSupplierName("OCV Default Shore Supplier");
                    s.setContactName("Operations Department");
                    s.setStatus(SupplierStatus.ACTIVE);
                    s.setVerificationStatus(SupplierVerificationStatus.VERIFIED);
                    return supplierRepository.save(s);
                });
    }
}
