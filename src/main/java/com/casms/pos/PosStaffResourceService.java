package com.casms.pos;

import static com.casms.pos.PosPermission.ResourceType;

import com.casms.common.exception.*;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** An area assignment alone grants no service/activity operation. */
@Service
@RequiredArgsConstructor
@Transactional
public class PosStaffResourceService {
    private final JdbcTemplate jdbc;
    private final PosRepository repository;
    private final ResourceOperationService source;

    public List<PosDtos.StaffResource> list(
            UUID ship, UUID area, UUID user, UUID cruise, ResourceType type) {
        UUID assignment = assignment(ship, area, user, cruise);
        return jdbc
                .query(
                        "SELECT * FROM "
                                + table(type)
                                + " WHERE assignment_id=? ORDER BY "
                                + key(type),
                        (r, n) ->
                                new PosDtos.StaffResource(
                                        assignment,
                                        type,
                                        PosRepository.uuid(r, key(type)),
                                        PosRepository.permissions(r, "permissions"),
                                        r.getBoolean("enabled"),
                                        r.getBoolean("inherit_permissions")),
                        assignment)
                .stream()
                .filter(r -> type != ResourceType.SERVICE || hasServiceDuty(assignment, r.resourceId()))
                .map(
                        r ->
                                new PosDtos.StaffResource(
                                        assignment,
                                        type,
                                        r.resourceId(),
                                        effective(
                                                assignment,
                                                type,
                                                r.resourceId(),
                                                source.get(type, r.resourceId()).permissions()),
                                        r.enabled(),
                                        r.inherited()))
                .toList();
    }

