package com.casms.identity.service;

import com.casms.identity.entity.RolePermission;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RolePermissionInitializer implements CommandLineRunner {

    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        initializeRolePermissions();
    }

    @Transactional
    public void initializeRolePermissions() {
        log.info("Initializing role permissions...");

        // ADMIN: identity administration and technical ship/device configuration only.
        saveRolePermission(Role.ADMIN, EnumSet.of(
                Permission.USER_CREATE, Permission.USER_READ, Permission.USER_UPDATE,
                Permission.USER_ROLE_ASSIGN, Permission.USER_STATUS_CHANGE,
                Permission.SHIP_MODEL_CREATE, Permission.SHIP_MODEL_READ, Permission.SHIP_MODEL_UPDATE,
                Permission.SHIP_MODEL_DELETE, Permission.SHIP_MODEL_PUBLISH,
                Permission.SHIP_MODEL_OBJECT_CREATE, Permission.SHIP_MODEL_OBJECT_UPDATE,
                Permission.SHIP_MODEL_OBJECT_DELETE, Permission.SHIP_CONFIGURATION_READ,
                Permission.SHIP_CONFIGURATION_VALIDATE,
                Permission.BLE_ANCHOR_CREATE, Permission.BLE_ANCHOR_READ, Permission.BLE_ANCHOR_UPDATE,
                Permission.BLE_ANCHOR_DELETE, Permission.BLE_ANCHOR_ACTIVATE, Permission.BLE_ANCHOR_DEACTIVATE,
                Permission.BLE_ANCHOR_RETIRE, Permission.BLE_ANCHOR_INSTALL, Permission.BLE_ANCHOR_VALIDATE,
                Permission.BLE_TELEMETRY_RECEIVE, Permission.BLE_TELEMETRY_READ
        ));

        // CRUISE_MANAGER: quản lý lịch trình chuyến đi và hải trình (Quản lý lịch trình)
        saveRolePermission(Role.CRUISE_MANAGER, EnumSet.of(
                Permission.SERVICE_READ, Permission.SERVICE_REQUEST_READ,
                Permission.CRUISE_CREATE,
                Permission.CRUISE_READ,
                Permission.CRUISE_UPDATE_DRAFT,
                Permission.CRUISE_DELETE_DRAFT,
                Permission.CRUISE_ASSIGN_SHIP,
                Permission.CRUISE_CHANGE_SHIP,
                Permission.CRUISE_UNASSIGN_SHIP,
                Permission.CRUISE_VALIDATE,
                Permission.CRUISE_PUBLISH,
                Permission.CRUISE_START,
                Permission.CRUISE_COMPLETE,
                Permission.CRUISE_CANCEL,
                Permission.CRUISE_STATUS_CHANGE,

                Permission.ITINERARY_CREATE,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_UPDATE_DRAFT,
                Permission.ITINERARY_DELETE_DRAFT,
                Permission.ITINERARY_VALIDATE,
                Permission.ITINERARY_SUBMIT,
                Permission.ITINERARY_APPLY_CHANGE,
                Permission.ITINERARY_PUBLISH,
                Permission.ITINERARY_CANCEL,
                Permission.ITINERARY_DAYS_MANAGE,

                Permission.ITINERARY_DAY_CREATE,
                Permission.ITINERARY_DAY_READ,
                Permission.ITINERARY_DAY_UPDATE,
                Permission.ITINERARY_DAY_DELETE,
                Permission.ITINERARY_DAY_TYPE_SET,
                Permission.ITINERARY_DAY_TIMEZONE_SET,

                Permission.CRUISE_STOP_ADD,
                Permission.CRUISE_STOP_READ,
                Permission.CRUISE_STOP_UPDATE,
                Permission.CRUISE_STOP_REMOVE_DRAFT,
                Permission.CRUISE_STOP_REORDER,
                Permission.CRUISE_STOP_ALL_ABOARD,
                Permission.CRUISE_STOP_ACTUAL_ARRIVAL,
                Permission.CRUISE_STOP_ACTUAL_DEPARTURE,
                Permission.CRUISE_STOP_OMIT,
                Permission.CRUISE_STOP_SUBSTITUTE,
                Permission.CRUISE_STOP_VALIDATE,
                Permission.CRUISE_STOP_TIME_MANAGE,

                Permission.PORT_READ,
                Permission.DESTINATION_READ,

                Permission.CRUISE_REGULATION_ASSIGN,
                Permission.CRUISE_REGULATION_READ,
                Permission.CRUISE_REGULATION_REMOVE,
                Permission.REGULATION_READ,

                Permission.ITINERARY_CHANGE_RECORD,
                Permission.ITINERARY_CHANGE_READ,

                Permission.REPORT_GENERATE,

                // Cross-module read permissions
                Permission.SHIP_READ,
                Permission.OPERATIONAL_ISSUE_READ,
                Permission.SUPPLIER_READ,
                Permission.EXCURSION_READ,
                Permission.TOUR_GROUP_READ,
                Permission.BOOKING_READ
        ));

        // SHIP_MANAGER: quản lý đội tàu, boong, cabin, 3D model và quy định tàu (Quản lý vận hành)
        saveRolePermission(Role.SHIP_MANAGER, EnumSet.of(
                Permission.SHIP_CREATE,
                Permission.SHIP_READ,
                Permission.SHIP_UPDATE,
                Permission.SHIP_DELETE,
                Permission.SHIP_STATUS_CHANGE,
                Permission.SHIP_SCHEDULE_CHECK,
                Permission.SHIP_CAPACITY_SUMMARY,
                Permission.SHIP_AFFECTED_CRUISES,
                Permission.SHIP_CURRENT_CRUISE,
                Permission.SHIP_VALIDATE,
                Permission.SHIP_READINESS,

                Permission.SHIP_MODEL_CREATE,
                Permission.SHIP_MODEL_READ,
                Permission.SHIP_MODEL_UPDATE,
                Permission.SHIP_MODEL_DELETE,
                Permission.SHIP_MODEL_PUBLISH,
                Permission.SHIP_MODEL_OBJECT_CREATE,
                Permission.SHIP_MODEL_OBJECT_UPDATE,
                Permission.SHIP_MODEL_OBJECT_DELETE,
                Permission.SHIP_CONFIGURATION_READ,
                Permission.SHIP_CONFIGURATION_VALIDATE,

                Permission.BLE_ANCHOR_CREATE,
                Permission.BLE_ANCHOR_READ,
                Permission.BLE_ANCHOR_UPDATE,
                Permission.BLE_ANCHOR_DELETE,
                Permission.BLE_ANCHOR_ACTIVATE,
                Permission.BLE_ANCHOR_DEACTIVATE,
                Permission.BLE_ANCHOR_RETIRE,
                Permission.BLE_ANCHOR_INSTALL,
                Permission.BLE_ANCHOR_VALIDATE,
                Permission.BLE_TELEMETRY_RECEIVE,
                Permission.BLE_TELEMETRY_READ,

                Permission.DECK_CREATE,
                Permission.DECK_READ,
                Permission.DECK_UPDATE,
                Permission.DECK_DELETE,
                Permission.DECK_CABINS_VIEW,
                Permission.DECK_VALIDATE,

                Permission.CABIN_CREATE,
                Permission.CABIN_READ,
                Permission.CABIN_UPDATE,
                Permission.CABIN_DELETE,
                Permission.CABIN_SEARCH,
                Permission.CABIN_STATUS_CHANGE,
                Permission.CABIN_BLOCK,
                Permission.CABIN_RELEASE,
                Permission.CABIN_ASSIGNMENTS_VIEW,
                Permission.CABIN_AVAILABILITY,
                Permission.CABIN_VALIDATE,

                Permission.SHIP_REGULATION_ASSIGN,
                Permission.SHIP_REGULATION_READ,
                Permission.SHIP_REGULATION_REMOVE,
                Permission.REGULATION_READ,

                Permission.REPORT_GENERATE,

                Permission.CRUISE_READ,
                Permission.CRUISE_MANIFEST_READ,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.CRUISE_STOP_READ,
                Permission.PORT_READ,
                Permission.DESTINATION_READ
        ));

        // OPERATIONS_MANAGER: quản lý vận hành chung, sự cố, quy định, công suất và điều phối
        saveRolePermission(Role.OPERATIONS_MANAGER, EnumSet.of(
                Permission.OPERATIONAL_ISSUE_CREATE,
                Permission.OPERATIONAL_ISSUE_READ,
                Permission.OPERATIONAL_ISSUE_UPDATE,
                Permission.OPERATIONAL_ISSUE_RESOLVE,
                Permission.OPERATIONAL_ISSUE_ESCALATE,
                Permission.OPERATIONAL_ISSUE_ASSIGN,

                Permission.SHIP_READ,
                Permission.SHIP_MODEL_READ,
                Permission.SHIP_CONFIGURATION_READ,
                Permission.BLE_ANCHOR_READ,
                Permission.BLE_TELEMETRY_READ,
                Permission.SHIP_STATUS_CHANGE,
                Permission.SHIP_SCHEDULE_CHECK,
                Permission.SHIP_CAPACITY_SUMMARY,
                Permission.SHIP_AFFECTED_CRUISES,
                Permission.SHIP_CURRENT_CRUISE,
                Permission.SHIP_READINESS,

                Permission.DECK_READ,
                Permission.CABIN_READ,
                Permission.CABIN_SEARCH,
                Permission.CABIN_AVAILABILITY,

                Permission.CRUISE_READ,
                Permission.CRUISE_MANIFEST_READ,
                Permission.CRUISE_VALIDATE,
                Permission.CRUISE_START,
                Permission.CRUISE_COMPLETE,
                Permission.CRUISE_STATUS_CHANGE,

                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.ITINERARY_DAYS_MANAGE,
                Permission.CRUISE_STOP_READ,
                Permission.CRUISE_STOP_ACTUAL_ARRIVAL,
                Permission.CRUISE_STOP_ACTUAL_DEPARTURE,
                Permission.CRUISE_STOP_OMIT,
                Permission.CRUISE_STOP_SUBSTITUTE,
                Permission.CRUISE_STOP_VALIDATE,
                Permission.CRUISE_STOP_TIME_MANAGE,

                Permission.PORT_CREATE,
                Permission.PORT_READ,
                Permission.PORT_UPDATE,
                Permission.PORT_DELETE_UNUSED,
                Permission.PORT_ACTIVATE,
                Permission.PORT_DEACTIVATE,
                Permission.PORT_VALIDATE,
                Permission.DESTINATION_CREATE,
                Permission.DESTINATION_READ,
                Permission.DESTINATION_UPDATE,
                Permission.DESTINATION_DELETE_UNUSED,
                Permission.DESTINATION_PORTS_VIEW,
                Permission.DESTINATION_VALIDATE,

                Permission.CRUISE_STAFF_ASSIGN,
                Permission.CRUISE_STAFF_READ,
                Permission.CRUISE_STAFF_REMOVE,
                Permission.ITINERARY_APPROVE,
                Permission.ITINERARY_REJECT,

                Permission.REGULATION_CREATE,
                Permission.REGULATION_READ,
                Permission.REGULATION_UPDATE,
                Permission.REGULATION_DELETE,
                Permission.REGULATION_ACTIVATE,
                Permission.REGULATION_DEACTIVATE,
                Permission.REGULATION_VALIDATE,

                Permission.SHIP_REGULATION_ASSIGN,
                Permission.SHIP_REGULATION_READ,
                Permission.SHIP_REGULATION_REMOVE,

                Permission.CRUISE_REGULATION_ASSIGN,
                Permission.CRUISE_REGULATION_READ,
                Permission.CRUISE_REGULATION_REMOVE,

                Permission.ITINERARY_CHANGE_RECORD,
                Permission.ITINERARY_CHANGE_READ,

                Permission.BOOKING_READ,

                Permission.REPORT_GENERATE,
                Permission.REPORT_EXPORT,

                // Cross-module
                Permission.SUPPLIER_CREATE,
                Permission.SUPPLIER_READ,
                Permission.SUPPLIER_UPDATE,
                Permission.SUPPLIER_STATUS_CHANGE,
                Permission.SUPPLIER_VERIFY,
                Permission.SUPPLIER_VALIDATE,
                Permission.EXCURSION_READ,
                Permission.TOUR_GROUP_READ,
                Permission.TOUR_GROUP_PASSENGER_READ,
                Permission.SERVICE_READ
        ));

        // BOOKING_AGENT: tiếp nhận, cập nhật, điều phối phòng, hủy và xác nhận booking
        saveRolePermission(Role.BOOKING_AGENT, EnumSet.of(
                Permission.SERVICE_READ, Permission.SERVICE_REQUEST_READ, Permission.TOUR_PACKAGE_ASSIGN,
                Permission.BOOKING_CREATE,
                Permission.BOOKING_READ,
                Permission.BOOKING_UPDATE,
                Permission.BOOKING_CONFIRM,
                Permission.BOOKING_CANCEL,
                Permission.BOOKING_CONTRACT_MANAGE,
                Permission.BOOKING_CABIN_ASSIGN,

                Permission.CABIN_READ,
                Permission.CABIN_SEARCH,
                Permission.CABIN_STATUS_CHANGE,
                Permission.CABIN_BLOCK,
                Permission.CABIN_RELEASE,
                Permission.CABIN_ASSIGNMENTS_VIEW,
                Permission.CABIN_AVAILABILITY,

                Permission.CHECKIN_CREATE,
                Permission.CHECKIN_READ,
                Permission.CHECKIN_UPDATE,
                Permission.CHECKIN_STEP_UPDATE,
                Permission.CHECKIN_IDENTITY_VERIFY,
                Permission.CHECKIN_DOCUMENT_VERIFY,
                Permission.CHECKIN_PAYMENT_VERIFY,
                Permission.CHECKIN_BOARDING,

                Permission.NOTIFICATION_SEND,

                Permission.CRUISE_READ,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.CRUISE_STOP_READ,
                Permission.PORT_READ,
                Permission.DESTINATION_READ,

                // Cross-module reference read permissions
                Permission.SHIP_READ,
                Permission.SHIP_MODEL_READ,
                Permission.SHIP_CONFIGURATION_READ,
                Permission.DECK_READ,
                Permission.DECK_CABINS_VIEW,
                Permission.REPORT_GENERATE
        ));

        // CUSTOMER_SERVICE: hỗ trợ khách hàng, thay đổi thông tin/phòng, hủy theo điều kiện
        saveRolePermission(Role.CUSTOMER_SERVICE, EnumSet.of(
                Permission.SERVICE_READ, Permission.SERVICE_REQUEST_READ, Permission.SERVICE_REQUEST_CREATE,
                Permission.USER_READ,
                Permission.USER_UPDATE,

                Permission.BOOKING_READ,
                Permission.BOOKING_UPDATE,
                Permission.BOOKING_CANCEL,
                Permission.CHECKIN_READ,
                Permission.CHECKIN_UPDATE,

                Permission.CABIN_READ,
                Permission.CABIN_SEARCH,
                Permission.CABIN_AVAILABILITY,
                Permission.CABIN_ASSIGNMENTS_VIEW,

                Permission.NOTIFICATION_SEND,
                Permission.NOTIFICATION_READ,
                Permission.FEEDBACK_READ,
                Permission.FEEDBACK_REVIEW,
                Permission.REPORT_GENERATE,

                Permission.CRUISE_READ,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.CRUISE_STOP_READ,
                Permission.PORT_READ,
                Permission.DESTINATION_READ,

                // Cross-module reference read permissions
                Permission.SHIP_READ,
                Permission.DECK_READ,
                Permission.EXCURSION_READ,
                Permission.ACTIVITY_READ
        ));

        // CHECKIN_STAFF: xử lý check-in, xác minh danh tính, cấp boarding pass và quản lý BLE tag
        saveRolePermission(Role.CHECKIN_STAFF, EnumSet.of(
                Permission.CHECKIN_CREATE,
                Permission.CHECKIN_READ,
                Permission.CHECKIN_UPDATE,
                Permission.CHECKIN_STEP_UPDATE,
                Permission.CHECKIN_IDENTITY_VERIFY,
                Permission.CHECKIN_DOCUMENT_VERIFY,
                Permission.CHECKIN_PAYMENT_VERIFY,
                Permission.CHECKIN_BOARDING,
                Permission.CHECKOUT_PROCESS,
                Permission.PASSENGER_BLE_ASSIGN,
                Permission.PASSENGER_BLE_READ,
                Permission.PASSENGER_BLE_REVOKE,
                Permission.PASSENGER_BLE_HISTORY,

                Permission.CABIN_READ,
                Permission.CABIN_SEARCH,
                Permission.CABIN_AVAILABILITY,

                Permission.CRUISE_READ,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.CRUISE_STOP_READ,
                Permission.PORT_READ,
                Permission.DESTINATION_READ,

                // Cross-module reference read permissions
                Permission.SHIP_READ,
                Permission.DECK_READ
        ));

        // ACTIVITY_SERVICE_MANAGER: unified onboard activity/service and shore excursion management
        saveRolePermission(Role.ACTIVITY_SERVICE_MANAGER, EnumSet.of(
                Permission.ACTIVITY_CREATE,
                Permission.ACTIVITY_READ,
                Permission.ACTIVITY_UPDATE,
                Permission.ACTIVITY_LIFECYCLE,
                Permission.ACTIVITY_REGISTRATION,
                Permission.ACTIVITY_ATTENDANCE,
                Permission.REGISTRATION_MANAGE,
                Permission.PARTICIPATION_ATTENDANCE,

                Permission.SERVICE_CREATE,
                Permission.SERVICE_READ,
                Permission.SERVICE_UPDATE,
                Permission.SERVICE_DELETE_UNUSED,
                Permission.SERVICE_ACTIVATE,
                Permission.SERVICE_SUSPEND,
                Permission.PRODUCT_MANAGE,
                Permission.TOUR_PACKAGE_MANAGE,
                Permission.PACKAGE_COMPONENT_MANAGE,

                Permission.SERVICE_REQUEST_READ,
                Permission.SERVICE_REQUEST_ASSIGN,
                Permission.SERVICE_REQUEST_EXECUTE,
                Permission.FEEDBACK_READ,
                Permission.FEEDBACK_REVIEW,

                Permission.CRUISE_READ,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.CRUISE_STOP_READ,
                Permission.PORT_READ,
                Permission.DESTINATION_READ,
                Permission.SHIP_READ,
                Permission.DECK_READ,
                Permission.NOTIFICATION_SEND,
                Permission.NOTIFICATION_READ,
                Permission.REPORT_GENERATE,
                Permission.REPORT_EXPORT,

                Permission.EXCURSION_CREATE,
                Permission.EXCURSION_READ,
                Permission.EXCURSION_UPDATE,
                Permission.EXCURSION_DELETE_DRAFT,
                Permission.EXCURSION_STATUS_CHANGE,
                Permission.EXCURSION_VALIDATE,
                Permission.EXCURSION_BOOKING,

                Permission.SUPPLIER_CREATE,
                Permission.SUPPLIER_READ,
                Permission.SUPPLIER_UPDATE,
                Permission.SUPPLIER_DELETE,
                Permission.SUPPLIER_STATUS_CHANGE,
                Permission.SUPPLIER_VERIFY,
                Permission.SUPPLIER_VALIDATE,

                Permission.TOUR_GROUP_CREATE,
                Permission.TOUR_GROUP_READ,
                Permission.TOUR_GROUP_UPDATE,
                Permission.TOUR_GROUP_GUIDE_ASSIGN,
                Permission.TOUR_GROUP_STATUS_CHANGE,
                Permission.TOUR_GROUP_VALIDATE,

                Permission.TOUR_GROUP_PASSENGER_ASSIGN,
                Permission.TOUR_GROUP_PASSENGER_READ,
                Permission.TOUR_GROUP_PASSENGER_MOVE,
                Permission.TOUR_GROUP_PASSENGER_REMOVE,
                Permission.TOUR_GROUP_PASSENGER_ATTENDANCE,
                Permission.TOUR_GROUP_PASSENGER_VALIDATE,

                Permission.CRUISE_READ,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.CRUISE_STOP_READ,
                Permission.PORT_READ,
                Permission.DESTINATION_READ,
                Permission.NOTIFICATION_SEND,
                Permission.NOTIFICATION_READ,
                Permission.REPORT_GENERATE,
                Permission.REPORT_EXPORT
        ));

        // ONBOARD_STAFF: Onboard Sales & Service Staff
        // Tiếp nhận/thực hiện yêu cầu dịch vụ và bán hàng POS; KHÔNG sửa danh mục hoặc hoạt động.
        saveRolePermission(Role.ONBOARD_STAFF, EnumSet.of(
                // Tiếp nhận & thực hiện yêu cầu dịch vụ
                Permission.SERVICE_READ,
                Permission.SERVICE_REQUEST_READ,
                Permission.SERVICE_REQUEST_ASSIGN,
                Permission.SERVICE_REQUEST_EXECUTE,

                // Bán hàng qua POS
                Permission.PRODUCT_SALE,
                Permission.PAYMENT_PROCESS,

                // Điểm danh và kiểm tra hoạt động / tour bờ
                Permission.ACTIVITY_READ,
                Permission.ACTIVITY_ATTENDANCE,
                Permission.PARTICIPATION_ATTENDANCE,
                Permission.EXCURSION_READ,
                Permission.TOUR_GROUP_READ,
                Permission.TOUR_GROUP_PASSENGER_READ,
                Permission.TOUR_GROUP_PASSENGER_ATTENDANCE,

                // Vận hành checkout và BLE
                Permission.CHECKOUT_PROCESS,
                Permission.BLE_TELEMETRY_READ,

                // Thông báo và tham chiếu chuyến
                Permission.NOTIFICATION_SEND,
                Permission.CRUISE_READ,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.CRUISE_STOP_READ,
                Permission.PORT_READ,
                Permission.DESTINATION_READ,
                Permission.SHIP_READ,
                Permission.DECK_READ
        ));

        // FINANCE_OFFICER: thanh toán, hóa đơn, công nợ và quyết toán cuối chuyến
        saveRolePermission(Role.FINANCE_OFFICER, EnumSet.of(
                Permission.BOOKING_CONFIRM,
                Permission.PAYMENT_PROCESS,
                Permission.PAYMENT_READ,
                Permission.PAYMENT_REFUND,
                Permission.PAYMENT_REPORT,
                Permission.INVOICE_GENERATE,
                Permission.SETTLEMENT_PROCESS,
                Permission.REPORT_GENERATE,
                Permission.REPORT_EXPORT,
                Permission.BOOKING_READ,
                Permission.CHECKIN_READ,

                Permission.CRUISE_READ,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.CRUISE_STOP_READ
        ));

        // PASSENGER: chức năng dành cho hành khách (chỉ dữ liệu của mình, không tự confirm hoặc cấp boarding pass)
        saveRolePermission(Role.PASSENGER, EnumSet.of(
                Permission.BOOKING_CREATE,
                Permission.BOOKING_READ,
                Permission.BOOKING_CANCEL,

                Permission.CHECKIN_CREATE,
                Permission.CHECKIN_READ,

                // Xem chuyến tàu, hải trình, lịch trình và lịch sử thay đổi
                Permission.CRUISE_READ,
                Permission.SHIP_READ,
                Permission.CABIN_READ,
                Permission.DECK_READ,
                Permission.DECK_CABINS_VIEW,
                Permission.ITINERARY_READ,
                Permission.ITINERARY_DAY_READ,
                Permission.CRUISE_STOP_READ,
                Permission.ITINERARY_CHANGE_READ,
                Permission.PORT_READ,
                Permission.DESTINATION_READ,

                // Hoạt động và tour
                Permission.ACTIVITY_READ,
                Permission.ACTIVITY_REGISTRATION,
                Permission.EXCURSION_READ,
                Permission.EXCURSION_BOOKING,

                // Dịch vụ và phản hồi
                Permission.SERVICE_READ,
                Permission.SERVICE_REQUEST_CREATE,
                Permission.SERVICE_REQUEST_READ,
                Permission.TOUR_PACKAGE_ASSIGN,
                Permission.FEEDBACK_SUBMIT,
                Permission.FEEDBACK_READ,

                // Thông báo và thanh toán
                Permission.NOTIFICATION_READ,
                Permission.PAYMENT_READ
        ));

        log.info("Role permissions initialized successfully.");
    }

    /**
     * Tạo mới hoặc cập nhật tập quyền chuẩn xác của một role.
     */
    private void saveRolePermission(Role role, Set<Permission> permissions) {
        RolePermission rolePermission = rolePermissionRepository
                .findByRole(role)
                .orElseGet(RolePermission::new);

        rolePermission.setRole(role);
        rolePermission.setPermissions(EnumSet.copyOf(permissions));

        rolePermissionRepository.save(rolePermission);

        log.info(
                "Configured permissions for role: {} ({} permissions)",
                role,
                permissions.size()
        );
    }
}
