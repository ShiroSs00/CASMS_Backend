package com.casms.activity.service;

import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Applies existing cruise duty assignments to execution APIs; never grants an authority. */
@Component
@RequiredArgsConstructor
public class AssignedOnboardWorkScope {
    private final JdbcTemplate jdbc;

    public static boolean restricted() { return actor() != null; }

    private static CasmsPrincipal actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CasmsPrincipal principal
                && principal.hasRole(Role.ONBOARD_STAFF) ? principal : null;
    }

    public boolean activity(UUID id) { return assigned(id, true); }
    public boolean excursion(UUID id) { return assigned(id, false); }
    public void requireActivity(UUID id) { if (!activity(id)) deny(); }
    public void requireExcursion(UUID id) { if (!excursion(id)) deny(); }

    public boolean group(UUID id) {
        var principal = actor();
        if (principal == null) return true;
        if (id == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "TourGroup" task
                    JOIN "ShoreExcursion" tour USING (shore_excursion_id)
                    JOIN "CruiseStop" stop USING (cruise_stop_id)
                    JOIN "CruiseStaff" staff ON staff.cruise_id=stop.cruise_id
                    WHERE task.tour_group_id=? AND task.guide_id=? AND staff.user_id=?
                        AND staff.status='ACTIVE' AND staff.role_in_cruise='ONBOARD_STAFF')
                """, Boolean.class, id, principal.getUserId(), principal.getUserId()));
    }

    public void requireGroup(UUID id) { if (!group(id)) deny(); }

    private boolean assigned(UUID id, boolean onboard) {
        var principal = actor();
        if (principal == null) return true;
        if (id == null) return false;
        String resource = onboard ? "onboard_activity_id" : "shore_excursion_id";
        String cruiseMatch = onboard
                ? "EXISTS(SELECT 1 FROM \"OnboardActivity\" task WHERE task.onboard_activity_id=duty.onboard_activity_id AND task.cruise_id=staff.cruise_id)"
                : "EXISTS(SELECT 1 FROM \"ShoreExcursion\" task JOIN \"CruiseStop\" stop USING(cruise_stop_id) WHERE task.shore_excursion_id=duty.shore_excursion_id AND stop.cruise_id=staff.cruise_id)";
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "CruiseStaffDuty" duty
                    JOIN "CruiseStaff" staff USING (cruise_staff_id)
                    WHERE duty.%s = ? AND duty.ended_at IS NULL
                        AND staff.user_id = ? AND staff.status = 'ACTIVE'
                        AND staff.role_in_cruise = 'ONBOARD_STAFF' AND %s)
                """.formatted(resource, cruiseMatch), Boolean.class, id, principal.getUserId()));
    }

    private static void deny() {
        throw new AccessDeniedException("Hoạt động không thuộc nhiệm vụ được phân công của bạn.");
    }
}
