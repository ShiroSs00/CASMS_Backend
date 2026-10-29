package com.casms.ship.controller;

import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.security.jwt.JwtService;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Port;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.ship.repository.ShipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CruiseRbacAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private PortRepository portRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdCruiseIds = new ArrayList<>();
    private final List<UUID> createdShipIds = new ArrayList<>();
    private final List<UUID> createdPortIds = new ArrayList<>();

    private Cruise testCruise;
    private Ship testShip;

    @BeforeEach
    void setUp() {
        Port depPort = new Port();
        depPort.setPortCode("PORT-" + UUID.randomUUID().toString().substring(0, 6));
        depPort.setPortName("Departure Port Test");
        depPort.setCountryCode("VN");
        depPort.setTimeZoneId("Asia/Ho_Chi_Minh");
        depPort.setStatus(PortStatus.ACTIVE);
        depPort = portRepository.save(depPort);
        createdPortIds.add(depPort.getPortId());

        testShip = new Ship();
        testShip.setShipCode("SH-" + UUID.randomUUID().toString().substring(0, 6));
        testShip.setShipName("Rbac Ship Test");
        testShip.setStatus(ShipStatus.ACTIVE);
        testShip.setTotalCapacity(500);
        testShip.setTotalDecks(5);
        testShip = shipRepository.save(testShip);
        createdShipIds.add(testShip.getShipId());

        testCruise = new Cruise();
        testCruise.setCruiseCode("CR-" + UUID.randomUUID().toString().substring(0, 6));
        testCruise.setCruiseName("RBAC Test Cruise");
        testCruise.setCruiseType(com.casms.ship.enums.CruiseCruiseType.MULTI_DAYS);
        testCruise.setStatus(CruiseStatus.DRAFT);
        testCruise.setDepartureDatetime(Instant.now().plus(5, ChronoUnit.DAYS));
        testCruise.setReturnDatetime(Instant.now().plus(7, ChronoUnit.DAYS));
        testCruise.setDeparturePort(depPort);
        testCruise.setReturnPort(depPort);
        testCruise = cruiseRepository.save(testCruise);
        createdCruiseIds.add(testCruise.getCruiseId());
    }

    @AfterEach
    void tearDown() {
        for (UUID cruiseId : createdCruiseIds) {
            cruiseRepository.findById(cruiseId).ifPresent(cruiseRepository::delete);
        }
        for (UUID shipId : createdShipIds) {
            shipRepository.findById(shipId).ifPresent(shipRepository::delete);
        }
        for (UUID portId : createdPortIds) {
            portRepository.findById(portId).ifPresent(portRepository::delete);
        }
        for (UUID userId : createdUserIds) {
            userRepository.findById(userId).ifPresent(userRepository::delete);
        }
    }

    private String createJwtToken(Role role) {
        User user = new User();
        user.setUsername("rbac-user-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);
        createdUserIds.add(user.getUserId());
        return jwtService.generateToken(user.getUsername(), user.getUserId());
    }

    @Test
    @DisplayName("BE-01: CRUISE_MANAGER có quyền gán/đổi/gỡ tàu nhưng gọi open-sale/stop-sale/resume-sale bị 403 Forbidden")
    void cruiseManager_canAssignShip_butCannotOpenSale() throws Exception {
        String crmToken = createJwtToken(Role.CRUISE_MANAGER);

        // 1. CRM calling assign-ship: not 403 (authorized!)
        mockMvc.perform(post("/cruise/{cruiseId}/assign-ship", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + crmToken)
                        .param("shipId", testShip.getShipId().toString()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status,
                            "CRUISE_MANAGER must have authority to assign ship, received 403");
                });

        // 2. CRM calling change-assigned-ship: not 403
        mockMvc.perform(put("/cruise/{cruiseId}/change-assigned-ship", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + crmToken)
                        .param("newShipId", testShip.getShipId().toString()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status,
                            "CRUISE_MANAGER must have authority to change ship, received 403");
                });

        // 3. CRM calling unassign-ship: not 403
        mockMvc.perform(post("/cruise/{cruiseId}/unassign-ship", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + crmToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status,
                            "CRUISE_MANAGER must have authority to unassign ship, received 403");
                });

        // 4. CRM calling open-sale: MUST BE 403 FORBIDDEN!
        mockMvc.perform(post("/cruise/{cruiseId}/open-sale", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + crmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // 5. CRM calling stop-sale: MUST BE 403 FORBIDDEN!
        mockMvc.perform(post("/cruise/{cruiseId}/stop-sale", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + crmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Testing stop sale\"}"))
                .andExpect(status().isForbidden());

        // 6. CRM calling resume-sale: MUST BE 403 FORBIDDEN!
        mockMvc.perform(post("/cruise/{cruiseId}/resume-sale", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + crmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BE-01: SHIP_MANAGER không có quyền gán tàu và không có quyền mở bán (đều nhận 403)")
    void shipManager_cannotAssignShip_andCannotOpenSale() throws Exception {
        String smToken = createJwtToken(Role.SHIP_MANAGER);

        // SHIP_MANAGER calling assign-ship: MUST BE 403
        mockMvc.perform(post("/cruise/{cruiseId}/assign-ship", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + smToken)
                        .param("shipId", testShip.getShipId().toString()))
                .andExpect(status().isForbidden());

        // SHIP_MANAGER calling change-assigned-ship: MUST BE 403
        mockMvc.perform(put("/cruise/{cruiseId}/change-assigned-ship", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + smToken)
                        .param("newShipId", testShip.getShipId().toString()))
                .andExpect(status().isForbidden());

        // SHIP_MANAGER calling unassign-ship: MUST BE 403
        mockMvc.perform(post("/cruise/{cruiseId}/unassign-ship", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + smToken))
                .andExpect(status().isForbidden());

        // SHIP_MANAGER calling open-sale: MUST BE 403
        mockMvc.perform(post("/cruise/{cruiseId}/open-sale", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + smToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BE-01: OPERATIONS_MANAGER có quyền mở/dừng/mở lại bán theo quyền mới (không bị 403), nhưng không trực tiếp gán tàu (403)")
    void operationsManager_canManageSale_butCannotAssignShip() throws Exception {
        String opsToken = createJwtToken(Role.OPERATIONS_MANAGER);

        // 1. OPS calling open-sale: NOT 403 (has CRUISE_OPEN_SALE!)
        mockMvc.perform(post("/cruise/{cruiseId}/open-sale", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status,
                            "OPERATIONS_MANAGER must have authority to open sale, received 403");
                });

        // 2. OPS calling stop-sale: NOT 403 (has CRUISE_STOP_SALE!)
        mockMvc.perform(post("/cruise/{cruiseId}/stop-sale", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Testing stop sale\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status,
                            "OPERATIONS_MANAGER must have authority to stop sale, received 403");
                });

        // 3. OPS calling resume-sale: NOT 403 (has CRUISE_RESUME_SALE!)
        mockMvc.perform(post("/cruise/{cruiseId}/resume-sale", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status,
                            "OPERATIONS_MANAGER must have authority to resume sale, received 403");
                });

        // 4. OPS calling assign-ship: MUST BE 403 FORBIDDEN!
        mockMvc.perform(post("/cruise/{cruiseId}/assign-ship", testCruise.getCruiseId())
                        .header("Authorization", "Bearer " + opsToken)
                        .param("shipId", testShip.getShipId().toString()))
                .andExpect(status().isForbidden());
    }
}
