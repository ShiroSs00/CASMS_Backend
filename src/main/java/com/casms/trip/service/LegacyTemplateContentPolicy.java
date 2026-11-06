package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.trip.dto.request.ScheduleDraftSaveRequest;
import com.casms.trip.dto.request.TripPlanEntryDraftDto;
import com.casms.trip.entity.TripPlanEntry;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripPlanEntryKind;

import org.springframework.http.HttpStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Keeps legacy service windows readable without allowing template writes or implicit copies. */
final class LegacyTemplateContentPolicy {
    private LegacyTemplateContentPolicy() {}

    static List<TripPlanEntry> legacyEntries(TripScheduleVersion version) {
        return version.getEntries().stream()
                .filter(e -> e.getKind() == TripPlanEntryKind.SERVING_WINDOW)
                .toList();
    }

    static void rejectLegacyClone(TripScheduleVersion source) {
        if (source != null && !legacyEntries(source).isEmpty()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "LEGACY_TEMPLATE_CLONE_BLOCKED",
                    "Nguồn có khung phục vụ lịch sử. Chưa thể nhân bản khi chưa có quyết định xử lý"
                        + " dữ liệu này.");
        }
    }

    static List<TripPlanEntry> protectSave(
            TripScheduleVersion version, ScheduleDraftSaveRequest request) {
        List<TripPlanEntry> legacy = legacyEntries(version);
        Set<String> keys = new HashSet<>();
        // Run before mutating any managed entity (even queries later in reconciliation may flush).
        for (TripPlanEntryDraftDto incoming :
                request.getEntries() == null
                        ? List.<TripPlanEntryDraftDto>of()
                        : request.getEntries()) {
            if (!keys.add(incoming.getClientKey())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_CLIENT_KEY",
                        "Khóa mục kế hoạch bị trùng lặp.");
            }
            TripPlanEntry old =
                    legacy.stream()
                            .filter(e -> Objects.equals(e.getClientKey(), incoming.getClientKey()))
                            .findFirst()
                            .orElse(null);
            if (incoming.getKind() == TripPlanEntryKind.SERVING_WINDOW) {
                if (old == null || !snapshot(old).equals(incoming)) throw readOnly();
            } else if (old != null) {
                throw readOnly(); // Reusing the key with another kind is also an edit.
            } else if (incoming.getServiceId() != null) {
                throw readOnly(); // A service window cannot be disguised as another entry kind.
            }
        }
        for (TripPlanEntry old : legacy) {
            if (old.getStop() != null
                    && (request.getStops() == null
                            || request.getStops().stream()
                                    .noneMatch(
                                            s ->
                                                    Objects.equals(
                                                            s.getClientKey(),
                                                            old.getStop().getClientKey())))) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "LEGACY_TEMPLATE_REFERENCE_LOCKED",
                        "Không thể xóa lần ghé cảng đang được khung phục vụ lịch sử tham chiếu.");
            }
        }
        return legacy; // Omission means preserve, never delete.
    }

    private static BusinessException readOnly() {
        return new BusinessException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "LEGACY_SERVING_WINDOW_READ_ONLY",
                "Khung phục vụ không được tạo hoặc sửa trong mẫu lịch trình. Dữ liệu lịch sử chỉ"
                    + " được đọc và giữ nguyên.");
    }

    private static TripPlanEntryDraftDto snapshot(TripPlanEntry old) {
        TripPlanEntryDraftDto dto = new TripPlanEntryDraftDto();
        dto.setClientKey(old.getClientKey());
        dto.setDayNumber(old.getDay().getDayNumber());
        dto.setKind(old.getKind());
        dto.setStopClientKey(old.getStop() == null ? null : old.getStop().getClientKey());
        dto.setSourceActivityId(old.getSourceActivityId());
        dto.setCategory(old.getCategory());
        dto.setSourceExcursionId(old.getSourceExcursionId());
        dto.setServiceId(old.getServiceId());
        dto.setSeriesClientKey(old.getSeriesClientKey());
        dto.setDestinationId(
                old.getDestination() == null ? null : old.getDestination().getDestinationId());
        dto.setTitle(old.getTitle());
        dto.setStartDayNumber(old.getStartDayNumber());
        dto.setPlannedStartTime(old.getPlannedStartTime());
        dto.setEndDayNumber(old.getEndDayNumber());
        dto.setPlannedEndTime(old.getPlannedEndTime());
        dto.setTimeZoneId(old.getTimeZoneId());
        dto.setLocationText(old.getLocationText());
        dto.setDeckId(old.getDeck() == null ? null : old.getDeck().getDeckId());
        dto.setCabinId(old.getCabin() == null ? null : old.getCabin().getCabinId());
        dto.setMeetingPoint(old.getMeetingPoint());
        dto.setNotes(old.getNotes());
        return dto;
    }
}
