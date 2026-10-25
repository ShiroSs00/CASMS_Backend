package com.casms.activity.controller;

import com.casms.activity.entity.*;
import com.casms.activity.service.ActivityService;
import com.casms.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
@Tag(name = "Activity Management", description = "Quản lý hoạt động trên tàu, đăng ký tham gia và điểm danh hành khách")
public class ActivityController {
    private final ActivityService service;

    @Schema(name = "ActivityRequest", description = "Thông tin tạo hoặc cập nhật hoạt động trên tàu")
    public record ActivityRequest(
            @Schema(description = "Tên hoạt động", example = "Yoga buổi sáng") @NotBlank @jakarta.validation.constraints.Size(max=200) String activityName,
            @Schema(description = "Mô tả hoạt động") @jakarta.validation.constraints.Size(max=1000) String description,
            @Schema(description = "Thời gian bắt đầu (ISO-8601)") @NotNull Instant startTime,
            @Schema(description = "Thời gian kết thúc (ISO-8601)") @NotNull Instant endTime,
            @Schema(description = "Địa điểm tổ chức (text)") @jakarta.validation.constraints.Size(max=250) String location,
            @Schema(description = "ID tầng (deck) nơi diễn ra — VD: tầng có spa, nhà hàng") UUID deckId,
            @Schema(description = "ID phòng/cabin cụ thể nơi diễn ra — VD: phòng spa 201") UUID cabinId,
            @Schema(description = "Sức chứa tối đa", example = "30") @NotNull @Positive Integer capacity,
            @Schema(description = "Giá tham gia (mặc định 0.0)") @DecimalMin("0.0") BigDecimal price,
            @Schema(description = "Mã tiền tệ gồm 3 ký tự (mặc định USD)", example = "USD") @jakarta.validation.constraints.Size(min=3,max=3) String currencyCode,
            @Schema(description = "ID chuyến du thuyền") @NotNull UUID cruiseId) {}
    @Schema(name = "ActivityRegistrationRequest", description = "Thông tin đăng ký hoạt động hoặc tour tham quan")
    public record RegistrationRequest(@Schema(description = "ID hành khách trong booking") @NotNull UUID bookingPassengerId,
            @Schema(description = "Số lượng chỗ đăng ký", example = "1") @Positive Integer quantity) {}
    @Schema(name = "ActivityRegistrationUpdateRequest", description = "Thông tin cập nhật đăng ký đang chờ xử lý")
    public record RegistrationUpdateRequest(@Schema(description = "Số lượng chỗ đăng ký mới") @Positive Integer quantity,
            @Schema(description = "Ghi chú") @jakarta.validation.constraints.Size(max=500) String notes) {}
    public record ActivityView(UUID id, String code, String name, String description, Instant startTime, Instant endTime,
            String location,
            UUID deckId, Integer deckNumber, String deckName,
            UUID cabinId, String cabinNumber,
            Integer capacity, BigDecimal price, String currencyCode, String status, UUID cruiseId) {
        static ActivityView of(OnboardActivity a) {
            return new ActivityView(
                    a.getOnboardActivityId(), a.getActivityCode(), a.getActivityName(), a.getDescription(),
                    a.getStartTime(), a.getEndTime(), a.getLocation(),
                    a.getDeck() != null ? a.getDeck().getDeckId() : null,
                    a.getDeck() != null ? a.getDeck().getDeckNumber() : null,
                    a.getDeck() != null ? a.getDeck().getDeckName() : null,
                    a.getCabin() != null ? a.getCabin().getCabinId() : null,
                    a.getCabin() != null ? a.getCabin().getCabinNumber() : null,
                    a.getCapacity(), a.getPrice(), a.getCurrencyCode(), a.getStatus().name(), a.getCruise().getCruiseId());
        }
    }
    public record RegistrationView(UUID id, UUID passengerId, UUID bookingPassengerId, UUID activityId, UUID excursionId, Integer quantity, String status, String notes, Instant registeredAt) {
        static RegistrationView of(Registration r) { return new RegistrationView(r.getRegistrationId(), r.getPassenger().getPassengerId(), r.getBookingPassenger().getBookingPassengerId(), r.getOnboardActivity()==null?null:r.getOnboardActivity().getOnboardActivityId(), r.getShoreExcursion()==null?null:r.getShoreExcursion().getShoreExcursionId(), r.getQuantity(), r.getStatus().name(), r.getNotes(), r.getRegistrationTime()); }
    }
    public record ParticipationView(UUID id, UUID registrationId, String status, Instant checkInTime, Instant checkOutTime) {
        static ParticipationView of(Participation p) { return new ParticipationView(p.getParticipationId(), p.getRegistration().getRegistrationId(), p.getStatus().name(), p.getCheckInTime(), p.getCheckOutTime()); }
    }
    public record ActivityAvailabilityView(UUID activityId,Integer capacity,Integer availableSeats,String status){}
    public record ActivityScheduleCheckView(UUID activityId,boolean conflictFree,long conflicts){}

