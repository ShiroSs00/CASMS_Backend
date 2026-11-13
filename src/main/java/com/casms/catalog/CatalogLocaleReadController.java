package com.casms.catalog;

import com.casms.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Internal operational catalogue labels. Domain identifiers and prices stay locale-neutral. */
@RestController
@RequestMapping("/catalog/labels")
@RequiredArgsConstructor
@PreAuthorize("principal.enabled and principal.accountNonLocked and principal.staff and hasAnyAuthority('SERVICE_READ','ACTIVITY_READ','TOUR_PACKAGE_MANAGE','CRUISE_READ','SUPPLIER_READ')")
public class CatalogLocaleReadController {
    private final JdbcTemplate jdbc;
    private static final Pattern INPUT_LOCALE = Pattern.compile("^[a-zA-Z]{2}(-[a-zA-Z]{2})?$");
    private static final Pattern LOCALE = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    public enum Kind { SERVICE_CATEGORY, SERVICE, ACTIVITY, PACKAGE, POLICY, SHORE_TOUR }
    static String normalizeLocale(String requested) {
        String raw = requested == null || requested.isBlank() ? "vi" : requested.trim();
        // Locale.forLanguageTag silently truncates malformed tags; reject them first.
        if (!INPUT_LOCALE.matcher(raw).matches()) {
            throw new IllegalArgumentException("Supported locale syntax: vi, en, en-US, etc.");
        }
        String normalized = Locale.forLanguageTag(raw).toLanguageTag();
        if (!LOCALE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Supported locale syntax: vi, en, en-US, etc.");
        }
        return normalized;
    }
    public record Label(String kind, String code, String locale, String resolvedLocale,
                        String displayName, String description, String status) {}

    @GetMapping("/{kind}")
    public ApiResponse<List<Label>> list(@PathVariable Kind kind,
            @RequestParam(defaultValue = "vi") String locale,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page >= 0 and 1 <= size <= 100 are required");
        }
        String normalized = normalizeLocale(locale);
        String language = normalized.substring(0, 2);
        String source = switch (kind) {
            case SERVICE_CATEGORY -> "SELECT category_code AS code, category_name AS name, "
                + "NULL::text AS description, 'ACTIVE'::text AS status FROM \"ServiceCategory\"";
            case SERVICE -> "SELECT service_code AS code, service_name AS name, "
                + "description::text AS description, status::text AS status FROM \"Service\"";
            case ACTIVITY -> "SELECT activity_code AS code, activity_name AS name, "
                + "description::text AS description, status::text AS status FROM \"OnboardActivity\" WHERE catalog_entry";
            case PACKAGE -> "SELECT package_code AS code, package_name AS name, "
                + "description::text AS description, status::text AS status FROM \"TourPackage\"";
            case POLICY -> "SELECT policy_code AS code, policy_name AS name, "
                + "description::text AS description, status::text AS status FROM \"CommercialPolicyCatalog\"";
            case SHORE_TOUR -> "SELECT tour_code AS code, tour_name AS name, "
                + "NULL::text AS description, status::text AS status FROM \"OperationalSeedTour\"";
        };
        String sql = """
            SELECT b.code, b.status,
              COALESCE(e.display_name, l.display_name, v.display_name, b.name) AS display_name,
              COALESCE(e.description, l.description, v.description, b.description) AS description,
              CASE WHEN e.display_name IS NOT NULL THEN ?
                   WHEN l.display_name IS NOT NULL THEN ? ELSE 'vi' END AS resolved_locale
            FROM ( %s ) b
            LEFT JOIN "CatalogTranslation" e
              ON e.entity_kind = ? AND e.entity_code = b.code AND e.locale = ?
            LEFT JOIN "CatalogTranslation" l
              ON l.entity_kind = ? AND l.entity_code = b.code AND l.locale = ?
            LEFT JOIN "CatalogTranslation" v
              ON v.entity_kind = ? AND v.entity_code = b.code AND v.locale = 'vi'
            ORDER BY b.code LIMIT ? OFFSET ?
            """.formatted(source);
        List<Label> data = jdbc.query(sql, (rs, n) -> new Label(kind.name(), rs.getString("code"),
                normalized, rs.getString("resolved_locale"), rs.getString("display_name"),
                rs.getString("description"), rs.getString("status")),
            normalized, language, kind.name(), normalized, kind.name(), language, kind.name(),
            size, (long) page * size);
        return ApiResponse.success(data);
    }
}
