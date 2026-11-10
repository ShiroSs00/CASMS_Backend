package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.service.ItineraryProgramService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/itineraries/{itineraryId}/programs")
@RequiredArgsConstructor
public class ItineraryProgramController {
    private final ItineraryProgramService service;
    public record ShoreSelection(@NotNull UUID sourceExcursionId, @NotNull UUID cruiseStopId, @NotNull UUID destinationId) {}
    public record AssignRequest(@NotNull @Size(max=100) List<@NotNull UUID> activityIds,
                                @NotNull @Size(max=100) List<@NotNull @Valid ShoreSelection> excursions) {
        @AssertTrue(message="Choose at least one activity or excursion")
        public boolean isNonEmpty() { return activityIds == null || excursions == null || !activityIds.isEmpty() || !excursions.isEmpty(); }
    }
    public record ActivitySchedule(@NotNull Instant startsAt, @NotNull Instant endsAt, @NotNull UUID areaId,
                                    @NotBlank @Size(max=250) String location, @NotNull @Positive Integer capacity,
                                    @NotNull @DecimalMin("0.0") @Digits(integer=15,fraction=4) BigDecimal price) {}
    public record ExcursionSchedule(@NotNull Instant startsAt, @NotNull Instant returnsAt,
                                     @NotBlank @Size(max=250) String meetingPoint, @NotBlank @Size(max=255) String location,
                                     @NotNull @Positive Integer capacity, @NotNull @DecimalMin("0.0") @Digits(integer=15,fraction=4) BigDecimal price) {}

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ITINERARY_READ','CRUISE_READ')")
    public ApiResponse<Map<String,Object>> read(@PathVariable UUID itineraryId) {
        return ApiResponse.success(service.read(itineraryId));
    }

    @PostMapping("/assignments")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<ItineraryProgramService.AssignmentResult> assign(@PathVariable UUID itineraryId,
            @RequestHeader("If-Match") Long expected, @RequestBody @Valid AssignRequest input) {
        return ApiResponse.success(service.assign(itineraryId, expected, input.activityIds(), input.excursions().stream()
                .map(e -> new ItineraryProgramService.ShoreSelection(e.sourceExcursionId(),e.cruiseStopId(),e.destinationId())).toList()));
    }

    @PutMapping("/activities/{activityId}/schedule")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<Long> activity(@PathVariable UUID itineraryId, @PathVariable UUID activityId,
            @RequestHeader("If-Match") Long expected, @RequestBody @Valid ActivitySchedule input) {
        return ApiResponse.success(service.scheduleActivity(itineraryId, activityId, expected,
                new ItineraryProgramService.ActivitySchedule(input.startsAt(),input.endsAt(),input.areaId(),input.location(),input.capacity(),input.price())));
    }

    @PutMapping("/excursions/{excursionId}/schedule")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<Long> excursion(@PathVariable UUID itineraryId, @PathVariable UUID excursionId,
            @RequestHeader("If-Match") Long expected, @RequestBody @Valid ExcursionSchedule input) {
        return ApiResponse.success(service.scheduleExcursion(itineraryId, excursionId, expected,
                new ItineraryProgramService.ExcursionSchedule(input.startsAt(),input.returnsAt(),input.meetingPoint(),input.location(),input.capacity(),input.price())));
    }

    @DeleteMapping("/{kind:activities|excursions}/{programId}")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<Long> remove(@PathVariable UUID itineraryId, @PathVariable String kind,
            @PathVariable UUID programId, @RequestHeader("If-Match") Long expected) {
        return ApiResponse.success(service.remove(itineraryId, kind, programId, expected));
    }
}