    @Operation(summary = "Danh sách hoạt động", description = "Lấy danh sách các hoạt động trên tàu")
    @PreAuthorize("hasAuthority('ACTIVITY_READ')")
    @GetMapping
    public ApiResponse<List<ActivityView>> list() {
        return ApiResponse.success(service.listActivities().stream().map(ActivityView::of).toList());
    }

    @Operation(summary = "Chi tiết hoạt động", description = "Lấy thông tin một hoạt động theo ID")
    @PreAuthorize("hasAuthority('ACTIVITY_READ')")
    @GetMapping("/{id}")
    public ApiResponse<ActivityView> get(@Parameter(description = "ID hoạt động", required = true) @PathVariable UUID id) {
        return ApiResponse.success(ActivityView.of(service.activity(id)));
    }

    @Operation(summary = "Kiểm tra chỗ còn trống", description = "Trả về sức chứa, số chỗ còn lại và trạng thái hoạt động")
    @PreAuthorize("hasAuthority('ACTIVITY_READ')")
    @GetMapping("/{id}/availability")
    public ApiResponse<ActivityAvailabilityView> availability(@Parameter(description = "ID hoạt động", required = true) @PathVariable UUID id) {
        OnboardActivity a = service.activity(id);
        return ApiResponse.success(new ActivityAvailabilityView(id, a.getCapacity(), service.availableSeats(id), a.getStatus().name()));
    }

    @Operation(summary = "Kiểm tra lịch hoạt động", description = "Kiểm tra xung đột lịch của hoạt động tại cùng địa điểm trên cùng chuyến")
    @PreAuthorize("hasAuthority('ACTIVITY_READ')")
    @GetMapping("/{id}/schedule-check")
    public ApiResponse<ActivityScheduleCheckView> scheduleCheck(@Parameter(description = "ID hoạt động", required = true) @PathVariable UUID id) {
        long conflicts = service.scheduleConflicts(id);
        return ApiResponse.success(new ActivityScheduleCheckView(id, conflicts == 0, conflicts));
    }

    @Operation(summary = "Tạo hoạt động", description = "Tạo hoạt động mới trên chuyến du thuyền")
    @PreAuthorize("hasAuthority('ACTIVITY_CREATE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ActivityView> create(@jakarta.validation.Valid @RequestBody ActivityRequest r) {
        OnboardActivity a = new OnboardActivity();
        apply(a, r);
        return ApiResponse.success(ActivityView.of(service.createActivity(a, r.cruiseId(), r.deckId(), r.cabinId())));
    }

    @Operation(summary = "Cập nhật hoạt động", description = "Cập nhật thông tin hoạt động theo ID")
    @PreAuthorize("hasAuthority('ACTIVITY_UPDATE')")
    @PutMapping("/{id}")
    public ApiResponse<ActivityView> update(@Parameter(description = "ID hoạt động", required = true) @PathVariable UUID id, @jakarta.validation.Valid @RequestBody ActivityRequest r) {
        OnboardActivity a = new OnboardActivity();
        apply(a, r);
        return ApiResponse.success(ActivityView.of(service.updateActivity(id, a, r.deckId(), r.cabinId())));
    }

