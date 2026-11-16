package com.casms.ship.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class CruisePlanOperationsAccess implements WebMvcConfigurer, HandlerInterceptor {
    static final String RECEIVED_PLANS = """
            SELECT cruise_id FROM "CruisePlanReview"
            WHERE submitted_at IS NOT NULL AND status IN ('SUBMITTED','APPROVED','REJECTED','REVISION_REQUESTED')
            """;

    private final JdbcTemplate jdbc;
    private final DepartureSaleTermsService terms;

    public static boolean operationsOnly(Authentication auth) {
        if (auth == null) return false;
        var roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        return roles.contains("ROLE_OPERATIONS_MANAGER")
                && roles.stream().noneMatch(Set.of("ROLE_CRUISE_MANAGER", "ROLE_ADMIN",
                        "ROLE_HR_MANAGER", "ROLE_SHIP_MANAGER", "ROLE_ACTIVITY_SERVICE_MANAGER")::contains);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(
                "/cruise/{cruiseId}/**", "/cruises/{cruiseId}/**", "/cruise-plans/{cruiseId}/**",
                "/api/cruise/{cruiseId}/**", "/api/cruises/{cruiseId}/**", "/itineraries/{itineraryId}/**",
                "/itinerary-days/**", "/itinerary-change-history/**", "/cruise-stops/**",
                "/ships/{shipId}/**", "/decks/**", "/cabins/**",
                "/activities/{id}/**", "/shore-excursions/{id}/**");
    }

    public static boolean requiresRecordScope(Authentication auth) {
        if (!(auth != null && auth.getPrincipal() instanceof CasmsPrincipal actor) || actor.hasRole(Role.ADMIN))
            return false;
        return actor.hasRole(Role.CRUISE_MANAGER) || actor.hasRole(Role.OPERATIONS_MANAGER)
                || actor.hasRole(Role.SHIP_MANAGER) || actor.hasRole(Role.ACTIVITY_SERVICE_MANAGER)
                || actor.hasRole(Role.HR_MANAGER);
    }

    static UUID currentShipManager() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CasmsPrincipal actor
                && actor.hasRole(Role.SHIP_MANAGER) && !actor.hasRole(Role.ADMIN) ? actor.getUserId() : null;
    }

    static final String VISIBLE_CRUISES = """
            SELECT c.cruise_id FROM "Cruise" c LEFT JOIN "Trip" t ON t.trip_id=c.source_trip_id
            WHERE (? AND coalesce(c.preparation_coordinator_id,t.created_by_user_id)=?)
               OR (? AND c.cruise_id IN (%s))
               OR (? AND EXISTS (SELECT 1 FROM "CruiseStaff" s WHERE s.cruise_id=c.cruise_id
                    AND s.role_in_cruise='SHIP_MANAGER' AND s.status='ACTIVE' AND s.user_id=?))
               OR (? AND (EXISTS (SELECT 1 FROM "CruiseWorkHandoff" w WHERE w.cruise_id=c.cruise_id
                    AND w.work_group='ASM' AND w.assignee_id=?)
                    OR EXISTS (SELECT 1 FROM "DepartureFarePackage" f WHERE f.cruise_id=c.cruise_id AND f.created_by=?)))
            """.formatted(RECEIVED_PLANS);

    static Object[] scopeArguments(CasmsPrincipal actor) {
        return scopeArguments(actor, true, true);
    }

    private static Object[] scopeArguments(CasmsPrincipal actor, boolean includeShip, boolean includeAsm) {
        UUID user = actor.getUserId();
        return new Object[] { actor.hasRole(Role.CRUISE_MANAGER), user, actor.hasRole(Role.OPERATIONS_MANAGER),
                includeShip && actor.hasRole(Role.SHIP_MANAGER), user,
                includeAsm && actor.hasRole(Role.ACTIVITY_SERVICE_MANAGER), user, user };
    }

    public Set<UUID> visibleCruises() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!requiresRecordScope(auth)) return null;
        return Set.copyOf(jdbc.queryForList(VISIBLE_CRUISES, UUID.class,
                scopeArguments((CasmsPrincipal) auth.getPrincipal())));
    }

    public Set<UUID> visibleStops() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!requiresRecordScope(auth)) return null;
        return Set.copyOf(jdbc.queryForList("""
                SELECT s.cruise_stop_id FROM "CruiseStop" s JOIN "Itinerary" i USING(itinerary_id)
                WHERE i.cruise_id IN (
                """ + VISIBLE_CRUISES + ")", UUID.class, scopeArguments((CasmsPrincipal) auth.getPrincipal())));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        var operationsOnly = operationsOnly(auth);
        if (!operationsOnly && !requiresRecordScope(auth)) return true;
        if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> vars)
                || vars.isEmpty()) return true;
        var pattern = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        UUID cruiseId = null;
        try {
            if (vars.containsKey("shipId") || vars.containsKey("deckId") || vars.containsKey("cabinId")) {
                if (auth.getPrincipal() instanceof CasmsPrincipal actor && actor.hasRole(Role.SHIP_MANAGER))
                    requireAssignedShip(vars, actor);
                // A Cruise resource may also pass a ship or cabin identifier.
                if (!vars.containsKey("cruiseId") && !vars.containsKey("itineraryId") && !vars.containsKey("cruiseStopId"))
                    return true;
            }
            if (vars.get("cruiseId") instanceof String value) cruiseId = UUID.fromString(value);
            else if (vars.get("itineraryId") instanceof String value) {
                cruiseId = jdbc.queryForList("SELECT cruise_id FROM \"Itinerary\" WHERE itinerary_id=?",
                        UUID.class, UUID.fromString(value)).stream().findFirst().orElse(null);
            } else if (vars.get("itineraryDayId") instanceof String value) {
                cruiseId = jdbc.queryForList("""
                        SELECT i.cruise_id FROM "ItineraryDay" d JOIN "Itinerary" i USING(itinerary_id)
                        WHERE d.itinerary_day_id=?
                        """, UUID.class, UUID.fromString(value)).stream().findFirst().orElse(null);
            } else if (vars.get("historyId") instanceof String value && pattern.startsWith("/itinerary-change-history/")) {
                cruiseId = jdbc.queryForList("""
                        SELECT i.cruise_id FROM "ItineraryChangeHistory" h JOIN "Itinerary" i USING(itinerary_id)
                        WHERE h.change_history_id=?
                        """, UUID.class, UUID.fromString(value)).stream().findFirst().orElse(null);
            } else if (vars.get("cruiseStopId") instanceof String value) {
                cruiseId = jdbc.queryForList("""
                        SELECT i.cruise_id FROM "Itinerary" i JOIN "CruiseStop" s USING(itinerary_id)
                        WHERE s.cruise_stop_id=?
                        """, UUID.class, UUID.fromString(value)).stream().findFirst().orElse(null);
            } else if (pattern.startsWith("/activities/{id}") && vars.get("id") instanceof String value) {
                var parents = jdbc.queryForList("""
                        SELECT cruise_id FROM "OnboardActivity" WHERE onboard_activity_id=?
                        """, UUID.class, UUID.fromString(value));
                if (parents.size() == 1 && parents.getFirst() == null) return true; // shared catalogue
                cruiseId = parents.stream().findFirst().orElse(null);
            } else if (pattern.startsWith("/shore-excursions/{id}") && vars.get("id") instanceof String value) {
                var parents = jdbc.queryForList("""
                        SELECT i.cruise_id FROM "ShoreExcursion" e LEFT JOIN "CruiseStop" s USING(cruise_stop_id)
                        LEFT JOIN "Itinerary" i USING(itinerary_id) WHERE e.shore_excursion_id=?
                        """, UUID.class, UUID.fromString(value));
                if (parents.size() == 1 && parents.getFirst() == null) return true; // shared catalogue
                cruiseId = parents.stream().findFirst().orElse(null);
            } else return true;
        } catch (IllegalArgumentException invalidId) {
            return true; // Let Spring report the invalid route parameter.
        }
        if (cruiseId == null) throw new AccessDeniedException("Chuyến khởi hành ngoài phạm vi công việc.");
        String uri = request.getRequestURI();
        if ("GET".equals(request.getMethod()) && (pattern.endsWith("/public-itinerary")
                || pattern.endsWith("/sellability") || pattern.endsWith("/cruise-package-offers/available")
                || (pattern.equals("/cruise/{cruiseId}") || pattern.equals("/cruises/{cruiseId}"))
                    && "OPEN_FOR_SALE".equals(request.getParameter("saleState"))))
            return true;
        boolean ops = operationsOnly || auth.getPrincipal() instanceof CasmsPrincipal actor
                && actor.hasRole(Role.OPERATIONS_MANAGER);
        if (ops && (pattern.endsWith("/preparation/operating-budget") && "GET".equals(request.getMethod())
                || pattern.endsWith("/preparation/operating-budget/review"))) {
            return true; // Budget review scope is based on its independent submission.
        }
        boolean saleTerms = pattern.equals("/cruise-plans/{cruiseId}/commercial-terms/sale")
                || pattern.equals("/cruise-plans/{cruiseId}/commercial-terms/sale/history");
        boolean saleHistory = pattern.equals("/cruise-plans/{cruiseId}/commercial-terms/sale/history");
        if (ops && saleTerms && terms.pendingSetup(cruiseId)) return true;
        boolean reviewAction = !"GET".equals(request.getMethod()) && (saleTerms
                || Set.of("approve","reject","request-revision","ready","open-sale","stop-sale","resume-sale",
                        "confirm","return","disembarkation").contains(uri.substring(uri.lastIndexOf('/') + 1)));
        if (operationsOnly || ops && (reviewAction || saleHistory)) {
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(" + RECEIVED_PLANS + " AND cruise_id=?)", Boolean.class, cruiseId))) return true;
            throw new AccessDeniedException("Chuyến khởi hành chưa được gửi xét duyệt");
        }
        if (auth.getPrincipal() instanceof CasmsPrincipal actor) {
            String operation = uri.substring(uri.lastIndexOf('/') + 1);
            if (pattern.endsWith("/{cruiseId}/staff/candidates")
                    || actor.hasRole(Role.HR_MANAGER) && !"GET".equals(request.getMethod())
                        && (pattern.endsWith("/{cruiseId}/staff") || pattern.contains("/{cruiseId}/staff/"))) {
                if (actor.hasRole(Role.HR_MANAGER) && Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM "StaffingRequest" WHERE cruise_id=? AND status='SENT_TO_HR')
                        """, Boolean.class, cruiseId))) return true;
                throw new AccessDeniedException("Yêu cầu nhân sự này ngoài phạm vi phân công.");
            }
            boolean crmWrite = !"GET".equals(request.getMethod()) &&
                    (pattern.startsWith("/cruise-plans/") && Set.of("submit","withdraw","windows","cruise-packages").contains(operation)
                        || Set.of("draft","assign-ship","change-assigned-ship","unassign-ship",
                            "sync-operational-programs","sale-terms-request").contains(operation)
                        || pattern.contains("/pricing-policy") && Set.of("pricing-policy","submit").contains(operation)
                        || pattern.startsWith("/itinerary-days/") && !operation.startsWith("validate")
                        || pattern.startsWith("/itineraries/") && Set.of("submit","apply-change","return-to-draft","publish","cancel").contains(operation)
                        || pattern.startsWith("/cruise-stops/") && (pattern.equals("/cruise-stops/{cruiseStopId}")
                            || Set.of("reorder","reorder-with-reason","planned-arrival","planned-departure").contains(operation)));
            if (crmWrite) requireCoordinator(cruiseId, actor);
            if (actor.hasRole(Role.HR_MANAGER) && pattern.contains("/{cruiseId}/staff")
                    && Boolean.TRUE.equals(jdbc.queryForObject("""
                       SELECT EXISTS(SELECT 1 FROM "StaffingRequest" WHERE cruise_id=? AND status IN
                           ('SENT_TO_HR','HR_SUBMITTED','SHIP_CONFIRMED','PLANNER_CONFIRMED'))
                       """, Boolean.class, cruiseId))) return true;
            boolean managementRead = pattern.equals("/cruise-plans/{cruiseId}")
                    || pattern.equals("/cruise-plans/{cruiseId}/cruise-packages") || saleTerms;
            if (pattern.equals("/cruise-plans/{cruiseId}/sale-terms-request")
                    || pattern.equals("/cruise-plans/{cruiseId}/ship-assignment-context"))
                requireCoordinator(cruiseId, actor);
            Object[] scope = scopeArguments(actor, !managementRead || pattern.equals("/cruise-plans/{cruiseId}"), !managementRead);
            Object[] arguments = java.util.Arrays.copyOf(scope, scope.length + 1);
            arguments[scope.length] = cruiseId;
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM (" + VISIBLE_CRUISES + ") permitted WHERE cruise_id=?)",
                    Boolean.class, arguments))) return true;
        }
        throw new AccessDeniedException("Chuyến khởi hành ngoài phạm vi công việc.");
    }

    public void requireItineraryCoordinator(UUID itineraryId) {
        var cruiseId = jdbc.queryForList("SELECT cruise_id FROM \"Itinerary\" WHERE itinerary_id=?",
                UUID.class, itineraryId).stream().findFirst().orElse(null);
        requireCruiseCoordinator(cruiseId);
    }

    public void requireCruiseCoordinator(UUID cruiseId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CasmsPrincipal actor))
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        requireCoordinator(cruiseId, actor);
    }

    private void requireCoordinator(UUID cruiseId, CasmsPrincipal actor) {
        if (cruiseId == null || !actor.hasRole(Role.CRUISE_MANAGER) || !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "Cruise" c LEFT JOIN "Trip" t ON c.source_trip_id=t.trip_id
                    WHERE c.cruise_id=? AND coalesce(c.preparation_coordinator_id,t.created_by_user_id)=?)
                """, Boolean.class, cruiseId, actor.getUserId())))
            throw new AccessDeniedException("Bạn chưa được giao điều phối chuyến khởi hành này.");
    }

    private void requireAssignedShip(Map<?, ?> vars, CasmsPrincipal actor) {
        UUID ship = vars.get("shipId") instanceof String value ? UUID.fromString(value) : null;
        if (ship == null && vars.get("deckId") instanceof String value) {
            ship = jdbc.queryForList("SELECT ship_id FROM \"Deck\" WHERE deck_id=?",
                    UUID.class, UUID.fromString(value)).stream().findFirst().orElse(null);
        }
        if (ship == null && vars.get("cabinId") instanceof String value) {
            ship = jdbc.queryForList("SELECT ship_id FROM \"Cabin\" WHERE cabin_id=?",
                    UUID.class, UUID.fromString(value)).stream().findFirst().orElse(null);
        }
        if (ship == null || !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "Ship" s WHERE s.ship_id=? AND s.created_by_ship_manager_id=?)
                    OR EXISTS(SELECT 1 FROM "CruiseStaff" crew JOIN "Cruise" c USING(cruise_id)
                    WHERE c.ship_id=? AND crew.user_id=? AND crew.role_in_cruise='SHIP_MANAGER'
                    AND crew.status='ACTIVE' AND c.status NOT IN ('CANCELLED','COMPLETED'))
                """, Boolean.class, ship, actor.getUserId(), ship, actor.getUserId()))) {
            throw new AccessDeniedException("Tàu này chưa được phân công cho bạn.");
        }
    }
}
