package com.casms.ship.service;

import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.repository.CruiseStaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/** Departure scope comes from existing CruiseStaff assignments, never the Booking creator. */
@Service
@RequiredArgsConstructor
public class CruiseStaffAccessPolicy {
    private final CruiseStaffRepository assignments;

    public void requireManifestRead(UUID cruiseId) {
        CasmsPrincipal principal = principal();
        if (!principal.hasAuthority("CRUISE_MANIFEST_READ")) {
            throw new AccessDeniedException("Cruise manifest read permission is required");
        }
        requireAssignedDeparture(principal, cruiseId);
    }

    public void requireCheckinStaffScope(UUID cruiseId, UUID actorId) {
        CasmsPrincipal principal = principal();
        if (!principal.isStaff() || !Objects.equals(actorId, principal.getUserId())) {
            throw new AccessDeniedException("Check-in actor does not match the authenticated staff account");
        }
        requireAssignedDeparture(principal, cruiseId);
    }

    private void requireAssignedDeparture(CasmsPrincipal principal, UUID cruiseId) {
        if (principal.hasRole(Role.BOOKING_AGENT)
                && (cruiseId == null || !assignments.existsByCruise_CruiseIdAndUser_UserIdAndStatus(
                        cruiseId, principal.getUserId(), "ACTIVE"))) {
            throw new AccessDeniedException("An active CruiseStaff assignment is required for this Departure");
        }
    }

    private CasmsPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CasmsPrincipal principal) || !principal.isEnabled()) {
            throw new AccessDeniedException("An active authenticated account is required");
        }
        return principal;
    }
}
