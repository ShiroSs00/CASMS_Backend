package com.casms.ship.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripRepository;
import com.casms.trip.repository.TripScheduleVersionRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CruiseRbacAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private PortRepository portRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripScheduleVersionRepository versionRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdCruiseIds = new ArrayList<>();
    private final List<UUID> createdShipIds = new ArrayList<>();
    private final List<UUID> createdPortIds = new ArrayList<>();

    private static final String VALID_OPEN_SALE_BODY =
            """
            {"expectedRevision":1,"saleScope":"CABIN_ONLY","fingerprint":"rbac-fixture"}
            """;

    private Trip sourceTrip;
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
        testShip.setStatus(ShipStatus.READY);
        testShip.setTotalCapacity(500);
        testShip.setTotalDecks(8);
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
            jdbc.update("DELETE FROM \"CruisePlanReview\" WHERE cruise_id=?", cruiseId);
            cruiseRepository.findById(cruiseId).ifPresent(cruiseRepository::delete);
        }
        if (sourceTrip != null) {
            tripRepository.deleteById(sourceTrip.getTripId());
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

    private User createActor(Role role) {
        User user = new User();
        user.setUsername("rbac-user-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);
        createdUserIds.add(user.getUserId());
        return user;
    }

    private String createJwtToken(Role role) {
        User actor = createActor(role);
        return jwtService.generateToken(actor.getUsername(), actor.getUserId());
    }

    private MockHttpServletRequestBuilder openSale(String body) {
        return post("/cruise/{cruiseId}/open-sale", testCruise.getCruiseId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void submitPlanFixture() {
        User crm = createActor(Role.CRUISE_MANAGER);
        Trip trip = new Trip();
        trip.setTripCode("TR-" + UUID.randomUUID().toString().substring(0, 8));
        trip.setName("RBAC schedule source");
        trip.setDurationDays(2);
        trip.setDurationNights(1);
        trip.setDurationGroup(TripDurationGroup.SHORT);
        trip.setStatus(TripStatus.READY);
        trip.setCreatedByUser(crm);
        sourceTrip = tripRepository.save(trip);

        TripScheduleVersion source = new TripScheduleVersion();
        source.setTrip(sourceTrip);
        source.setVersionNumber(1);
        source.setStatus(TripScheduleVersionStatus.PUBLISHED);
        source.setCreator(crm);
        source.setPublishedBy(crm);
        source.setPublishedAt(Instant.now());
        source = versionRepository.save(source);
        testCruise.setSourceTrip(sourceTrip);
        testCruise.setSourceVersion(source);
        testCruise.setSourceVersionRevision(source.getRevision());
        testCruise = cruiseRepository.save(testCruise);

        jdbc.update("""
                INSERT INTO "CruisePlanReview" (cruise_id, status, submitted_by, submitted_at)
                VALUES (?, 'SUBMITTED', ?, now())
                """, testCruise.getCruiseId(), crm.getUserId());
    }

    @Test
    @DisplayName(
            "BE-01: CRUISE_MANAGER có quyền gán/đổi/gỡ tàu nhưng gọi"
                + " open-sale/stop-sale/resume-sale bị 403 Forbidden")
    void cruiseManager_canAssignShip_butCannotOpenSale() throws Exception {
        String crmToken = createJwtToken(Role.CRUISE_MANAGER);

        // 1. CRM calling assign-ship: not 403 (authorized!)
        mockMvc.perform(
                        post("/cruise/{cruiseId}/assign-ship", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + crmToken)
                                .param("shipId", testShip.getShipId().toString()))
                .andExpect(
                        result -> {
                            int status = result.getResponse().getStatus();
                            org.junit.jupiter.api.Assertions.assertNotEquals(
                                    403,
                                    status,
                                    "CRUISE_MANAGER must have authority to assign ship, received"
                                        + " 403");
                        });

        // 2. CRM calling change-assigned-ship: not 403
        mockMvc.perform(
                        put("/cruise/{cruiseId}/change-assigned-ship", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + crmToken)
                                .param("newShipId", testShip.getShipId().toString()))
                .andExpect(
                        result -> {
                            int status = result.getResponse().getStatus();
                            org.junit.jupiter.api.Assertions.assertNotEquals(
                                    403,
                                    status,
                                    "CRUISE_MANAGER must have authority to change ship, received"
                                        + " 403");
                        });

        // 3. CRM calling unassign-ship: not 403
        mockMvc.perform(
                        post("/cruise/{cruiseId}/unassign-ship", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + crmToken))
                .andExpect(
                        result -> {
                            int status = result.getResponse().getStatus();
                            org.junit.jupiter.api.Assertions.assertNotEquals(
                                    403,
                                    status,
                                    "CRUISE_MANAGER must have authority to unassign ship, received"
                                        + " 403");
                        });

        // 4. CRM calling open-sale: MUST BE 403 FORBIDDEN!
        mockMvc.perform(
                        openSale(VALID_OPEN_SALE_BODY).header("Authorization", "Bearer " + crmToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 5. CRM calling stop-sale: MUST BE 403 FORBIDDEN!
        mockMvc.perform(
                        post("/cruise/{cruiseId}/stop-sale", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + crmToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Testing stop sale\"}"))
                .andExpect(status().isForbidden());

        // 6. CRM calling resume-sale: MUST BE 403 FORBIDDEN!
        mockMvc.perform(
                        post("/cruise/{cruiseId}/resume-sale", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + crmToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
            "BE-01: SHIP_MANAGER không có quyền gán tàu và không có quyền mở bán (đều nhận 403)")
    void shipManager_cannotAssignShip_andCannotOpenSale() throws Exception {
        String smToken = createJwtToken(Role.SHIP_MANAGER);

        // SHIP_MANAGER calling assign-ship: MUST BE 403
        mockMvc.perform(
                        post("/cruise/{cruiseId}/assign-ship", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + smToken)
                                .param("shipId", testShip.getShipId().toString()))
                .andExpect(status().isForbidden());

        // SHIP_MANAGER calling change-assigned-ship: MUST BE 403
        mockMvc.perform(
                        put("/cruise/{cruiseId}/change-assigned-ship", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + smToken)
                                .param("newShipId", testShip.getShipId().toString()))
                .andExpect(status().isForbidden());

        // SHIP_MANAGER calling unassign-ship: MUST BE 403
        mockMvc.perform(
                        post("/cruise/{cruiseId}/unassign-ship", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + smToken))
                .andExpect(status().isForbidden());

        // SHIP_MANAGER calling open-sale: MUST BE 403
        mockMvc.perform(
                        openSale(VALID_OPEN_SALE_BODY).header("Authorization", "Bearer " + smToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void anonymousOpenSaleIsForbiddenEvenWithCsrfToken() throws Exception {
        mockMvc.perform(openSale(VALID_OPEN_SALE_BODY).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void operationsManagerCannotAccessUnsubmittedDraftEvenWithValidPayload() throws Exception {
        String opsToken = createJwtToken(Role.OPERATIONS_MANAGER);
        mockMvc.perform(openSale(VALID_OPEN_SALE_BODY).header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        // The MVC scope interceptor also takes precedence over request binding.
        mockMvc.perform(openSale("{}").header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void openSaleBindingRejectsMissingFieldsAndMalformedJsonForInScopeActor() throws Exception {
        submitPlanFixture();
        String opsToken = createJwtToken(Role.OPERATIONS_MANAGER);
        mockMvc.perform(openSale("{}").header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(result -> assertInstanceOf(
                        HandlerMethodValidationException.class, result.getResolvedException()));
        mockMvc.perform(openSale("{").header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        mockMvc.perform(openSale("{}").header("Authorization", "Bearer " + createJwtToken(Role.CRUISE_MANAGER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(result -> assertInstanceOf(
                        HandlerMethodValidationException.class, result.getResolvedException()));
    }

    @Test
    void operationsManagerReachesSaleGuardsForSubmittedPlanButCannotAssignShip() throws Exception {
        submitPlanFixture();
        String opsToken = createJwtToken(Role.OPERATIONS_MANAGER);
        mockMvc.perform(openSale(VALID_OPEN_SALE_BODY).header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("READY_CONFIRMATION_REQUIRED"));

        mockMvc.perform(
                        post("/cruise/{cruiseId}/stop-sale", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + opsToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"QA stop sale\",\"expectedRevision\":-1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_REVISION"));

        mockMvc.perform(
                        post("/cruise/{cruiseId}/resume-sale", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + opsToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("READY_CONFIRMATION_REQUIRED"));

        mockMvc.perform(
                        post("/cruise/{cruiseId}/assign-ship", testCruise.getCruiseId())
                                .header("Authorization", "Bearer " + opsToken)
                                .param("shipId", testShip.getShipId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertEquals(CruiseStatus.DRAFT, cruiseRepository.findById(testCruise.getCruiseId())
                .orElseThrow().getStatus());
    }
}
