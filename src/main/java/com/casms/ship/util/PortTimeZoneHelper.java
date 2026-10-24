package com.casms.ship.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Tiện ích hỗ trợ quản lý múi giờ, định dạng và tự động suy luận múi giờ cảng theo chuẩn IANA.
 */
@Slf4j
public final class PortTimeZoneHelper {

    private PortTimeZoneHelper() {}

    /**
     * Bản đồ ánh xạ mặc định mã quốc gia (ISO 3166-1 alpha-2) sang múi giờ IANA chuẩn.
     * Áp dụng cho các tuyến du thuyền chính trong khu vực Châu Á.
     */
    private static final Map<String, String> COUNTRY_DEFAULT_TIMEZONES = Map.ofEntries(
            Map.entry("JP", "Asia/Tokyo"),          // Nhật Bản (JST: UTC+9, không có DST)
            Map.entry("VN", "Asia/Ho_Chi_Minh"),    // Việt Nam (ICT: UTC+7)
            Map.entry("SG", "Asia/Singapore"),      // Singapore (SGT: UTC+8)
            Map.entry("TH", "Asia/Bangkok"),        // Thái Lan (ICT: UTC+7)
            Map.entry("KR", "Asia/Seoul"),          // Hàn Quốc (KST: UTC+9)
            Map.entry("MY", "Asia/Kuala_Lumpur"),   // Malaysia (MYT: UTC+8)
            Map.entry("PH", "Asia/Manila"),         // Philippines (PST: UTC+8)
            Map.entry("TW", "Asia/Taipei"),         // Đài Loan (CST: UTC+8)
            Map.entry("HK", "Asia/Hong_Kong"),      // Hồng Kông (HKT: UTC+8)
            Map.entry("MO", "Asia/Macau"),          // Ma Cao (CST: UTC+8)
            Map.entry("KH", "Asia/Phnom_Penh"),     // Campuchia (ICT: UTC+7)
            Map.entry("ID", "Asia/Jakarta"),        // Indonesia - Western (WIB: UTC+7)
            Map.entry("CN", "Asia/Shanghai")        // Trung Quốc (CST: UTC+8)
    );

    /**
     * Tự động suy luận múi giờ IANA mặc định dựa trên mã quốc gia (ISO 3166-1 alpha-2).
     */
    public static Optional<String> resolveDefaultTimeZoneByCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(COUNTRY_DEFAULT_TIMEZONES.get(countryCode.trim().toUpperCase()));
    }

    /**
     * Kiểm tra xem timeZoneId có hợp lệ theo chuẩn IANA Database của Java hay không.
     */
    public static boolean isValidTimeZoneId(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            return false;
        }
        try {
            ZoneId.of(timeZoneId.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Tính toán UTC Offset hiện tại (ví dụ: "+09:00", "+07:00", "+00:00").
     */
    public static String calculateUtcOffset(String timeZoneId) {
        return calculateUtcOffset(timeZoneId, Instant.now());
    }

    /**
     * Tính toán UTC Offset tại một mốc thời điểm cụ thể.
     */
    public static String calculateUtcOffset(String timeZoneId, Instant instant) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            return null;
        }
        try {
            ZoneId zoneId = ZoneId.of(timeZoneId.trim());
            ZoneOffset offset = zoneId.getRules().getOffset(instant != null ? instant : Instant.now());
            return offset.getId().equals("Z") ? "+00:00" : offset.getId();
        } catch (Exception e) {
            log.warn("Cannot calculate UTC offset for invalid timeZoneId: {}", timeZoneId);
            return null;
        }
    }

    /**
     * Lấy chuỗi hiển thị múi giờ kèm UTC offset (ví dụ: "Asia/Tokyo (UTC+09:00)").
     */
    public static String formatTimeZoneDisplay(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            return null;
        }
        String offset = calculateUtcOffset(timeZoneId);
        return offset != null ? timeZoneId.trim() + " (UTC" + offset + ")" : timeZoneId.trim();
    }

    /**
     * Lấy thời gian địa phương hiện tại tại múi giờ đó theo chuẩn ISO-8601 (ví dụ: "2026-10-24T15:15:30+09:00").
     */
    public static String getCurrentLocalTimeIso(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            return null;
        }
        try {
            ZoneId zoneId = ZoneId.of(timeZoneId.trim());
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            return now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            log.warn("Cannot format current local time for invalid timeZoneId: {}", timeZoneId);
            return null;
        }
    }
}