    @Operation(summary = "Xóa hoạt động", description = "Xóa hoạt động theo ID khi đáp ứng các điều kiện nghiệp vụ")
    @PreAuthorize("hasAuthority('ACTIVITY_UPDATE')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "ID hoạt động", required = true) @PathVariable UUID id) {
        service.deleteActivity(id);
    }

    @Operation(summary = "Chuyển trạng thái hoạt động", description = "Thực hiện hành động vòng đời hoạt động: open, start, complete hoặc cancel")
    @PreAuthorize("hasAuthority('ACTIVITY_LIFECYCLE')")
    @PostMapping("/{id}/actions/{action}")
    public ApiResponse<ActivityView> lifecycle(@Parameter(description = "ID hoạt động", required = true) @PathVariable UUID id, @Parameter(description = "Hành động: open, start, complete hoặc cancel", required = true) @PathVariable String action) {
        return ApiResponse.success(ActivityView.of(service.lifecycle(id, action)));
    }

    @Operation(summary = "Danh sách đăng ký", description = "Lấy danh sách đăng ký; hành khách chỉ xem đăng ký của mình, nhân viên có thể lọc theo hoạt động")
    @PreAuthorize("hasAnyAuthority('ACTIVITY_ATTENDANCE', 'ACTIVITY_REGISTRATION')")
    @GetMapping("/registrations")
    public ApiResponse<List<RegistrationView>> registrations(@Parameter(description = "Lọc theo ID hoạt động") @RequestParam(required = false) UUID activityId, org.springframework.security.core.Authentication auth) {
        var p = (com.casms.security.principal.CasmsPrincipal) auth.getPrincipal();
        return ApiResponse.success(service.listRegistrations(activityId, p.getUserId(), p.isStaff()).stream().map(RegistrationView::of).toList());
    }

    @Operation(summary = "Chi tiết đăng ký", description = "Lấy thông tin đăng ký theo ID với kiểm tra quyền sở hữu")
    @PreAuthorize("hasAnyAuthority('ACTIVITY_ATTENDANCE', 'ACTIVITY_REGISTRATION')")
    @GetMapping("/registrations/{id}")
    public ApiResponse<RegistrationView> registration(@Parameter(description = "ID đăng ký", required = true) @PathVariable UUID id, org.springframework.security.core.Authentication auth) {
        var p = (com.casms.security.principal.CasmsPrincipal) auth.getPrincipal();
        return ApiResponse.success(RegistrationView.of(service.getRegistration(id, p.getUserId(), p.isStaff())));
    }

    @Operation(summary = "Đăng ký hoạt động trên tàu", description = "Tạo đăng ký tham gia hoạt động cho hành khách trong booking")
    @PreAuthorize("hasAuthority('ACTIVITY_REGISTRATION')")
    @PostMapping("/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegistrationView> register(@Parameter(description = "ID hoạt động", required = true) @PathVariable UUID id, @jakarta.validation.Valid @RequestBody RegistrationRequest r, org.springframework.security.core.Authentication auth) {
        var p = (com.casms.security.principal.CasmsPrincipal) auth.getPrincipal();
        return ApiResponse.success(RegistrationView.of(service.register(r.bookingPassengerId(), id, r.quantity(), p.getUserId(), p.isStaff())));
    }

    @Operation(summary = "Đăng ký tour tham quan", description = "Tạo đăng ký tour trên bờ cho hành khách trong booking")
    @PreAuthorize("hasAuthority('EXCURSION_BOOKING')")
    @PostMapping("/excursions/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegistrationView> registerExcursion(@Parameter(description = "ID tour tham quan", required = true) @PathVariable UUID id, @jakarta.validation.Valid @RequestBody RegistrationRequest r, org.springframework.security.core.Authentication auth) {
        var p = (com.casms.security.principal.CasmsPrincipal) auth.getPrincipal();
        return ApiResponse.success(RegistrationView.of(service.registerExcursion(r.bookingPassengerId(), id, r.quantity(), p.getUserId(), p.isStaff())));
    }

