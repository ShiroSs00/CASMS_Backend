package com.casms.catalog;

import com.casms.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only draft catalogue benefits; final usage enforcement must remain in booking/fulfillment. */
@RestController
@RequestMapping("/catalog/package-benefits")
@RequiredArgsConstructor
@PreAuthorize("principal.enabled and principal.accountNonLocked and principal.staff and hasAnyAuthority('TOUR_PACKAGE_MANAGE','SERVICE_READ','CRUISE_READ')")
public class PackageBenefitReadController {
    private final JdbcTemplate jdbc;

    public record Benefit(String resourceType, String resourceCode, String benefitType,
                          Integer quantityLimit, String unitCode, String ownerScope,
                          BigDecimal discountPercent) {}
    public record PackageBenefits(String packageCode, String packageType, String status,
                                  List<Benefit> benefits, List<String> blockers) {}

    static boolean configuredQuota(Integer quantity, String unit) {
        return quantity != null && quantity > 0 && unit != null && !unit.isBlank();
    }

    @GetMapping("/{code}")
    public ApiResponse<PackageBenefits> detail(@PathVariable String code) {
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{0,49}")) {
            throw new IllegalArgumentException("Invalid package code");
        }
        List<Map<String, Object>> packages = jdbc.queryForList("""
            SELECT package_code, package_type::text AS package_type, status::text AS status
            FROM "TourPackage" WHERE package_code = ?
            """, code);
        if (packages.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found");
        var p = packages.getFirst();
        List<Benefit> benefits = new ArrayList<>();
        benefits.addAll(jdbc.query("""
            SELECT s.service_code AS resource_code, ps.benefit_type::text AS benefit_type,
              ps.quantity_limit, ps.unit_code, ps.owner_scope, ps.discount_percent
            FROM "TourPackageService" ps JOIN "TourPackage" p USING (tour_package_id)
            JOIN "Service" s USING (service_id)
            WHERE p.package_code = ? ORDER BY s.service_code
            """, (rs, row) -> new Benefit("SERVICE", rs.getString("resource_code"),
                rs.getString("benefit_type"), (Integer) rs.getObject("quantity_limit"),
                rs.getString("unit_code"), rs.getString("owner_scope"),
                rs.getBigDecimal("discount_percent")), code));
        benefits.addAll(jdbc.query("""
            SELECT a.activity_code AS resource_code, pa.benefit_type::text AS benefit_type,
              pa.quantity_limit, pa.unit_code, pa.owner_scope
            FROM "TourPackageActivity" pa JOIN "TourPackage" p USING (tour_package_id)
            JOIN "OnboardActivity" a USING (onboard_activity_id)
            WHERE p.package_code = ? ORDER BY a.activity_code
            """, (rs, row) -> new Benefit("ACTIVITY", rs.getString("resource_code"),
                rs.getString("benefit_type"), (Integer) rs.getObject("quantity_limit"),
                rs.getString("unit_code"), rs.getString("owner_scope"), null), code));
        benefits.addAll(jdbc.query("""
            SELECT x.excursion_code AS resource_code, px.benefit_type::text AS benefit_type,
              px.quantity_limit, px.unit_code, px.owner_scope
            FROM "TourPackageExcursion" px JOIN "TourPackage" p USING (tour_package_id)
            JOIN "ShoreExcursion" x USING (shore_excursion_id)
            WHERE p.package_code = ? ORDER BY x.excursion_code
            """, (rs, row) -> new Benefit("SHORE_EXCURSION", rs.getString("resource_code"),
                rs.getString("benefit_type"), (Integer) rs.getObject("quantity_limit"),
                rs.getString("unit_code"), rs.getString("owner_scope"), null), code));
        List<String> blockers = new ArrayList<>();
        if (benefits.isEmpty()) blockers.add("PACKAGE_BENEFITS_MISSING");
        for (Benefit b : benefits) {
            if ("INCLUDED".equals(b.benefitType()) && !configuredQuota(b.quantityLimit(), b.unitCode()))
                blockers.add("BENEFIT_QUOTA_MISSING:" + b.resourceType() + ":" + b.resourceCode());
        }
        return ApiResponse.success(new PackageBenefits(code, p.get("package_type").toString(),
                p.get("status").toString(), benefits, blockers));
    }
}
