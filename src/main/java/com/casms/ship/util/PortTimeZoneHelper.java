package com.casms.ship.util;

import com.casms.ship.dto.response.TimeZoneOptionResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tiện ích hỗ trợ quản lý múi giờ, định dạng và tự động suy luận múi giờ cảng theo chuẩn IANA.
 * Hỗ trợ các tuyến du thuyền nội địa và đa quốc gia trên toàn cầu.
 */
@Slf4j
public final class PortTimeZoneHelper {

    private PortTimeZoneHelper() {}

    /**
     * Bản đồ ánh xạ mặc định mã quốc gia (ISO 3166-1 alpha-2) sang múi giờ IANA chuẩn.
     * Áp dụng cho các tuyến du thuyền quốc tế chính (Châu Á, Châu Âu/Địa Trung Hải, Châu Mỹ/Caribbean, Châu Đại Dương).
     */
    private static final Map<String, String> COUNTRY_DEFAULT_TIMEZONES = Map.ofEntries(
            // --- Châu Á & Đông Nam Á ---
            Map.entry("JP", "Asia/Tokyo"),          // Nhật Bản (JST: UTC+9)
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
            Map.entry("CN", "Asia/Shanghai"),       // Trung Quốc (CST: UTC+8)
            Map.entry("IN", "Asia/Kolkata"),        // Ấn Độ (IST: UTC+5:30)
            Map.entry("AE", "Asia/Dubai"),          // UAE / Dubai (GST: UTC+4)
            Map.entry("QA", "Asia/Qatar"),          // Qatar (AST: UTC+3)
            Map.entry("OM", "Asia/Muscat"),         // Oman (GST: UTC+4)
            Map.entry("SA", "Asia/Riyadh"),         // Saudi Arabia (AST: UTC+3)
            Map.entry("MV", "Indian/Maldives"),     // Maldives (MVT: UTC+5)

            // --- Châu Âu & Địa Trung Hải ---
            Map.entry("IT", "Europe/Rome"),         // Ý (CET: UTC+1 / CEST: UTC+2)
            Map.entry("ES", "Europe/Madrid"),       // Tây Ban Nha (CET: UTC+1 / CEST: UTC+2)
            Map.entry("FR", "Europe/Paris"),        // Pháp (CET: UTC+1 / CEST: UTC+2)
            Map.entry("GR", "Europe/Athens"),       // Hy Lạp (EET: UTC+2 / EEST: UTC+3)
            Map.entry("HR", "Europe/Zagreb"),       // Croatia (CET: UTC+1 / CEST: UTC+2)
            Map.entry("TR", "Europe/Istanbul"),     // Thổ Nhĩ Kỳ (TRT: UTC+3)
            Map.entry("GB", "Europe/London"),       // Vương quốc Anh (GMT: UTC+0 / BST: UTC+1)
            Map.entry("DE", "Europe/Berlin"),       // Đức (CET: UTC+1 / CEST: UTC+2)
            Map.entry("NO", "Europe/Oslo"),         // Na Uy / Fjord (CET: UTC+1 / CEST: UTC+2)
            Map.entry("NL", "Europe/Amsterdam"),    // Hà Lan (CET: UTC+1 / CEST: UTC+2)
            Map.entry("PT", "Europe/Lisbon"),       // Bồ Đào Nha (WET: UTC+0 / WEST: UTC+1)
            Map.entry("SE", "Europe/Stockholm"),    // Thụy Điển (CET: UTC+1 / CEST: UTC+2)
            Map.entry("DK", "Europe/Copenhagen"),   // Đan Mạch (CET: UTC+1 / CEST: UTC+2)
            Map.entry("MT", "Europe/Malta"),        // Malta (CET: UTC+1 / CEST: UTC+2)
            Map.entry("CY", "Asia/Nicosia"),        // Síp (EET: UTC+2 / EEST: UTC+3)

            // --- Châu Mỹ & Vùng Caribbean ---
            Map.entry("US", "America/New_York"),    // Hoa Kỳ - Đông (EST: UTC-5 / EDT: UTC-4)
            Map.entry("CA", "America/Toronto"),     // Canada - Đông (EST: UTC-5 / EDT: UTC-4)
            Map.entry("BS", "America/Nassau"),      // Bahamas (EST: UTC-5 / EDT: UTC-4)
            Map.entry("MX", "America/Mexico_City"), // Mexico (CST: UTC-6)
            Map.entry("KY", "America/Cayman"),      // Quần đảo Cayman (EST: UTC-5)
            Map.entry("JM", "America/Jamaica"),     // Jamaica (EST: UTC-5)
            Map.entry("PR", "America/Puerto_Rico"), // Puerto Rico (AST: UTC-4)
            Map.entry("BR", "America/Sao_Paulo"),   // Brazil (BRT: UTC-3)

            // --- Châu Đại Dương ---
            Map.entry("AU", "Australia/Sydney"),    // Úc (AEST: UTC+10 / AEDT: UTC+11)
            Map.entry("NZ", "Pacific/Auckland"),    // New Zealand (NZST: UTC+12 / NZDT: UTC+13)
            Map.entry("FJ", "Pacific/Fiji")         // Fiji (FJT: UTC+12)
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
     * Phân giải chuỗi timeZoneId thành ZoneId chuẩn IANA (BE-R05a).
     * Tuyệt đối không fallback âm thầm về Asia/Ho_Chi_Minh nếu thiếu hoặc sai định dạng.
     */
    public static ZoneId resolveZone(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            throw new com.casms.common.exception.BusinessException("MISSING_PORT_TIME_ZONE", "Múi giờ của cảng không được để trống.");
        }
        try {
            return ZoneId.of(timeZoneId.trim());
        } catch (Exception e) {
            throw new com.casms.common.exception.BusinessException("INVALID_PORT_TIME_ZONE", "Múi giờ cảng không hợp lệ theo chuẩn IANA: " + timeZoneId);
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

    /**
     * Lấy danh sách các múi giờ IANA chuẩn hỗ trợ cho giao diện người dùng (dropdown / search).
     *
     * @param search Từ khóa tìm kiếm (theo tên múi giờ hoặc thành phố, ví dụ: "Tokyo", "Paris", "New_York")
     * @param countryCode Mã quốc gia (ISO-2) để ưu tiên đưa múi giờ khuyến nghị lên đầu danh sách
     * @return Danh sách các lựa chọn múi giờ kèm UTC offset và giờ địa phương hiện tại
     */
    public static List<TimeZoneOptionResponse> getSupportedTimeZoneOptions(String search, String countryCode) {
        String cleanSearch = search != null ? search.trim().toLowerCase() : null;
        String normalizedCountry = countryCode != null ? countryCode.trim().toUpperCase() : null;
        String recommendedTz = normalizedCountry != null ? COUNTRY_DEFAULT_TIMEZONES.get(normalizedCountry) : null;

        Instant now = Instant.now();

        // Lọc các ZoneId chuẩn IANA (loại bỏ các alias lỗi thời như Etc/*, SystemV/*)
        Set<String> allZoneIds = ZoneId.getAvailableZoneIds();

        List<TimeZoneOptionResponse> options = allZoneIds.stream()
                .filter(id -> isValidCanonicalZone(id))
                .filter(id -> {
                    if (cleanSearch == null || cleanSearch.isEmpty()) {
                        return true;
                    }
                    return id.toLowerCase().contains(cleanSearch);
                })
                .map(id -> {
                    boolean isRec = recommendedTz != null && recommendedTz.equalsIgnoreCase(id);
                    String offset = calculateUtcOffset(id, now);
                    String display = id + (offset != null ? " (UTC" + offset + ")" : "");
                    String localIso = getCurrentLocalTimeIso(id);

                    return TimeZoneOptionResponse.builder()
                            .timeZoneId(id)
                            .displayName(display)
                            .countryCode(isRec ? normalizedCountry : null)
                            .utcOffset(offset)
                            .currentLocalTime(localIso)
                            .isRecommended(isRec)
                            .build();
                })
                .sorted((a, b) -> {
                    // Ưu tiên múi giờ khuyến nghị lên đầu
                    if (a.isRecommended() && !b.isRecommended()) return -1;
                    if (!a.isRecommended() && b.isRecommended()) return 1;
                    return a.getTimeZoneId().compareTo(b.getTimeZoneId());
                })
                .limit(100) // Giới hạn kích thước payload hợp lý cho frontend dropdown
                .collect(Collectors.toList());

        return options;
    }

    private static boolean isValidCanonicalZone(String id) {
        if (id == null) return false;
        // Giữ lại các vùng địa lý chính theo chuẩn IANA
        return id.startsWith("Asia/")
                || id.startsWith("Europe/")
                || id.startsWith("America/")
                || id.startsWith("Pacific/")
                || id.startsWith("Atlantic/")
                || id.startsWith("Indian/")
                || id.startsWith("Australia/")
                || id.startsWith("Africa/")
                || id.equals("UTC");
    }
}
