package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruisePreparationConfirmRequest;
import com.casms.ship.dto.request.CruisePreparationSubmitRequest;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.staffing.StaffingWorkflowService;
import com.casms.staffing.StaffingWorkflowService.*;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Never falls back to the application database. Fixtures use only a disposable local database. */
@SpringBootTest(properties = "casms.booking-email.enabled=false")
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="CASMS_TEST_POSTGRES_URL", matches=".+")
class ItineraryProgramsPostgresIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("CASMS_TEST_POSTGRES_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:(?:55475|56559)/casms75_test"))
            throw new IllegalStateException("Use only the disposable CASMS-75 database");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
    }
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired ItineraryProgramService programs;
    @Autowired ItineraryProgramPolicy policy;
    @Autowired CruiseMainflowService plans;
    @Autowired StaffingWorkflowService staffing;
    @Autowired CruiseWorkHandoffService handoffs;
    @Autowired CruiseStaffService cruiseStaff;
    @Autowired CruisePreparationService preparation;
    @Autowired RolePermissionRepository roles;
    @Autowired MockMvc mvc;
    @Autowired com.casms.security.jwt.JwtService jwt;
    private UUID cruiseId, itineraryId, stopId, areaId, serviceAreaId, sourceActivity, sourceExcursion, destinationId, shipId;
    private final Instant departure = Instant.parse("2030-02-01T00:00:00Z");
    private final Instant arrival = departure.plusSeconds(8*3600);
    private final Instant portDeparture = departure.plusSeconds(18*3600);

    @BeforeEach void fixture() {
        String code = UUID.randomUUID().toString().substring(0,8);
        Ship ship = new Ship(); ship.setShipCode("SH-"+code); ship.setShipName("Test vessel");
        ship.setStatus(ShipStatus.READY); ship.setTotalCapacity(100); ship.setTotalDecks(1); em.persist(ship);
        shipId=ship.getShipId();
        Deck deck = new Deck(); deck.setShip(ship); deck.setDeckNumber(1); deck.setDeckName("Open deck");
        deck.setPriceMultiplier(BigDecimal.ONE); em.persist(deck);
        ShipModel model = new ShipModel(); model.setShip(ship); model.setVersion(code);
        model.setFileUri("/fixture.glb"); model.setFileFormat("glb"); model.setStatus(ShipModelStatus.READY); em.persist(model);
        areaId=area(model,deck,"Event space"); serviceAreaId=area(model,deck,"Service space");
        Port port = new Port(); port.setPortCode("VN-"+code); port.setPortName("Test port");
        port.setCountryCode("VN"); port.setTimeZoneId("Asia/Ho_Chi_Minh"); port.setStatus(PortStatus.ACTIVE); em.persist(port);
        Destination destination = new Destination(); destination.setDestinationName("Planned visit");
        destination.setCountryCode("VN"); destination.setStatus(DestinationStatus.ACTIVE); em.persist(destination);
        destinationId=destination.getDestinationId();
        Cruise cruise=new Cruise(); cruise.setCruiseCode("CR-"+code); cruise.setCruiseName("Staged program test");
        cruise.setShip(ship); cruise.setDeparturePort(port); cruise.setReturnPort(port);
        cruise.setDepartureDatetime(departure); cruise.setReturnDatetime(departure.plusSeconds(2*86400));
        cruise.setCruiseType(CruiseCruiseType.MULTI_DAYS); cruise.setStatus(CruiseStatus.DRAFT); em.persist(cruise);
        cruiseId=cruise.getCruiseId();
        em.flush();
        UUID sourceTrip = jdbc.queryForObject("""
                INSERT INTO "Trip" (trip_code, name, duration_days, duration_nights, trip_type)
                VALUES (?, 'Program fixture itinerary', 3, 2, 'ROUND_TRIP') RETURNING trip_id
                """, UUID.class, "TR-" + code);
        UUID sourceVersion = jdbc.queryForObject("""
                INSERT INTO "TripScheduleVersion" (trip_id, version_number, status)
                VALUES (?, 1, 'PUBLISHED') RETURNING version_id
                """, UUID.class, sourceTrip);
        jdbc.update("""
                UPDATE "Cruise" SET source_trip_id=?, source_version_id=? WHERE cruise_id=?
                """, sourceTrip, sourceVersion, cruiseId);
        Itinerary itinerary = new Itinerary(); itinerary.setCruise(cruise); itinerary.setItineraryName("Draft");
        itinerary.setStartDate(LocalDate.of(2030,2,1)); itinerary.setEndDate(LocalDate.of(2030,2,3));
        itinerary.setItineraryType(ItineraryType.ROUND_TRIP); itinerary.setStatus(ItineraryStatus.DRAFT); em.persist(itinerary);
        itineraryId=itinerary.getItineraryId();
        ItineraryDay first = null;
        for(int n=1;n<=3;n++) {
            ItineraryDay day=new ItineraryDay(); day.setItinerary(itinerary); day.setDayNumber(n);
            day.setDate(LocalDate.of(2030,2,n)); day.setDayType(n==1?ItineraryDayType.PORT_DAY:ItineraryDayType.SEA_DAY);
            em.persist(day); if(n==1) first=day;
        }
        CruiseStop stop=new CruiseStop(); stop.setItinerary(itinerary); stop.setPort(port); stop.setDestination(destination);
        stop.setArrivalItineraryDay(first); stop.setDepartureItineraryDay(first); stop.setStopOrder(1);
        stop.setPlannedArrival(arrival); stop.setPlannedDeparture(portDeparture); stop.setAllAboardTime(portDeparture.minusSeconds(3600));
        stop.setCallType(CruiseStopCallType.NORMAL); stop.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop.setStatus(CruiseStopStatus.PLANNED); em.persist(stop); stopId=stop.getCruiseStopId();
        em.flush(); em.clear();
        jdbc.update("INSERT INTO \"PortDestinationAccess\" (port_id,destination_id) VALUES (?,?)",
                port.getPortId(),destinationId);
        jdbc.update("UPDATE \"ShipModelObject\" SET area_usage='ACTIVITY_SPACE' WHERE ship_model_object_id=?",areaId);
        jdbc.update("UPDATE \"ShipModelObject\" SET area_usage='SERVICE' WHERE ship_model_object_id=?",serviceAreaId);
        sourceActivity=UUID.randomUUID(); sourceExcursion=UUID.randomUUID(); UUID supplier=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO "OnboardActivity"(onboard_activity_id,activity_code,activity_name,status,catalog_entry,created_at,updated_at)
            VALUES (?,?,'Catalog activity','PLANNED',true,now(),now())
            """,sourceActivity,"AC-T"+code);
        jdbc.update("""
            INSERT INTO "Supplier"(supplier_id,supplier_name,contact_name,status,verification_status,created_at,updated_at)
            VALUES (?,'Synthetic provider','Test contact','ACTIVE','VERIFIED',now(),now())
            """,supplier);
        jdbc.update("""
            INSERT INTO "ShoreExcursion"(shore_excursion_id,excursion_code,excursion_name,supplier_id,destination_id,meeting_point,status,created_at,updated_at)
            VALUES (?,?,'Catalog excursion',?,?,'Pier','PLANNED',now(),now())
            """,sourceExcursion,"EX-T"+code,supplier,destinationId);
    }
    private UUID area(ShipModel model,Deck deck,String name) {
        ShipModelObject area=new ShipModelObject(); area.setShipModel(model); area.setDeck(deck);
        area.setNodeKey(UUID.randomUUID().toString()); area.setDisplayName(name); area.setObjectType(ShipModelObjectType.FACILITY);
        em.persist(area); return area.getShipModelObjectId();
    }
    private ItineraryProgramService.AssignmentResult assignBoth() {
        return programs.assign(itineraryId,revision(),List.of(sourceActivity),
                List.of(new ItineraryProgramService.ShoreSelection(sourceExcursion,stopId,destinationId)));
    }
    private long revision() { return jdbc.queryForObject("SELECT revision FROM \"Cruise\" WHERE cruise_id=?",Long.class,cruiseId); }
    private ItineraryProgramService.ActivitySchedule activitySchedule(UUID area) {
        return new ItineraryProgramService.ActivitySchedule(arrival,arrival.plusSeconds(7200),area,"Event deck",20,BigDecimal.ZERO);
    }
    private void assertCode(String expected, Executable action) {
        assertEquals(expected,assertThrows(BusinessException.class,action).getCode());
    }
    private User createUser(Role... roles) {
        var user=new User();
        user.setUsername("casms75-"+UUID.randomUUID()); user.setPasswordHash("unused-synthetic-password-hash");
        user.setStatus(UserStatus.ACTIVE); user.setRoles(Set.of(roles));
        em.persist(user); em.flush();
        return user;
    }

    private String token(Role role) {
        var user = createUser(role);
        return "Bearer " + jwt.generateToken(user.getUsername(), user.getUserId());
    }

    private String coordinatorToken() {
        var user = createUser(Role.CRUISE_MANAGER);
        jdbc.update("UPDATE \"Cruise\" SET preparation_coordinator_id=? WHERE cruise_id=?", user.getUserId(), cruiseId);
        return "Bearer " + jwt.generateToken(user.getUsername(), user.getUserId());
    }

    private String token(CasmsPrincipal actor) {
        return "Bearer " + jwt.generateToken(actor.getUsername(), actor.getUserId());
    }

    private CasmsPrincipal actor(Role... role) {
        return new CasmsPrincipal(createUser(role), roles);
    }

    private void staffingFixture(CasmsPrincipal crm, CasmsPrincipal ship, CasmsPrincipal hr) {
        var cabin = new Cabin();
        cabin.setShip(em.getReference(Ship.class, shipId));
        cabin.setDeck(em.getReference(Deck.class, serviceDeck()));
        cabin.setCabinNumber("101");
        cabin.setCabinType(CabinType.INTERIOR);
        cabin.setCapacity(2);
        cabin.setStatus(CabinStatus.AVAILABLE);
        em.persist(cabin);
        em.flush();
        jdbc.update("UPDATE \"Cruise\" SET preparation_coordinator_id=? WHERE cruise_id=?", crm.getUserId(), cruiseId);
        em.clear();
        preparation.saveOperatingBudget(cruiseId, revision(), BigDecimal.valueOf(1000), crm);
        var membership = new CruiseStaffRequest();
        membership.setUserId(ship.getUserId());
        membership.setRoleInCruise("SHIP_MANAGER");
        cruiseStaff.assignStaff(cruiseId, membership, hr);
    }

    private UUID serviceDeck() {
        return jdbc.queryForObject("SELECT deck_id FROM \"ShipModelObject\" WHERE ship_model_object_id=?",
                UUID.class, serviceAreaId);
    }

    private Snapshot completeAsm(Snapshot request, CasmsPrincipal asm, CasmsPrincipal crm) {
        var pending = handoffs.inbox(asm).stream()
                .filter(task -> task.cruiseId().equals(cruiseId)).findFirst().orElseThrow();
        var task = handoffs.respond(cruiseId, CruiseWorkHandoffService.Group.ASM,
                CruiseWorkHandoffService.Action.ACCEPT, pending.revision(), null, asm);
        handoffs.respond(cruiseId, CruiseWorkHandoffService.Group.ASM,
                CruiseWorkHandoffService.Action.COMPLETE, task.revision(), "QA program complete", asm);
        var promoted = staffing.detail(request.id(), crm);
        assertEquals("PENDING_SHIP_REVIEW", promoted.status());
        return promoted;
    }

    @Test void activitySelectionUsesOnlyTheEffectiveReadyModel() throws Exception {
        String crm = coordinatorToken();
        var readyArea = em.find(ShipModelObject.class, areaId);
        UUID readyModel = readyArea.getShipModel().getShipModelId();
        var draft = new ShipModel();
        draft.setShip(em.getReference(Ship.class, shipId));
        draft.setVersion("new-draft");
        draft.setFileUri("/draft.glb");
        draft.setFileFormat("glb");
        em.persist(draft);
        UUID draftArea = area(draft, readyArea.getDeck(), "New activity space");
        em.flush();
        jdbc.update("UPDATE \"ShipModelObject\" SET area_usage='ACTIVITY_SPACE' WHERE ship_model_object_id=?", draftArea);
        assertEquals(serviceDeck(), policy.requireActivityArea(areaId, cruiseId, false));
        assertCode("ACTIVITY_AREA_REQUIRED", () -> policy.requireActivityArea(draftArea, cruiseId, false));
        mvc.perform(get("/cruise-plans/{id}/program-options", cruiseId).header("Authorization", crm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ship.modelId").value(readyModel.toString()))
                .andExpect(jsonPath("$.data.activityAreas.length()").value(1))
                .andExpect(jsonPath("$.data.activityAreas[0].id").value(areaId.toString()));
        jdbc.update("UPDATE \"ShipModel\" SET status='READY' WHERE ship_model_id=?", draft.getShipModelId());
        assertEquals(serviceDeck(), policy.requireActivityArea(draftArea, cruiseId, false));
        assertCode("ACTIVITY_AREA_REQUIRED", () -> policy.requireActivityArea(areaId, cruiseId, false));
        mvc.perform(get("/cruise-plans/{id}/program-options", cruiseId).header("Authorization", crm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ship.modelId").value(draft.getShipModelId().toString()))
                .andExpect(jsonPath("$.data.activityAreas.length()").value(1))
                .andExpect(jsonPath("$.data.activityAreas[0].id").value(draftArea.toString()));
    }

    @Test void budgetGateAndDeckBlockersUseThePublishedHttpContract() throws Exception {
        String crm = coordinatorToken();
        var asm = actor(Role.ACTIVITY_SERVICE_MANAGER);
        String budget = "/cruise/{id}/preparation/operating-budget";
        String request = "/cruises/{id}/staffing-requests";
        mvc.perform(post(request, cruiseId).header("Authorization", crm)
                        .contentType("application/json").content("{\"scheduleRevision\":" + revision() + ",\"requirements\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREPARATION_BUDGET_REQUIRED"));
        mvc.perform(get(budget, cruiseId).header("Authorization", crm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canEdit").value(true));
        mvc.perform(put(budget, cruiseId).header("Authorization", crm).contentType("application/json")
                        .content("{\"expectedRevision\":" + revision() + ",\"amountVnd\":\"1000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.amountVnd").value("1000"));
        mvc.perform(post(request, cruiseId).header("Authorization", crm)
                        .contentType("application/json").content("{\"scheduleRevision\":" + revision() + ",\"requirements\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_ASM"));
        var task = handoffs.inbox(asm).stream().filter(row -> cruiseId.equals(row.cruiseId())).findFirst().orElseThrow();
        handoffs.respond(cruiseId, CruiseWorkHandoffService.Group.ASM,
                CruiseWorkHandoffService.Action.ACCEPT, task.revision(), null, asm);

        String locations = "/cruise/{id}/service-locations";
        mvc.perform(get(locations, cruiseId).header("Authorization", token(asm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipId").value(shipId.toString()))
                .andExpect(jsonPath("$.data.deckBlocker").doesNotExist())
                .andExpect(jsonPath("$.data.canPropose").value(true));

        Ship emptyShip = new Ship();
        emptyShip.setShipCode("EMPTY-" + UUID.randomUUID().toString().substring(0, 8));
        emptyShip.setShipName("Test ship without decks");
        emptyShip.setStatus(ShipStatus.DRAFT);
        emptyShip.setTotalCapacity(100);
        emptyShip.setTotalDecks(1);
        em.persist(emptyShip);
        em.flush();
        jdbc.update("UPDATE \"Cruise\" SET ship_id=? WHERE cruise_id=?", emptyShip.getShipId(), cruiseId);
        em.clear();
        mvc.perform(get(locations, cruiseId).header("Authorization", token(asm)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deckBlocker").value("SHIP_DECK_REQUIRED"))
                .andExpect(jsonPath("$.data.canPropose").value(false));
        jdbc.update("UPDATE \"Cruise\" SET ship_id=NULL WHERE cruise_id=?", cruiseId);
        em.clear();
        mvc.perform(get(locations, cruiseId).header("Authorization", token(asm)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deckBlocker").value("SHIP_REQUIRED"));
    }

    @Test void staffingV2ShipsWithoutServiceAreasAndTechnicalApprovalHasItsOwnRevision() throws Exception {
        var crm = actor(Role.CRUISE_MANAGER);
        var asm = actor(Role.ACTIVITY_SERVICE_MANAGER);
        var ship = actor(Role.SHIP_MANAGER, Role.OPERATIONS_MANAGER);
        var hr = actor(Role.HR_MANAGER);
        var ops = actor(Role.OPERATIONS_MANAGER);
        staffingFixture(crm, ship, hr);
        var pending = staffing.submit(cruiseId, new CreateRequest(revision(), List.of()), crm);
        assertEquals("PENDING_ASM", pending.status());
        var shipReview = completeAsm(pending, asm, crm);
        var empty = staffing.saveShipPlan(pending.id(), new ShipPlanInput(shipReview.revision(), List.of(), List.of()), ship);
        assertTrue(empty.shipPlanReady());
        assertTrue(empty.plannedServices().isEmpty());
        mvc.perform(get("/cruises/staffing-requests/{id}/review-details", pending.id())
                        .header("Authorization", token(ship)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shipPlanReady").value(true))
                .andExpect(jsonPath("$.data.request.planResetImpact.assignmentsToClear").value(0));
        var checked = advance(empty.request(), "SHIP_CHECKED", ship);
        assertEquals("SENT_TO_HR", checked.status());

        var technical = preparation.getPreparationDashboard(cruiseId).getSections().stream()
                .filter(section -> "TECHNICAL_NAUTICAL".equals(section.getSectionCode())).findFirst().orElseThrow();
        assertEquals("READY", technical.getDataReadiness());
        assertEquals("NOT_SUBMITTED", technical.getSubmissionStatus());
        var submit = new CruisePreparationSubmitRequest();
        submit.setExpectedRevision(technical.getRevision());
        submit.setEvidence("QA technical inspection");
        var submitted = asActor(ship, () -> preparation.submitSection(
                cruiseId, technical.getSectionCode(), submit, ship.getUserId(), Set.of("SHIP_MANAGER")));
        assertEquals("PENDING", submitted.getApprovalStatus());
        assertEquals(submitted.getRevision(), submitted.getSubmittedRevision());
        var confirm = new CruisePreparationConfirmRequest();
        confirm.setExpectedRevision(submitted.getSubmittedRevision());
        assertCode("SELF_APPROVAL_FORBIDDEN",
                () -> asActor(ship, () -> preparation.confirmSection(
                        cruiseId, technical.getSectionCode(), confirm, ship.getUserId(), ship.getUsername())));
        var confirmed = asActor(ops, () -> preparation.confirmSection(
                cruiseId, technical.getSectionCode(), confirm, ops.getUserId(), ops.getUsername()));
        assertEquals("CONFIRMED", confirmed.getApprovalStatus());
        assertEquals(submitted.getRevision(), confirmed.getSubmittedRevision());
        assertEquals(ops.getUserId(), confirmed.getConfirmedByUserId());
        mvc.perform(get("/cruise/{id}/preparation", cruiseId).header("Authorization", token(ship)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections[?(@.sectionCode == 'TECHNICAL_NAUTICAL')].approvalStatus",
                        org.hamcrest.Matchers.contains("CONFIRMED")))
                .andExpect(jsonPath("$.data.sections[?(@.sectionCode == 'TECHNICAL_NAUTICAL')].submittedRevision",
                        org.hamcrest.Matchers.contains(submitted.getSubmittedRevision().intValue())));

        var staffed = advance(checked, "HR_SUBMITTED", hr);
        assertEquals("HR_SUBMITTED", staffed.status());
        assertCode("STAFFING_ROLE_REQUIRED", () -> advance(staffed, "PLANNER_CONFIRMED", ship));
        assertCode("STAFFING_PLANNER_SCOPE_FORBIDDEN", () -> advance(staffed, "PLANNER_CONFIRMED", actor(Role.CRUISE_MANAGER)));
        assertCode("STAFFING_REVISION_CONFLICT", () -> staffing.transition(staffed.id(), "PLANNER_CONFIRMED",
                new ReviewRequest(staffed.revision() - 1, null), crm));
        var reviewed = advance(staffed, "PLANNER_CONFIRMED", crm);
        assertEquals("PLANNER_CONFIRMED", reviewed.status());
        var approved = advance(reviewed, "OPS_APPROVED", ops);
        assertEquals("OPS_APPROVED", approved.status());
        assertTrue(staffing.ready(cruiseId));

        programs.assign(itineraryId, revision(), List.of(sourceActivity), List.of());
        assertTrue(staffing.detail(pending.id(), crm).shipPlanStale());
        assertFalse(staffing.ready(cruiseId));
    }

    private <T> T asActor(CasmsPrincipal actor, Supplier<T> action) {
        var original = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(original);
        }
    }

    private Snapshot advance(Snapshot base, String action, CasmsPrincipal actor) {
        return staffing.transition(base.id(), action, new ReviewRequest(base.revision(), null), actor);
    }

    @Test void staffingEligibilityAndReturnUseLiveShipConfigurationWithoutGrantingPosAccess() throws Exception {
        var crm = actor(Role.CRUISE_MANAGER);
        var asm = actor(Role.ACTIVITY_SERVICE_MANAGER);
        var ship = actor(Role.SHIP_MANAGER);
        var hr = actor(Role.HR_MANAGER);
        var worker = actor(Role.ONBOARD_STAFF);
        var replacement = actor(Role.ONBOARD_STAFF);
        staffingFixture(crm, ship, hr);
        UUID service = UUID.randomUUID(), terminal = UUID.randomUUID(), deck = serviceDeck();
        jdbc.update("""
                INSERT INTO "Service" (service_id,service_code,service_name,service_type,status,
                    requires_schedule,created_at,updated_at)
                VALUES (?,?,'QA onboard service','FOOD','ACTIVE',true,now(),now())
                """, service, "QA-" + service);
        jdbc.update("""
                INSERT INTO "ServiceSchedule" (schedule_code,service_id,cruise_id,starts_at,ends_at,location,capacity)
                VALUES (?,?,?,?,?,'Fixture location',10)
                """, "QA-" + service, service, cruiseId, Timestamp.from(arrival), Timestamp.from(arrival.plusSeconds(900)));
        var pending = staffing.submit(cruiseId, new CreateRequest(revision(), List.of()), crm);
        var shipReview = completeAsm(pending, asm, crm);
        assertCode("STAFFING_SHIP_SCOPE_FORBIDDEN", () -> staffing.saveShipPlan(pending.id(),
                new ShipPlanInput(shipReview.revision(), List.of(), List.of()), actor(Role.SHIP_MANAGER)));
        var needs = List.of(new NeedInput(serviceAreaId, "ONBOARD_STAFF", 1));
        var plan = staffing.saveShipPlan(pending.id(),
                new ShipPlanInput(shipReview.revision(), List.of(serviceAreaId), needs), ship);
        assertEquals("DECK_SERVICE_REQUIRED", plan.plannedServices().getFirst().placementBlocker());
        jdbc.update("""
                INSERT INTO "DeckServiceAssignment" (assignment_id,deck_id,service_id,created_at)
                VALUES (gen_random_uuid(),?,?,now())
                """, deck, service);
        assertEquals("AREA_SERVICE_REQUIRED", staffing.reviewDetails(pending.id(), ship).plannedServices().getFirst().placementBlocker());
        jdbc.update("""
                INSERT INTO "AreaServiceAssignment" (assignment_id,area_object_id,service_id,inherit_permissions)
                VALUES (gen_random_uuid(),?,?,true)
                """, serviceAreaId, service);
        assertEquals("SERVICE_AREA_POS_REQUIRED", staffing.reviewDetails(pending.id(), ship).plannedServices().getFirst().placementBlocker());
        jdbc.update("""
                INSERT INTO "PosTerminal" (terminal_id,ship_id,area_object_id,terminal_code,terminal_name,secret_hash)
                VALUES (?,?,?,?,'QA terminal','unused')
                """, terminal, shipId, serviceAreaId, "QA-" + terminal);
        jdbc.update("""
                INSERT INTO "PosTerminalArea" (terminal_id,area_object_id,enabled) VALUES (?,?,true)
                ON CONFLICT DO NOTHING
                """, terminal, serviceAreaId);
        jdbc.update("""
                INSERT INTO "ServiceOperationPolicy" (service_id,permissions) VALUES (?,ARRAY['SERVICE_READ'])
                ON CONFLICT (service_id) DO UPDATE SET permissions=excluded.permissions
                """, service);
        assertEquals("SERVICE_POS_PERMISSION_REQUIRED", staffing.reviewDetails(pending.id(), ship).plannedServices().getFirst().placementBlocker());
        jdbc.update("UPDATE \"PosTerminal\" SET permissions=ARRAY['SERVICE_READ'] WHERE terminal_id=?", terminal);
        var configured = staffing.reviewDetails(pending.id(), ship).plannedServices().getFirst();
        assertEquals(List.of(new Placement(serviceAreaId, terminal)), configured.configuredPlacements());
        assertTrue(configured.eligiblePlacements().isEmpty());
        var withTerminal = staffing.placeAreaPos(pending.id(), serviceAreaId, new AreaPosInput(terminal, plan.request().revision()), ship);
        assertEquals(configured.configuredPlacements(), withTerminal.plannedServices().getFirst().eligiblePlacements());
        mvc.perform(get("/cruises/staffing-requests/{id}/review-details", pending.id())
                        .header("Authorization", token(ship)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plannedServices[0].eligiblePlacements[0].areaId").value(serviceAreaId.toString()))
                .andExpect(jsonPath("$.data.plannedServices[0].eligiblePlacements[0].terminalId").value(terminal.toString()));
        var withPlacement = staffing.placeService(pending.id(), service,
                new ServicePlacementInput(serviceAreaId, terminal, withTerminal.request().revision()), ship);
        var dispatched = advance(withPlacement.request(), "SHIP_CHECKED", ship);
        assertEquals("SENT_TO_HR", dispatched.status());
        UUID need = dispatched.needs().getFirst().needId();
        var assigned = staffing.assignNeed(pending.id(), need, worker.getUserId(), new NeedAssignmentInput(dispatched.revision(), true), hr);
        var unassigned = staffing.assignNeed(pending.id(), need, worker.getUserId(), new NeedAssignmentInput(assigned.revision(), false), hr);
        var replaced = staffing.assignNeed(pending.id(), need, replacement.getUserId(), new NeedAssignmentInput(unassigned.revision(), true), hr);
        assertEquals(List.of(replacement.getUserId()), replaced.needs().getFirst().assignedUserIds());
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM "AreaStaffAssignment" a JOIN "CruiseStaff" cs USING (cruise_staff_id)
                WHERE cs.cruise_id=? AND a.enabled
                """, Long.class, cruiseId));
        var serviceAssigned = staffing.assignServiceNeed(pending.id(), service, worker.getUserId(),
                new NeedAssignmentInput(replaced.revision(), true), hr);
        assertEquals(List.of(worker.getUserId()), serviceAssigned.serviceNeeds().getFirst().assignedUserIds());
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM "StaffServiceAssignment" ssa
                JOIN "AreaStaffAssignment" a USING (assignment_id)
                JOIN "CruiseStaff" cs USING (cruise_staff_id)
                WHERE cs.cruise_id=? AND cs.user_id=? AND ssa.service_id=? AND ssa.enabled AND ssa.inherit_permissions
                """, Long.class, cruiseId, worker.getUserId(), service));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM "CruiseStaffDuty" d
                JOIN "CruiseStaff" cs USING (cruise_staff_id)
                WHERE cs.cruise_id=? AND cs.user_id=? AND d.service_id=? AND d.ended_at IS NULL
                """, Long.class, cruiseId, worker.getUserId(), service));
        assertEquals(new PlanResetImpact(1, 2), serviceAssigned.planResetImpact());
        jdbc.update("UPDATE \"PosTerminal\" SET permissions='{}' WHERE terminal_id=?", terminal);
        var stale = staffing.detail(pending.id(), hr);
        assertTrue(stale.shipPlanStale());
        assertTrue(stale.needs().getFirst().assignedUserIds().isEmpty());
        assertEquals(2, stale.planResetImpact().assignmentsToClear());
        var returned = staffing.transition(pending.id(), "HR_RETURN_SHIP", new ReviewRequest(stale.revision(), "QA correction"), hr);
        assertEquals("PENDING_SHIP_REVIEW", returned.status());
        assertEquals(0, returned.planResetImpact().assignmentsToClear());
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM "AreaStaffAssignment" a JOIN "CruiseStaff" cs USING (cruise_staff_id)
                WHERE cs.cruise_id=? AND a.enabled
                """, Long.class, cruiseId));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM "StaffServiceAssignment" ssa
                JOIN "AreaStaffAssignment" a USING (assignment_id)
                JOIN "CruiseStaff" cs USING (cruise_staff_id)
                WHERE cs.cruise_id=? AND ssa.enabled
                """, Long.class, cruiseId));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM "CruiseStaffDuty" d
                JOIN "CruiseStaff" cs USING (cruise_staff_id)
                WHERE cs.cruise_id=? AND d.service_id=? AND d.ended_at IS NULL
                """, Long.class, cruiseId, service));
        var saved = staffing.saveShipPlan(pending.id(), new ShipPlanInput(returned.revision(), List.of(serviceAreaId), needs), ship);
        assertNotEquals(need, saved.request().needs().getFirst().needId());
        assertTrue(saved.request().needs().getFirst().assignedUserIds().isEmpty());
        assertEquals(2L, jdbc.queryForObject("""
                SELECT count(*) FROM "CruiseStaff" WHERE cruise_id=? AND role_in_cruise='ONBOARD_STAFF' AND status='ACTIVE'
                """, Long.class, cruiseId));
        var toAsm = staffing.transition(pending.id(), "SHIP_RETURN_ASM",
                new ReviewRequest(saved.request().revision(), "QA program correction"), ship);
        assertEquals("PENDING_ASM", toAsm.status());
    }

    @Test void stagedAssignmentCompletesOnlyAfterBothSchedulesAndAllowsConcurrentAreaActivities() {
        assertDoesNotThrow(()->policy.requireComplete(cruiseId)); // no catalog-wide requirement
        var assigned=assignBoth();
        assertNull(jdbc.queryForObject("SELECT start_time FROM \"OnboardActivity\" WHERE onboard_activity_id=?",Timestamp.class,assigned.activityIds().getFirst()));
        assertEquals(2,policy.issues(cruiseId).size());
        assertCode("ITINERARY_PROGRAMS_INCOMPLETE",()->policy.requireComplete(cruiseId));
        programs.scheduleActivity(itineraryId,assigned.activityIds().getFirst(),revision(),activitySchedule(areaId));
        programs.scheduleExcursion(itineraryId,assigned.excursionIds().getFirst(),revision(),
                new ItineraryProgramService.ExcursionSchedule(arrival.plusSeconds(3600),portDeparture.minusSeconds(7200),"Pier","Planned visit",20,BigDecimal.ZERO));
        var another=programs.assign(itineraryId,revision(),List.of(sourceActivity),List.of());
        programs.scheduleActivity(itineraryId,another.activityIds().getFirst(),revision(),activitySchedule(areaId));
        assertTrue(policy.issues(cruiseId).isEmpty());
        assertDoesNotThrow(()->policy.requireComplete(cruiseId));
        assertEquals(areaId,jdbc.queryForObject("SELECT area_object_id FROM \"OnboardActivity\" WHERE onboard_activity_id=?",UUID.class,another.activityIds().getFirst()));
        assertNotNull(programs.read(itineraryId).get("excursions"));
    }

    @Test void staleRevisionAndForeignPortCallDoNotCreatePrograms() {
        var assigned=assignBoth();
        assertCode("STALE_REVISION",()->programs.assign(itineraryId,assigned.revision()-1,List.of(sourceActivity),List.of()));
        assertCode("PORT_CALL_SCOPE_MISMATCH",()->programs.assign(itineraryId,revision(),List.of(),
                List.of(new ItineraryProgramService.ShoreSelection(sourceExcursion,UUID.randomUUID(),destinationId))));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM \"OnboardActivity\" WHERE cruise_id=?",Integer.class,cruiseId));
    }

    @Test void destinationMustBePlannedAtTheExactCall() {
        assertCode("EXCURSION_DESTINATION_NOT_PLANNED",()->programs.assign(itineraryId,revision(),List.of(),
                List.of(new ItineraryProgramService.ShoreSelection(sourceExcursion,stopId,UUID.randomUUID()))));
    }

    @Test void plannedDestinationMustAlsoBeServedByThePort() {
        jdbc.update("""
            DELETE FROM "PortDestinationAccess" WHERE destination_id=? AND port_id=
                (SELECT port_id FROM "CruiseStop" WHERE cruise_stop_id=?)
            """,destinationId,stopId);
        long before=revision();
        assertCode("EXCURSION_DESTINATION_NOT_SERVED",()->programs.assign(itineraryId,before,List.of(),
                List.of(new ItineraryProgramService.ShoreSelection(sourceExcursion,stopId,destinationId))));
        assertEquals(before,revision());
        assertEquals(0,jdbc.queryForObject(
                "SELECT count(*) FROM \"ShoreExcursion\" WHERE cruise_stop_id=?",Integer.class,stopId));
    }

    @Test void excursionPickerReturnsOnlyTemplatesForTheSelectedDestination() throws Exception {
        var other = new Destination();
        other.setDestinationName("Another planned visit");
        other.setCountryCode("VN");
        other.setStatus(DestinationStatus.ACTIVE);
        em.persist(other);
        em.flush();
        jdbc.update("""
                INSERT INTO "ShoreExcursion" (shore_excursion_id, excursion_code, excursion_name,
                    supplier_id, destination_id, meeting_point, status, created_at, updated_at)
                SELECT ?, ?, excursion_name, supplier_id, ?, meeting_point, status, now(), now()
                FROM "ShoreExcursion" WHERE shore_excursion_id=?
                """, UUID.randomUUID(), "EX-" + UUID.randomUUID().toString().substring(0, 8),
                other.getDestinationId(), sourceExcursion);
        jdbc.update("""
                INSERT INTO "ShoreExcursion" (shore_excursion_id, excursion_code, excursion_name,
                    supplier_id, meeting_point, status, created_at, updated_at)
                SELECT ?, ?, excursion_name, supplier_id, meeting_point, status, now(), now()
                FROM "ShoreExcursion" WHERE shore_excursion_id=?
                """, UUID.randomUUID(), "EX-" + UUID.randomUUID().toString().substring(0, 8),
                sourceExcursion);
        var assigned = assignBoth();
        assertFalse(assigned.excursionIds().isEmpty());
        UUID portId = jdbc.queryForObject(
                "SELECT port_id FROM \"CruiseStop\" WHERE cruise_stop_id=?", UUID.class, stopId);

        mvc.perform(get("/trip-planning/catalogs/sources/excursions")
                        .header("Authorization", token(Role.CRUISE_MANAGER))
                        .param("destinationId", destinationId.toString())
                        .param("portId", portId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sourceExcursionId").value(sourceExcursion.toString()))
                .andExpect(jsonPath("$.data[0].destinationId").value(destinationId.toString()));
    }

    @Test void legacyTemplateWithoutDestinationCannotBeAssigned() {
        jdbc.update("UPDATE \"ShoreExcursion\" SET destination_id=NULL WHERE shore_excursion_id=?", sourceExcursion);
        long before=revision();
        assertCode("EXCURSION_SOURCE_DESTINATION_MISMATCH",()->programs.assign(itineraryId,before,List.of(),
                List.of(new ItineraryProgramService.ShoreSelection(sourceExcursion,stopId,destinationId))));
        assertEquals(before,revision());
        assertEquals(0,jdbc.queryForObject(
                "SELECT count(*) FROM \"ShoreExcursion\" WHERE cruise_stop_id=?",Integer.class,stopId));
    }

    @Test void normalPortCallMustFitCruiseWindowEvenWithoutExcursions() {
        jdbc.update("UPDATE \"CruiseStop\" SET planned_arrival=? WHERE cruise_stop_id=?",Timestamp.from(departure.minusSeconds(1)),stopId);
        assertTrue(policy.issues(cruiseId).stream().anyMatch(i -> i.code().equals("PORT_CALL_OUTSIDE_CRUISE")));
    }

    @Test void failedMixedBatchRollsBackEarlierOnboardCopiesAndRevision() {
        TestTransaction.flagForCommit(); TestTransaction.end(); // commit only synthetic fixtures in disposable DB
        long before=revision();
        assertCode("EXCURSION_DESTINATION_NOT_PLANNED",()->programs.assign(itineraryId,before,List.of(sourceActivity),
                List.of(new ItineraryProgramService.ShoreSelection(sourceExcursion,stopId,UUID.randomUUID()))));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM \"OnboardActivity\" WHERE cruise_id=?",Integer.class,cruiseId));
        assertEquals(before,revision());
    }

    @Test void rejectsServiceAndUnclassifiedAreasAndOutOfCruiseTimes() {
        var assigned=assignBoth(); UUID id=assigned.activityIds().getFirst();
        assertCode("ACTIVITY_AREA_REQUIRED",()->programs.scheduleActivity(itineraryId,id,revision(),activitySchedule(serviceAreaId)));
        assertCode("ACTIVITY_AREA_REQUIRED",()->policy.requireActivityArea(areaId,UUID.randomUUID(),false));
        jdbc.update("UPDATE \"ShipModelObject\" SET area_usage='OTHER' WHERE ship_model_object_id=?",areaId);
        assertCode("ACTIVITY_AREA_REQUIRED",()->programs.scheduleActivity(itineraryId,id,revision(),activitySchedule(areaId)));
        assertCode("ACTIVITY_OUTSIDE_CRUISE",()->programs.scheduleActivity(itineraryId,id,revision(),
                new ItineraryProgramService.ActivitySchedule(departure.minusSeconds(1),arrival,areaId,"Deck",20,BigDecimal.ZERO)));
    }

    @Test void rejectsShoreReturnAtAllAboardAndMissingLocation() {
        var assigned=assignBoth(); UUID id=assigned.excursionIds().getFirst();
        assertCode("EXCURSION_OUTSIDE_PORT_CALL",()->programs.scheduleExcursion(itineraryId,id,revision(),
                new ItineraryProgramService.ExcursionSchedule(arrival,portDeparture.minusSeconds(3600),"Pier","Visit",20,BigDecimal.ZERO)));
        assertCode("PROGRAM_DETAILS_REQUIRED",()->programs.scheduleExcursion(itineraryId,id,revision(),
                new ItineraryProgramService.ExcursionSchedule(arrival,portDeparture.minusSeconds(7200),"Pier"," ",20,BigDecimal.ZERO)));
    }

    @Test void incompleteDraftProgramsCanBeRemoved() {
        var assigned=assignBoth();
        programs.remove(itineraryId,"activities",assigned.activityIds().getFirst(),revision());
        programs.remove(itineraryId,"excursions",assigned.excursionIds().getFirst(),revision());
        assertTrue(policy.issues(cruiseId).isEmpty());
    }

    @Test void databasePreventsReclassifyingAnAssignedActivitySpace() {
        var assigned=assignBoth();
        programs.scheduleActivity(itineraryId,assigned.activityIds().getFirst(),revision(),activitySchedule(areaId));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE \"ShipModelObject\" SET area_usage='SERVICE' WHERE ship_model_object_id=?",areaId));
    }

    @Test void databaseRejectsLegacyActivityAssignmentToServiceArea() {
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO \"AreaActivityAssignment\"(area_object_id,activity_id) VALUES (?,?)",serviceAreaId,sourceActivity));
    }

    @Test void databaseRejectsLegacyServiceAssignmentToActivitySpace() {
        var ex=assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "INSERT INTO \"AreaServiceAssignment\"(area_object_id,service_id) VALUES (?,?)",areaId,UUID.randomUUID()));
        assertTrue(ex.getMostSpecificCause().getMessage().contains("Activity spaces cannot host service assignments"));
    }

    @Test
    void httpAssignmentReturnsOccurrenceAndReadUsesTextStatus() throws Exception {
        String authorization=coordinatorToken();
        mvc.perform(post("/itineraries/{id}/programs/assignments",itineraryId).header("Authorization",authorization).header("If-Match",revision())
                .contentType("application/json").content("{\"activityIds\":[\""+sourceActivity+"\"],\"excursions\":[]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.activityIds.length()").value(1));
        mvc.perform(get("/itineraries/{id}/programs",itineraryId).header("Authorization",authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities[0].status").value("PLANNED"));
        mvc.perform(post("/itineraries/{id}/programs/assignments",itineraryId).header("Authorization",authorization).header("If-Match",revision())
                .contentType("application/json").content("{\"activityIds\":[],\"excursions\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test void httpOperationsSubmissionAndDirectItinerarySubmissionRejectIncompletePrograms() throws Exception {
        assignBoth(); String authorization=coordinatorToken();
        mvc.perform(post("/cruise-plans/{id}/submit",cruiseId).header("Authorization",authorization)
                .contentType("application/json").content("{\"expectedRevision\":0,\"fingerprint\":\""
                        + plans.get(cruiseId).fingerprint() + "\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PLAN_INCOMPLETE"))
                .andExpect(jsonPath("$.details.blockers",org.hamcrest.Matchers.hasItem("ACTIVITY_OUTSIDE_CRUISE")));
        mvc.perform(post("/itineraries/{id}/submit",itineraryId).header("Authorization",authorization))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ITINERARY_PROGRAMS_INCOMPLETE"));
        assertEquals("DRAFT",jdbc.queryForObject("SELECT status::text FROM \"Itinerary\" WHERE itinerary_id=?",String.class,itineraryId));
    }

    @Test void httpAreaUsageRequiresModelUpdatePermissionWhileCrmCanRead() throws Exception {
        String crm=token(com.casms.identity.enums.Role.CRUISE_MANAGER);
        mvc.perform(get("/ships/{ship}/areas/usages",shipId).header("Authorization",crm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(put("/ships/{ship}/areas/{area}/usage",shipId,areaId).header("Authorization",crm)
                .contentType("application/json").content("{\"usage\":\"OTHER\"}"))
                .andExpect(status().isForbidden());
        String manager=token(com.casms.identity.enums.Role.SHIP_MANAGER);
        mvc.perform(put("/ships/{ship}/areas/{area}/usage",shipId,areaId).header("Authorization",manager)
                .contentType("application/json").content("{\"usage\":\"OTHER\"}"))
                .andExpect(status().isForbidden());
        String admin=token(com.casms.identity.enums.Role.ADMIN);
        mvc.perform(put("/ships/{ship}/areas/{area}/usage",shipId,areaId).header("Authorization",admin)
                .contentType("application/json").content("{\"usage\":\"OTHER\"}"))
                .andExpect(status().isOk());
        assertEquals("OTHER",jdbc.queryForObject("SELECT area_usage FROM \"ShipModelObject\" WHERE ship_model_object_id=?",String.class,areaId));
    }

    @Test void readPermissionCannotMutatePrograms() throws Exception {
        jdbc.update("""
                INSERT INTO "CruisePlanReview" (cruise_id,status,submitted_at) VALUES (?,'SUBMITTED',now())
                """, cruiseId);
        String authorization=token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        mvc.perform(post("/itineraries/{id}/programs/assignments",itineraryId).header("Authorization",authorization).header("If-Match",revision())
                .contentType("application/json").content("{\"activityIds\":[\""+sourceActivity+"\"],\"excursions\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/itineraries/{id}/programs",itineraryId).header("Authorization",authorization)).andExpect(status().isOk());
    }
}
