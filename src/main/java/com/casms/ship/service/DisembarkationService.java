package com.casms.ship.service;

import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStopRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisembarkationService {
    private final CruiseRepository cruises;
    private final CruiseStopRepository stops;
    private final ShoreExcursionRepository excursions;

    public record Input(@NotNull UUID stopId, @NotNull Instant expectedArrival,
                        @NotNull Long expectedRevision, @NotNull Instant startsAt,
                        @NotNull @Positive Integer durationMinutes) {}
    public record Plan(UUID stopId, String portName, String timeZone, Instant arrival, Instant scheduledEnd,
                       Instant startsAt, Integer durationMinutes, Instant endsAt, long revision,
                       boolean canEdit, boolean needsUpdate, Integer expectedPassengerCount) {}

    @Transactional(readOnly = true)
    public Plan get(UUID cruiseId, boolean canManage) {
        var cruise = cruises.findById(cruiseId).orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        var stop = finalStop(cruise);
        return stop == null ? null : view(cruise, stop, canManage);
    }

    @Transactional
    public Plan save(UUID cruiseId, Input input) {
        var cruise = cruises.findByIdForUpdate(cruiseId).orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        var last = finalStop(cruise);
        if (!editable(cruise) || last == null || !last.getCruiseStopId().equals(input.stopId())) {
            throw conflict("DISEMBARKATION_STOP_CHANGED", "Cảng kết thúc hoặc trạng thái tour đã thay đổi. Tải lại lịch để tiếp tục.");
        }
        var stop = stops.findByIdForUpdate(input.stopId()).orElseThrow(() -> new ResourceNotFoundException("Cruise stop", input.stopId()));
        if (!Objects.equals(stop.getPlannedArrival(), input.expectedArrival()) ||
                stop.getDisembarkationRevision() != input.expectedRevision()) {
            throw conflict("DISEMBARKATION_STALE", "Lịch hoặc khung trả khách vừa thay đổi. Tải lại trước khi lưu.");
        }
        var starts = input.startsAt();
        var arrival = stop.getPlannedArrival();
        var end = stop.getPlannedDeparture();
        if (starts.isBefore(arrival) || !starts.isBefore(end)
                || starts.plusSeconds(input.durationMinutes() * 60L).isAfter(end)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DISEMBARKATION_OUTSIDE_WINDOW",
                    "Khung trả khách phải nằm giữa giờ tàu cập cảng và giờ kết thúc đã lên lịch.");
        }
        boolean overlaps = excursions.findByCruiseStop_CruiseStopId(stop.getCruiseStopId()).stream()
                .anyMatch(excursion -> !"CANCELLED".equals(excursion.getStatus().name())
                        && excursion.getPlannedReturnTime() != null && excursion.getPlannedReturnTime().isAfter(starts));
        if (overlaps) throw conflict("DISEMBARKATION_EXCURSION_OVERLAP",
                "Tour bờ ở cảng cuối vẫn đang diễn ra khi bắt đầu trả khách. Cập nhật giờ tour bờ hoặc giờ trả khách.");
        stop.setDisembarkationStartsAt(starts);
        stop.setDisembarkationMinutes(input.durationMinutes());
        stop.setDisembarkationPlannedArrival(arrival);
        stop.setDisembarkationRevision(stop.getDisembarkationRevision() + 1);
        return view(cruise, stop, true);
    }

    private CruiseStop finalStop(Cruise cruise) {
        return stops.findFirstByItineraryCruiseCruiseIdOrderByStopOrderDesc(cruise.getCruiseId())
                .filter(stop -> cruise.getReturnPort() != null && stop.getPort() != null
                        && cruise.getReturnPort().getPortId().equals(stop.getPort().getPortId()))
                .orElse(null);
    }

    private Plan view(Cruise cruise, CruiseStop stop, boolean canManage) {
        var starts = stop.validDisembarkationStart();
        var minutes = starts == null ? null : stop.getDisembarkationMinutes();
        var facts = stop.getPortFacts();
        return new Plan(stop.getCruiseStopId(), stop.getPort().getPortName(),
                facts == null ? null : facts.timeZoneId(), stop.getPlannedArrival(), stop.getPlannedDeparture(),
                starts, minutes, starts == null ? null : starts.plusSeconds(minutes * 60L),
                stop.getDisembarkationRevision(), canManage && editable(cruise),
                starts == null && stop.getDisembarkationStartsAt() != null, cruise.getExpectedPassengerCount());
    }

    private boolean editable(Cruise cruise) {
        return cruise.getStatus() != CruiseStatus.CANCELLED && cruise.getStatus() != CruiseStatus.COMPLETED;
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
