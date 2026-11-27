package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.entity.Port;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripScheduleVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TripGeographyCatalog {
    private static final Set<String> ISO_COUNTRY_CODES = Set.of(Locale.getISOCountries());

    public enum Kind { COUNTRY, REGION }
    public enum Status { ACTIVE, INACTIVE }
    public record Item(Kind kind, String code, String label, String description, Status status) {}

    private final JdbcTemplate jdbc;

    public List<Item> list(boolean activeOnly) {
        return jdbc.query("""
                SELECT kind, code, label, description, status
                FROM trip_geography_catalog
                WHERE NOT ? OR status = 'ACTIVE'
                ORDER BY kind, display_order, code
                """, (row, index) -> new Item(
                Kind.valueOf(row.getString("kind")), row.getString("code"),
                row.getString("label"), row.getString("description"),
                Status.valueOf(row.getString("status"))), activeOnly);
    }

    @Transactional
    public Item createCountry(String code, String label) {
        String inputCode = code == null ? "" : code.strip();
        String normalized = normalize(inputCode);
        if (!inputCode.matches("[A-Za-z]{2}") || !ISO_COUNTRY_CODES.contains(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COUNTRY_CODE",
                    "Mã quốc gia phải thuộc ISO 3166-1 alpha-2.");
        }
        String countryLabel = label == null ? "" : label.strip();
        if (countryLabel.isBlank() || countryLabel.indexOf(0) >= 0
                || countryLabel.codePointCount(0, countryLabel.length()) > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COUNTRY_LABEL",
                    "Tên quốc gia phải từ 1 đến 100 ký tự.");
        }
        int inserted = jdbc.update("""
                INSERT INTO trip_geography_catalog (kind, code, label, display_order, status)
                SELECT 'COUNTRY', ?, ?, COALESCE(MAX(display_order), 0) + 1, 'ACTIVE'
                FROM trip_geography_catalog
                WHERE kind = 'COUNTRY'
                ON CONFLICT (kind, code) DO NOTHING
                """, normalized, countryLabel);
        if (inserted == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "COUNTRY_ALREADY_EXISTS",
                    "Quốc gia " + normalized + " đã có trong danh mục.");
        }
        return new Item(Kind.COUNTRY, normalized, countryLabel, null, Status.ACTIVE);
    }

    @Transactional
    public Item changeStatus(Kind kind, String code, Status status) {
        String normalized = normalize(code);
        if (kind == Kind.REGION || "VN".equals(normalized)) {
            String vietnamStatus = jdbc.queryForObject("""
                    SELECT status FROM trip_geography_catalog
                    WHERE kind = 'COUNTRY' AND code = 'VN' FOR UPDATE
                    """, String.class);
            if (kind == Kind.REGION && status == Status.ACTIVE
                    && !Status.ACTIVE.name().equals(vietnamStatus)) {
                throw new BusinessException(HttpStatus.CONFLICT, "PARENT_COUNTRY_INACTIVE",
                        "Bật Việt Nam trước khi bật miền.");
            }
        }
        int updated = jdbc.update("""
                UPDATE trip_geography_catalog SET status = ? WHERE kind = ? AND code = ?
                """, status.name(), kind.name(), normalized);
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GEOGRAPHY_NOT_FOUND",
                    "Không tìm thấy mục địa lý " + normalized);
        }
        if (kind == Kind.COUNTRY && "VN".equals(normalized) && status == Status.INACTIVE) {
            jdbc.update("UPDATE trip_geography_catalog SET status = 'INACTIVE' WHERE kind = 'REGION'");
        }
        return list(false).stream()
                .filter(item -> item.kind() == kind && item.code().equals(normalized))
                .findFirst().orElseThrow();
    }

    public Selection selection() {
        Set<String> countries = new HashSet<>();
        Set<String> regions = new HashSet<>();
        for (Item item : list(true)) {
            (item.kind() == Kind.COUNTRY ? countries : regions).add(item.code());
        }
        return new Selection(countries, regions);
    }

    public record Selection(Set<String> countries, Set<String> regions) {
        public List<String> unavailable(Trip trip) {
            List<String> unavailable = new ArrayList<>();
            for (String code : CountryScopeValidator.resolveAllowedCountries(trip)) {
                if (!countries.contains(normalize(code))) unavailable.add(code);
            }
            if (CountryScopeValidator.isDomestic(trip)) {
                for (String code : trip.getDomesticRegions()) {
                    if (code != null && !code.isBlank() && !regions.contains(normalize(code))) {
                        unavailable.add(code);
                    }
                }
            }
            return unavailable;
        }

        public List<String> unavailable(Port port) {
            if (port == null || port.getCountryCode() == null) return List.of();
            String country = normalize(port.getCountryCode());
            if (!countries.contains(country)) return List.of(country);
            String region = VietnamGeographyRegistry.resolveDomesticRegion(port);
            if ("VN".equals(country) && region != null && !regions.contains(region)) {
                return List.of(region);
            }
            return List.of();
        }

        public void requireAvailable(Trip trip) {
            TripGeographyCatalog.requireAvailable(unavailable(trip));
        }

        public void requireAvailable(Port port) {
            TripGeographyCatalog.requireAvailable(unavailable(port));
        }

        public List<String> unavailable(TripScheduleVersion version) {
            Set<String> unavailable = new java.util.LinkedHashSet<>(unavailable(version.getTrip()));
            version.getDays().forEach(day -> unavailable.addAll(unavailable(day.getDeparturePort())));
            version.getStops().forEach(stop -> unavailable.addAll(unavailable(stop.getPort())));
            return List.copyOf(unavailable);
        }

        public void requireAvailable(TripScheduleVersion version) {
            TripGeographyCatalog.requireAvailable(unavailable(version));
        }
    }

    public static String recreateMessage(List<String> codes) {
        return "Quốc gia hoặc miền hiện không thể chọn: " + String.join(", ", codes)
                + ". Vui lòng tạo lại chuyến hoặc lịch nháp với danh mục đang hoạt động.";
    }

    private static void requireAvailable(List<String> unavailable) {
        if (!unavailable.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TRIP_GEOGRAPHY_UNAVAILABLE",
                    recreateMessage(unavailable));
        }
    }

    private static String normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }
}
