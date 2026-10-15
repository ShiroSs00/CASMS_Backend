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

        // ADMIN - all permissions
        saveRolePermission(Role.ADMIN, EnumSet.allOf(Permission.class));

        // CRUISE_MANAGER - cruise, itinerary, itinerary day, cruise stop management
        saveRolePermission(Role.CRUISE_MANAGER, Set.of(
                Permission.CRUISE_CREATE, Permission.CRUISE_READ, Permission.CRUISE_UPDATE_DRAFT,
                Permission.CRUISE_DELETE_DRAFT, Permission.CRUISE_ASSIGN_SHIP, Permission.CRUISE_CHANGE_SHIP,
                Permission.CRUISE_UNASSIGN_SHIP, Permission.CRUISE_VALIDATE, Permission.CRUISE_PUBLISH,
                Permission.CRUISE_START, Permission.CRUISE_COMPLETE, Permission.CRUISE_CANCEL, Permission.CRUISE_STATUS_CHANGE,
                Permission.ITINERARY_CREATE, Permission.ITINERARY_READ, Permission.ITINERARY_UPDATE_DRAFT,
                Permission.ITINERARY_DELETE_DRAFT, Permission.ITINERARY_VALIDATE, Permission.ITINERARY_SUBMIT,
                Permission.ITINERARY_APPROVE, Permission.ITINERARY_REJECT, Permission.ITINERARY_APPLY_CHANGE,
                Permission.ITINERARY_PUBLISH, Permission.ITINERARY_CANCEL, Permission.ITINERARY_DAYS_MANAGE,
                Permission.ITINERARY_DAY_CREATE, Permission.ITINERARY_DAY_READ, Permission.ITINERARY_DAY_UPDATE,
                Permission.ITINERARY_DAY_DELETE, Permission.ITINERARY_DAY_TYPE_SET, Permission.ITINERARY_DAY_TIMEZONE_SET,
                Permission.CRUISE_STOP_ADD, Permission.CRUISE_STOP_READ, Permission.CRUISE_STOP_UPDATE,
                Permission.CRUISE_STOP_REMOVE_DRAFT, Permission.CRUISE_STOP_REORDER, Permission.CRUISE_STOP_ALL_ABOARD,
                Permission.CRUISE_STOP_ACTUAL_ARRIVAL, Permission.CRUISE_STOP_ACTUAL_DEPARTURE,
                Permission.CRUISE_STOP_OMIT, Permission.CRUISE_STOP_SUBSTITUTE, Permission.CRUISE_STOP_VALIDATE,
                Permission.CRUISE_STOP_TIME_MANAGE,
                Permission.PORT_READ, Permission.DESTINATION_READ,
                Permission.CRUISE_REGULATION_ASSIGN, Permission.CRUISE_REGULATION_READ, Permission.CRUISE_REGULATION_REMOVE,
                Permission.REGULATION_READ,
                Permission.ITINERARY_CHANGE_RECORD, Permission.ITINERARY_CHANGE_READ,
                Permission.REPORT_GENERATE
        ));

        // SHIP_MANAGER - ship, deck, cabin management
        saveRolePermission(Role.SHIP_MANAGER, Set.of(
                Permission.SHIP_CREATE, Permission.SHIP_READ, Permission.SHIP_UPDATE, Permission.SHIP_DELETE,
                Permission.SHIP_STATUS_CHANGE, Permission.SHIP_SCHEDULE_CHECK,
                Permission.DECK_CREATE, Permission.DECK_READ, Permission.DECK_UPDATE, Permission.DECK_DELETE,
                Permission.DECK_CABINS_VIEW,
                Permission.CABIN_CREATE, Permission.CABIN_READ, Permission.CABIN_UPDATE, Permission.CABIN_DELETE,
                Permission.CABIN_SEARCH, Permission.CABIN_STATUS_CHANGE, Permission.CABIN_BLOCK, Permission.CABIN_RELEASE,
                Permission.CABIN_ASSIGNMENTS_VIEW, Permission.CABIN_AVAILABILITY,
                Permission.SHIP_CAPACITY_SUMMARY,
                Permission.SHIP_REGULATION_ASSIGN, Permission.SHIP_REGULATION_READ, Permission.SHIP_REGULATION_REMOVE,
                Permission.REGULATION_READ,
                Permission.REPORT_GENERATE
        ));

        // OPERATIONS_MANAGER - operational monitoring, issues, ship/cruise monitoring
        saveRolePermission(Role.OPERATIONS_MANAGER, Set.of(
                // Operational Issues
                Permission.OPERATIONAL_ISSUE_CREATE, Permission.OPERATIONAL_ISSUE_READ,
                Permission.OPERATIONAL_ISSUE_UPDATE, Permission.OPERATIONAL_ISSUE_RESOLVE,
                Permission.OPERATIONAL_ISSUE_ESCALATE, Permission.OPERATIONAL_ISSUE_ASSIGN,
                // Ship monitoring
                Permission.SHIP_READ, Permission.SHIP_STATUS_CHANGE, Permission.SHIP_SCHEDULE_CHECK,
                Permission.SHIP_CAPACITY_SUMMARY, Permission.SHIP_AFFECTED_CRUISES,
                // Cruise monitoring
                Permission.CRUISE_READ, Permission.CRUISE_VALIDATE, Permission.CRUISE_START,
                Permission.CRUISE_COMPLETE, Permission.CRUISE_STATUS_CHANGE,
                // Itinerary/Cruise Stop monitoring
                Permission.ITINERARY_READ, Permission.ITINERARY_DAYS_MANAGE,
                Permission.CRUISE_STOP_READ, Permission.CRUISE_STOP_ACTUAL_ARRIVAL,
                Permission.CRUISE_STOP_ACTUAL_DEPARTURE, Permission.CRUISE_STOP_OMIT,
                Permission.CRUISE_STOP_SUBSTITUTE, Permission.CRUISE_STOP_VALIDATE,
                Permission.CRUISE_STOP_TIME_MANAGE,
                // Port/Destination
                Permission.PORT_READ, Permission.PORT_VALIDATE,
                Permission.DESTINATION_READ, Permission.DESTINATION_VALIDATE,
                // Regulations
                Permission.REGULATION_CREATE, Permission.REGULATION_READ, Permission.REGULATION_UPDATE,
                Permission.REGULATION_DELETE, Permission.REGULATION_ACTIVATE, Permission.REGULATION_DEACTIVATE,
                Permission.REGULATION_VALIDATE,
                Permission.SHIP_REGULATION_ASSIGN, Permission.SHIP_REGULATION_READ, Permission.SHIP_REGULATION_REMOVE,
                Permission.CRUISE_REGULATION_ASSIGN, Permission.CRUISE_REGULATION_READ, Permission.CRUISE_REGULATION_REMOVE,
                // Itinerary history
                Permission.ITINERARY_CHANGE_RECORD, Permission.ITINERARY_CHANGE_READ,
                // Reports
                Permission.REPORT_GENERATE, Permission.REPORT_EXPORT
        ));

        // BOOKING_AGENT - booking, check-in, cabin assignment
        saveRolePermission(Role.BOOKING_AGENT, Set.of(
                Permission.BOOKING_CREATE, Permission.BOOKING_READ, Permission.BOOKING_UPDATE,
                Permission.BOOKING_CANCEL, Permission.BOOKING_CONTRACT_MANAGE, Permission.BOOKING_CABIN_ASSIGN,
                Permission.CABIN_READ, Permission.CABIN_SEARCH, Permission.CABIN_STATUS_CHANGE,
                Permission.CHECKIN_CREATE, Permission.CHECKIN_READ, Permission.CHECKIN_UPDATE,
                Permission.CHECKIN_STEP_UPDATE, Permission.CHECKIN_IDENTITY_VERIFY,
                Permission.CHECKIN_DOCUMENT_VERIFY, Permission.CHECKIN_PAYMENT_VERIFY,
                Permission.CHECKIN_BOARDING,
                Permission.CABIN_ASSIGNMENTS_VIEW,
                Permission.NOTIFICATION_SEND
        ));

        // CHECKIN_STAFF - check-in operations
        saveRolePermission(Role.CHECKIN_STAFF, Set.of(
                Permission.CHECKIN_CREATE, Permission.CHECKIN_READ, Permission.CHECKIN_UPDATE,
                Permission.CHECKIN_STEP_UPDATE, Permission.CHECKIN_IDENTITY_VERIFY,
                Permission.CHECKIN_DOCUMENT_VERIFY, Permission.CHECKIN_PAYMENT_VERIFY,
                Permission.CHECKIN_BOARDING, Permission.CHECKOUT_PROCESS,
                Permission.CABIN_READ, Permission.CABIN_SEARCH, Permission.CABIN_AVAILABILITY
        ));

        // ONBOARD_STAFF - onboard operations
        saveRolePermission(Role.ONBOARD_STAFF, Set.of(
                Permission.ACTIVITY_CREATE, Permission.ACTIVITY_READ, Permission.ACTIVITY_UPDATE,
                Permission.ACTIVITY_REGISTRATION, Permission.ACTIVITY_ATTENDANCE,
                Permission.EXCURSION_CREATE, Permission.EXCURSION_READ, Permission.EXCURSION_UPDATE,
                Permission.EXCURSION_BOOKING, Permission.NOTIFICATION_SEND,
                Permission.CHECKOUT_PROCESS
        ));

        // FINANCE_OFFICER - payment, invoice, settlement
        saveRolePermission(Role.FINANCE_OFFICER, Set.of(
                Permission.PAYMENT_PROCESS, Permission.PAYMENT_REFUND, Permission.PAYMENT_REPORT,
                Permission.INVOICE_GENERATE, Permission.SETTLEMENT_PROCESS,
                Permission.REPORT_GENERATE, Permission.REPORT_EXPORT,
                Permission.BOOKING_READ, Permission.CHECKIN_READ
        ));

        // CUSTOMER_SERVICE - customer support
        saveRolePermission(Role.CUSTOMER_SERVICE, Set.of(
                Permission.USER_READ, Permission.USER_UPDATE,
                Permission.BOOKING_READ, Permission.BOOKING_UPDATE,
                Permission.CHECKIN_READ, Permission.CHECKIN_UPDATE,
                Permission.NOTIFICATION_SEND, Permission.NOTIFICATION_READ,
                Permission.REPORT_GENERATE
        ));

        // PASSENGER - limited access
        saveRolePermission(Role.PASSENGER, Set.of(
                Permission.BOOKING_READ, Permission.CHECKIN_CREATE, Permission.CHECKIN_READ,
                Permission.ACTIVITY_READ, Permission.ACTIVITY_REGISTRATION,
                Permission.EXCURSION_READ, Permission.EXCURSION_BOOKING,
                Permission.NOTIFICATION_READ, Permission.PAYMENT_PROCESS
        ));

        log.info("Role permissions initialized successfully");
    }

    private void saveRolePermission(Role role, Set<Permission> permissions) {
        RolePermission rp = rolePermissionRepository.findByRole(role).orElseGet(() -> {
            RolePermission newRp = new RolePermission();
            newRp.setRole(role);
            return newRp;
        });
        rp.setPermissions(permissions);
        rolePermissionRepository.save(rp);
        log.info("Configured permissions for role: {} ({} permissions)", role, permissions.size());
    }
}