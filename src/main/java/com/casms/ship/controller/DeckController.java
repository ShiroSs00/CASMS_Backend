package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.*;
import com.casms.ship.dto.response.*;
import com.casms.ship.service.DeckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deck")
@RequiredArgsConstructor
public class DeckController {

    private final DeckService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeckResponse> create(@RequestBody @Valid DeckCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping
    public ApiResponse<Page<DeckResponse>> list(Pageable pageable) {
        return ApiResponse.success(service.list(pageable));
    }

    @GetMapping("/{deckId}")
    public ApiResponse<DeckResponse> detail(@PathVariable UUID deckId) {
        return ApiResponse.success(service.detail(deckId));
    }

    @PatchMapping("/{deckId}")
    public ApiResponse<DeckResponse> update(@PathVariable UUID deckId, 
                                            @RequestBody @Valid DeckUpdateRequest request) {
        return ApiResponse.success(service.update(deckId, request));
    }

    @DeleteMapping("/{deckId}/unused")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(@PathVariable UUID deckId) {
        service.deleteUnused(deckId);
        return ApiResponse.success("Deleted unused deck successfully", null);
    }

    @GetMapping("/{deckId}/cabins")
    public ApiResponse<List<CabinSummaryResponse>> listCabinsByDeck(@PathVariable UUID deckId) {
        return ApiResponse.success(service.listCabinsByDeck(deckId));
    }

    @GetMapping("/{deckId}/validate-ship")
    public ApiResponse<Boolean> validateBelongsToShip(@PathVariable UUID deckId, 
                                                      @RequestParam UUID shipId) {
        return ApiResponse.success(service.validateBelongsToShip(deckId, shipId));
    }

    @PostMapping("/{deckId}/validate")
    public ApiResponse<Void> validateDeck(@PathVariable UUID deckId) {
        service.validateDeck(deckId);
        return ApiResponse.success("Deck validation passed", null);
    }

    @GetMapping("/{deckId}/capacity")
    public ApiResponse<DeckCapacityResponse> getDeckCapacity(@PathVariable UUID deckId) {
        return ApiResponse.success(service.getDeckCapacity(deckId));
    }
}