    public PosDtos.StaffResource assign(
            UUID ship,
            UUID area,
            UUID user,
            UUID cruise,
            ResourceType type,
            UUID id,
            PosDtos.AssignmentInput input) {
        UUID assignment = assignment(ship, area, user, cruise);
        if (type == ResourceType.SERVICE && !hasServiceDuty(assignment, id)) {
            throw new BusinessException("POS_STAFF_SERVICE_MISMATCH",
                    "HR must assign this service before a POS grant can be issued");
        }
        var policy = source.get(type, id);
        ResourceOperationService.validate(type, input.permissions(), policy.permissions());
        String areaTable =
                type == ResourceType.SERVICE
                        ? "\"AreaServiceAssignment\""
                        : "\"AreaActivityAssignment\"";
        String scope =
                type == ResourceType.ACTIVITY
                        ? " AND EXISTS(SELECT 1 FROM \"OnboardActivity\" act WHERE"
                              + " act.onboard_activity_id=a.activity_id AND act.cruise_id=?)"
                        : "";
        Object[] params =
                type == ResourceType.ACTIVITY
                        ? new Object[] {area, id, cruise}
                        : new Object[] {area, id};
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM "
                                + areaTable
                                + " a WHERE a.area_object_id=? AND a."
                                + key(type)
                                + "=? AND a.enabled"
                                + scope
                                + ")",
                        Boolean.class,
                        params)))
            throw new BusinessException(
                    "STAFF_RESOURCE_NOT_IN_AREA",
                    "Link this resource to the employee's area and cruise first");
        jdbc.update(
                "INSERT INTO "
                        + table(type)
                        + " (assignment_id,"
                        + key(type)
                        + ",permissions,inherit_permissions,enabled) VALUES"
                        + " (?,?,string_to_array(?,','),?,?) ON CONFLICT (assignment_id,"
                        + key(type)
                        + ") DO UPDATE SET"
                        + " permissions=excluded.permissions,inherit_permissions=excluded.inherit_permissions,enabled=excluded.enabled",
                assignment,
                id,
                PosRepository.csv(input.permissions()),
                input.permissions() == null,
                input.enabled());
        return new PosDtos.StaffResource(
                assignment,
                type,
                id,
                effective(assignment, type, id, policy.permissions()),
                input.enabled(),
                input.permissions() == null);
    }

    public void remove(UUID ship, UUID area, UUID user, UUID cruise, ResourceType type, UUID id) {
        UUID assignment = assignment(ship, area, user, cruise);
        jdbc.update(
                "DELETE FROM " + table(type) + " WHERE assignment_id=? AND " + key(type) + "=?",
                assignment,
                id);
    }

    Set<PosPermission> restrict(
            PosService.Access access, ResourceType type, UUID id, Set<PosPermission> parent) {
        boolean manager = manager(access.employee());
        if (type == ResourceType.SERVICE) {
            var assignment = access.assignment();
            boolean matches = manager
                    ? hasServiceDutyForCruise(assignment.cruiseId(), access.employee().getUserId(), id)
                    : hasServiceDuty(assignment.assignmentId(), id);
            if (!matches) return Set.of();
        }
        if (manager) return parent;
        return effective(access.assignment().assignmentId(), type, id, parent);
    }

    private boolean hasServiceDuty(UUID assignment, UUID service) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM "AreaStaffAssignment" a
                    JOIN "CruiseStaff" cs ON cs.cruise_staff_id = a.cruise_staff_id
                    JOIN "CruiseStaffDuty" d ON d.cruise_staff_id = cs.cruise_staff_id
                    WHERE a.assignment_id = ? AND cs.status = 'ACTIVE'
                      AND d.service_id = ? AND d.ended_at IS NULL)
                """, Boolean.class, assignment, service));
    }

    private boolean hasServiceDutyForCruise(UUID cruise, UUID user, UUID service) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM "CruiseStaff" cs
                    JOIN "CruiseStaffDuty" d ON d.cruise_staff_id = cs.cruise_staff_id
                    WHERE cs.cruise_id = ? AND cs.user_id = ? AND cs.status = 'ACTIVE'
                      AND d.service_id = ? AND d.ended_at IS NULL)
                """, Boolean.class, cruise, user, service));
    }

    static boolean manager(CasmsPrincipal user) {
        return user.isStaff() && user.hasRole(Role.OPERATIONS_MANAGER);
    }

    private Set<PosPermission> effective(
            UUID assignment, ResourceType type, UUID id, Set<PosPermission> parent) {
        if (assignment == null) return Set.of();
        var rows =
                jdbc.query(
                        "SELECT * FROM "
                                + table(type)
                                + " WHERE assignment_id=? AND "
                                + key(type)
                                + "=? AND enabled",
                        (r, n) -> {
                            var permissions = EnumSet.noneOf(PosPermission.class);
                            permissions.addAll(parent);
                            if (!r.getBoolean("inherit_permissions"))
                                permissions.retainAll(PosRepository.permissions(r, "permissions"));
                            return Set.copyOf(permissions);
                        },
                        assignment,
                        id);
        return rows.isEmpty() ? Set.of() : rows.getFirst();
    }

    private UUID assignment(UUID ship, UUID area, UUID user, UUID cruise) {
        repository
                .area(ship, area, true)
                .orElseThrow(() -> new ResourceNotFoundException("Physical area in ship", area));
        var ids =
                jdbc.queryForList(
                        "SELECT a.assignment_id FROM \"AreaStaffAssignment\" a JOIN \"CruiseStaff\""
                            + " cs ON cs.cruise_staff_id=a.cruise_staff_id JOIN \"Cruise\" c ON"
                            + " c.cruise_id=cs.cruise_id WHERE a.area_object_id=? AND cs.user_id=?"
                            + " AND cs.cruise_id=? AND c.ship_id=? FOR UPDATE OF a",
                        UUID.class,
                        area,
                        user,
                        cruise,
                        ship);
        if (ids.isEmpty())
            throw new ResourceNotFoundException("Employee area/cruise assignment", user);
        return ids.getFirst();
    }

    private static String table(ResourceType type) {
        return type == ResourceType.SERVICE
                ? "\"StaffServiceAssignment\""
                : "\"StaffActivityAssignment\"";
    }

    private static String key(ResourceType type) {
        return type == ResourceType.SERVICE ? "service_id" : "activity_id";
    }
}
