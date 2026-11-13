package com.casms.catalog;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Human-reviewed staging tours; no auto-promotion to live ShoreExcursion. */
@RestController
@RequestMapping("/catalog/tour-candidates")
@RequiredArgsConstructor
@PreAuthorize("principal.enabled and principal.accountNonLocked and hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_VALIDATE') and hasAuthority('SUPPLIER_VERIFY')")
public class OperationalTourReviewController {
    private final JdbcTemplate jdbc;

    public record ReviewInput(@NotNull UUID portId, @NotNull UUID supplierId,
            @NotBlank @Size(max = 2048) String portEvidenceUrl,
            @NotBlank @Size(max = 2048) String supplierEvidenceUrl,
            @NotBlank String note) {}
    public record Candidate(String code, String name, String status, UUID portId, UUID supplierId) {}

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
          SELECT tour_code, tour_name, status, verified_port_id, verified_supplier_id
          FROM "OperationalSeedTour" ORDER BY tour_code LIMIT 200
          """, (r,i) -> new Candidate(r.getString(1),r.getString(2),r.getString(3),
             r.getObject(4,UUID.class),r.getObject(5,UUID.class))));
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
          SELECT tour_name, status FROM "OperationalSeedTour" WHERE tour_code=? FOR UPDATE
          """,code);
        if (row.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Tour not found");
        if (!"DRAFT".equals(row.getFirst().get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a draft can be verified");
        boolean portValid = !jdbc.queryForList("""
                SELECT port_id FROM "Port" WHERE port_id=? AND status='ACTIVE'
                  AND trim(country_code)='VN' AND latitude IS NOT NULL AND longitude IS NOT NULL
                FOR SHARE
                """, UUID.class, input.portId()).isEmpty();
        boolean supplierValid = !jdbc.queryForList("""
                SELECT supplier_id FROM "Supplier" WHERE supplier_id=? AND status='ACTIVE'
                  AND verification_status='VERIFIED'
                FOR SHARE
                """, UUID.class, input.supplierId()).isEmpty();
        if (!portValid || !supplierValid)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Active Vietnam port with coordinates and verified active supplier required");
        jdbc.update("""
          UPDATE "OperationalSeedTour" SET status='VERIFIED',verified_port_id=?,
            verified_supplier_id=?,reviewed_by=?,reviewed_at=now(),
            port_evidence_url=?,supplier_evidence_url=?,review_note=? WHERE tour_code=?
          """,input.portId(),input.supplierId(),actor.getUserId(),
              input.portEvidenceUrl().trim(),input.supplierEvidenceUrl().trim(),input.note().trim(),code);
        return ApiResponse.success(new Candidate(code, (String) row.getFirst().get("tour_name"),
                "VERIFIED", input.portId(), input.supplierId()));
    }
}
