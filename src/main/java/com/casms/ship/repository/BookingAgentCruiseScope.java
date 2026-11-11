package com.casms.ship.repository;

import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStaff;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class BookingAgentCruiseScope {
    private static final Set<String> CRUISE_MANAGEMENT_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_CRUISE_MANAGER", "ROLE_OPERATIONS_MANAGER",
            "ROLE_SHIP_MANAGER", "ROLE_ACTIVITY_SERVICE_MANAGER");

    private BookingAgentCruiseScope() {}

    public static UUID assignee(Authentication auth) {
        if (auth == null || !isScoped(auth.getAuthorities())) return null;
        if (auth.getPrincipal() instanceof CasmsPrincipal principal && principal.getUserId() != null) {
            return principal.getUserId();
        }
        throw new AccessDeniedException("Không xác định được nhân viên đặt chỗ");
    }

    public static UUID assignee(CasmsPrincipal principal) {
        if (principal == null || !isScoped(principal.getAuthorities())) return null;
        if (principal.getUserId() != null) return principal.getUserId();
        throw new AccessDeniedException("Không xác định được nhân viên đặt chỗ");
    }

    private static boolean isScoped(Collection<? extends GrantedAuthority> authorities) {
        var names = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        return names.contains("ROLE_BOOKING_AGENT") && Collections.disjoint(names, CRUISE_MANAGEMENT_ROLES);
    }

    public static Predicate assigned(Path<Cruise> cruise, CriteriaQuery<?> query, CriteriaBuilder builder, UUID userId) {
        var match = query.subquery(Integer.class);
        var assignment = match.from(CruiseStaff.class);
        match.select(builder.literal(1)).where(
                builder.equal(assignment.get("cruise"), cruise),
                builder.equal(assignment.get("user").get("userId"), userId),
                builder.equal(assignment.get("roleInCruise"), "BOOKING_AGENT"),
                builder.equal(assignment.get("status"), "ACTIVE"));
        return builder.exists(match);
    }

    public static void requireAssigned(CruiseRepository cruises, UUID cruiseId, UUID assignee) {
        if (assignee != null && !cruises.exists((root, query, builder) -> builder.and(
                builder.equal(root.get("cruiseId"), cruiseId), assigned(root, query, builder, assignee)))) {
            throw new AccessDeniedException("Chuyến khởi hành chưa được phân công cho bạn");
        }
    }
}
