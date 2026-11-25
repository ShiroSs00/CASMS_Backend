package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.payment.service.ExchangeRateSyncService;
import com.casms.security.principal.CasmsPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/usd-display-rate")
public class UsdDisplayRateController {
    private final ExchangeRateSyncService exchangeRateSyncService;

    public record Input(
            @NotNull(message = "Ngày hiệu lực không được để trống.")
            LocalDate effectiveOn,
            @NotNull(message = "Tỷ giá không được để trống.")
            @DecimalMin(value = "1000", message = "Tỷ giá USD/VND tối thiểu là 1.000 VND.")
            @DecimalMax(value = "1000000", message = "Tỷ giá USD/VND tối đa là 1.000.000 VND.")
            @Digits(integer = 8, fraction = 4, message = "Tỷ giá chỉ hỗ trợ tối đa 8 chữ số nguyên và 4 chữ số thập phân.")
            BigDecimal vndPerUsd,
            @NotBlank(message = "Nguồn tỷ giá không được để trống.")
            @Size(max = 160, message = "Nguồn tỷ giá tối đa 160 ký tự.")
            String source) {}

    public record Rate(
            LocalDate effectiveOn,
            BigDecimal vndPerUsd,
            String source,
            Instant publishedAt,
            boolean autoSynced) {
        static Rate of(ExchangeRateSyncService.RateView view) {
            return view == null
                    ? null
                    : new Rate(view.effectiveOn(), view.vndPerUsd(), view.source(), view.publishedAt(), view.autoSynced());
        }
    }

    @GetMapping
    public ApiResponse<Rate> current() {
        return ApiResponse.success(Rate.of(exchangeRateSyncService.getCurrentRate()));
    }

    @PutMapping
    @PreAuthorize("hasRole('FINANCE_OFFICER') and hasAuthority('PAYMENT_REPORT')")
    public ApiResponse<Rate> save(@Valid @RequestBody Input input, @AuthenticationPrincipal CasmsPrincipal actor) {
        ExchangeRateSyncService.RateView saved = exchangeRateSyncService.saveManualRate(
                input.effectiveOn(), input.vndPerUsd(), input.source(), actor.getUserId());
        return ApiResponse.success(Rate.of(saved));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('FINANCE_OFFICER') and hasAuthority('PAYMENT_REPORT')")
    public ApiResponse<ExchangeRateSyncService.SyncResult> syncNow(
            @RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(exchangeRateSyncService.syncNow(actor.getUserId(), force));
    }
}
