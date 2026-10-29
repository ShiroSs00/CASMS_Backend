package com.casms.trip.service;

import com.casms.ship.entity.Port;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Bảng ánh xạ chuẩn hóa địa lý cảng biển và điểm đến Việt Nam (Bắc Bộ - Trung Bộ - Nam Bộ)
 */
@Slf4j
public final class VietnamGeographyRegistry {

    private VietnamGeographyRegistry() {}

    private static final Map<String, String> PORT_CODE_TO_REGION;
    private static final Map<String, String> DESTINATION_KEYWORD_TO_REGION;

    static {
        Map<String, String> ports = new HashMap<>();
        // 1. Miền Bắc (Bắc Bộ)
        ports.put("VNHLG", "BAC_BO"); // Hạ Long International Cruise Port
        ports.put("VNHPH", "BAC_BO"); // Hải Phòng Port
        ports.put("VNCAM", "BAC_BO"); // Cẩm Phả
        ports.put("VNCPH", "BAC_BO"); // Cẩm Phả alt
        ports.put("VNQNH", "BAC_BO"); // Cái Lân / Quảng Ninh
        ports.put("VNHTH", "BAC_BO"); // Hòn Gai
        ports.put("VNLCG", "BAC_BO"); // Lạch Huyện
        ports.put("VNDSP", "BAC_BO"); // Đình Vũ
        ports.put("VNTHA", "BAC_BO"); // Thanh Hóa
        ports.put("VNNBI", "BAC_BO"); // Nghi Sơn

        // 2. Miền Trung (Trung Bộ)
        ports.put("VNDAD", "TRUNG_BO"); // Đà Nẵng / Tiên Sa
        ports.put("VNNHA", "TRUNG_BO"); // Nha Trang Tourist Port
        ports.put("VNHUE", "TRUNG_BO"); // Chân Mây / Thừa Thiên Huế
        ports.put("VNCNA", "TRUNG_BO"); // Cam Ranh International Port
        ports.put("VNUIH", "TRUNG_BO"); // Quy Nhơn Port
        ports.put("VNCAN", "TRUNG_BO"); // Cửa Lò
        ports.put("VNCUA", "TRUNG_BO"); // Cửa Lò alt
        ports.put("VNDON", "TRUNG_BO"); // Đồng Hới / Hòn La
        ports.put("VNVAN", "TRUNG_BO"); // Vũng Áng
        ports.put("VNDQT", "TRUNG_BO"); // Dung Quất
        ports.put("VNTKY", "TRUNG_BO"); // Kỳ Hà / Chu Lai
        ports.put("VNTUY", "TRUNG_BO"); // Tuy Hòa / Vũng Rô
        ports.put("VNPTT", "TRUNG_BO"); // Phan Thiết

        // 3. Miền Nam (Nam Bộ)
        ports.put("VNSGN", "NAM_BO"); // TP. Hồ Chí Minh / Sài Gòn Hiệp Phước
        ports.put("VNVUT", "NAM_BO"); // Cái Mép - Phú Mỹ / Bà Rịa - Vũng Tàu
        ports.put("VNPQC", "NAM_BO"); // Phú Quốc International Port
        ports.put("VNCTO", "NAM_BO"); // Cần Thơ Port
        ports.put("VNBTR", "NAM_BO"); // Bến Tre
        ports.put("VNCMU", "NAM_BO"); // Cà Mau / Năm Căn
        ports.put("VNMTH", "NAM_BO"); // Mỹ Tho
        ports.put("VNSOT", "NAM_BO"); // Sóc Trăng / Trần Đề
        ports.put("VNHAT", "NAM_BO"); // Hà Tiên
        ports.put("VNDTI", "NAM_BO"); // Đồng Tháp

        PORT_CODE_TO_REGION = Collections.unmodifiableMap(ports);

        Map<String, String> kw = new HashMap<>();
        // Bắc Bộ keywords
        kw.put("bắc bộ", "BAC_BO");
        kw.put("miền bắc", "BAC_BO");
        kw.put("hạ long", "BAC_BO");
        kw.put("hải phòng", "BAC_BO");
        kw.put("quảng ninh", "BAC_BO");
        kw.put("cẩm phả", "BAC_BO");
        kw.put("hòn gai", "BAC_BO");
        kw.put("nghi sơn", "BAC_BO");

        // Trung Bộ keywords
        kw.put("trung bộ", "TRUNG_BO");
        kw.put("miền trung", "TRUNG_BO");
        kw.put("đà nẵng", "TRUNG_BO");
        kw.put("nha trang", "TRUNG_BO");
        kw.put("huế", "TRUNG_BO");
        kw.put("chân mây", "TRUNG_BO");
        kw.put("cam ranh", "TRUNG_BO");
        kw.put("quy nhơn", "TRUNG_BO");
        kw.put("cửa lò", "TRUNG_BO");
        kw.put("đồng hới", "TRUNG_BO");
        kw.put("hòn la", "TRUNG_BO");
        kw.put("vũng áng", "TRUNG_BO");
        kw.put("quảng bình", "TRUNG_BO");
        kw.put("quảng trị", "TRUNG_BO");
        kw.put("quảng nam", "TRUNG_BO");
        kw.put("quảng ngãi", "TRUNG_BO");
        kw.put("bình định", "TRUNG_BO");
        kw.put("phú yên", "TRUNG_BO");
        kw.put("khánh hòa", "TRUNG_BO");
        kw.put("ninh thuận", "TRUNG_BO");
        kw.put("bình thuận", "TRUNG_BO");

        // Nam Bộ keywords
        kw.put("nam bộ", "NAM_BO");
        kw.put("miền nam", "NAM_BO");
        kw.put("sài gòn", "NAM_BO");
        kw.put("hồ chí minh", "NAM_BO");
        kw.put("vũng tàu", "NAM_BO");
        kw.put("cái mép", "NAM_BO");
        kw.put("phú mỹ", "NAM_BO");
        kw.put("phú quốc", "NAM_BO");
        kw.put("kiên giang", "NAM_BO");
        kw.put("cần thơ", "NAM_BO");
        kw.put("bến tre", "NAM_BO");
        kw.put("cà mau", "NAM_BO");
        kw.put("năm căn", "NAM_BO");
        kw.put("hà tiên", "NAM_BO");
        kw.put("mỹ tho", "NAM_BO");
        kw.put("tiền giang", "NAM_BO");
        kw.put("đồng bằng sông cửu long", "NAM_BO");

        DESTINATION_KEYWORD_TO_REGION = Collections.unmodifiableMap(kw);
    }

