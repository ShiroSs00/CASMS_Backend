package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.ItineraryType;
import com.casms.trip.entity.Trip;
import org.springframework.http.HttpStatus;

import java.util.*;

/**
 * Validator chuẩn hóa phạm vi quốc gia (Country Scope C18) theo OCV BE Spec v1.2.
 * Quy tắc:
 * 1. DOMESTIC -> bắt buộc VN (kể cả khi countryCodes rỗng).
 * 2. INTERNATIONAL -> sử dụng danh sách countryCodes (yêu cầu cấu hình khi áp dụng).
 * 3. Kiểm tra nhất quán cho cảng xuất phát, cảng đích, và tất cả cảng ghé trung gian.
 */
public final class CountryScopeValidator {

    private CountryScopeValidator() {}

    /**
     * Xác định danh sách mã quốc gia hợp lệ dựa trên Trip.
     * Quy tắc: DOMESTIC luôn trả ["VN"].
     */
    public static List<String> resolveAllowedCountries(Trip trip) {
        if (trip == null) {
            return Collections.emptyList();
        }

        if (isDomestic(trip)) {
            return List.of("VN");
        }

        List<String> codes = trip.getCountryCodes();
        if (codes != null && !codes.isEmpty()) {
            return codes.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .distinct()
                    .toList();
        }

        return Collections.emptyList();
    }

    public static boolean isDomestic(Trip trip) {
        if (trip == null) {
            return false;
        }
        return "DOMESTIC".equalsIgnoreCase(trip.getPlannedScope());
    }

    public static boolean isInternational(Trip trip) {
        if (trip == null) {
            return false;
        }
        return "INTERNATIONAL".equalsIgnoreCase(trip.getPlannedScope());
    }

    /**
     * Kiểm tra tính hợp lệ của một cảng đối với phạm vi của Trip.
     * Ném BusinessException với mã "PORT_COUNTRY_SCOPE_VIOLATION" hoặc "SCOPE_COUNTRY_REQUIRED".
     */
    public static void validatePort(Port port, Trip trip, String portRole) {
        if (port == null || trip == null) {
            return;
        }

        if (isInternational(trip)) {
            List<String> tripCountries = trip.getCountryCodes();
            if (tripCountries == null || tripCountries.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SCOPE_COUNTRY_REQUIRED",
                        "Chuyến đi quốc tế " + trip.getTripCode() + " chưa được cấu hình danh sách quốc gia (countryCodes).");
            }
        }

        List<String> allowed = resolveAllowedCountries(trip);
        if (allowed.isEmpty()) {
            return;
        }

        String portCountry = port.getCountryCode() != null ? port.getCountryCode().trim().toUpperCase() : null;
        if (portCountry == null || !allowed.contains(portCountry)) {
            String roleText = (portRole != null && !portRole.isBlank()) ? portRole : "Cảng";
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PORT_COUNTRY_SCOPE_VIOLATION",
                    String.format("%s '%s' (quốc gia: %s) không thuộc phạm vi quốc gia của Trip nguồn: %s",
                            roleText,
                            port.getPortCode() != null ? port.getPortCode() : (port.getPortName() != null ? port.getPortName() : "N/A"),
                            portCountry != null ? portCountry : "N/A",
                            allowed));
        }

        // Nếu là chuyến nội địa và có giới hạn miền
        if (isDomestic(trip)) {
            List<String> allowedRegions = trip.getDomesticRegions();
            if (allowedRegions != null && !allowedRegions.isEmpty()) {
                String portRegion = VietnamGeographyRegistry.resolveDomesticRegion(port);
                if (portRegion == null || !allowedRegions.contains(portRegion)) {
                    String roleText = (portRole != null && !portRole.isBlank()) ? portRole : "Cảng";
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "PORT_OUTSIDE_COUNTRY_SCOPE",
                            String.format("%s '%s' (miền: %s) không thuộc phạm vi khu vực nội địa cho phép: %s",
                                    roleText,
                                    port.getPortName() != null ? port.getPortName() : port.getPortCode(),
                                    portRegion != null ? portRegion : "CHƯA_XÁC_ĐỊNH",
                                    allowedRegions));
                }
            }
        }
    }
}
