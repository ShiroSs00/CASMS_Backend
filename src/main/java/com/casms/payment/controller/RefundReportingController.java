package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.payment.service.RefundReportingService;
import com.casms.security.principal.CasmsPrincipal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/refunds/report")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FINANCE_OFFICER') and hasAuthority('PAYMENT_REFUND')")
public class RefundReportingController {
    private final RefundReportingService reports;

    @GetMapping
    public ApiResponse<List<RefundReportingService.Totals>> summary(
            @AuthenticationPrincipal CasmsPrincipal actor,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until) {
        return ApiResponse.success(reports.summary(actor, from, until));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(@AuthenticationPrincipal CasmsPrincipal actor,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=refunds.csv")
                .body(reports.csv(actor, from, until));
    }

    public record ExportRange(Instant from, Instant until) {}

    @PostMapping("/export")
    public ApiResponse<String> exportForWeb(@AuthenticationPrincipal CasmsPrincipal actor,
            @RequestBody ExportRange range) {
        return ApiResponse.success(reports.csv(actor, range.from(), range.until()));
    }
}
