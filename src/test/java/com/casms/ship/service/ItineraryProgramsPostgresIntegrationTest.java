package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
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
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:56559/casms75_test(?:\\?.*)?"))
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
        model.setFileUri("/fixture.glb"); model.setFileFormat("glb"); em.persist(model);
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
            INSERT INTO "ShoreExcursion"(shore_excursion_id,excursion_code,excursion_name,supplier_id,meeting_point,status,created_at,updated_at)
            VALUES (?,?,'Catalog excursion',?,'Pier','PLANNED',now(),now())
            """,sourceExcursion,"EX-T"+code,supplier);
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
        return new ItineraryProgramService.ActivitySchedule(arrival,arrival.plusSeconds(3600),area,"Event deck",20,BigDecimal.ZERO);
    }
    private void assertCode(String expected, Executable action) {
        assertEquals(expected,assertThrows(BusinessException.class,action).getCode());
    }
    private String token(Role role) {
        var user=new User();
        user.setUsername("casms75-"+UUID.randomUUID()); user.setPasswordHash("unused-synthetic-password-hash");
        user.setStatus(UserStatus.ACTIVE); user.setRoles(Set.of(role));
        em.persist(user); em.flush();
        return "Bearer "+jwt.generateToken(user.getUsername(),user.getUserId());
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
        String authorization=token(com.casms.identity.enums.Role.CRUISE_MANAGER);
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
        assignBoth(); String authorization=token(com.casms.identity.enums.Role.CRUISE_MANAGER);
        mvc.perform(post("/cruise-plans/{id}/submit",cruiseId).header("Authorization",authorization)
                .contentType("application/json").content("{\"expectedRevision\":0}"))
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
        String authorization=token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        mvc.perform(post("/itineraries/{id}/programs/assignments",itineraryId).header("Authorization",authorization).header("If-Match",revision())
                .contentType("application/json").content("{\"activityIds\":[\""+sourceActivity+"\"],\"excursions\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/itineraries/{id}/programs",itineraryId).header("Authorization",authorization)).andExpect(status().isOk());
    }
}
