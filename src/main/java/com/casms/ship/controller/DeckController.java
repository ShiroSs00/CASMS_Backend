package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.DeckCreateRequest;
import com.casms.ship.dto.request.DeckUpdateRequest;
import com.casms.ship.dto.response.CabinSummaryResponse;
import com.casms.ship.dto.response.DeckCapacityResponse;
import com.casms.ship.dto.response.DeckResponse;
import com.casms.ship.service.DeckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Deck Management", description = "Quản lý boong / tầng tàu du lịch")
@RestController
@RequestMapping("/decks")
@RequiredArgsConstructor
public class DeckController {

    private final DeckService service;

    @Operation(summary = "Tạo tầng mới", description = "Tạo tầng mới trên tàu, xác thực số tầng không vượt quá tổng số boong và không trùng trên cùng tàu")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeckResponse> create(@RequestBody @Valid DeckCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "Danh sách tầng", description = "Lấy danh sách các tầng trên hệ thống (hỗ trợ phân trang)")
    @GetMapping
    public ApiResponse<Page<DeckResponse>> list(Pageable pageable) {
        return ApiResponse.success(service.list(pageable));
    }

    @Operation(summary = "Chi tiết tầng", description = "Lấy thông tin chi tiết của một tầng theo ID")
    @GetMapping("/{deckId}")
    public ApiResponse<DeckResponse> detail(
            @Parameter(description = "ID của tầng", required = true) @PathVariable UUID deckId) {
        return ApiResponse.success(service.detail(deckId));
    }

    @Operation(summary = "Cập nhật tầng", description = "Cập nhật tên hoặc số thứ tự tầng (kiểm tra trùng lặp và giới hạn của tàu)")
    @PatchMapping("/{deckId}")
    public ApiResponse<DeckResponse> update(
            @Parameter(description = "ID của tầng", required = true) @PathVariable UUID deckId,
            @RequestBody @Valid DeckUpdateRequest request) {
        return ApiResponse.success(service.update(deckId, request));
    }

    @Operation(summary = "Xóa tầng chưa sử dụng", description = "Xóa tầng nếu tầng không chứa cabin nào")
    @DeleteMapping("/{deckId}/unused")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(
            @Parameter(description = "ID của tầng", required = true) @PathVariable UUID deckId) {
        service.deleteUnused(deckId);
        return ApiResponse.success("Deleted unused deck successfully", null);
    }

    @Operation(summary = "Danh sách phòng theo tầng", description = "Lấy toàn bộ danh sách cabin thuộc về tầng này")
    @GetMapping("/{deckId}/cabins")
    public ApiResponse<List<CabinSummaryResponse>> listCabinsByDeck(
            @Parameter(description = "ID của tầng", required = true) @PathVariable UUID deckId) {
        return ApiResponse.success(service.listCabinsByDeck(deckId));
    }

    @Operation(summary = "Kiểm tra tầng thuộc tàu", description = "Xác nhận tầng có thuộc về tàu chỉ định hay không")
    @GetMapping("/{deckId}/validate-ship")
    public ApiResponse<Boolean> validateBelongsToShip(
            @Parameter(description = "ID của tầng", required = true) @PathVariable UUID deckId,
            @Parameter(description = "ID của tàu cần kiểm tra", required = true) @RequestParam UUID shipId) {
        return ApiResponse.success(service.validateBelongsToShip(deckId, shipId));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ của tầng", description = "Xác minh số thứ tự tầng không vượt quá giới hạn tổng số boong của tàu")
    @PostMapping("/{deckId}/validate")
    public ApiResponse<Void> validateDeck(
            @Parameter(description = "ID của tầng", required = true) @PathVariable UUID deckId) {
        service.validateDeck(deckId);
        return ApiResponse.success("Deck validation passed", null);
    }

    @Operation(summary = "Xem sức chứa của tầng", description = "Lấy tổng số phòng, số phòng trống và tổng sức chứa khách trên tầng này")
    @GetMapping("/{deckId}/capacity")
    public ApiResponse<DeckCapacityResponse> getDeckCapacity(
            @Parameter(description = "ID của tầng", required = true) @PathVariable UUID deckId) {
        return ApiResponse.success(service.getDeckCapacity(deckId));
    }
}