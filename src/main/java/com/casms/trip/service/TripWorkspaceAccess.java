package com.casms.trip.service;

import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class TripWorkspaceAccess implements WebMvcConfigurer, HandlerInterceptor {
    private final JdbcTemplate jdbc;

    static CasmsPrincipal currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CasmsPrincipal actor ? actor : null;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/trips", "/trips/**", "/trip-schedule-versions/**")
                .excludePathPatterns("/trip-schedule-versions/eligible-sources",
                        "/trip-schedule-versions/eligible-sources/**", "/trips/review-queue");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var actor = currentActor();
        if (actor == null || !actor.hasRole(Role.CRUISE_MANAGER) || !actor.isEnabled() || !actor.isAccountNonLocked())
            throw denied();
        if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> vars))
            return true;
        try {
            UUID tripId = vars.get("tripId") instanceof String value ? UUID.fromString(value) : null;
            UUID versionId = vars.get("versionId") instanceof String value ? UUID.fromString(value) : null;
            if (tripId != null && !Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM "Trip" WHERE trip_id=? AND created_by_user_id=?)
                    """, Boolean.class, tripId, actor.getUserId()))) throw denied();
            if (versionId != null && !Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM "TripScheduleVersion" v JOIN "Trip" t USING(trip_id)
                        WHERE v.version_id=? AND t.created_by_user_id=?)
                    """, Boolean.class, versionId, actor.getUserId()))) throw denied();
        } catch (IllegalArgumentException invalidId) {
            return true;
        }
        return true;
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Khung chuyến này thuộc phạm vi của người lập khác.");
    }
}
