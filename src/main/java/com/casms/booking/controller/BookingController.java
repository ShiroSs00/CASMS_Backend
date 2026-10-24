package com.casms.booking.controller;

import com.casms.booking.dto.request.BookingCancelRequest;
import com.casms.booking.dto.request.BookingContractAcceptRequest;
import com.casms.booking.dto.request.BookingCreateRequest;
import com.casms.booking.dto.request.CabinChangeRequest;
import com.casms.booking.dto.response.AvailableCabinResponse;
import com.casms.booking.dto.response.BookingDetailResponse;
import com.casms.booking.dto.response.BookingSummaryResponse;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.service.BookingService;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.enums.CabinType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Booking & Contracts", description = "Quản lý đặt chỗ chuyến đi, hành khách tự chọn phòng và hợp đồng")
@RestController
@RequestMapping("/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Tra cứu phòng trống để tự chọn (Công khai / Khách vãng lai)",
               description = "Tìm kiếm danh sách phòng trống trên tàu của chuyến đi để hành khách xem sơ đồ và tự chọn phòng (Không yêu cầu đăng nhập)")
    @GetMapping("/available-cabins")
    public ApiResponse<List<AvailableCabinResponse>> getAvailableCabins(
            @Parameter(description = "ID của chuyến đi", required = true) @RequestParam UUID cruiseId,
            @Parameter(description = "ID tầng/boong (tùy chọn)") @RequestParam(required = false) UUID deckId,
            @Parameter(description = "Hạng phòng (INTERIOR, OCEAN_VIEW, BALCONY, SUITE)") @RequestParam(required = false) CabinType cabinType,
            @Parameter(description = "Sức chứa tối thiểu yêu cầu") @RequestParam(required = false) Integer minCapacity) {
        return ApiResponse.success(bookingService.getAvailableCabins(cruiseId, deckId, cabinType, minCapacity));
    }

    @Operation(summary = "Tra cứu phòng còn trống kèm node và vị trí trên model 3D",
            description = "Dùng cabinId trong kết quả để gửi POST /booking; modelNodeKey và transforms dùng để gắn phòng được chọn vào GLB")
    @GetMapping("/3d-available-cabins")
    public ApiResponse<List<AvailableCabinResponse>> getThreeDimensionalAvailableCabins(
            @Parameter(description = "ID chuyến đi", required = true) @RequestParam UUID cruiseId,
            @Parameter(description = "ID deck (tùy chọn)") @RequestParam(required = false) UUID deckId,
            @Parameter(description = "Loại cabin (tùy chọn)") @RequestParam(required = false) CabinType cabinType,
            @Parameter(description = "Sức chứa còn trống tối thiểu") @RequestParam(required = false) Integer minCapacity) {
        return ApiResponse.success(bookingService.getThreeDimensionalAvailableCabins(cruiseId, deckId, cabinType, minCapacity));
    }

    @Operation(summary = "Tạo đặt chỗ chuyến đi (Hỗ trợ cả khách chưa đăng nhập và đã đăng nhập)",
               description = "Hành khách tự chọn cabin cụ thể và gói dịch vụ. Nếu chưa đăng nhập, hệ thống tự động liên kết hoặc tạo tài khoản hành khách dựa theo thông tin liên hệ (Email/SĐT).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingDetailResponse> create(
            @RequestBody @Valid BookingCreateRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(bookingService.createBooking(request, principal));
    }

    @Operation(summary = "Tra cứu đơn đặt chỗ cho khách vãng lai",
               description = "Khách hàng không cần đăng nhập, có thể tra cứu đơn đặt chỗ bằng Mã đặt chỗ (Booking Reference) kết hợp Email hoặc Số điện thoại")
    @GetMapping("/lookup")
    public ApiResponse<BookingDetailResponse> lookup(
            @Parameter(description = "Mã tham chiếu đơn đặt chỗ (VD: BK-20261019-XXXX)", required = true) @RequestParam String reference,
            @Parameter(description = "Email người đặt (để xác thực)") @RequestParam(required = false) String email,
            @Parameter(description = "Số điện thoại người đặt (để xác thực)") @RequestParam(required = false) String phoneNumber) {
        return ApiResponse.success(bookingService.lookupBooking(reference, email, phoneNumber));
    }

    @Operation(summary = "Danh sách đơn đặt chỗ",
               description = "Tra cứu danh sách đơn đặt chỗ (Hành khách chỉ xem đơn của chính mình, nhân viên xem toàn bộ theo bộ lọc)")
    @PreAuthorize("hasAnyAuthority('BOOKING_READ')")
    @GetMapping
    public ApiResponse<Page<BookingSummaryResponse>> list(
            @Parameter(description = "Lọc theo chuyến đi") @RequestParam(required = false) UUID cruiseId,
            @Parameter(description = "Lọc theo trạng thái (Draft, Confirmed, Cancelled)") @RequestParam(required = false) BookingStatus status,
            Pageable pageable,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(bookingService.list(cruiseId, status, pageable, principal));
    }

    @Operation(summary = "Chi tiết đơn đặt chỗ",
               description = "Lấy đầy đủ thông tin chi tiết một đơn đặt chỗ gồm danh sách hành khách, phòng đã chọn, gói dịch vụ và hợp đồng")
    @PreAuthorize("hasAnyAuthority('BOOKING_READ')")
    @GetMapping("/{bookingId}")
    public ApiResponse<BookingDetailResponse> detail(
            @Parameter(description = "ID đơn đặt chỗ", required = true) @PathVariable UUID bookingId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(bookingService.detail(bookingId, principal));
    }

    @Operation(summary = "Xác nhận hợp đồng bản cứng",
               description = "Xác nhận hợp đồng đã ký bản cứng, ghi nhận mã lưu trữ hồ sơ, người thực hiện và thời điểm ký để đủ điều kiện xác nhận đặt chỗ")
    @PreAuthorize("hasAuthority('BOOKING_CONTRACT_MANAGE')")
    @PostMapping("/{bookingId}/contract/accept")
    public ApiResponse<BookingDetailResponse> acceptContract(
            @Parameter(description = "ID đơn đặt chỗ", required = true) @PathVariable UUID bookingId,
            @RequestBody @Valid BookingContractAcceptRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success("Contract accepted successfully", bookingService.acceptContract(bookingId, request, principal));
    }

    @Operation(summary = "Xác nhận đặt chỗ",
               description = "Chuyển trạng thái đơn đặt chỗ sang Confirmed sau khi đã thanh toán tiền cọc hoặc thanh toán toàn bộ")
    @PreAuthorize("hasAuthority('BOOKING_CONFIRM')")
    @PostMapping("/{bookingId}/confirm")
    public ApiResponse<BookingDetailResponse> confirm(
            @Parameter(description = "ID đơn đặt chỗ", required = true) @PathVariable UUID bookingId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(bookingService.confirm(bookingId, principal));
    }

    @Operation(summary = "Hủy đơn đặt chỗ",
               description = "Hủy đặt chỗ, tự động đóng hiệu lực gán phòng đã chọn để giải phóng chỗ trống và ghi nhận lý do hủy")
    @PreAuthorize("hasAnyAuthority('BOOKING_CANCEL')")
    @PostMapping("/{bookingId}/cancel")
    public ApiResponse<BookingDetailResponse> cancel(
            @Parameter(description = "ID đơn đặt chỗ", required = true) @PathVariable UUID bookingId,
            @RequestBody @Valid BookingCancelRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(bookingService.cancel(bookingId, request, principal));
    }

    @Operation(summary = "Đổi phòng cho hành khách",
               description = "Chuyển hành khách sang phòng trống khác trên cùng con tàu theo yêu cầu")
    @PreAuthorize("hasAnyAuthority('BOOKING_CABIN_ASSIGN', 'BOOKING_UPDATE')")
    @PostMapping("/change-cabin")
    public ApiResponse<BookingDetailResponse> changeCabin(
            @RequestBody @Valid CabinChangeRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(bookingService.changeCabin(request, principal));
    }
}
