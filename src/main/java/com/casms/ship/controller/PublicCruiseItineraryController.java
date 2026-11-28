package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.response.PublicCruiseItineraryResponse;
import com.casms.ship.service.PublicCruiseItineraryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PublicCruiseItineraryController {
    private final PublicCruiseItineraryService service;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @GetMapping({"/cruise/{cruiseId}/public-itinerary", "/cruises/{cruiseId}/public-itinerary"})
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lịch trình công khai của Departure đã phát hành",
            description = "Đọc ngày, cảng và DestinationVisit từ lịch trình thực tế. Dữ liệu lịch trình không xác nhận trạng thái mở bán. Dùng sellability và catalog Cruise Package riêng.")
    public ApiResponse<PublicCruiseItineraryResponse> get(@PathVariable UUID cruiseId) {
        return ApiResponse.success(service.get(cruiseId));
    }

    @GetMapping({"/cruise/{cruiseId}/public-experiences", "/cruises/{cruiseId}/public-experiences"})
    @PreAuthorize("permitAll()")
    @Operation(summary = "Danh mục dịch vụ trên tàu, hoạt động giải trí và tour tham quan bờ công khai của chuyến")
    public ApiResponse<Map<String, Object>> experiences(@PathVariable UUID cruiseId) {
        List<Map<String, Object>> services = jdbc.queryForList(
                "SELECT DISTINCT s.service_id AS \"serviceId\", s.service_name AS \"serviceName\", "
                        + "COALESCE(sc.category_name, s.service_type) AS \"categoryName\", "
                        + "d.deck_name AS \"deckName\", d.deck_number AS \"deckNumber\", "
                        + "s.description, s.price, COALESCE(s.currency_code, 'VND') AS \"currencyCode\", "
                        + "s.operating_periods AS \"operatingPeriods\", "
                        + "s.operating_windows::text AS \"operatingWindows\", "
                        + "s.can_buy_pre_cruise AS \"canBuyPreCruise\", s.can_buy_onboard AS \"canBuyOnboard\" "
                        + "FROM \"Cruise\" c "
                        + "JOIN \"Deck\" d ON d.ship_id = c.ship_id "
                        + "JOIN \"DeckServiceAssignment\" dsa ON dsa.deck_id = d.deck_id "
                        + "JOIN \"Service\" s ON s.service_id = dsa.service_id "
                        + "LEFT JOIN \"ServiceCategory\" sc ON sc.service_category_id = s.service_category_id "
                        + "WHERE c.cruise_id = ? AND COALESCE(s.status, 'ACTIVE') <> 'INACTIVE' "
                        + "ORDER BY d.deck_number NULLS LAST, s.service_name",
                cruiseId);
        for (Map<String, Object> row : services) {
            if (row.get("operatingWindows") instanceof String json) {
                try {
                    row.put("operatingWindows", mapper.readTree(json));
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("Invalid stored service operating windows", ex);
                }
            }
        }

        List<Map<String, Object>> activities = jdbc.queryForList(
                "SELECT oa.onboard_activity_id AS \"activityId\", oa.activity_name AS \"activityName\", "
                        + "oa.category, oa.location, d.deck_name AS \"deckName\", "
                        + "oa.start_time AS \"startTime\", oa.end_time AS \"endTime\", "
                        + "oa.description, oa.price, COALESCE(oa.currency_code, 'VND') AS \"currencyCode\", "
                        + "oa.capacity "
                        + "FROM \"OnboardActivity\" oa "
                        + "LEFT JOIN \"Deck\" d ON d.deck_id = oa.deck_id "
                        + "WHERE oa.cruise_id = ? AND COALESCE(oa.status, 'PLANNED') <> 'CANCELLED' "
                        + "ORDER BY oa.start_time NULLS LAST, oa.activity_name",
                cruiseId);

        List<Map<String, Object>> shoreExcursions = jdbc.queryForList(
                "SELECT e.shore_excursion_id AS \"excursionId\", e.excursion_name AS \"excursionName\", "
                        + "p.port_name AS \"portName\", e.start_time AS \"startTime\", "
                        + "e.planned_return_time AS \"plannedReturnTime\", e.meeting_point AS \"meetingPoint\", "
                        + "e.price, COALESCE(e.currency_code, 'VND') AS \"currencyCode\", "
                        + "e.maximum_capacity AS \"maximumCapacity\", e.notes "
                        + "FROM \"ShoreExcursion\" e "
                        + "JOIN \"CruiseStop\" cs ON cs.cruise_stop_id = e.cruise_stop_id "
                        + "JOIN \"Itinerary\" i ON i.itinerary_id = cs.itinerary_id "
                        + "LEFT JOIN \"Port\" p ON p.port_id = COALESCE(e.port_id, cs.port_id) "
                        + "WHERE i.cruise_id = ? AND COALESCE(e.status, 'PLANNED') <> 'CANCELLED' "
                        + "ORDER BY e.start_time NULLS LAST, e.excursion_name",
                cruiseId);

        return ApiResponse.success(Map.of(
                "cruiseId", cruiseId,
                "services", services,
                "activities", activities,
                "shoreExcursions", shoreExcursions));
    }
}