    @Operation(summary = "Xác nhận đăng ký", description = "Xác nhận đăng ký đang chờ và tạo bản ghi tham gia")
    @PreAuthorize("hasAuthority('ACTIVITY_REGISTRATION')")
    @PostMapping("/registrations/{id}/confirm")
    public ApiResponse<RegistrationView> confirm(@Parameter(description = "ID đăng ký", required = true) @PathVariable UUID id, org.springframework.security.core.Authentication auth) {
        var p = (com.casms.security.principal.CasmsPrincipal) auth.getPrincipal();
        return ApiResponse.success(RegistrationView.of(service.confirmRegistration(id, p.getUserId(), p.isStaff())));
    }

    @Operation(summary = "Cập nhật đăng ký", description = "Cập nhật số lượng hoặc ghi chú khi đăng ký còn ở trạng thái chờ")
    @PreAuthorize("hasAuthority('ACTIVITY_REGISTRATION')")
    @PutMapping("/registrations/{id}")
    public ApiResponse<RegistrationView> updateRegistration(@Parameter(description = "ID đăng ký", required = true) @PathVariable UUID id, @jakarta.validation.Valid @RequestBody RegistrationUpdateRequest r, org.springframework.security.core.Authentication auth) {
        var p = (com.casms.security.principal.CasmsPrincipal) auth.getPrincipal();
        return ApiResponse.success(RegistrationView.of(service.updatePending(id, r.quantity(), r.notes(), p.getUserId(), p.isStaff())));
    }

    @Operation(summary = "Hủy đăng ký", description = "Hủy đăng ký tham gia kèm lý do tùy chọn")
    @PreAuthorize("hasAuthority('ACTIVITY_REGISTRATION')")
    @PostMapping("/registrations/{id}/cancel")
    public ApiResponse<RegistrationView> cancel(@Parameter(description = "ID đăng ký", required = true) @PathVariable UUID id, @Parameter(description = "Lý do hủy") @RequestParam(required = false) String reason, org.springframework.security.core.Authentication auth) {
        var p = (com.casms.security.principal.CasmsPrincipal) auth.getPrincipal();
        return ApiResponse.success(RegistrationView.of(service.cancelRegistration(id, reason, p.getUserId(), p.isStaff())));
    }

    @Operation(summary = "Danh sách lượt tham gia", description = "Lấy danh sách bản ghi tham gia hoạt động")
    @PreAuthorize("hasAuthority('ACTIVITY_ATTENDANCE')")
    @GetMapping("/participations")
    public ApiResponse<List<ParticipationView>> participations() {
        return ApiResponse.success(service.listParticipations().stream().map(ParticipationView::of).toList());
    }

    @Operation(summary = "Chi tiết lượt tham gia", description = "Lấy trạng thái điểm danh của một lượt tham gia theo ID")
    @PreAuthorize("hasAuthority('ACTIVITY_ATTENDANCE')")
    @GetMapping("/participations/{id}")
    public ApiResponse<ParticipationView> participation(@Parameter(description = "ID lượt tham gia", required = true) @PathVariable UUID id) {
        return ApiResponse.success(ParticipationView.of(service.participation(id)));
    }

    @Operation(summary = "Cập nhật điểm danh", description = "Thực hiện check-in, checkout, complete hoặc no-show cho lượt tham gia")
    @PreAuthorize("hasAuthority('ACTIVITY_ATTENDANCE')")
    @PostMapping("/participations/{id}/{action}")
    public ApiResponse<ParticipationView> attendance(@Parameter(description = "ID lượt tham gia", required = true) @PathVariable UUID id, @Parameter(description = "Hành động: check-in, checkout, complete hoặc no-show", required = true) @PathVariable String action) {
        return ApiResponse.success(ParticipationView.of(service.attendance(id, action)));
    }
    private static void apply(OnboardActivity a, ActivityRequest r) {
        a.setActivityName(r.activityName()); a.setDescription(r.description());
        a.setStartTime(r.startTime()); a.setEndTime(r.endTime()); a.setLocation(r.location());
        a.setCapacity(r.capacity()); a.setPrice(r.price()); a.setCurrencyCode(r.currencyCode());
        // deck/cabin sẽ được resolve trong service — ở đây chỉ ghi nhận ID request
    }
}
