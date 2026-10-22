package com.casms.identity.service;

import com.casms.identity.entity.RolePermission;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.RolePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolePermissionInitializerTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private RolePermissionInitializer initializer;

    private Map<Role, Set<Permission>> rolePermissionsMap;

    @BeforeEach
    void setUp() {
        ArgumentCaptor<RolePermission> captor = ArgumentCaptor.forClass(RolePermission.class);
        initializer.initializeRolePermissions();

        verify(rolePermissionRepository, atLeastOnce()).save(captor.capture());

        rolePermissionsMap = new HashMap<>();
        for (RolePermission rp : captor.getAllValues()) {
            rolePermissionsMap.put(rp.getRole(), rp.getPermissions());
        }
    }

    @Test
    void admin_hasAllPermissions() {
        Set<Permission> adminPerms = rolePermissionsMap.get(Role.ADMIN);
        assertNotNull(adminPerms);
        assertEquals(Permission.values().length, adminPerms.size());
        assertTrue(adminPerms.contains(Permission.SUPPLIER_CREATE));
        assertTrue(adminPerms.contains(Permission.TOUR_GROUP_PASSENGER_ASSIGN));
    }

    @Test
    void cruiseManager_hasCrossModuleReads_butNoUnrelatedWrites() {
        Set<Permission> perms = rolePermissionsMap.get(Role.CRUISE_MANAGER);
        assertNotNull(perms);

        // Positive checks: minimal cross-module reads
        assertTrue(perms.contains(Permission.CRUISE_READ));
        assertTrue(perms.contains(Permission.SHIP_READ), "Cruise Manager must have SHIP_READ for assignment");
        assertTrue(perms.contains(Permission.OPERATIONAL_ISSUE_READ), "Cruise Manager must have OPERATIONAL_ISSUE_READ");
        assertTrue(perms.contains(Permission.SUPPLIER_READ), "Cruise Manager must have SUPPLIER_READ");
        assertTrue(perms.contains(Permission.EXCURSION_READ), "Cruise Manager must have EXCURSION_READ");
        assertTrue(perms.contains(Permission.TOUR_GROUP_READ), "Cruise Manager must have TOUR_GROUP_READ");
        assertTrue(perms.contains(Permission.ITINERARY_CREATE));
        assertTrue(perms.contains(Permission.ITINERARY_APPROVE));

        // Negative checks: no unauthorized writes
        assertFalse(perms.contains(Permission.SUPPLIER_CREATE), "Cruise Manager must not create suppliers");
        assertFalse(perms.contains(Permission.EXCURSION_CREATE), "Cruise Manager must not create excursions");
        assertFalse(perms.contains(Permission.TOUR_GROUP_CREATE), "Cruise Manager must not create tour groups");
        assertFalse(perms.contains(Permission.OPERATIONAL_ISSUE_UPDATE), "Cruise Manager must not update issues");
        assertFalse(perms.contains(Permission.SHIP_CREATE), "Cruise Manager must not create ships");
    }

    @Test
    void shipManager_hasReadinessAndCurrentCruise_butNoActivityWrites() {
        Set<Permission> perms = rolePermissionsMap.get(Role.SHIP_MANAGER);
        assertNotNull(perms);

        // Positive checks
        assertTrue(perms.contains(Permission.SHIP_READ));
        assertTrue(perms.contains(Permission.SHIP_READINESS), "Ship Manager must have SHIP_READINESS");
        assertTrue(perms.contains(Permission.SHIP_CURRENT_CRUISE), "Ship Manager must have SHIP_CURRENT_CRUISE");
        assertTrue(perms.contains(Permission.SHIP_AFFECTED_CRUISES), "Ship Manager must have SHIP_AFFECTED_CRUISES");
        assertTrue(perms.contains(Permission.DECK_CREATE));
        assertTrue(perms.contains(Permission.CABIN_CREATE));

        // Negative checks
        assertFalse(perms.contains(Permission.SUPPLIER_CREATE));
        assertFalse(perms.contains(Permission.CRUISE_CREATE));
        assertFalse(perms.contains(Permission.ITINERARY_CREATE));
    }

    @Test
    void onboardStaff_hasSalesAndServicePermissions_noCatalogOrActivityMutations() {
        Set<Permission> perms = rolePermissionsMap.get(Role.ONBOARD_STAFF);
        assertNotNull(perms);

        // Positive checks: Service execution, POS sales, Attendance, Read activities & excursions
        assertTrue(perms.contains(Permission.SERVICE_READ));
        assertTrue(perms.contains(Permission.SERVICE_REQUEST_READ));
        assertTrue(perms.contains(Permission.SERVICE_REQUEST_EXECUTE));
        assertTrue(perms.contains(Permission.PRODUCT_SALE));
        assertTrue(perms.contains(Permission.PAYMENT_PROCESS));
        assertTrue(perms.contains(Permission.ACTIVITY_READ));
        assertTrue(perms.contains(Permission.ACTIVITY_ATTENDANCE));
        assertTrue(perms.contains(Permission.EXCURSION_READ));
        assertTrue(perms.contains(Permission.TOUR_GROUP_READ));
        assertTrue(perms.contains(Permission.TOUR_GROUP_PASSENGER_ATTENDANCE));

        // Negative checks: Onboard staff must NOT modify catalog or activities
        assertFalse(perms.contains(Permission.ACTIVITY_CREATE));
        assertFalse(perms.contains(Permission.ACTIVITY_UPDATE));
        assertFalse(perms.contains(Permission.EXCURSION_CREATE));
        assertFalse(perms.contains(Permission.EXCURSION_UPDATE));
        assertFalse(perms.contains(Permission.TOUR_GROUP_CREATE));
        assertFalse(perms.contains(Permission.TOUR_GROUP_UPDATE));
        assertFalse(perms.contains(Permission.SERVICE_CREATE));
        assertFalse(perms.contains(Permission.SERVICE_UPDATE));
        assertFalse(perms.contains(Permission.SUPPLIER_CREATE));
        assertFalse(perms.contains(Permission.SHIP_CREATE));
        assertFalse(perms.contains(Permission.CRUISE_CREATE));
        assertFalse(perms.contains(Permission.ITINERARY_APPROVE));
    }

    @Test
    void activityManager_hasSeaServiceAndActivityPermissions() {
        Set<Permission> perms = rolePermissionsMap.get(Role.ACTIVITY_MANAGER);
        assertNotNull(perms);

        // Positive checks: Activities, services, tour packages
        assertTrue(perms.contains(Permission.ACTIVITY_CREATE));
        assertTrue(perms.contains(Permission.ACTIVITY_UPDATE));
        assertTrue(perms.contains(Permission.ACTIVITY_LIFECYCLE));
        assertTrue(perms.contains(Permission.SERVICE_CREATE));
        assertTrue(perms.contains(Permission.SERVICE_UPDATE));
        assertTrue(perms.contains(Permission.TOUR_PACKAGE_MANAGE));
        assertTrue(perms.contains(Permission.PACKAGE_COMPONENT_MANAGE));
        assertTrue(perms.contains(Permission.SERVICE_REQUEST_EXECUTE));

        // Negative checks: Not managing shore excursions, suppliers or ship fleet
        assertFalse(perms.contains(Permission.EXCURSION_CREATE));
        assertFalse(perms.contains(Permission.SUPPLIER_CREATE));
        assertFalse(perms.contains(Permission.TOUR_GROUP_CREATE));
        assertFalse(perms.contains(Permission.SHIP_CREATE));
    }

    @Test
    void shoreManager_hasShoreExcursionsSuppliersAndTourGroups() {
        Set<Permission> perms = rolePermissionsMap.get(Role.SHORE_MANAGER);
        assertNotNull(perms);

        // Positive checks: Shore excursions, suppliers, tour groups, guides
        assertTrue(perms.contains(Permission.EXCURSION_CREATE));
        assertTrue(perms.contains(Permission.EXCURSION_UPDATE));
        assertTrue(perms.contains(Permission.SUPPLIER_CREATE));
        assertTrue(perms.contains(Permission.SUPPLIER_UPDATE));
        assertTrue(perms.contains(Permission.SUPPLIER_VERIFY));
        assertTrue(perms.contains(Permission.TOUR_GROUP_CREATE));
        assertTrue(perms.contains(Permission.TOUR_GROUP_UPDATE));
        assertTrue(perms.contains(Permission.TOUR_GROUP_GUIDE_ASSIGN));
        assertTrue(perms.contains(Permission.TOUR_GROUP_PASSENGER_ASSIGN));

        // Negative checks: Not managing onboard activities or ship fleet
        assertFalse(perms.contains(Permission.ACTIVITY_CREATE));
        assertFalse(perms.contains(Permission.SERVICE_CREATE));
        assertFalse(perms.contains(Permission.SHIP_CREATE));
        assertFalse(perms.contains(Permission.CABIN_CREATE));
    }

    @Test
    void operationsManager_hasSupplierManagementAndOim() {
        Set<Permission> perms = rolePermissionsMap.get(Role.OPERATIONS_MANAGER);
        assertNotNull(perms);

        assertTrue(perms.contains(Permission.SUPPLIER_CREATE));
        assertTrue(perms.contains(Permission.SUPPLIER_READ));
        assertTrue(perms.contains(Permission.SUPPLIER_UPDATE));
        assertTrue(perms.contains(Permission.SUPPLIER_VERIFY));
        assertTrue(perms.contains(Permission.OPERATIONAL_ISSUE_READ));
        assertTrue(perms.contains(Permission.OPERATIONAL_ISSUE_UPDATE));
        assertTrue(perms.contains(Permission.BOOKING_READ));
        assertTrue(perms.contains(Permission.CABIN_READ));
    }

    @Test
    void bookingAgentAndCheckinStaff_haveMinimalReadAccess() {
        Set<Permission> bookingPerms = rolePermissionsMap.get(Role.BOOKING_AGENT);
        assertNotNull(bookingPerms);
        assertTrue(bookingPerms.contains(Permission.SHIP_READ));
        assertTrue(bookingPerms.contains(Permission.DECK_READ));
        assertTrue(bookingPerms.contains(Permission.CABIN_READ));
        assertTrue(bookingPerms.contains(Permission.CABIN_BLOCK));
        assertTrue(bookingPerms.contains(Permission.CABIN_RELEASE));
        assertFalse(bookingPerms.contains(Permission.SHIP_CREATE));
        assertFalse(bookingPerms.contains(Permission.CABIN_CREATE));

        Set<Permission> checkinPerms = rolePermissionsMap.get(Role.CHECKIN_STAFF);
        assertNotNull(checkinPerms);
        assertTrue(checkinPerms.contains(Permission.SHIP_READ));
        assertTrue(checkinPerms.contains(Permission.DECK_READ));
        assertTrue(checkinPerms.contains(Permission.CABIN_READ));
        assertFalse(checkinPerms.contains(Permission.SHIP_CREATE));
    }

    @Test
    void passenger_hasNoAdminMutations() {
        Set<Permission> perms = rolePermissionsMap.get(Role.PASSENGER);
        assertNotNull(perms);

        // Negative security checks: Passenger must not have write permissions to Admin entities
        assertFalse(perms.contains(Permission.SUPPLIER_CREATE));
        assertFalse(perms.contains(Permission.SUPPLIER_UPDATE));
        assertFalse(perms.contains(Permission.EXCURSION_CREATE));
        assertFalse(perms.contains(Permission.TOUR_GROUP_CREATE));
        assertFalse(perms.contains(Permission.TOUR_GROUP_PASSENGER_ASSIGN));
        assertFalse(perms.contains(Permission.OPERATIONAL_ISSUE_CREATE));
        assertFalse(perms.contains(Permission.SHIP_CREATE));
        assertFalse(perms.contains(Permission.CRUISE_CREATE));
        assertFalse(perms.contains(Permission.ITINERARY_CREATE));
        assertFalse(perms.contains(Permission.CHECKIN_BOARDING));
        assertFalse(perms.contains(Permission.BOOKING_CONFIRM));
    }

    @Test
    void financeOfficer_hasNoActivityMutations() {
        Set<Permission> perms = rolePermissionsMap.get(Role.FINANCE_OFFICER);
        assertNotNull(perms);

        assertFalse(perms.contains(Permission.SUPPLIER_CREATE));
        assertFalse(perms.contains(Permission.TOUR_GROUP_CREATE));
        assertFalse(perms.contains(Permission.TOUR_GROUP_PASSENGER_ASSIGN));
        assertFalse(perms.contains(Permission.ITINERARY_CREATE));
    }
}
