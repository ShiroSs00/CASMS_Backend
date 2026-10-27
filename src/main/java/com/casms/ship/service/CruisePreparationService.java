package com.casms.ship.service;

import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.repository.ServiceScheduleRepository;
import com.casms.ship.dto.request.CruisePreparationConfirmRequest;
import com.casms.ship.dto.request.CruisePreparationReturnRequest;
import com.casms.ship.dto.request.CruisePreparationSubmitRequest;
import com.casms.ship.dto.response.CruisePreparationResponse;
import com.casms.ship.dto.response.CruisePreparationSectionResponse;
import com.casms.ship.dto.response.CruisePreparationSectionResponse.PreparationCheckItemDto;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruisePreparationSection;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruisePreparationService {

    private final CruiseRepository cruiseRepository;
    private final CruisePreparationSectionRepository preparationSectionRepository;
    private final CruiseStaffRepository cruiseStaffRepository;
    private final DeckRepository deckRepository;
    private final CabinRepository cabinRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final ServiceScheduleRepository serviceScheduleRepository;
    private final OnboardActivityRepository onboardActivityRepository;

    private static final List<SectionTemplate> TEMPLATES = List.of(
            new SectionTemplate("TECHNICAL_NAUTICAL", "Kỹ thuật Hàng hải & Tàu", "SHIP_MANAGER"),
            new SectionTemplate("HOTEL_CABIN", "Khách sạn & Buồng phòng", "CRUISE_MANAGER"),
            new SectionTemplate("FOOD_BEVERAGE", "Ẩm thực & Dịch vụ F&B", "CRUISE_MANAGER"),
            new SectionTemplate("ENTERTAINMENT_SHORE", "Hoạt động Giải trí & Tour bờ", "CRUISE_MANAGER"),
            new SectionTemplate("SAFETY_CREW", "An toàn & Thuyền viên", "OPERATIONS_MANAGER")
    );

    private record SectionTemplate(String code, String name, String ownerRole) {}

    @Transactional
    public CruisePreparationResponse getPreparationDashboard(UUID cruiseId) {
        return getPreparationDashboard(cruiseId, null);
    }

    @Transactional
    public CruisePreparationResponse getPreparationDashboard(UUID cruiseId, CasmsPrincipal principal) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        ensureSectionsInitialized(cruise);

        List<CruisePreparationSection> sections = preparationSectionRepository.findByCruiseCruiseIdOrderBySectionCodeAsc(cruiseId);
        List<CruisePreparationSectionResponse> sectionResponses = new ArrayList<>();

        boolean allConfirmed = true;
        for (CruisePreparationSection section : sections) {
            if (!"CONFIRMED".equalsIgnoreCase(section.getStatus())) {
                allConfirmed = false;
            }
            List<PreparationCheckItemDto> checkItems = computeCheckItems(cruise, section.getSectionCode());
            sectionResponses.add(CruisePreparationSectionResponse.builder()
                    .sectionCode(section.getSectionCode())
                    .sectionName(section.getSectionName())
                    .ownerRole(section.getOwnerRole())
                    .status(section.getStatus())
                    .evidence(section.getEvidence())
                    .notes(section.getNotes())
                    .submittedByUserId(section.getSubmittedByUserId())
                    .submittedAt(section.getSubmittedAt())
                    .confirmedByUserId(section.getConfirmedByUserId())
                    .confirmedByUsername(section.getConfirmedByUsername())
                    .confirmedAt(section.getConfirmedAt())
                    .returnReason(section.getReturnReason())
                    .returnedAt(section.getReturnedAt())
                    .revision(section.getRevision())
                    .checkItems(checkItems)
                    .build());
        }

        String overallStatus = allConfirmed ? "CONFIRMED" :
                (sectionResponses.stream().allMatch(s -> "SUBMITTED".equals(s.getStatus()) || "CONFIRMED".equals(s.getStatus()))
                        ? "READY_FOR_SIGNOFF" : "INCOMPLETE");

        return CruisePreparationResponse.builder()
                .cruiseId(cruise.getCruiseId())
                .cruiseCode(cruise.getCruiseCode())
                .cruiseName(cruise.getCruiseName())
                .cruiseStatus(cruise.getStatus().name())
                .overallStatus(overallStatus)
                .allConfirmed(allConfirmed)
                .sections(sectionResponses)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Transactional
    public CruisePreparationSectionResponse submitSection(UUID cruiseId, String sectionCode,
                                                         CruisePreparationSubmitRequest request,
                                                         UUID actorId, Set<String> roles) {
        CruisePreparationSection section = preparationSectionRepository
                .findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId, sectionCode)
                .orElseThrow(() -> new ResourceNotFoundException("CruisePreparationSection", sectionCode));

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(section.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    String.format("Phân hệ chuẩn bị đã bị cập nhật (revision hiện tại: %d, mong đợi: %d)",
                            section.getRevision(), request.getExpectedRevision()));
        }

        // Kiểm tra phân quyền theo vai trò phụ trách phân hệ
        if (roles != null && !roles.isEmpty() && !roles.contains("ADMIN") && !roles.contains("OPERATIONS_MANAGER")
                && !roles.contains(section.getOwnerRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_SECTION_OWNERSHIP",
                    String.format("Chỉ vai trò phụ trách %s mới có quyền nộp xác nhận cho phân hệ %s",
                            section.getOwnerRole(), section.getSectionName()));
        }

        if ("CONFIRMED".equalsIgnoreCase(section.getStatus())) {
            throw new BusinessException("SECTION_ALREADY_CONFIRMED", "Phân hệ này đã được phê duyệt xác nhận (CONFIRMED)");
        }

        section.setStatus("SUBMITTED");
        section.setEvidence(request.getEvidence());
        if (request.getNotes() != null) {
            section.setNotes(request.getNotes());
        }
        section.setSubmittedByUserId(actorId);
        section.setSubmittedAt(Instant.now());
        section.setReturnReason(null);
        section.setReturnedAt(null);

        section = preparationSectionRepository.save(section);
        log.info("Submitted preparation section {} for cruise {} by user {}", sectionCode, cruiseId, actorId);
        return mapToResponse(section, computeCheckItems(section.getCruise(), sectionCode));
    }

    @Transactional
    public CruisePreparationSectionResponse confirmSection(UUID cruiseId, String sectionCode,
                                                          CruisePreparationConfirmRequest request,
                                                          UUID actorId, String actorUsername) {
        CruisePreparationSection section = preparationSectionRepository
                .findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId, sectionCode)
                .orElseThrow(() -> new ResourceNotFoundException("CruisePreparationSection", sectionCode));

        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(section.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    String.format("Phân hệ chuẩn bị đã bị cập nhật (revision hiện tại: %d, mong đợi: %d)",
                            section.getRevision(), request.getExpectedRevision()));
        }

        if (!"SUBMITTED".equalsIgnoreCase(section.getStatus())) {
            throw new BusinessException("SECTION_NOT_SUBMITTED",
                    "Chỉ có thể xác nhận phân hệ đang ở trạng thái SUBMITTED (hiện tại: " + section.getStatus() + ")");
        }

        // Maker-Checker: người nộp bằng chứng không được tự duyệt cho chính mình
        if (section.getSubmittedByUserId() != null && section.getSubmittedByUserId().equals(actorId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "Người nộp hồ sơ chuẩn bị không được tự phê duyệt xác nhận phân hệ này");
        }

        // BE-H05: Chặn confirm khi item bắt buộc chưa đạt tiêu chuẩn
        List<PreparationCheckItemDto> checkItems = computeCheckItems(section.getCruise(), sectionCode);
        List<String> failedRequired = checkItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequired()) && !item.isReady())
                .map(PreparationCheckItemDto::getItemName)
                .toList();
        if (!failedRequired.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SECTION_PREREQUISITES_NOT_MET",
                    "Không thể xác nhận phân hệ " + section.getSectionName() + " do còn hạng mục bắt buộc chưa đạt tiêu chuẩn: " + String.join(", ", failedRequired));
        }

        section.setStatus("CONFIRMED");
        section.setConfirmedByUserId(actorId);
        section.setConfirmedByUsername(actorUsername);
        section.setConfirmedAt(Instant.now());
        if (request != null && request.getNotes() != null) {
            String existingNotes = section.getNotes() != null ? section.getNotes() + " | " : "";
            section.setNotes(existingNotes + "Xác nhận: " + request.getNotes());
        }

        section = preparationSectionRepository.save(section);
        log.info("Confirmed preparation section {} for cruise {} by user {}", sectionCode, cruiseId, actorUsername);
        return mapToResponse(section, computeCheckItems(section.getCruise(), sectionCode));
    }

    @Transactional
    public CruisePreparationSectionResponse returnSection(UUID cruiseId, String sectionCode,
                                                         CruisePreparationReturnRequest request,
                                                         UUID actorId) {
        CruisePreparationSection section = preparationSectionRepository
                .findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId, sectionCode)
                .orElseThrow(() -> new ResourceNotFoundException("CruisePreparationSection", sectionCode));

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(section.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    String.format("Phân hệ chuẩn bị đã bị cập nhật (revision hiện tại: %d, mong đợi: %d)",
                            section.getRevision(), request.getExpectedRevision()));
        }

        if (!"SUBMITTED".equalsIgnoreCase(section.getStatus()) && !"CONFIRMED".equalsIgnoreCase(section.getStatus())) {
            throw new BusinessException("INVALID_SECTION_STATUS",
                    "Chỉ có thể trả lại phân hệ ở trạng thái SUBMITTED hoặc CONFIRMED (hiện tại: " + section.getStatus() + ")");
        }

        section.setStatus("RETURNED");
        section.setReturnedByUserId(actorId);
        section.setReturnReason(request.getReason());
        section.setReturnedAt(Instant.now());
        if (request.getNotes() != null) {
            section.setNotes(request.getNotes());
        }

        section = preparationSectionRepository.save(section);
        log.info("Returned preparation section {} for cruise {} by user {} with reason: {}",
                sectionCode, cruiseId, actorId, request.getReason());
        return mapToResponse(section, computeCheckItems(section.getCruise(), sectionCode));
    }

    @Transactional
    public void invalidateSignoffs(UUID cruiseId, String reason) {
        List<CruisePreparationSection> sections = preparationSectionRepository.findByCruiseCruiseIdOrderBySectionCodeAsc(cruiseId);
        for (CruisePreparationSection section : sections) {
            if ("CONFIRMED".equalsIgnoreCase(section.getStatus())) {
                section.setStatus("RETURNED");
                section.setReturnReason("Hủy xác nhận tự động do thay đổi thông tin chuyến đi: " + reason);
                section.setReturnedAt(Instant.now());
                preparationSectionRepository.save(section);
                log.info("Invalidated confirmation for preparation section {} on cruise {}", section.getSectionCode(), cruiseId);
            }
        }
    }

    private void ensureSectionsInitialized(Cruise cruise) {
        if (!preparationSectionRepository.existsByCruiseCruiseId(cruise.getCruiseId())) {
            for (SectionTemplate tpl : TEMPLATES) {
                CruisePreparationSection section = new CruisePreparationSection();
                section.setCruise(cruise);
                section.setSectionCode(tpl.code());
                section.setSectionName(tpl.name());
                section.setOwnerRole(tpl.ownerRole());
                section.setStatus("NOT_STARTED");
                section.setRevision(0L);
                preparationSectionRepository.save(section);
            }
            preparationSectionRepository.flush();
        }
    }

    private List<PreparationCheckItemDto> computeCheckItems(Cruise cruise, String sectionCode) {
        List<PreparationCheckItemDto> items = new ArrayList<>();
        UUID cruiseId = cruise.getCruiseId();
        Ship ship = cruise.getShip();

        switch (sectionCode) {
            case "TECHNICAL_NAUTICAL" -> {
                boolean shipAssigned = (ship != null);
                items.add(PreparationCheckItemDto.builder()
                        .itemCode("SHIP_ASSIGNED")
                        .itemName("Gán tàu hợp lệ")
                        .ready(shipAssigned)
                        .required(true)
                        .state(shipAssigned ? "PASSED" : "BLOCKED")
                        .evidence(shipAssigned ? "Đã gán: " + ship.getShipName() : "Chưa gán tàu")
                        .details(shipAssigned ? "Tàu ID: " + ship.getShipId() : "Chưa có tàu được gán")
                        .build());
                boolean shipActive = (ship != null && ship.getStatus() == ShipStatus.ACTIVE);
                items.add(PreparationCheckItemDto.builder()
                        .itemCode("SHIP_STATUS_ACTIVE")
                        .itemName("Tàu ở trạng thái ACTIVE")
                        .ready(shipActive)
                        .required(true)
                        .state(shipActive ? "PASSED" : "BLOCKED")
                        .evidence(ship != null ? "Trạng thái: " + ship.getStatus() : "Chưa có tàu")
                        .details(shipActive ? "Tàu đang hoạt động" : "Tàu không ở trạng thái ACTIVE")
                        .build());
                long decks = ship != null ? deckRepository.countByShipShipId(ship.getShipId()) : 0;
                long cabins = ship != null ? cabinRepository.countByShipShipId(ship.getShipId()) : 0;
                boolean configReady = decks > 0 && cabins > 0;
                items.add(PreparationCheckItemDto.builder()
                        .itemCode("SHIP_CONFIG")
                        .itemName("Cấu hình boong và cabin")
                        .ready(configReady)
                        .required(true)
                        .state(configReady ? "PASSED" : "BLOCKED")
                        .evidence("Tầng boong: " + decks + ", Cabin: " + cabins)
                        .details(configReady ? "Boong và phòng đã được cấu hình" : "Thiếu boong hoặc phòng")
                        .build());
            }
            case "HOTEL_CABIN" -> {
                long totalCabins = ship != null ? cabinRepository.countByShipShipId(ship.getShipId()) : 0;
                long availableCabins = ship != null ? cabinRepository.countByShipShipIdAndStatus(ship.getShipId(), CabinStatus.AVAILABLE) : 0;
                boolean cabinReady = totalCabins > 0 && availableCabins > 0;
                items.add(PreparationCheckItemDto.builder()
                        .itemCode("CABIN_INVENTORY")
                        .itemName("Kiểm kê buồng phòng khách sạn")
                        .ready(cabinReady)
                        .required(true)
                        .state(cabinReady ? "PASSED" : "BLOCKED")
                        .evidence(cabinReady ? "Khả dụng: " + availableCabins + "/" + totalCabins + " phòng" : "Không có phòng khả dụng trên tàu")
                        .details("Tổng phòng: " + totalCabins + ", Khả dụng: " + availableCabins)
                        .build());
            }
            case "FOOD_BEVERAGE" -> {
                long scheduleCount = serviceScheduleRepository.countByCruise_CruiseId(cruiseId);
                boolean hasFB = scheduleCount > 0;
                items.add(PreparationCheckItemDto.builder()
                        .itemCode("SERVING_WINDOWS")
                        .itemName("Khung giờ phục vụ ăn uống & dịch vụ")
                        .ready(hasFB)
                        .required(true)
                        .state(hasFB ? "PASSED" : "BLOCKED")
                        .evidence(hasFB ? "Đã cấu hình " + scheduleCount + " khung giờ phục vụ ẩm thực/dịch vụ" : "Chưa có khung giờ phục vụ dịch vụ/ẩm thực nào được cấu hình cho chuyến đi")
                        .details(hasFB ? "Đã đồng bộ lịch trình và khung giờ phục vụ" : "Yêu cầu cấu hình ServiceSchedule cho chuyến đi")
                        .build());
            }
            case "ENTERTAINMENT_SHORE" -> {
                long excursions = shoreExcursionRepository.countByCruiseId(cruiseId);
                long activities = onboardActivityRepository.countByCruise_CruiseId(cruiseId);
                boolean entReady = excursions > 0 || activities > 0;
                items.add(PreparationCheckItemDto.builder()
                        .itemCode("SHORE_EXCURSIONS")
                        .itemName("Hoạt động Giải trí & Tour bờ")
                        .ready(entReady)
                        .required(true)
                        .state(entReady ? "PASSED" : "BLOCKED")
                        .evidence(entReady ? "Tour bờ: " + excursions + ", Hoạt động onboard: " + activities : "Chưa cấu hình tour bờ hoặc hoạt động giải trí")
                        .details("Tour bờ liên kết: " + excursions + ", Hoạt động trên tàu: " + activities)
                        .build());
            }
            case "SAFETY_CREW" -> {
                boolean captainAssigned = cruiseStaffRepository.existsByCruise_CruiseIdAndRoleInCruiseAndStatus(
                        cruiseId, "CAPTAIN", "ACTIVE");
                long activeCrew = cruiseStaffRepository.countByCruise_CruiseIdAndStatus(cruiseId, "ACTIVE");
                boolean crewReady = captainAssigned && activeCrew >= 1;
                items.add(PreparationCheckItemDto.builder()
                        .itemCode("CAPTAIN_ASSIGNED")
                        .itemName("Phân công Thuyền trưởng & Thuyền viên")
                        .ready(crewReady)
                        .required(true)
                        .state(crewReady ? "PASSED" : "BLOCKED")
                        .evidence(captainAssigned ? "Thuyền trưởng đã phân công (Tổng thuyền viên hoạt động: " + activeCrew + ")" : "Chưa phân công Thuyền trưởng")
                        .details("Thuyền trưởng: " + (captainAssigned ? "SẴN SÀNG" : "CHƯA PHÂN CÔNG") + ", Thuyền viên: " + activeCrew)
                        .build());
            }
            default -> {}
        }
        return items;
    }

    private CruisePreparationSectionResponse mapToResponse(CruisePreparationSection section, List<PreparationCheckItemDto> checkItems) {
        return CruisePreparationSectionResponse.builder()
                .sectionCode(section.getSectionCode())
                .sectionName(section.getSectionName())
                .ownerRole(section.getOwnerRole())
                .status(section.getStatus())
                .evidence(section.getEvidence())
                .notes(section.getNotes())
                .submittedByUserId(section.getSubmittedByUserId())
                .submittedAt(section.getSubmittedAt())
                .confirmedByUserId(section.getConfirmedByUserId())
                .confirmedByUsername(section.getConfirmedByUsername())
                .confirmedAt(section.getConfirmedAt())
                .returnReason(section.getReturnReason())
                .returnedAt(section.getReturnedAt())
                .revision(section.getRevision())
                .checkItems(checkItems)
                .build();
    }
}
