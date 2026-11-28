package com.casms.ship.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ShipOperationalProfile(
        @Size(max = 80) String manufacturerShipCode,
        @Pattern(regexp = "[0-9]{7}", message = "Số IMO phải gồm chính xác 7 chữ số (0-9)") String imoNumber,
        @Size(max = 150) String shipyard,
        @Pattern(regexp = "[A-Z]{2}") String buildCountry,
        @Min(1) @Max(9999) Integer buildYear,
        @PastOrPresent(message = "Ngày bàn giao không được ở tương lai") LocalDate deliveryDate,
        @Pattern(regexp = "[A-Z]{2}", message = "Mã quốc gia đăng ký phải theo chuẩn ISO 3166-1 alpha-2") String registrationCountry,
        @Size(min = 1, max = 50, message = "Số đăng ký phải từ 1 đến 50 ký tự") String registrationNumber,
        @Size(min = 2, max = 150, message = "Tên tổ chức đăng kiểm phải từ 2 đến 150 ký tự") String classificationSociety,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @PastOrPresent(message = "Ngày đăng kiểm gần nhất không được ở tương lai")
        LocalDate classificationDate,
        LocalDate classificationExpiryDate,
        @Size(max = 100) List<@NotBlank @Size(max = 255) String> certificateReferences,
        @Size(max = 100) List<@NotBlank @Size(max = 255) String> warrantyReferences,
        @Size(max = 100) List<@NotBlank @Size(max = 255) String> maintenanceReferences) {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public ShipOperationalProfile {
        manufacturerShipCode = trim(manufacturerShipCode);
        imoNumber = trim(imoNumber);
        shipyard = trim(shipyard);
        buildCountry = upper(buildCountry);
        registrationCountry = upper(registrationCountry);
        registrationNumber = upper(registrationNumber);
        classificationSociety = trim(classificationSociety);
        certificateReferences = normalizeRefs(certificateReferences);
        warrantyReferences = normalizeRefs(warrantyReferences);
        maintenanceReferences = normalizeRefs(maintenanceReferences);
    }

    @AssertTrue(message = "Quốc gia đăng ký và số đăng ký phải được nhập đầy đủ cùng nhau")
    @JsonIgnore
    public boolean isRegistrationComplete() {
        return (registrationCountry == null) == (registrationNumber == null);
    }

    @AssertTrue(message = "Mã quốc gia đăng ký không hợp lệ theo chuẩn ISO 3166-1 alpha-2")
    @JsonIgnore
    public boolean isRegistrationCountrySupported() {
        return registrationCountry == null || ISO_COUNTRIES.contains(registrationCountry);
    }

    @AssertTrue(message = "Số IMO không hợp lệ theo thuật toán kiểm tra checksum IMO")
    @JsonIgnore
    public boolean isImoChecksumValid() {
        if (imoNumber == null || !imoNumber.matches("[0-9]{7}")) {
            return true; // @Pattern handles non-7-digit format
        }
        int sum = 0;
        for (int i = 0; i < 6; i++) {
            sum += (imoNumber.charAt(i) - '0') * (7 - i);
        }
        return (sum % 10) == (imoNumber.charAt(6) - '0');
    }

    @AssertTrue(message = "Ngày hết hạn đăng kiểm không được trước ngày bàn giao")
    @JsonIgnore
    public boolean isExpiryNotBeforeDelivery() {
        return deliveryDate == null || classificationExpiryDate == null
                || !classificationExpiryDate.isBefore(deliveryDate);
    }

    @AssertTrue(message = "Ngày hết hạn đăng kiểm không được trước ngày đăng kiểm gần nhất")
    @JsonIgnore
    public boolean isExpiryNotBeforeLastClassification() {
        return classificationDate == null || classificationExpiryDate == null
                || !classificationExpiryDate.isBefore(classificationDate);
    }

    @AssertTrue(message = "Danh sách tham chiếu hồ sơ không được chứa mục trùng lặp")
    @JsonIgnore
    public boolean isReferencesUnique() {
        return hasNoDuplicates(certificateReferences)
                && hasNoDuplicates(warrantyReferences)
                && hasNoDuplicates(maintenanceReferences);
    }

    public ShipOperationalProfile withLegacyManufacturing(ShipOperationalProfile previous) {
        return new ShipOperationalProfile(
                previous == null ? null : previous.manufacturerShipCode(),
                imoNumber,
                previous == null ? null : previous.shipyard(),
                previous == null ? null : previous.buildCountry(),
                previous == null ? null : previous.buildYear(),
                deliveryDate, registrationCountry, registrationNumber, classificationSociety,
                classificationDate, classificationExpiryDate,
                certificateReferences, warrantyReferences, maintenanceReferences);
    }

    private static List<String> normalizeRefs(List<String> refs) {
        if (refs == null) return null;
        return refs.stream().map(v -> v == null ? "" : v.strip()).toList();
    }

    private static boolean hasNoDuplicates(List<String> refs) {
        if (refs == null || refs.size() < 2) return true;
        Set<String> seen = new HashSet<>();
        for (String item : refs) {
            if (!seen.add(item.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
