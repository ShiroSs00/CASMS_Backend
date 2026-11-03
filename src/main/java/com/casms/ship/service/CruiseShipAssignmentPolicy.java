package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.response.ShipAssignmentSafetyResponse;
import com.casms.ship.dto.response.ShipAssignmentSafetyResponse.Dependency;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

/** Shared authorization and conservative ship-link protection for all assignment entry points. */
@Service
@RequiredArgsConstructor
public class CruiseShipAssignmentPolicy {
    private final JdbcTemplate jdbc;

    public boolean canMutate() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CasmsPrincipal principal
                && principal.hasRole(Role.CRUISE_MANAGER)
                && auth.getAuthorities().stream().anyMatch(a -> "CRUISE_ASSIGN_SHIP".equals(a.getAuthority()));
    }

    public UUID requireActor(UUID suppliedActor) {
        if (!canMutate()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Chỉ Quản lý lịch trình có quyền phân công tàu được thay đổi phân công.");
        }
        var principal = (CasmsPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (suppliedActor != null && !suppliedActor.equals(principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Tài khoản thao tác không khớp phiên hiện tại.");
        }
        return principal.getUserId();
    }

    public ShipAssignmentSafetyResponse assess(UUID id, UUID requestedShipId, String operation) {
        var cruise = jdbc.queryForMap("SELECT ship_id,status FROM \"Cruise\" WHERE cruise_id=?", id);
        UUID currentShipId = (UUID) cruise.get("ship_id");
        var plans = jdbc.queryForList("SELECT status,ready_at FROM \"CruisePlanReview\" WHERE cruise_id=?", id);
        String planStatus = plans.isEmpty() ? "DRAFT" : (String) plans.getFirst().get("status");
        boolean ready = !plans.isEmpty() && plans.getFirst().get("ready_at") != null;
        boolean editable = List.of("DRAFT", "WITHDRAWN", "REVISION_REQUESTED").contains(planStatus);
        boolean changed = "UNASSIGN".equals(operation) ? currentShipId != null
                : "ASSIGN".equals(operation) || !Objects.equals(currentShipId, requestedShipId);
        List<Dependency> dependencies = new ArrayList<>();
        dependencies.add(dependency("CONFIRMED_BOOKINGS", count("SELECT count(*) FROM \"Booking\" WHERE cruise_id=? AND booking_status='Confirmed'", id), true, false,
                "Có đặt chỗ đã xác nhận; không tự chuyển hoặc hủy đặt chỗ khi đổi/gỡ tàu."));
        dependencies.add(dependency("PASSENGER_COMMITMENTS", count("SELECT count(*) FROM \"BookingPassenger\" p JOIN \"Booking\" b USING(booking_id) WHERE b.cruise_id=? AND b.booking_status='Confirmed' AND p.participation_status='Active'", id), true, false,
                "Có hành khách đã cam kết phục vụ trên tàu hiện tại."));
        dependencies.add(dependency("DRAFT_HOLDS", count("SELECT count(*) FROM \"Booking\" WHERE cruise_id=? AND booking_status='Draft'", id), true, false,
                "Có đặt chỗ đang giữ tạm; không tự hủy giữ chỗ để đổi/gỡ tàu."));
        dependencies.add(dependency("BOOKING_HISTORY", count("SELECT count(*) FROM \"Booking\" WHERE cruise_id=? AND booking_status='Cancelled'", id), true, true,
                "Có lịch sử đặt chỗ gắn chuyến; chưa có quy tắc bảo toàn tham chiếu tàu lịch sử khi thay phân công."));
        dependencies.add(dependency("CABIN_ASSIGNMENTS", count("SELECT count(*) FROM \"CabinAssignment\" a JOIN \"BookingPassenger\" p USING(booking_passenger_id) JOIN \"Booking\" b USING(booking_id) WHERE b.cruise_id=?", id), true, false,
                "Có phân bổ cabin, gồm lịch sử phân bổ; không tự chuyển, đóng hoặc xóa liên kết cabin."));
        dependencies.add(dependency("ACTIVITY_SHIP_LOCATION", count("SELECT count(*) FROM \"OnboardActivity\" WHERE cruise_id=? AND (deck_id IS NOT NULL OR cabin_id IS NOT NULL)", id), true, false,
                "Hoạt động gắn boong/phòng cụ thể; chưa có quy trình chuyển địa điểm an toàn."));
        dependencies.add(dependency("ACTIVITY_LOCATION_UNRESOLVED", currentShipId == null ? 0 : count("SELECT count(*) FROM \"OnboardActivity\" WHERE cruise_id=? AND deck_id IS NULL AND cabin_id IS NULL AND (length(trim(COALESCE(location,'')))>0 OR length(trim(COALESCE(meeting_point,'')))>0)", id), true, true,
                "Địa điểm hoạt động đang lưu bằng mô tả; chưa xác định được có phụ thuộc tàu hiện tại hay không."));
        dependencies.add(dependency("SERVICE_CONFIGURATION", count("SELECT count(*) FROM \"ServiceSchedule\" s LEFT JOIN \"CruiseStop\" p USING(cruise_stop_id) LEFT JOIN \"Itinerary\" i USING(itinerary_id) WHERE s.cruise_id=? OR i.cruise_id=?", id, id), true, true,
                "Lịch dịch vụ có địa điểm dạng mô tả; chưa có dữ liệu xác nhận chuyển sang tàu khác an toàn."));
        dependencies.add(dependency("PACKAGE_CONFIGURATION", count("SELECT count(*) FROM \"TourPackage\" p WHERE p.owner_cruise_id=? OR EXISTS(SELECT 1 FROM \"CruiseTourPackage\" c WHERE c.tour_package_id=p.tour_package_id AND c.cruise_id=?)", id, id), true, true,
                "Có gói thuộc hoặc liên kết chuyến theo mô hình hiện tại; chưa xác định an toàn các quyền lợi/cấu hình khi đổi tàu."));
        dependencies.add(dependency("CRUISE_PACKAGE_ADDON_OFFERS", count("SELECT count(*) FROM \"CruisePackageAddonOffer\" WHERE cruise_id=?",id), true, true,
                "Có Add-on gắn với tàu và hoạt động/dịch vụ của chuyến; không tự remap giá, giai đoạn bán hoặc quyền lợi."));
        dependencies.add(dependency("FARE_PACKAGE_OFFERS", count("SELECT count(*) FROM \"DepartureFarePackage\" WHERE cruise_id=?", id), true, true,
                "Có gói vé của chuyến tham chiếu phiên bản mẫu và hạng phòng trên tàu; chưa chứng minh tương thích với tàu đích. Không tự chuyển hạng, giá, duyệt hoặc quyền lợi."));
        dependencies.add(dependency("FARE_PACKAGE_PRICE_REVISIONS", count("SELECT count(*) FROM \"DepartureFarePackagePrice\" p JOIN \"DepartureFarePackage\" f USING(offer_id) WHERE f.cruise_id=?", id), true, true,
                "Có lịch sử revision giá gói vé; không tự thay giá hoặc chuyển phê duyệt sang tàu khác."));
        dependencies.add(dependency("FARE_PACKAGE_BENEFIT_BINDINGS", count("SELECT count(*) FROM \"DepartureFarePackageBenefit\" b JOIN \"DepartureFarePackage\" f USING(offer_id) WHERE f.cruise_id=?", id), true, true,
                "Có quyền lợi đã liên kết với lần phục vụ của chuyến; chưa chứng minh sử dụng an toàn trên tàu đích. Không tự ánh xạ lại quyền lợi."));
        dependencies.add(dependency("PURCHASED_FARE_SNAPSHOTS", count("SELECT count(*) FROM \"BookingFareSnapshot\" s JOIN \"Booking\" b USING(booking_id) WHERE b.cruise_id=?", id), true, false,
                "Có snapshot gói vé đã mua gắn hành khách và tàu; phải bảo toàn lịch sử, không tự ánh xạ lại đặt chỗ."));
        dependencies.add(dependency("CABIN_PRICING_CONFIGURATION", count("SELECT count(*) FROM \"CruisePricingPolicy\" WHERE cruise_id=?", id), true, true,
                "Có cấu hình giá theo hạng phòng; chưa có quy trình đối soát cấu hình trên tàu khác."));
        long confirmed = count("SELECT count(*) FROM \"CruisePreparationSection\" WHERE cruise_id=? AND status='CONFIRMED'", id);
        long submitted = count("SELECT count(*) FROM \"CruisePreparationSection\" WHERE cruise_id=? AND status='SUBMITTED'", id);
        dependencies.add(dependency("CRUISE_PLAN_APPROVAL", editable ? 0 : 1, true, false,
                "Kế hoạch đã gửi/duyệt hoặc không ở trạng thái được sửa; dùng quy trình thu hồi/yêu cầu sửa đổi hiện có."));
        dependencies.add(dependency("PREPARATION_SUBMISSION", submitted, true, true,
                "Có hồ sơ chuẩn bị đang chờ xác nhận; chưa có cách xử lý an toàn khi đổi/gỡ tàu."));
        dependencies.add(dependency("PREPARATION_SIGNOFF", confirmed, false, false,
                "Nếu thao tác được phép, các hồ sơ đã xác nhận sẽ được yêu cầu bổ sung theo quy trình hiện hành."));
        dependencies.add(dependency("READINESS_FINGERPRINT", ready ? 1 : 0, false, false,
                "Xác nhận sẵn sàng hiện hữu gắn với tàu; thay đổi được phép sẽ làm xác nhận cũ hết hiệu lực."));
        List<String> blockers = new ArrayList<>();
        if (changed) dependencies.stream().filter(Dependency::blocking).map(Dependency::reason).forEach(blockers::add);
        else if (!editable) blockers.add("Kế hoạch hiện không cho phép sửa phân công tàu.");
        String cruiseStatus = (String) cruise.get("status");
        boolean validState = "UNASSIGN".equals(operation) ? "DRAFT".equals(cruiseStatus)
                : List.of("DRAFT", "PUBLISHED").contains(cruiseStatus);
        if (!validState) blockers.add("Trạng thái chuyến khởi hành không cho phép thao tác phân công này.");
        var categories = new ArrayList<String>();
        if(changed)dependencies.stream().filter(Dependency::blocking).map(Dependency::category).forEach(categories::add);
        if(!editable&&!categories.contains("CRUISE_PLAN_APPROVAL"))categories.add("CRUISE_PLAN_APPROVAL");
        if(!validState)categories.add("CRUISE_STATE");
        return new ShipAssignmentSafetyResponse(id, currentShipId, requestedShipId, operation, changed, blockers.isEmpty(),
                List.copyOf(dependencies), List.copyOf(categories), List.copyOf(blockers),
                new ShipAssignmentSafetyResponse.ApprovalImpact(planStatus, ready, confirmed, submitted, changed,
                        "Chỉ khi thao tác được phép: hủy xác nhận hồ sơ hiện hữu và đánh giá lại điều kiện sẵn sàng; không tự duyệt lại kế hoạch."));
    }

    public boolean requiresImpact(UUID id) {
        return !downstreamBlockers(id).isEmpty()
                || exists("SELECT EXISTS(SELECT 1 FROM \"OnboardActivity\" WHERE cruise_id=?)", id);
    }

    public List<String> downstreamBlockers(UUID id) {
        return assess(id, null, "CHANGE").blockers();
    }

    public ShipAssignmentSafetyResponse requireSafeDownstream(UUID id, UUID requestedShipId, String operation) {
        var safety = assess(id, requestedShipId, operation);
        if (!safety.canProceed()) {
            throw new BusinessException(HttpStatus.CONFLICT, "SHIP_CHANGE_IMPACT_BLOCKED",
                    "Chưa thể thay đổi phân công tàu khi dữ liệu phụ thuộc chưa có cơ chế chuyển đổi an toàn.",
                    Map.of("blockers", safety.blockers(), "shipAssignmentSafety", safety));
        }
        return safety;
    }

    private Dependency dependency(String category, long count, boolean blocks, boolean clarification, String reason) {
        return new Dependency(category, count, count > 0 && blocks, count > 0 && clarification, reason);
    }

    private long count(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }
}
