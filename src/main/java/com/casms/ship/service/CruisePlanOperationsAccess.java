package com.casms.ship.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class CruisePlanOperationsAccess implements WebMvcConfigurer, HandlerInterceptor {
    static final String RECEIVED_PLANS = """
            SELECT cruise_id FROM "CruisePlanReview"
            WHERE submitted_at IS NOT NULL AND status IN ('SUBMITTED','APPROVED','REJECTED')
            """;

    private final JdbcTemplate jdbc;
    private final DepartureSaleTermsService terms;

    public static boolean operationsOnly(Authentication auth) {
        if (auth == null) return false;
        var roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        return roles.contains("ROLE_OPERATIONS_MANAGER")
                && !roles.contains("ROLE_CRUISE_MANAGER") && !roles.contains("ROLE_ADMIN");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(
                "/cruise/{cruiseId}/**", "/cruises/{cruiseId}/**", "/cruise-plans/{cruiseId}/**",
                "/api/cruise/{cruiseId}/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!operationsOnly(auth)) return true;
        if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> vars)
                || !(vars.get("cruiseId") instanceof String value)) return true;
        UUID cruiseId;
        try {
            cruiseId = UUID.fromString(value);
        } catch (IllegalArgumentException invalidId) {
            return true; // Let Spring report the invalid route parameter.
        }
        var pattern = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if ((pattern.equals("/cruise-plans/{cruiseId}/commercial-terms/sale")
                || pattern.equals("/cruise-plans/{cruiseId}/commercial-terms/sale/history"))
                && terms.pendingSetup(cruiseId)) return true;
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(" + RECEIVED_PLANS + " AND cruise_id=?)", Boolean.class, cruiseId))) return true;
        throw new AccessDeniedException("Chuyến khởi hành chưa được gửi xét duyệt");
    }
}
