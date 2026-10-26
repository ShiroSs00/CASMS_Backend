package com.casms.ship.service;

import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.CruiseChangeImpactRequest;
import com.casms.ship.dto.response.CruiseChangeImpactResponse;
import com.casms.ship.dto.response.CruiseImpactTaskResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseImpactTask;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruiseImpactTaskRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruiseImpactAssessmentService {

    private final CruiseRepository cruiseRepository;
    private final ShipRepository shipRepository;
    private final DeckRepository deckRepository;
    private final CabinRepository cabinRepository;
    private final BookingRepository bookingRepository;
    private final BookingPassengerRepository bookingPassengerRepository;
    private final OnboardActivityRepository onboardActivityRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final CruiseImpactTaskRepository cruiseImpactTaskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CruiseChangeImpactResponse previewChangeImpact(UUID cruiseId, CruiseChangeImpactRequest request) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException("STALE_REVISION",
                    String.format("Cruise revision đã thay đổi (hiện tại: %d, mong đợi: %d). Vui lòng tải lại trang trước khi tiếp tục.",
                            cruise.getRevision(), request.getExpectedRevision()));
        }

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<CruiseImpactTaskResponse> tasks = new ArrayList<>();

        boolean scheduleImpacted = false;
        long dateShiftDays = 0;

        Instant newDep = request.getNewDepartureDatetime();
        Instant newRet = request.getNewReturnDatetime();

        if (newDep != null) {
            if (cruise.getDepartureDatetime() != null) {
                Duration diff = Duration.between(cruise.getDepartureDatetime(), newDep);
                if (Math.abs(diff.toMillis()) > 1000) {
                    scheduleImpacted = true;
                    dateShiftDays = Math.round((double) diff.toMillis() / 86400000.0);
                }
            } else {
                scheduleImpacted = true;
            }
            if (newRet != null && !newRet.isAfter(newDep)) {
                blockers.add("Thời điểm trở về mới phải sau thời điểm khởi hành mới");
            }
        }

        long bookedPassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);
        long affectedBookings = bookingRepository.countByCruiseCruiseId(cruiseId);
        long affectedActivities = onboardActivityRepository.countByCruise_CruiseId(cruiseId);
        long affectedExcursions = shoreExcursionRepository.countByCruiseId(cruiseId);

        boolean shipImpacted = false;
        UUID newShipId = request.getNewShipId();

        if (newShipId != null && (cruise.getShip() == null || !cruise.getShip().getShipId().equals(newShipId))) {
            shipImpacted = true;
            Ship newShip = shipRepository.findById(newShipId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ship", newShipId));

            if (newShip.getStatus() != ShipStatus.ACTIVE) {
                blockers.add("Tàu mới không ở trạng thái ACTIVE (hiện tại: " + newShip.getStatus() + ")");
            }

            Instant effectiveDep = (newDep != null ? newDep : cruise.getDepartureDatetime());
            Instant effectiveRet = (newRet != null ? newRet : cruise.getReturnDatetime());
            if (effectiveDep != null && effectiveRet != null &&
                    cruiseRepository.existsOverlappingCruisesExcluding(newShipId, cruiseId, effectiveDep, effectiveRet)) {
                blockers.add("Tàu mới bị trùng lịch vận hành với chuyến đi khác trong khoảng thời gian này");
            }

            if (newShip.getTotalCapacity() < bookedPassengers) {
                blockers.add(String.format("Sức chứa tàu mới (%d) nhỏ hơn số khách đã đặt (%d)",
                        newShip.getTotalCapacity(), bookedPassengers));
            }

            long decks = deckRepository.countByShipShipId(newShipId);
            long cabins = cabinRepository.countByShipShipId(newShipId);
            if (decks == 0 || cabins == 0) {
                blockers.add("Tàu mới chưa được cấu hình đầy đủ boong hoặc phòng");
            }

            if (bookedPassengers > 0) {
                warnings.add("Cần phân bổ lại phòng cho " + bookedPassengers + " khách đã đặt khi đổi tàu");
                tasks.add(CruiseImpactTaskResponse.builder()
                        .cruiseId(cruiseId)
                        .taskCode("TASK-" + cruise.getCruiseCode() + "-REASSIGN-CABINS")
                        .category("CABIN_REASSIGNMENT")
                        .title("Phân bổ lại phòng sau khi đổi tàu")
                        .description("Tàu được đổi từ " + (cruise.getShip() != null ? cruise.getShip().getShipName() : "None") + " sang " + newShip.getShipName() + ". Cần xếp lại phòng cho khách đã đặt.")
                        .status("PENDING")
                        .severity("BLOCKING")
                        .evidence("Số khách bị ảnh hưởng: " + bookedPassengers)
                        .revision(1L)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
            }

            long activitiesOnDeck = onboardActivityRepository.countByCruise_CruiseIdAndDeckIsNotNull(cruiseId);
            if (activitiesOnDeck > 0) {
                warnings.add("Có " + activitiesOnDeck + " hoạt động trên tàu cần định cấu hình lại vị trí tầng/phòng trên tàu mới");
            }
        }

        if (scheduleImpacted && bookedPassengers > 0) {
            warnings.add("Lịch khởi hành thay đổi " + dateShiftDays + " ngày ảnh hưởng tới " + bookedPassengers + " khách đã đặt");
            tasks.add(CruiseImpactTaskResponse.builder()
                    .cruiseId(cruiseId)
                    .taskCode("TASK-" + cruise.getCruiseCode() + "-NOTIFY-SCHEDULE")
                    .category("PASSENGER_NOTIFICATION")
                    .title("Thông báo dời lịch khởi hành cho hành khách")
                    .description("Chuyến đi thay đổi ngày khởi hành từ " + cruise.getDepartureDatetime() + " sang " + newDep + ". Cần liên hệ thông báo hành khách.")
                    .status("PENDING")
                    .severity("WARNING")
                    .evidence("Số booking: " + affectedBookings + ", Số hành khách: " + bookedPassengers)
                    .revision(1L)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
        }

        if (scheduleImpacted && affectedExcursions > 0) {
            warnings.add("Có " + affectedExcursions + " tour tham quan cần điều chỉnh lại giờ đón/trả với nhà cung cấp");
            tasks.add(CruiseImpactTaskResponse.builder()
                    .cruiseId(cruiseId)
                    .taskCode("TASK-" + cruise.getCruiseCode() + "-RESCHEDULE-EXCURSIONS")
                    .category("EXCURSION_RESCHEDULE")
                    .title("Xác nhận lại lịch tour tham quan với nhà cung cấp")
                    .description("Lịch ghé cảng thay đổi, cần xác nhận lại dịch vụ và thời gian đón với đối tác tour.")
                    .status("PENDING")
                    .severity("WARNING")
                    .evidence("Số tour bị ảnh hưởng: " + affectedExcursions)
                    .revision(1L)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
        }

        boolean canProceed = blockers.isEmpty();

        return CruiseChangeImpactResponse.builder()
                .cruiseId(cruiseId)
                .currentRevision(cruise.getRevision())
                .scheduleImpacted(scheduleImpacted)
                .dateShiftDays(dateShiftDays)
                .shipImpacted(shipImpacted)
                .bookedPassengersCount(bookedPassengers)
                .affectedBookingsCount(affectedBookings)
                .affectedActivitiesCount(affectedActivities)
                .affectedExcursionsCount(affectedExcursions)
                .blockers(blockers)
                .warnings(warnings)
                .generatedTasks(tasks)
                .canProceed(canProceed)
                .build();
    }

    @Transactional
    public List<CruiseImpactTaskResponse> generateAndPersistImpactTasks(UUID cruiseId, CruiseChangeImpactRequest request) {
        CruiseChangeImpactResponse preview = previewChangeImpact(cruiseId, request);
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        List<CruiseImpactTaskResponse> persisted = new ArrayList<>();
        for (CruiseImpactTaskResponse taskDto : preview.getGeneratedTasks()) {
            CruiseImpactTask task = cruiseImpactTaskRepository.findByTaskCode(taskDto.getTaskCode())
                    .orElseGet(CruiseImpactTask::new);

            task.setCruise(cruise);
            task.setTaskCode(taskDto.getTaskCode());
            task.setCategory(taskDto.getCategory());
            task.setTitle(taskDto.getTitle());
            task.setDescription(taskDto.getDescription());
            if (task.getStatus() == null) {
                task.setStatus("PENDING");
            }
            task.setSeverity(taskDto.getSeverity());
            task.setEvidence(taskDto.getEvidence());

            CruiseImpactTask saved = cruiseImpactTaskRepository.save(task);
            persisted.add(toTaskResponse(saved));
        }

        return persisted;
    }

    @Transactional(readOnly = true)
    public List<CruiseImpactTaskResponse> listTasks(UUID cruiseId, String status) {
        if (!cruiseRepository.existsById(cruiseId)) {
            throw new ResourceNotFoundException("Cruise", cruiseId);
        }

        List<CruiseImpactTask> tasks = (status != null && !status.isBlank())
                ? cruiseImpactTaskRepository.findByCruiseCruiseIdAndStatusOrderByCreatedAtDesc(cruiseId, status.trim().toUpperCase())
                : cruiseImpactTaskRepository.findByCruiseCruiseIdOrderByCreatedAtDesc(cruiseId);

        return tasks.stream().map(this::toTaskResponse).toList();
    }

    @Transactional
    public CruiseImpactTaskResponse resolveTask(UUID taskId, String resolutionNotes, UUID actorId) {
        CruiseImpactTask task = cruiseImpactTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("CruiseImpactTask", taskId));

        task.setStatus("RESOLVED");
        task.setResolutionNotes(resolutionNotes);

        if (actorId != null) {
            userRepository.findById(actorId).ifPresent(task::setAssignedTo);
        }

        CruiseImpactTask saved = cruiseImpactTaskRepository.save(task);
        return toTaskResponse(saved);
    }

    private CruiseImpactTaskResponse toTaskResponse(CruiseImpactTask task) {
        return CruiseImpactTaskResponse.builder()
                .taskId(task.getTaskId())
                .cruiseId(task.getCruise().getCruiseId())
                .taskCode(task.getTaskCode())
                .category(task.getCategory())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .severity(task.getSeverity())
                .assignedToUserId(task.getAssignedTo() != null ? task.getAssignedTo().getUserId() : null)
                .assignedToUsername(task.getAssignedTo() != null ? task.getAssignedTo().getUsername() : null)
                .evidence(task.getEvidence())
                .resolutionNotes(task.getResolutionNotes())
                .revision(task.getRevision())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
