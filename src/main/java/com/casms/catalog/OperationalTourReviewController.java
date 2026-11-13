package com.casms.catalog;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Human-reviewed staging tours; no auto-promotion to live ShoreExcursion. */
@RestController
@RequestMapping("/catalog/tour-candidates")
@RequiredArgsConstructor
@PreAuthorize("principal.enabled and principal.accountNonLocked and hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_VALIDATE') and hasAuthority('SUPPLIER_VERIFY')")
public class OperationalTourReviewController {
    private final JdbcTemplate jdbc;

    public record ReviewInput(@NotNull UUID portId, @NotNull UUID destinationId,
            @NotNull UUID cruiseStopId, @NotNull UUID supplierId,
            @NotNull @Min(0) Integer roundTripTravelMinutes, @NotNull Instant tourStartTime,
            @NotBlank @Size(max = 2048) String portEvidenceUrl,
            @NotBlank @Size(max = 2048) String supplierEvidenceUrl,
            @NotBlank String note) {}
    public record Candidate(String code, String name, String status, UUID portId,
                            UUID destinationId, UUID cruiseStopId, UUID supplierId) {}

    static boolean validEvidenceUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ignored) { return false; }
    }

    @GetMapping
    public ApiResponse<List<Candidate>> list() {
        return ApiResponse.success(jdbc.query("""
          SELECT tour_code, tour_name, status, verified_port_id, verified_destination_id,
                 verified_cruise_stop_id, verified_supplier_id
          FROM "OperationalSeedTour" ORDER BY tour_code LIMIT 200
          """, (r,i) -> new Candidate(r.getString(1),r.getString(2),r.getString(3),
             r.getObject(4,UUID.class),r.getObject(5,UUID.class),r.getObject(6,UUID.class),
             r.getObject(7,UUID.class))));
    }

    @PostMapping("/{code}/verify")
    @Transactional
    public ApiResponse<Candidate> verify(@PathVariable String code,
            @Valid @RequestBody ReviewInput input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        if (actor == null || actor.getUserId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Authenticated reviewer required");
        if (!code.matches("VN-SHORE-[0-9]{3}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid staging tour code");
        if (!validEvidenceUrl(input.portEvidenceUrl()) || !validEvidenceUrl(input.supplierEvidenceUrl()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"HTTP(S) evidence URLs are required");
        if (input.note().trim().length() < 12 || input.note().trim().length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Review note must be 12..2000 chars");
        var row = jdbc.queryForList("""
          SELECT tour_name, status, duration_minutes FROM "OperationalSeedTour" WHERE tour_code=? FOR UPDATE
          """,code);
        if (row.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Tour not found");
        if (!"DRAFT".equals(row.getFirst().get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a draft can be verified");
        boolean portValid = !jdbc.queryForList("""
                SELECT p.port_id FROM "Port" p
                JOIN "PortDestinationAccess" a ON a.port_id=p.port_id AND a.destination_id=?
                JOIN "Destination" d ON d.destination_id=a.destination_id AND d.status='ACTIVE'
                WHERE p.port_id=? AND p.status='ACTIVE'
                  AND trim(p.country_code)='VN' AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL
                FOR SHARE OF p
                """, UUID.class, input.destinationId(), input.portId()).isEmpty();
        boolean supplierValid = !jdbc.queryForList("""
                SELECT supplier_id FROM "Supplier" WHERE supplier_id=? AND status='ACTIVE'
                  AND verification_status='VERIFIED'
                FOR SHARE
                """, UUID.class, input.supplierId()).isEmpty();
        var stopRows = jdbc.queryForList("""
                SELECT s.planned_arrival, s.planned_departure, s.all_aboard_time
                FROM "CruiseStop" s WHERE s.cruise_stop_id=? AND s.port_id=?
                  AND s.status NOT IN ('OMITTED','SUBSTITUTED')
                  AND (s.destination_id=? OR EXISTS(SELECT 1 FROM "CruiseDestinationVisit" v
                       WHERE v.cruise_stop_id=s.cruise_stop_id AND v.destination_id=?)) FOR SHARE
                """, input.cruiseStopId(), input.portId(), input.destinationId(), input.destinationId());
        if (!portValid || !supplierValid || stopRows.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Active Vietnam port serving the destination, planned port call and verified supplier required");
        var stop = stopRows.getFirst();
        Instant arrival = ((java.sql.Timestamp) stop.get("planned_arrival")).toInstant();
        Instant deadline = ((java.sql.Timestamp) (stop.get("all_aboard_time") == null
                ? stop.get("planned_departure") : stop.get("all_aboard_time"))).toInstant();
        int durationMinutes = ((Number) row.getFirst().get("duration_minutes")).intValue();
        Instant plannedReturn = input.tourStartTime()
                .plus(Duration.ofMinutes(durationMinutes + input.roundTripTravelMinutes()));
        if (input.tourStartTime().isBefore(arrival) || !plannedReturn.isBefore(deadline))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tour travel and duration must fit the port-call return deadline");
        jdbc.update("""
          UPDATE "OperationalSeedTour" SET status='VERIFIED',verified_port_id=?,verified_destination_id=?,
            verified_cruise_stop_id=?,verified_supplier_id=?,verified_travel_minutes=?,
            verified_tour_start_time=?,verified_planned_return_time=?,reviewed_by=?,reviewed_at=now(),
            port_evidence_url=?,supplier_evidence_url=?,review_note=? WHERE tour_code=?
          """,input.portId(),input.destinationId(),input.cruiseStopId(),input.supplierId(),
              input.roundTripTravelMinutes(),java.sql.Timestamp.from(input.tourStartTime()),
              java.sql.Timestamp.from(plannedReturn),actor.getUserId(),
              input.portEvidenceUrl().trim(),input.supplierEvidenceUrl().trim(),input.note().trim(),code);
        return ApiResponse.success(new Candidate(code, (String) row.getFirst().get("tour_name"),
                "VERIFIED", input.portId(), input.destinationId(), input.cruiseStopId(), input.supplierId()));
    }
}
