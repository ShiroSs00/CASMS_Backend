package com.casms.payment.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequiredArgsConstructor
@RequestMapping("/usd-display-rate")
public class UsdDisplayRateController {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;

    public record Input(@NotNull LocalDate effectiveOn,
                        @NotNull @DecimalMin("0.0001") @Digits(integer=8, fraction=4) BigDecimal vndPerUsd,
                        @NotBlank @Size(max=160) String source) {}
    public record Rate(LocalDate effectiveOn, BigDecimal vndPerUsd, String source) {}

    @GetMapping
    public ApiResponse<Rate> current() {
        return ApiResponse.success(jdbc.query("""
                SELECT effective_on, vnd_per_usd, source FROM "UsdDisplayRate"
                WHERE effective_on<=? ORDER BY effective_on DESC LIMIT 1
                """, (rs, i) -> new Rate(rs.getObject(1, LocalDate.class), rs.getBigDecimal(2), rs.getString(3)),
                LocalDate.now(VIETNAM)).stream().findFirst().orElse(null));
    }

    @PutMapping
    @PreAuthorize("hasRole('FINANCE_OFFICER') and hasAuthority('PAYMENT_REPORT')")
    public ApiResponse<Rate> save(@Valid @RequestBody Input input, @AuthenticationPrincipal CasmsPrincipal actor) {
        if (input.effectiveOn().isAfter(LocalDate.now(VIETNAM))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "REFERENCE_RATE_FUTURE_DATE",
                    "Ngày hiệu lực của tỷ giá phải là hôm nay hoặc trước đó.");
        }
        jdbc.update("""
                INSERT INTO "UsdDisplayRate"(effective_on,vnd_per_usd,source,published_by)
                VALUES(?,?,?,?)
                ON CONFLICT (effective_on) DO UPDATE SET vnd_per_usd=excluded.vnd_per_usd,
                    source=excluded.source,published_by=excluded.published_by,published_at=now()
                """, input.effectiveOn(),input.vndPerUsd(),input.source().strip(),actor.getUserId());
        return current();
    }
}