    /**
     * Xác định miền chuẩn hóa cho một cảng biển
     */
    public static String resolveDomesticRegion(Port port) {
        if (port == null) return null;

        // 1. Kiểm tra trực tiếp trên Port nếu đã gán domesticRegion
        if (port.getDomesticRegion() != null && !port.getDomesticRegion().isBlank()) {
            return port.getDomesticRegion().trim().toUpperCase();
        }

        // 2. Kiểm tra trực tiếp trên Destination nếu đã gán domesticRegion
        if (port.getDestination() != null && port.getDestination().getDomesticRegion() != null
                && !port.getDestination().getDomesticRegion().isBlank()) {
            return port.getDestination().getDomesticRegion().trim().toUpperCase();
        }

        // 3. Tra cứu theo bảng mã UN/LOCODE chuẩn hóa
        String code = port.getPortCode() != null ? port.getPortCode().trim().toUpperCase() : "";
        if (PORT_CODE_TO_REGION.containsKey(code)) {
            return PORT_CODE_TO_REGION.get(code);
        }

        // 4. Tra cứu từ khóa theo tên điểm đến (Destination Name)
        if (port.getDestination() != null && port.getDestination().getDestinationName() != null) {
            String destLower = port.getDestination().getDestinationName().toLowerCase();
            for (Map.Entry<String, String> entry : DESTINATION_KEYWORD_TO_REGION.entrySet()) {
                if (destLower.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        // 5. Tra cứu từ khóa theo tên hoặc mô tả cảng
        String portLower = (port.getPortName() != null ? port.getPortName().toLowerCase() : "") + " "
                + (port.getDescription() != null ? port.getDescription().toLowerCase() : "");
        for (Map.Entry<String, String> entry : DESTINATION_KEYWORD_TO_REGION.entrySet()) {
            if (portLower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 6. Quy tắc tọa độ địa lý vĩ độ (Latitude Bounding) cho cảng Việt Nam
        if ("VN".equalsIgnoreCase(port.getCountryCode()) && port.getLatitude() != null) {
            double lat = port.getLatitude().doubleValue();
            if (lat >= 19.5) {
                return "BAC_BO";
            } else if (lat >= 11.5) {
                return "TRUNG_BO";
            } else {
                return "NAM_BO";
            }
        }

        return null;
    }

    public static Map<String, String> getAllStandardPortMappings() {
        return PORT_CODE_TO_REGION;
    }
}
