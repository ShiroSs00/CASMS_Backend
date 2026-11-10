package com.casms.ship.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/ships/{shipId}/areas")
@RequiredArgsConstructor
public class ShipAreaUsageController {
    private final JdbcTemplate jdbc;
    public enum Usage { OTHER, SERVICE, ACTIVITY_SPACE }
    public record UsageRequest(@NotNull Usage usage) {}

    @GetMapping("/usages")
    @PreAuthorize("hasAuthority('SHIP_CONFIGURATION_READ') or (hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT'))")
    @Transactional(readOnly=true)
    public ApiResponse<List<Map<String,Object>>> list(@PathVariable UUID shipId) {
        return ApiResponse.success(jdbc.queryForList("""
                SELECT o.ship_model_object_id AS id,o.display_name,o.area_usage,o.deck_id,m.status AS model_status,
                  EXISTS(SELECT 1 FROM "AreaServiceAssignment" a WHERE a.area_object_id=o.ship_model_object_id AND a.enabled) AS has_services
                FROM "ShipModelObject" o JOIN "ShipModel" m USING(ship_model_id)
                WHERE m.ship_id=? AND o.object_type='FACILITY' ORDER BY o.display_name,o.ship_model_object_id
                """, shipId));
    }

    @PutMapping("/{areaId}/usage")
    @PreAuthorize("hasAuthority('SHIP_MODEL_OBJECT_UPDATE')")
    @Transactional
    public ApiResponse<Void> update(@PathVariable UUID shipId, @PathVariable UUID areaId,
                                    @RequestBody @Valid UsageRequest input) {
        var rows = jdbc.queryForList("""
                SELECT o.ship_model_object_id FROM "ShipModelObject" o JOIN "ShipModel" m USING(ship_model_id)
                WHERE o.ship_model_object_id=? AND m.ship_id=? AND o.object_type='FACILITY' FOR UPDATE OF o
                """, areaId, shipId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Ship area", areaId);
        if (input.usage() == Usage.ACTIVITY_SPACE && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM \"AreaServiceAssignment\" WHERE area_object_id=? AND enabled)", Boolean.class, areaId)))
            throw new BusinessException("AREA_HAS_SERVICES", "Unassign enabled services before classifying an activity space");
        jdbc.update("UPDATE \"ShipModelObject\" SET area_usage=? WHERE ship_model_object_id=?", input.usage().name(), areaId);
        return ApiResponse.success(null);
    }
}
