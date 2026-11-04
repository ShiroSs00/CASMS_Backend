package com.casms.ship;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dedicated disposable-test container only. Never falls back to the developer's datasource. */
@EnabledIfEnvironmentVariable(named = "OCVVIET_V4_TEST", matches = "true")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:15436/ocvviet_v4_test?stringtype=unspecified",
            "spring.datasource.username=ocvviet_test",
            "spring.datasource.password=ocvviet_test_only_20261028",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "spring.flyway.enabled=true",
            "spring.flyway.clean-disabled=true",
            "spring.flyway.locations=filesystem:target/v4-migrations-2",
            "spring.profiles.active=v4-isolated",
            "jwt.secret=isolated-mainflow-test-secret-only-at-least-64-characters-long-20261028"
        })
@AutoConfigureMockMvc
class MainflowRuntimePostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired com.casms.identity.repository.UserRepository users;
    @Autowired com.casms.security.jwt.JwtService jwt;
    @Autowired com.casms.ship.repository.PortRepository ports;
    @Autowired com.casms.trip.repository.TripScheduleVersionRepository versions;
    @Autowired com.casms.ship.repository.ShipRepository ships;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired com.casms.ship.repository.DeckRepository decks;
    @Autowired com.casms.ship.repository.CabinRepository cabins;
    @Autowired com.casms.servicepackage.repository.ServiceRepository services;
    @Autowired com.casms.activity.repository.SupplierRepository suppliers;
    @Autowired com.casms.ship.repository.CruiseRepository cruises;
    @Autowired com.casms.ship.service.ScheduleMaterializationService materialization;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    private String token(com.casms.identity.enums.Role... roles) {
        var u = new com.casms.identity.entity.User();
        u.setUsername("v4-" + roles[0] + "-" + java.util.UUID.randomUUID());
        u.setPasswordHash(passwords.encode("OcvViet-v4-test-only!"));
        u.setRoles(java.util.Set.of(roles));
        u.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
        u = users.save(u);
        return jwt.generateToken(u.getUsername(), u.getUserId());
    }

    private com.fasterxml.jackson.databind.JsonNode response(
            org.springframework.test.web.servlet.ResultActions request, int expected)
            throws Exception {
        var result = request.andExpect(status().is(expected)).andReturn();
        return recordHttpResult(result);
    }

    private com.fasterxml.jackson.databind.JsonNode recordHttpResult(
            org.springframework.test.web.servlet.MvcResult result) throws Exception {
        String text = result.getResponse().getContentAsString();
        var evidence = mapper.createObjectNode();
        evidence.put("method", result.getRequest().getMethod());
        evidence.put("path", result.getRequest().getRequestURI());
        evidence.put("query", result.getRequest().getQueryString());
        evidence.put("request", result.getRequest().getContentAsString());
        evidence.put("status", result.getResponse().getStatus());
        evidence.set("response", mapper.readTree(text));
        Files.createDirectories(Path.of("docs/mainflow-alignment"));
        Files.writeString(
                Path.of("docs/mainflow-alignment/runtime-http-proof.jsonl"),
                mapper.writeValueAsString(evidence) + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        return mapper.readTree(text).path("data");
    }

    @Test
    void crmFinalizesOwnTemplateAndCruisesDoNotInheritPlanApproval() throws Exception {
        mainflow(false);
    }

    @Test
    void independentPricingPlanAndReadyAllowRealUsdSaleQuoteAndBooking() throws Exception {
        mainflow(true);
    }

    private void mainflow(boolean golden) throws Exception {
        String crm = token(com.casms.identity.enums.Role.CRUISE_MANAGER),
                ops = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        var p = new com.casms.ship.entity.Port();
        p.setPortCode("V4-" + java.util.UUID.randomUUID());
        p.setPortName("Isolated fixture port");
        p.setCountryCode("VN");
        p.setDomesticRegion("BAC_BO");
        p.setTimeZoneId("Asia/Ho_Chi_Minh");
        p.setStatus(com.casms.ship.enums.PortStatus.ACTIVE);
        p = ports.save(p);
        String create =
                "{\"name\":\"V4 isolated"
                    + " template\",\"durationGroup\":\"SHORT\",\"plannedScope\":\"DOMESTIC\",\"domesticRegions\":[\"BAC_BO\"]}";
        var trip =
                response(
                        mvc.perform(
                                post("/trips")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(create)),
                        201);
        java.util.UUID tripId = java.util.UUID.fromString(trip.path("tripId").asText());
        var all = versions.findByTrip_TripIdOrderByVersionNumberAsc(tripId);
        assertEquals(1, all.size());
        var v = all.getFirst();
        assertEquals(com.casms.trip.enums.TripScheduleVersionStatus.DRAFT, v.getStatus());
        String content =
                """
{"expectedRevision":%s,"days":[{"dayNumber":1,"dayType":"PORT_DAY","shipTimeZoneId":"Asia/Ho_Chi_Minh","departurePortId":"%s","plannedDepartureTime":"08:00:00"}],
 "stops":[{"clientKey":"visit-1","portId":"%s","stopOrder":1,"arrivalDayNumber":1,"departureDayNumber":1,
           "plannedArrivalTime":"08:00:00","plannedDepartureTime":"18:00:00","allAboardDayNumber":1,"allAboardTime":"17:00:00","isEmbarkation":true,"isDisembarkation":true}],"entries":[]}
"""
                        .formatted(v.getRevision(), p.getPortId(), p.getPortId());
        var saved =
                response(
                        mvc.perform(
                                put("/trip-schedule-versions/{id}/content", v.getVersionId())
                                        .header("Authorization", "Bearer " + crm)
                                        .contentType("application/json")
                                        .content(content)),
                        200);
        var validation =
                response(
                        mvc.perform(
                                post("/trip-schedule-versions/{id}/validate", v.getVersionId())
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertTrue(validation.path("validForSubmit").asBoolean(), validation.toString());
        assertEquals("DRAFT", saved.path("status").asText());
        String revision = "{\"expectedRevision\":" + saved.path("revision").asLong() + "}";
        mvc.perform(
                        post("/trip-schedule-versions/{id}/finalize", v.getVersionId())
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content(revision))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/trip-schedule-versions/{id}/finalize", v.getVersionId())
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":999}"))
                .andExpect(status().isConflict());
        var finalized =
                response(
                        mvc.perform(
                                post("/trip-schedule-versions/{id}/finalize", v.getVersionId())
                                        .header("Authorization", "Bearer " + crm)
                                        .contentType("application/json")
                                        .content(revision)),
                        200);
        assertEquals("PUBLISHED", finalized.path("status").asText());
        assertEquals(64, finalized.path("contentHash").asText().length());
        var nextDraft =
                response(
                        mvc.perform(
                                post("/trips/{id}/schedule-versions", tripId)
                                        .header("Authorization", "Bearer " + crm)
                                        .contentType("application/json")
                                        .content(
                                                "{\"sourceVersionId\":\""
                                                        + v.getVersionId()
                                                        + "\"}")),
                        201);
        assertEquals("DRAFT", nextDraft.path("status").asText());
        assertEquals(2, nextDraft.path("versionNumber").asInt());
        var eligible =
                response(
                        mvc.perform(
                                get("/trip-schedule-versions/eligible-sources")
                                        .param("tripId", tripId.toString())
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals(1, eligible.path("content").size());
        assertEquals(
                v.getVersionId().toString(),
                eligible.path("content").get(0).path("versionId").asText());
        mvc.perform(
                        put("/trip-schedule-versions/{id}/content", v.getVersionId())
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content(content))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/trip-schedule-versions/{id}/publish", v.getVersionId())
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content(revision))
                .andExpect(status().isGone());
        String cruiseBody =
                "{\"cruiseName\":\"V4 departure\",\"sourceVersionId\":\""
                        + v.getVersionId()
                        + "\",\"expectedSourceVersionRevision\":"
                        + finalized.path("revision").asLong()
                        + ",\"itineraryMode\":\"CUSTOM\",\"departureDatetime\":\"2027-03-01T01:00:00Z\"}";
        var cruise =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(cruiseBody)),
                        201);
        mvc.perform(
                        post("/cruise")
                                .header("Authorization", "Bearer " + crm)
                                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                                .contentType("application/json")
                                .content(
                                        cruiseBody.replace(
                                                v.getVersionId().toString(),
                                                nextDraft.path("versionId").asText())))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(
                        post("/cruise")
                                .header("Authorization", "Bearer " + crm)
                                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                                .contentType("application/json")
                                .content(
                                        cruiseBody.replace(
                                                "\"expectedSourceVersionRevision\":"
                                                        + finalized.path("revision").asLong(),
                                                "\"expectedSourceVersionRevision\":999")))
                .andExpect(status().isConflict());
        assertEquals("DRAFT", cruise.path("status").asText());
        assertEquals("CUSTOM", cruise.path("itineraryMode").asText());
        java.util.UUID cruiseId = java.util.UUID.fromString(cruise.path("cruiseId").asText());
        assertEquals(
                Boolean.FALSE,
                jdbc.queryForObject(
                        "SELECT approval_inherited FROM \"Itinerary\" WHERE cruise_id=?",
                        Boolean.class,
                        cruiseId));
        assertEquals(
                "DRAFT",
                jdbc.queryForObject(
                        "SELECT status::text FROM \"Itinerary\" WHERE cruise_id=?",
                        String.class,
                        cruiseId));
        assertEquals(
                5,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePreparationSection\" WHERE cruise_id=?",
                        Integer.class,
                        cruiseId));
        var view =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", cruiseId)
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        assertEquals("DRAFT", view.path("planStatus").asText());
        assertEquals("NOT_READY", view.path("readyStatus").asText());
        for (String endpoint : java.util.List.of("readiness", "sale-readiness")) {
            var readiness =
                    response(
                            mvc.perform(
                                    get("/cruise/{id}/" + endpoint, cruiseId)
                                            .header("Authorization", "Bearer " + ops)),
                            200);
            if (endpoint.equals("readiness"))
                assertTrue(
                        readiness.path("itineraryConfigured").asBoolean(),
                        "Linked DRAFT itinerary exists before Plan approval");
            var approvalDetail =
                    java.util.stream.StreamSupport.stream(
                                    readiness.path("details").spliterator(), false)
                            .filter(d -> "PLAN_APPROVAL_REQUIRED".equals(d.path("code").asText()))
                            .findFirst()
                            .orElseThrow();
            assertEquals("CRUISE", approvalDetail.path("targetType").asText());
            assertEquals(cruiseId.toString(), approvalDetail.path("targetId").asText());
            assertEquals(
                    "/api/cruise-plans/" + cruiseId + "/approve",
                    approvalDetail.path("remediationUrl").asText());
            assertFalse(readiness.toString().contains("ITINERARY_NOT_APPROVED"));
        }
        // The negative open-sale command cannot change the lifecycle even with legacy
        // inherited=true data.
        jdbc.update("UPDATE \"Itinerary\" SET approval_inherited=true WHERE cruise_id=?", cruiseId);
        mvc.perform(
                        post("/cruise/{id}/open-sale", cruiseId)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content(
                                        "{\"expectedRevision\":1,\"saleScope\":\"CABIN_ONLY\",\"fingerprint\":\"stale\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(
                "DRAFT",
                jdbc.queryForObject(
                        "SELECT status::text FROM \"Cruise\" WHERE cruise_id=?",
                        String.class,
                        cruiseId));
        mvc.perform(
                        post("/cruise-plans/{id}/submit", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("PLAN_INCOMPLETE"));
        // Configure only CRM inputs. No OPS staffing, preparation, Ready or bookings exist.
        var ship = new com.casms.ship.entity.Ship();
        ship.setShipCode("V4-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        ship.setShipName("Isolated mainflow ship");
        ship.setStatus(com.casms.ship.enums.ShipStatus.READY);
        ship.setTotalCapacity(100);
        ship.setTotalDecks(1);
        ship = ships.save(ship);
        jdbc.update(
                "UPDATE \"Cruise\" SET ship_id=? WHERE cruise_id=?", ship.getShipId(), cruiseId);
        // Existing ship configuration is reused by Plan pricing checks and later preparation.
        var deck = new com.casms.ship.entity.Deck();
        deck.setShip(ship);
        deck.setDeckNumber(1);
        deck.setDeckName("Fixture deck");
        deck = decks.save(deck);
        var cabin = new com.casms.ship.entity.Cabin();
        cabin.setShip(ship);
        cabin.setDeck(deck);
        cabin.setCabinNumber("101");
        cabin.setCabinType(com.casms.ship.enums.CabinType.INTERIOR);
        cabin.setCapacity(2);
        cabin.setStatus(com.casms.ship.enums.CabinStatus.AVAILABLE);
        cabins.save(cabin);
        String windows =
                """
{"expectedRevision":%s,"bookingOpensAt":"2026-10-01T00:00:00Z","bookingClosesAt":"2027-02-20T00:00:00Z","guidanceOpensAt":"2027-02-21T00:00:00Z","guidanceClosesAt":"2027-02-28T00:00:00Z"}
"""
                        .formatted(cruise.path("revision").asLong());
        var configured =
                response(
                        mvc.perform(
                                put("/cruise-plans/{id}/windows", cruiseId)
                                        .header("Authorization", "Bearer " + crm)
                                        .contentType("application/json")
                                        .content(windows)),
                        200);
        long cruiseRevision = configured.path("windows").path("expectedRevision").asLong();
        String activity =
                """
{"cruiseId":"%s","activityName":"Isolated onboard program","description":"Fixture activity","location":"Main deck","startTime":"2027-03-01T02:00:00Z","endTime":"2027-03-01T03:00:00Z","capacity":20,"price":0,"currencyCode":"USD"}
"""
                        .formatted(cruiseId);
        response(
                mvc.perform(
                        post("/cruise-plans/{id}/activities", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", cruiseRevision)
                                .contentType("application/json")
                                .content(activity)),
                200);
        cruiseRevision++;
        var service = new com.casms.servicepackage.entity.Service();
        service.setServiceCode("V4-" + java.util.UUID.randomUUID());
        service.setServiceName("Isolated meal service");
        service.setServiceType("FOOD_BEVERAGE");
        service.setRequiresSchedule(true);
        service.setStatus(com.casms.servicepackage.enums.ServiceStatus.ACTIVE);
        service.setPrice(java.math.BigDecimal.ZERO);
        service.setCurrencyCode("USD");
        service = services.save(service);
        jdbc.update(
                "INSERT INTO \"DeckServiceAssignment\"(assignment_id,deck_id,service_id,created_at)"
                    + " VALUES(?,?,?,now())",
                java.util.UUID.randomUUID(),
                deck.getDeckId(),
                service.getServiceId());
        String serving =
                """
{"serviceId":"%s","startsAt":"2027-03-01T04:00:00Z","endsAt":"2027-03-01T05:00:00Z","location":"Dining room","capacity":20}
"""
                        .formatted(service.getServiceId());
        response(
                mvc.perform(
                        post("/cruise-plans/{id}/serving-windows", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", cruiseRevision)
                                .contentType("application/json")
                                .content(serving)),
                200);
        cruiseRevision++;
        String policy =
                """
{"expectedRevision":0,"policyVersion":"V4-EXPLICIT","currency":"USD","interiorBaseFare":1000000,"oceanViewBaseFare":1500000,"balconyBaseFare":2000000,"suiteBaseFare":3000000,"portFeePerPassenger":0,"taxRate":0,"depositPercentage":0.3,"finalPaymentDueDays":14,"fullPaymentCutoffDays":7,"cancellationPolicy":"Isolated fixture terms","autoApprove":false}
""";
        mvc.perform(
                        put("/cruise-plans/{id}/pricing-policy", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", cruiseRevision - 1)
                                .contentType("application/json")
                                .content(policy))
                .andExpect(status().isConflict());
        response(
                mvc.perform(
                        put("/cruise-plans/{id}/pricing-policy", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", cruiseRevision)
                                .contentType("application/json")
                                .content(policy)),
                200);
        // A saved row is not proof that CRM configured the applicable fares before Plan submission.
        var configuredDraft =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", cruiseId)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        String incompletePolicy =
                policy.replace("\"expectedRevision\":0", "\"expectedRevision\":1")
                        .replace("\"interiorBaseFare\":1000000", "\"interiorBaseFare\":null");
        response(
                mvc.perform(
                        put("/cruise-plans/{id}/pricing-policy", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .header(
                                        "If-Match",
                                        configuredDraft
                                                .path("windows")
                                                .path("expectedRevision")
                                                .asLong())
                                .contentType("application/json")
                                .content(incompletePolicy)),
                200);
        var incompletePlan =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", cruiseId)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertTrue(
                incompletePlan
                        .path("planBlockers")
                        .toString()
                        .contains("PRICING_POLICY_CONFIGURATION_REQUIRED"));
        mvc.perform(
                        post("/cruise-plans/{id}/submit", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":0}"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePlanEvent\" WHERE cruise_id=? AND"
                            + " action='SUBMIT'",
                        Integer.class,
                        cruiseId));
        policy = policy.replace("\"expectedRevision\":0", "\"expectedRevision\":2");
        response(
                mvc.perform(
                        put("/cruise-plans/{id}/pricing-policy", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .header(
                                        "If-Match",
                                        incompletePlan
                                                .path("windows")
                                                .path("expectedRevision")
                                                .asLong())
                                .contentType("application/json")
                                .content(policy)),
                200);
        var submitted =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/submit", cruiseId)
                                        .header("Authorization", "Bearer " + crm)
                                        .contentType("application/json")
                                        .content("{\"expectedRevision\":0}")),
                        200);
        assertEquals("SUBMITTED", submitted.path("planStatus").asText());
        assertTrue(submitted.path("submittedSnapshot").has("policy"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruiseStaff\" WHERE cruise_id=?",
                        Integer.class,
                        cruiseId));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"Booking\" WHERE cruise_id=?",
                        Integer.class,
                        cruiseId));
        long planRevision = submitted.path("revision").asLong();
        mvc.perform(
                        put("/cruise-plans/{id}/windows", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content(windows))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/cruise-plans/{id}/approve", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":" + planRevision + "}"))
                .andExpect(status().isForbidden());
        var queue =
                response(
                        mvc.perform(get("/cruise-plans").header("Authorization", "Bearer " + ops)),
                        200);
        assertTrue(queue.toString().contains(cruiseId.toString()));
        mvc.perform(
                        post("/cruise-plans/{id}/request-revision", cruiseId)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":" + planRevision + "}"))
                .andExpect(status().isUnprocessableEntity());
        var returned =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/request-revision", cruiseId)
                                        .header("Authorization", "Bearer " + ops)
                                        .contentType("application/json")
                                        .content(
                                                "{\"expectedRevision\":"
                                                        + planRevision
                                                        + ",\"reason\":\"Add plan detail\"}")),
                        200);
        assertEquals("REVISION_REQUESTED", returned.path("planStatus").asText());
        var returnedPrice =
                response(
                        mvc.perform(
                                get("/cruise/{id}/pricing-policy", cruiseId)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals("DRAFT", returnedPrice.path("status").asText());
        var restoredPolicy = mapper.readTree(policy);
        ((com.fasterxml.jackson.databind.node.ObjectNode) restoredPolicy)
                .put("expectedRevision", returnedPrice.path("revision").asLong());
        response(
                mvc.perform(
                        put("/cruise-plans/{id}/pricing-policy", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .header(
                                        "If-Match",
                                        returned.path("windows").path("expectedRevision").asLong())
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(restoredPolicy))),
                200);
        java.util.UUID opsId = java.util.UUID.fromString(returned.path("reviewedBy").asText());
        submitted =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/submit", cruiseId)
                                        .header("Authorization", "Bearer " + crm)
                                        .contentType("application/json")
                                        .content(
                                                "{\"expectedRevision\":"
                                                        + returned.path("revision").asLong()
                                                        + "}")),
                        200);
        planRevision = submitted.path("revision").asLong();
        mvc.perform(
                        post("/cruise-plans/{id}/approve", cruiseId)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/cruise-plans/{id}/approve", cruiseId)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":" + planRevision + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("PLAN_APPROVAL_CHECK_FAILED"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePlanEvent\" WHERE cruise_id=? AND"
                            + " action='APPROVE'",
                        Integer.class,
                        cruiseId));
        var approved = submitted;
        String readyRequest =
                "{\"expectedRevision\":"
                        + submitted.path("revision").asLong()
                        + ",\"fingerprint\":\""
                        + submitted.path("fingerprint").asText()
                        + "\"}";
        String guidance =
                """
{"sessionName":"Isolated predeparture guidance","location":"Main deck","startAt":"2027-02-22T01:00:00Z","endAt":"2027-02-22T02:00:00Z","instructorUserId":"%s","content":"Reviewed safety instructions","capacity":100}
"""
                        .formatted(opsId);
        mvc.perform(
                        post("/cruise-plans/{id}/guidance", cruiseId)
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content(guidance))
                .andExpect(status().isForbidden());
        var session =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/guidance", cruiseId)
                                        .header("Authorization", "Bearer " + ops)
                                        .contentType("application/json")
                                        .content(guidance)),
                        200);
        assertEquals("Scheduled", session.path("status").asText());
        mvc.perform(
                        post(
                                        "/cruise-plans/{id}/guidance/{session}/publish",
                                        cruiseId,
                                        session.path("sessionId").asText())
                                .header("Authorization", "Bearer " + ops)
                                .header("If-Match", 999))
                .andExpect(status().isConflict());
        var published =
                response(
                        mvc.perform(
                                post(
                                                "/cruise-plans/{id}/guidance/{session}/publish",
                                                cruiseId,
                                                session.path("sessionId").asText())
                                        .header("Authorization", "Bearer " + ops)
                                        .header("If-Match", session.path("revision").asLong())),
                        200);
        assertEquals("Published", published.path("status").asText());
        var current =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", cruiseId)
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        assertFalse(
                current.path("readyBlockers").toString().contains("GUIDANCE_PUBLICATION_REQUIRED"));
        assertNotEquals(
                approved.path("fingerprint").asText(), current.path("fingerprint").asText());
        mvc.perform(
                        post("/cruise/{id}/staff", cruiseId)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content(
                                        "{\"userId\":\""
                                                + opsId
                                                + "\",\"roleInCruise\":\"ONBOARD_STAFF\"}"))
                .andExpect(status().isUnprocessableEntity());
        String onboard = token(com.casms.identity.enums.Role.ONBOARD_STAFF);
        var onboardId = jwt.extractUserId(onboard);
        response(
                mvc.perform(
                        post("/cruise/{id}/staff", cruiseId)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content(
                                        "{\"userId\":\""
                                                + onboardId
                                                + "\",\"roleInCruise\":\"ONBOARD_STAFF\"}")),
                200);
        String shipToken = token(com.casms.identity.enums.Role.SHIP_MANAGER),
                opsB = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        // GET cannot create/reset sections or append an event.
        Integer eventsBefore =
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePlanEvent\" WHERE cruise_id=?",
                        Integer.class,
                        cruiseId);
        response(
                mvc.perform(
                        get("/cruise/{id}/preparation", cruiseId)
                                .header("Authorization", "Bearer " + ops)),
                200);
        assertEquals(
                eventsBefore,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePlanEvent\" WHERE cruise_id=?",
                        Integer.class,
                        cruiseId));
        for (String section :
                java.util.List.of(
                        "TECHNICAL_NAUTICAL",
                        "HOTEL_CABIN",
                        "FOOD_BEVERAGE",
                        "ENTERTAINMENT_SHORE",
                        "SAFETY_CREW")) {
            Long sectionRevision =
                    jdbc.queryForObject(
                            "SELECT revision FROM \"CruisePreparationSection\" WHERE cruise_id=?"
                                + " AND section_code=?",
                            Long.class,
                            cruiseId,
                            section);
            String owner = crm;
            var evidence =
                    response(
                            mvc.perform(
                                    post(
                                                    "/cruise/{id}/preparation/{section}/submit",
                                                    cruiseId,
                                                    section)
                                            .header("Authorization", "Bearer " + owner)
                                            .contentType("application/json")
                                            .content(
                                                    "{\"expectedRevision\":"
                                                            + sectionRevision
                                                            + ",\"evidence\":\"Verified isolated"
                                                            + " fixture evidence\"}")),
                            200);
            if (section.equals("SAFETY_CREW"))
                mvc.perform(
                                post(
                                                "/cruise/{id}/preparation/{section}/confirm",
                                                cruiseId,
                                                section)
                                        .header("Authorization", "Bearer " + crm)
                                        .contentType("application/json")
                                        .content(
                                                "{\"expectedRevision\":"
                                                        + evidence.path("revision").asLong()
                                                        + "}"))
                        .andExpect(status().isForbidden());
            var confirmed =
                    response(
                            mvc.perform(
                                    post(
                                                    "/cruise/{id}/preparation/{section}/confirm",
                                                    cruiseId,
                                                    section)
                                            .header("Authorization", "Bearer " + opsB)
                                            .contentType("application/json")
                                            .content(
                                                    "{\"expectedRevision\":"
                                                            + evidence.path("revision").asLong()
                                                            + "}")),
                            200);
            assertEquals("CONFIRMED", confirmed.path("status").asText());
        }
        approved =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/approve", cruiseId)
                                        .header("Authorization", "Bearer " + ops)
                                        .contentType("application/json")
                                        .content("{\"expectedRevision\":" + planRevision + "}")),
                        200);
        assertEquals("APPROVED", approved.path("planStatus").asText());
        assertEquals("NOT_READY", approved.path("readyStatus").asText());
        var priceAfterPlan =
                response(
                        mvc.perform(
                                get("/cruise/{id}/pricing-policy", cruiseId)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals("PENDING_APPROVAL", priceAfterPlan.path("status").asText());
        current =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", cruiseId)
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        assertTrue(current.path("readyBlockers").isEmpty(), current.toPrettyString());
        assertEquals("NOT_READY", current.path("readyStatus").asText());
        mvc.perform(
                        post("/cruise-plans/{id}/ready", cruiseId)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content(readyRequest))
                .andExpect(status().isConflict());
        String freshReady =
                "{\"expectedRevision\":"
                        + current.path("revision").asLong()
                        + ",\"fingerprint\":\""
                        + current.path("fingerprint").asText()
                        + "\"}";
        var ready =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/ready", cruiseId)
                                        .header("Authorization", "Bearer " + ops)
                                        .contentType("application/json")
                                        .content(freshReady)),
                        200);
        assertEquals("READY", ready.path("readyStatus").asText());
        assertFalse(ready.path("readyAt").asText().isBlank());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"Booking\" WHERE cruise_id=?",
                        Integer.class,
                        cruiseId));
        if (golden) {
            var price =
                    response(
                            mvc.perform(
                                    get("/cruise/{id}/pricing-policy", cruiseId)
                                            .header("Authorization", "Bearer " + crm)),
                            200);
            var pending = price;
            assertEquals("PENDING_APPROVAL", pending.path("status").asText());
            var pricingApproved =
                    response(
                            mvc.perform(
                                    post("/cruise/{id}/pricing-policy/approve", cruiseId)
                                            .header("Authorization", "Bearer " + ops)
                                            .contentType("application/json")
                                            .content(
                                                    "{\"expectedRevision\":"
                                                            + pending.path("revision").asLong()
                                                            + "}")),
                            200);
            assertEquals("APPROVED", pricingApproved.path("status").asText());
            var afterPricing =
                    response(
                            mvc.perform(
                                    get("/cruise-plans/{id}", cruiseId)
                                            .header("Authorization", "Bearer " + ops)),
                            200);
            assertEquals("APPROVED", afterPricing.path("planStatus").asText());
            assertEquals("READY", afterPricing.path("readyStatus").asText());
            var sale =
                    response(
                            mvc.perform(
                                    get("/cruise/{id}/sale-readiness", cruiseId)
                                            .param("saleScope", "CABIN_ONLY")
                                            .header("Authorization", "Bearer " + ops)),
                            200);
            assertTrue(sale.path("ready").asBoolean(), sale.toPrettyString());
            response(
                    mvc.perform(
                            post("/cruise/{id}/open-sale", cruiseId)
                                    .header("Authorization", "Bearer " + ops)
                                    .contentType("application/json")
                                    .content(
                                            "{\"expectedRevision\":"
                                                    + sale.path("cruiseRevision").asLong()
                                                    + ",\"saleScope\":\"CABIN_ONLY\",\"fingerprint\":\""
                                                    + sale.path("fingerprint").asText()
                                                    + "\",\"eventIdentity\":\""
                                                    + java.util.UUID.randomUUID()
                                                    + "\"}")),
                    200);
            String passenger = token(com.casms.identity.enums.Role.PASSENGER);
            var sellability =
                    response(
                            mvc.perform(
                                    get("/cruise/{id}/sellability", cruiseId)
                                            .header("Authorization", "Bearer " + passenger)),
                            200);
            assertTrue(sellability.path("sellable").asBoolean());
            response(
                    mvc.perform(
                            get("/booking/available-cabins")
                                    .param("cruiseId", cruiseId.toString())
                                    .header("Authorization", "Bearer " + passenger)),
                    200);
            String quoteBody =
                    "{\"cruiseId\":\""
                            + cruiseId
                            + "\",\"currency\":\"USD\",\"paymentPlan\":\"FULL\",\"passengers\":[{\"cabinId\":\""
                            + cabin.getCabinId()
                            + "\"}]}";
            var quote =
                    response(
                            mvc.perform(
                                    post("/booking/quotes")
                                            .header("Authorization", "Bearer " + passenger)
                                            .contentType("application/json")
                                            .content(quoteBody)),
                            201);
            assertEquals("USD", quote.path("currency").asText());
            assertTrue(
                    java.time.Instant.parse(quote.path("expiresAt").asText())
                            .isAfter(java.time.Instant.now()));
            String other = token(com.casms.identity.enums.Role.PASSENGER);
            mvc.perform(
                            get("/booking/quotes/{id}", quote.path("quoteId").asText())
                                    .header("Authorization", "Bearer " + other))
                    .andExpect(status().isForbidden());
            String bookingBody =
                    "{\"cruiseId\":\""
                            + cruiseId
                            + "\",\"quoteId\":\""
                            + quote.path("quoteId").asText()
                            + "\",\"currency\":\"USD\",\"paymentPlan\":\"FULL\",\"passengers\":[{\"fullName\":\"Isolated"
                            + " USD passenger\",\"cabinId\":\""
                            + cabin.getCabinId()
                            + "\"}]}";
            // Do not record the guest bearer token in HTTP evidence or report files.
            var guestRaw =
                    mvc.perform(
                                    post("/booking/quotes")
                                            .contentType("application/json")
                                            .content(quoteBody))
                            .andExpect(status().isCreated())
                            .andReturn();
            var guestQuote =
                    mapper.readTree(guestRaw.getResponse().getContentAsString()).path("data");
            assertFalse(guestQuote.path("guestToken").asText().isBlank());
            String guestBody =
                    bookingBody
                            .replace(
                                    quote.path("quoteId").asText(),
                                    guestQuote.path("quoteId").asText())
                            .replace(
                                    "\"fullName\":",
                                    "\"email\":\"qa-guest@example.invalid\",\"fullName\":");
            mvc.perform(
                            post("/booking")
                                    .header(
                                            "Idempotency-Key",
                                            java.util.UUID.randomUUID().toString())
                                    .contentType("application/json")
                                    .content(guestBody))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/booking")
                                    .header("Authorization", "Bearer " + other)
                                    .header(
                                            "Idempotency-Key",
                                            java.util.UUID.randomUUID().toString())
                                    .contentType("application/json")
                                    .content(guestBody))
                    .andExpect(status().isForbidden());
            String intent = java.util.UUID.randomUUID().toString();
            var booking =
                    response(
                            mvc.perform(
                                    post("/booking")
                                            .header("Authorization", "Bearer " + passenger)
                                            .header("Idempotency-Key", intent)
                                            .contentType("application/json")
                                            .content(bookingBody)),
                            201);
            var retry =
                    response(
                            mvc.perform(
                                    post("/booking")
                                            .header("Authorization", "Bearer " + passenger)
                                            .header("Idempotency-Key", intent)
                                            .contentType("application/json")
                                            .content(bookingBody)),
                            201);
            assertEquals(booking.path("bookingId"), retry.path("bookingId"));
            assertEquals("USD", booking.path("currency").asText());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM \"Booking\" WHERE cruise_id=?",
                            Integer.class,
                            cruiseId));
            var actual =
                    jdbc.queryForMap(
                            "SELECT"
                                + " booking_reference,booking_status::text,currency,agreed_fare_amount"
                                + " FROM \"Booking\" WHERE cruise_id=?",
                            cruiseId);
            assertFalse(actual.get("booking_reference").toString().isBlank());
            assertEquals("USD", actual.get("currency"));
            assertEquals("Draft", actual.get("booking_status"));
            var readBooking =
                    response(
                            mvc.perform(
                                    get("/booking/{id}", booking.path("bookingId").asText())
                                            .header("Authorization", "Bearer " + passenger)),
                            200);
            assertEquals(booking.path("bookingId"), readBooking.path("bookingId"));
            String finance = token(com.casms.identity.enums.Role.FINANCE_OFFICER),
                    paymentIntent = java.util.UUID.randomUUID().toString();
            String paymentBody =
                    "{\"bookingId\":\""
                            + booking.path("bookingId").asText()
                            + "\",\"requestedAmount\":"
                            + quote.path("totalAmount").asText()
                            + ",\"paymentMethod\":\"Cash\",\"idempotencyKey\":\""
                            + paymentIntent
                            + "\"}";
            var payment =
                    response(
                            mvc.perform(
                                    post("/payment/process")
                                            .header("Authorization", "Bearer " + finance)
                                            .contentType("application/json")
                                            .content(paymentBody)),
                            201);
            assertEquals("USD", payment.path("currency").asText());
            assertEquals("Pending", payment.path("status").asText());
            // Initiation is not collection: never manufacture a receipt or assert paid/Confirmed.
            assertEquals(
                    "Draft",
                    jdbc.queryForObject(
                            "SELECT booking_status::text FROM \"Booking\" WHERE cruise_id=?",
                            String.class,
                            cruiseId));
            var proof = mapper.createObjectNode();
            proof.put("cruiseId", cruiseId.toString());
            proof.set("booking", booking);
            proof.set("quote", quote);
            proof.set("pricing", pricingApproved);
            proof.set("databaseBooking", mapper.valueToTree(actual));
            proof.set("paymentInitiation", payment);
            proof.put("fundsCollected", false);
            var expiryQuote =
                    response(
                            mvc.perform(
                                    post("/booking/quotes")
                                            .header("Authorization", "Bearer " + passenger)
                                            .contentType("application/json")
                                            .content(quoteBody)),
                            201);
            jdbc.update(
                    "UPDATE \"BookingQuote\" SET expires_at=? WHERE quote_id=?",
                    java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
                    java.util.UUID.fromString(expiryQuote.path("quoteId").asText()));
            String expiredRequest =
                    bookingBody.replace(
                            quote.path("quoteId").asText(), expiryQuote.path("quoteId").asText());
            mvc.perform(
                            post("/booking")
                                    .header("Authorization", "Bearer " + passenger)
                                    .header(
                                            "Idempotency-Key",
                                            java.util.UUID.randomUUID().toString())
                                    .contentType("application/json")
                                    .content(expiredRequest))
                    .andExpect(status().isConflict())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.code")
                                    .value("QUOTE_EXPIRED"));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM \"Booking\" WHERE cruise_id=?",
                            Integer.class,
                            cruiseId));
            proof.put("expiredQuoteRejectedWithoutBooking", true);
            var guestRequest =
                    (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(guestBody);
            guestRequest.put("quoteGuestToken", guestQuote.path("guestToken").asText());
            String guestIntent = java.util.UUID.randomUUID().toString();
            var guestResult =
                    mvc.perform(
                                    post("/booking")
                                            .header("Idempotency-Key", guestIntent)
                                            .contentType("application/json")
                                            .content(mapper.writeValueAsString(guestRequest)))
                            .andExpect(status().isCreated())
                            .andReturn();
            var guestBooking =
                    mapper.readTree(guestResult.getResponse().getContentAsString()).path("data");
            var guestRetry =
                    mvc.perform(
                                    post("/booking")
                                            .header("Idempotency-Key", guestIntent)
                                            .contentType("application/json")
                                            .content(mapper.writeValueAsString(guestRequest)))
                            .andExpect(status().isCreated())
                            .andReturn();
            assertEquals(
                    guestBooking.path("bookingId"),
                    mapper.readTree(guestRetry.getResponse().getContentAsString())
                            .path("data")
                            .path("bookingId"));
            assertEquals("USD", guestBooking.path("currency").asText());
            assertEquals("Draft", guestBooking.path("bookingStatus").asText());
            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM \"Booking\" WHERE cruise_id=?",
                            Integer.class,
                            cruiseId));
            proof.put("guestBookingId", guestBooking.path("bookingId").asText());
            proof.put("guestQuoteOwnershipEnforced", true);
            proof.put("guestRetrySameBooking", true);
            Files.writeString(
                    Path.of("docs/mainflow-alignment/final-audit/g6-usd-golden-readback.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            return;
        }
        // A commercial decision cannot be forged by a previously APPROVED legacy policy.
        jdbc.update(
                "UPDATE \"CruisePricingPolicy\" SET status='APPROVED' WHERE cruise_id=?", cruiseId);
        var saleWhole =
                response(
                        mvc.perform(
                                get("/cruise/{id}/sale-readiness", cruiseId)
                                        .param("saleScope", "WHOLE_CRUISE")
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        var saleCabin =
                response(
                        mvc.perform(
                                get("/cruise/{id}/sale-readiness", cruiseId)
                                        .param("saleScope", "CABIN_ONLY")
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        assertNotEquals(
                saleWhole.path("fingerprint").asText(), saleCabin.path("fingerprint").asText());
        assertFalse(saleCabin.path("ready").asBoolean());
        assertTrue(
                saleCabin.path("blockers").toString().contains("PRICING_POLICY_APPROVAL_REQUIRED"));
        String passenger = token(com.casms.identity.enums.Role.PASSENGER);
        var sellability =
                response(
                        mvc.perform(
                                get("/cruise/{id}/sellability", cruiseId)
                                        .header("Authorization", "Bearer " + passenger)),
                        200);
        assertTrue(sellability.path("sellable").isBoolean());
        assertFalse(sellability.path("sellable").asBoolean());
        response(
                mvc.perform(
                                get("/booking/available-cabins")
                                        .param("cruiseId", cruiseId.toString())
                                        .header("Authorization", "Bearer " + passenger))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .jsonPath("$.code")
                                        .value("PRICING_POLICY_APPROVAL_REQUIRED")),
                409);
        String quote =
                "{\"cruiseId\":\""
                        + cruiseId
                        + "\",\"currency\":\"USD\",\"paymentPlan\":\"FULL\",\"passengers\":[{\"cabinId\":\""
                        + cabin.getCabinId()
                        + "\"}]}";
        response(
                mvc.perform(
                                post("/booking/quotes")
                                        .header("Authorization", "Bearer " + passenger)
                                        .contentType("application/json")
                                        .content(quote))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .jsonPath("$.code")
                                        .value("PRICING_POLICY_APPROVAL_REQUIRED")),
                409);
        String booking =
                "{\"cruiseId\":\""
                        + cruiseId
                        + "\",\"currency\":\"USD\",\"paymentPlan\":\"FULL\",\"passengers\":[{\"fullName\":\"Isolated"
                        + " QA passenger\",\"cabinId\":\""
                        + cabin.getCabinId()
                        + "\"}]}";
        String bookingIntent = java.util.UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++)
            response(
                    mvc.perform(
                                    post("/booking")
                                            .header("Authorization", "Bearer " + passenger)
                                            .header("Idempotency-Key", bookingIntent)
                                            .contentType("application/json")
                                            .content(booking))
                            .andExpect(
                                    org.springframework.test.web.servlet.result
                                            .MockMvcResultMatchers.jsonPath("$.code")
                                            .value("PRICING_POLICY_APPROVAL_REQUIRED")),
                    409);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"Booking\" WHERE cruise_id=?",
                        Integer.class,
                        cruiseId));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"BookingQuote\" WHERE cruise_id=?",
                        Integer.class,
                        cruiseId));
        // Relieve the fixture assignment through the real API; no rows are deleted.
        mvc.perform(
                        delete("/cruise/{id}/staff/{user}", cruiseId, opsId)
                                .header("Authorization", "Bearer " + ops))
                .andExpect(status().isNoContent());
        var invalidatedPreparation =
                response(
                        mvc.perform(
                                get("/cruise/{id}/preparation", cruiseId)
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        assertFalse(invalidatedPreparation.path("allConfirmed").asBoolean());
        for (var section : invalidatedPreparation.path("sections")) {
            assertEquals("RETURNED", section.path("status").asText());
            assertFalse(section.path("returnedAt").isNull());
            assertFalse(section.path("returnedByUserId").isNull());
        }
        assertEquals(
                5,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePreparationEvent\" WHERE cruise_id=? AND"
                            + " action='INVALIDATE'",
                        Integer.class,
                        cruiseId));
        var invalidated =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", cruiseId)
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        assertEquals("INVALIDATED", invalidated.path("readyStatus").asText());
        response(
                mvc.perform(
                                get("/booking/available-cabins")
                                        .param("cruiseId", cruiseId.toString())
                                        .header("Authorization", "Bearer " + passenger))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .jsonPath("$.code")
                                        .value("READY_CONFIRMATION_REQUIRED")),
                422);
        // Save fixture identities for subsequent browser QA; no bearer tokens or password hashes.
        var fixture = mapper.createObjectNode();
        fixture.put("cruiseId", cruiseId.toString());
        fixture.put("crmUsername", jwt.extractUsername(crm));
        fixture.put("opsUsername", jwt.extractUsername(ops));
        fixture.put("opsBUsername", jwt.extractUsername(opsB));
        fixture.put("shipUsername", jwt.extractUsername(shipToken));
        fixture.put("passengerUsername", jwt.extractUsername(passenger));
        Files.writeString(
                Path.of("docs/mainflow-alignment/runtime-fixture.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(fixture));
    }

    @Test
    void additiveMigrationsAndRuntimeOpenApi() throws Exception {
        assertEquals(
                "ocvviet_v4_test", jdbc.queryForObject("select current_database()", String.class));
        assertNotNull(
                jdbc.queryForObject("select to_regclass('\"CruisePlanReview\"')", String.class));
        String openapi =
                mvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertTrue(mapper.readTree(openapi).path("paths").has("/cruise-plans/{cruiseId}"));
        Files.createDirectories(Path.of("docs/mainflow-alignment"));
        Files.writeString(Path.of("docs/mainflow-alignment/runtime-openapi.json"), openapi);
    }

    @Test
    void customCopiesAreIndependentAndFixedRejectsStructuralWrites() throws Exception {
        String crm = token(com.casms.identity.enums.Role.CRUISE_MANAGER);
        var source = finalizedSource(crm);
        Long sourceRevision = source.getRevision();
        String body =
                "{\"cruiseName\":\"Isolated copy scope\",\"sourceVersionId\":\""
                        + source.getVersionId()
                        + "\",\"expectedSourceVersionRevision\":"
                        + sourceRevision
                        + ",\"itineraryMode\":\"CUSTOM\",\"departureDatetime\":\"2028-03-01T01:00:00Z\"}";
        var a =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        var b =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        java.util.UUID aid = java.util.UUID.fromString(a.path("cruiseId").asText()),
                bid = java.util.UUID.fromString(b.path("cruiseId").asText());
        var context =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}/ship-assignment-context", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertFalse(context.path("hasBookings").asBoolean());
        assertTrue(context.path("editablePlan").asBoolean());
        assertEquals(a.path("revision").asLong(), context.path("cruiseRevision").asLong());
        assertFalse(context.has("passengerList"));
        String ops = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        mvc.perform(
                        get("/cruise-plans/{id}/ship-assignment-context", aid)
                                .header("Authorization", "Bearer " + ops))
                .andExpect(status().isForbidden());
        var ship = new com.casms.ship.entity.Ship();
        ship.setShipCode("V4-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        ship.setShipName("Assignment fixture ship");
        ship.setStatus(com.casms.ship.enums.ShipStatus.READY);
        ship.setTotalCapacity(2);
        ship.setTotalDecks(1);
        ship = ships.save(ship);
        var deck = new com.casms.ship.entity.Deck();
        deck.setShip(ship);
        deck.setDeckNumber(1);
        deck.setDeckName("Assignment deck");
        deck = decks.save(deck);
        var cabin = new com.casms.ship.entity.Cabin();
        cabin.setShip(ship);
        cabin.setDeck(deck);
        cabin.setCabinNumber("101");
        cabin.setCabinType(com.casms.ship.enums.CabinType.INTERIOR);
        cabin.setCapacity(2);
        cabin.setStatus(com.casms.ship.enums.CabinStatus.AVAILABLE);
        cabins.save(cabin);
        mvc.perform(
                        post("/cruise/{id}/assign-ship", aid)
                                .param("shipId", ship.getShipId().toString())
                                .header("Authorization", "Bearer " + ops))
                .andExpect(status().isForbidden());
        a =
                response(
                        mvc.perform(
                                post("/cruise/{id}/assign-ship", aid)
                                        .param("shipId", ship.getShipId().toString())
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals(ship.getShipId().toString(), a.path("shipId").asText());
        String activity =
                """
{"cruiseId":"%s","activityName":"Scoped custom activity","location":"Main deck","startTime":"2028-03-01T02:00:00Z","endTime":"2028-03-01T03:00:00Z","capacity":10,"price":0,"currencyCode":"USD"}
"""
                        .formatted(aid);
        var saved =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/activities", aid)
                                        .header("Authorization", "Bearer " + crm)
                                        .header("If-Match", a.path("revision").asLong())
                                        .contentType("application/json")
                                        .content(activity)),
                        200);
        var currentPlan =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        long editRevision = currentPlan.path("windows").path("expectedRevision").asLong();
        mvc.perform(
                        put(
                                        "/cruise-plans/{id}/activities/{activity}",
                                        aid,
                                        saved.path("id").asText())
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", a.path("revision").asLong())
                                .contentType("application/json")
                                .content(activity))
                .andExpect(status().isConflict());
        var edited =
                response(
                        mvc.perform(
                                put(
                                                "/cruise-plans/{id}/activities/{activity}",
                                                aid,
                                                saved.path("id").asText())
                                        .header("Authorization", "Bearer " + crm)
                                        .header("If-Match", editRevision)
                                        .contentType("application/json")
                                        .content(
                                                activity.replace(
                                                        "\"capacity\":10", "\"capacity\":15"))),
                        200);
        assertEquals(15, edited.path("capacity").asInt());
        assertEquals(0, edited.path("price").asInt());
        var readback =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}/activities", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals(saved.path("id"), readback.get(0).path("id"));
        assertEquals(15, readback.get(0).path("capacity").asInt());
        mvc.perform(
                        post("/cruise/{id}/sync-operational-programs", aid)
                                .header("Authorization", "Bearer " + crm))
                .andExpect(status().isConflict())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("PROGRAMS_ALREADY_MATERIALIZED"));
        var afterSync =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}/activities", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals(15, afterSync.get(0).path("capacity").asInt());
        assertEquals("Scoped custom activity", afterSync.get(0).path("name").asText());
        var foreignShip = new com.casms.ship.entity.Ship();
        foreignShip.setShipCode("V4-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        foreignShip.setShipName("Foreign venue fixture");
        foreignShip.setStatus(com.casms.ship.enums.ShipStatus.READY);
        foreignShip.setTotalCapacity(2);
        foreignShip.setTotalDecks(1);
        foreignShip = ships.save(foreignShip);
        var foreignDeck = new com.casms.ship.entity.Deck();
        foreignDeck.setShip(foreignShip);
        foreignDeck.setDeckNumber(1);
        foreignDeck.setDeckName("Foreign deck");
        foreignDeck = decks.save(foreignDeck);
        var venueOptions =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}/program-options", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals(ship.getShipId().toString(), venueOptions.path("ship").path("id").asText());
        assertFalse(venueOptions.path("decks").isEmpty());
        for (var candidate : venueOptions.path("decks")) {
            assertEquals(ship.getShipId().toString(), candidate.path("shipId").asText());
            assertNotEquals(foreignDeck.getDeckId().toString(), candidate.path("id").asText());
        }
        String invalidVenue =
                activity.replace("\"capacity\":10", "\"capacity\":15")
                        .replace(
                                "\"location\":",
                                "\"deckId\":\"" + foreignDeck.getDeckId() + "\",\"location\":");
        long venueRevision =
                jdbc.queryForObject(
                        "SELECT revision FROM \"Cruise\" WHERE cruise_id=?", Long.class, aid);
        mvc.perform(
                        put(
                                        "/cruise-plans/{id}/activities/{activity}",
                                        aid,
                                        saved.path("id").asText())
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", venueRevision)
                                .contentType("application/json")
                                .content(invalidVenue))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("ACTIVITY_VENUE_MISMATCH"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.details.fieldPath")
                                .value("deckId"));
        mvc.perform(
                        get("/trip-planning/catalogs/sources/cabins")
                                .param("shipId", ship.getShipId().toString())
                                .param("deckId", foreignDeck.getDeckId().toString())
                                .header("Authorization", "Bearer " + crm))
                .andExpect(status().isUnprocessableEntity());
        String ownVenue =
                invalidVenue
                        .replace(foreignDeck.getDeckId().toString(), deck.getDeckId().toString())
                        .replace(
                                "\"deckId\":",
                                "\"cabinId\":\"" + cabin.getCabinId() + "\",\"deckId\":");
        var located =
                response(
                        mvc.perform(
                                put(
                                                "/cruise-plans/{id}/activities/{activity}",
                                                aid,
                                                saved.path("id").asText())
                                        .header("Authorization", "Bearer " + crm)
                                        .header("If-Match", venueRevision)
                                        .contentType("application/json")
                                        .content(ownVenue)),
                        200);
        assertEquals(deck.getDeckId().toString(), located.path("deckId").asText());
        assertEquals(cabin.getCabinId().toString(), located.path("cabinId").asText());
        var packagePlan =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        String packageBody =
                "{\"name\":\"Cruise scoped package\",\"description\":\"Explicit test"
                    + " package\",\"type\":\"BASIC\",\"price\":0,\"currency\":\"USD\"}";
        var ownPackage =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/packages", aid)
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "If-Match",
                                                packagePlan
                                                        .path("windows")
                                                        .path("expectedRevision")
                                                        .asLong())
                                        .contentType("application/json")
                                        .content(packageBody)),
                        200);
        String packageId = ownPackage.path("id").asText();
        packagePlan =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        response(
                mvc.perform(
                        put("/cruise-plans/{id}/packages/{package}", aid, packageId)
                                .header("Authorization", "Bearer " + crm)
                                .header(
                                        "If-Match",
                                        packagePlan
                                                .path("windows")
                                                .path("expectedRevision")
                                                .asLong())
                                .contentType("application/json")
                                .content(
                                        packageBody.replace(
                                                "Cruise scoped package", "Edited own package"))),
                200);
        packagePlan =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        String componentBody =
                "{\"referenceId\":\""
                        + saved.path("id").asText()
                        + "\",\"benefitType\":\"INCLUDED\"}";
        response(
                mvc.perform(
                        post("/cruise-plans/{id}/packages/{package}/activities", aid, packageId)
                                .header("Authorization", "Bearer " + crm)
                                .header(
                                        "If-Match",
                                        packagePlan
                                                .path("windows")
                                                .path("expectedRevision")
                                                .asLong())
                                .contentType("application/json")
                                .content(componentBody)),
                200);
        var componentView =
                response(
                        mvc.perform(
                                get(
                                                "/cruise-plans/{id}/packages/{package}/components",
                                                aid,
                                                packageId)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals(aid.toString(), componentView.path("cruiseId").asText());
        assertEquals(packageId, componentView.path("packageId").asText());
        assertEquals(1, componentView.path("components").size());
        assertEquals(
                "INCLUDED", componentView.path("components").get(0).path("benefitType").asText());
        packagePlan =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        mvc.perform(
                        post("/cruise-plans/{id}/packages/{package}/activities", aid, packageId)
                                .header("Authorization", "Bearer " + crm)
                                .header(
                                        "If-Match",
                                        packagePlan
                                                .path("windows")
                                                .path("expectedRevision")
                                                .asLong())
                                .contentType("application/json")
                                .content(componentBody))
                .andExpect(status().isConflict())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("PACKAGE_COMPONENT_DUPLICATE"));
        mvc.perform(
                        get("/cruise-plans/{id}/packages/{package}/components", bid, packageId)
                                .header("Authorization", "Bearer " + crm))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/cruise-plans/{id}/packages/{package}/components", aid, packageId)
                                .header("Authorization", "Bearer " + ops))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put(
                                        "/cruise-plans/{id}/activities/{activity}",
                                        bid,
                                        saved.path("id").asText())
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", b.path("revision").asLong())
                                .contentType("application/json")
                                .content(activity.replace(aid.toString(), bid.toString())))
                .andExpect(status().isForbidden());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"OnboardActivity\" WHERE cruise_id=?",
                        Integer.class,
                        bid));
        assertNotEquals(
                jdbc.queryForObject(
                        "SELECT itinerary_id FROM \"Itinerary\" WHERE cruise_id=?",
                        java.util.UUID.class,
                        aid),
                jdbc.queryForObject(
                        "SELECT itinerary_id FROM \"Itinerary\" WHERE cruise_id=?",
                        java.util.UUID.class,
                        bid));
        assertEquals(
                sourceRevision,
                versions.findById(source.getVersionId()).orElseThrow().getRevision());
        var fixed =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body.replace("CUSTOM", "FIXED"))),
                        201);
        java.util.UUID fixedId = java.util.UUID.fromString(fixed.path("cruiseId").asText());
        mvc.perform(
                        post("/cruise-plans/{id}/activities", fixedId)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", fixed.path("revision").asLong())
                                .contentType("application/json")
                                .content(activity.replace(aid.toString(), fixedId.toString())))
                .andExpect(status().isConflict())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("FIXED_ITINERARY_READ_ONLY"));
        // Seed an existing inherited FIXED activity to test commercial edits without a
        // structural-create bypass.
        java.util.UUID fixedActivityId = java.util.UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " \"OnboardActivity\"(onboard_activity_id,activity_code,activity_name,description,start_time,end_time,location,capacity,price,currency_code,status,created_at,updated_at,cruise_id)"
                    + " SELECT"
                    + " ?,activity_code||'-FIXED',activity_name,description,start_time,end_time,location,capacity,price,currency_code,status,now(),now(),?"
                    + " FROM \"OnboardActivity\" WHERE onboard_activity_id=?",
                fixedActivityId,
                fixedId,
                java.util.UUID.fromString(saved.path("id").asText()));
        String fixedBody =
                activity.replace(aid.toString(), fixedId.toString())
                        .replace("\"price\":0", "\"price\":500");
        var fixedEdit =
                response(
                        mvc.perform(
                                put(
                                                "/cruise-plans/{id}/activities/{activity}",
                                                fixedId,
                                                fixedActivityId)
                                        .header("Authorization", "Bearer " + crm)
                                        .header("If-Match", fixed.path("revision").asLong())
                                        .contentType("application/json")
                                        .content(fixedBody)),
                        200);
        assertEquals(500, fixedEdit.path("price").asInt());
        var fixedPlan =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", fixedId)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        mvc.perform(
                        put("/cruise-plans/{id}/activities/{activity}", fixedId, fixedActivityId)
                                .header("Authorization", "Bearer " + crm)
                                .header(
                                        "If-Match",
                                        fixedPlan.path("windows").path("expectedRevision").asLong())
                                .contentType("application/json")
                                .content(fixedBody.replace("Main deck", "Other location")))
                .andExpect(status().isConflict());
        for (String field : java.util.List.of("deckId", "cabinId")) {
            var changedVenue = mapper.readTree(fixedBody);
            ((com.fasterxml.jackson.databind.node.ObjectNode) changedVenue)
                    .put(field, java.util.UUID.randomUUID().toString());
            mvc.perform(
                            put(
                                            "/cruise-plans/{id}/activities/{activity}",
                                            fixedId,
                                            fixedActivityId)
                                    .header("Authorization", "Bearer " + crm)
                                    .header(
                                            "If-Match",
                                            fixedPlan
                                                    .path("windows")
                                                    .path("expectedRevision")
                                                    .asLong())
                                    .contentType("application/json")
                                    .content(mapper.writeValueAsString(changedVenue)))
                    .andExpect(status().isConflict())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.code")
                                    .value("FIXED_ITINERARY_READ_ONLY"));
        }
        var failure =
                assertThrows(
                        com.casms.common.exception.BusinessException.class,
                        () ->
                                new org.springframework.transaction.support.TransactionTemplate(
                                                transactions)
                                        .execute(
                                                tx ->
                                                        materialization.materializeSchedule(
                                                                cruises.findById(aid).orElseThrow(),
                                                                versions.findById(
                                                                                source
                                                                                        .getVersionId())
                                                                        .orElseThrow())));
        assertEquals("ITINERARY_ALREADY_MATERIALIZED", failure.getCode());
        // Legacy-corrupt fixture: a package linked in A must not reveal a program belonging to B.
        var foreignActivity = java.util.UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " \"OnboardActivity\"(onboard_activity_id,activity_code,activity_name,description,start_time,end_time,location,capacity,price,currency_code,status,created_at,updated_at,cruise_id)"
                    + " SELECT"
                    + " ?,activity_code||'-FOREIGN',activity_name,description,start_time,end_time,location,capacity,price,currency_code,status,now(),now(),?"
                    + " FROM \"OnboardActivity\" WHERE onboard_activity_id=?",
                foreignActivity,
                bid,
                java.util.UUID.fromString(saved.path("id").asText()));
        jdbc.update(
                "INSERT INTO"
                    + " \"TourPackageActivity\"(tour_package_id,onboard_activity_id,benefit_type)"
                    + " VALUES(?,?,'INCLUDED')",
                java.util.UUID.fromString(packageId),
                foreignActivity);
        mvc.perform(
                        get("/cruise-plans/{id}/packages/{package}/components", aid, packageId)
                                .header("Authorization", "Bearer " + crm))
                .andExpect(status().isForbidden());
    }

    @Test
    void withdrawalRejectionAndDualRoleMakerCheckerKeepAccurateAudit() throws Exception {
        String maker =
                token(
                        com.casms.identity.enums.Role.CRUISE_MANAGER,
                        com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        String reviewer = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        var source = finalizedSource(maker);
        String body =
                "{\"cruiseName\":\"Review audit fixture\",\"sourceVersionId\":\""
                        + source.getVersionId()
                        + "\",\"expectedSourceVersionRevision\":"
                        + source.getRevision()
                        + ",\"itineraryMode\":\"CUSTOM\",\"departureDatetime\":\"2028-03-01T01:00:00Z\"}";
        var cruise =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + maker)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        var id = java.util.UUID.fromString(cruise.path("cruiseId").asText());
        // Independent test fixture; ship assignment itself is exercised through its real API in the
        // G2 test.
        var ship = new com.casms.ship.entity.Ship();
        ship.setShipCode("V4-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        ship.setShipName("Review fixture ship");
        ship.setStatus(com.casms.ship.enums.ShipStatus.READY);
        ship.setTotalCapacity(2);
        ship.setTotalDecks(1);
        ship = ships.save(ship);
        jdbc.update("UPDATE \"Cruise\" SET ship_id=? WHERE cruise_id=?", ship.getShipId(), id);
        String windows =
                "{\"expectedRevision\":"
                        + cruise.path("revision").asLong()
                        + ",\"bookingOpensAt\":\"2027-02-01T00:00:00Z\",\"bookingClosesAt\":\"2027-11-01T00:00:00Z\",\"guidanceOpensAt\":\"2028-02-01T00:00:00Z\",\"guidanceClosesAt\":\"2028-02-20T00:00:00Z\"}";
        var configured =
                response(
                        mvc.perform(
                                put("/cruise-plans/{id}/windows", id)
                                        .header("Authorization", "Bearer " + maker)
                                        .contentType("application/json")
                                        .content(windows)),
                        200);
        String policy =
                "{\"expectedRevision\":0,\"policyVersion\":\"Review-qa\",\"currency\":\"USD\",\"interiorBaseFare\":0,\"oceanViewBaseFare\":0,\"balconyBaseFare\":0,\"suiteBaseFare\":0,\"portFeePerPassenger\":0,\"taxRate\":0,\"depositPercentage\":0,\"finalPaymentDueDays\":0,\"fullPaymentCutoffDays\":0,\"autoApprove\":false}";
        response(
                mvc.perform(
                        put("/cruise-plans/{id}/pricing-policy", id)
                                .header("Authorization", "Bearer " + maker)
                                .header(
                                        "If-Match",
                                        configured
                                                .path("windows")
                                                .path("expectedRevision")
                                                .asLong())
                                .contentType("application/json")
                                .content(policy)),
                200);
        var submitted =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/submit", id)
                                        .header("Authorization", "Bearer " + maker)
                                        .contentType("application/json")
                                        .content("{\"expectedRevision\":0}")),
                        200);
        long submittedRevision = submitted.path("revision").asLong();
        for (String decision : java.util.List.of("approve", "reject", "request-revision")) {
            mvc.perform(
                            post("/cruise-plans/{id}/" + decision, id)
                                    .header("Authorization", "Bearer " + maker)
                                    .contentType("application/json")
                                    .content(
                                            "{\"expectedRevision\":"
                                                    + submittedRevision
                                                    + ",\"reason\":\"Self review forbidden\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.code")
                                    .value("SELF_APPROVAL_FORBIDDEN"));
        }
        var withdrawn =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/withdraw", id)
                                        .header("Authorization", "Bearer " + maker)
                                        .contentType("application/json")
                                        .content(
                                                "{\"expectedRevision\":"
                                                        + submittedRevision
                                                        + "}")),
                        200);
        assertEquals("WITHDRAWN", withdrawn.path("planStatus").asText());
        assertTrue(withdrawn.path("reviewedBy").isNull());
        assertTrue(withdrawn.path("reviewedAt").isNull());
        submitted =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/submit", id)
                                        .header("Authorization", "Bearer " + maker)
                                        .contentType("application/json")
                                        .content(
                                                "{\"expectedRevision\":"
                                                        + withdrawn.path("revision").asLong()
                                                        + "}")),
                        200);
        mvc.perform(
                        post("/cruise-plans/{id}/reject", id)
                                .header("Authorization", "Bearer " + reviewer)
                                .contentType("application/json")
                                .content(
                                        "{\"expectedRevision\":"
                                                + submitted.path("revision").asLong()
                                                + "}"))
                .andExpect(status().isUnprocessableEntity());
        String reviewerB = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        String decisionBody =
                "{\"expectedRevision\":"
                        + submitted.path("revision").asLong()
                        + ",\"reason\":\"Review fixture rejection\"}";
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        com.fasterxml.jackson.databind.JsonNode rejected;
        try {
            java.util.List<
                            java.util.concurrent.Future<
                                    org.springframework.test.web.servlet.MvcResult>>
                    decisions = new java.util.ArrayList<>();
            for (String actor : java.util.List.of(reviewer, reviewerB))
                decisions.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                        throw new IllegalStateException("Review barrier timed out");
                                    return mvc.perform(
                                                    post("/cruise-plans/{id}/reject", id)
                                                            .header(
                                                                    "Authorization",
                                                                    "Bearer " + actor)
                                                            .contentType("application/json")
                                                            .content(decisionBody))
                                            .andReturn();
                                }));
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var results = new java.util.ArrayList<org.springframework.test.web.servlet.MvcResult>();
            for (var decision : decisions)
                results.add(decision.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(
                    java.util.List.of(200, 409),
                    results.stream().map(r -> r.getResponse().getStatus()).sorted().toList());
            for (var result : results) recordHttpResult(result);
            var successful =
                    results.stream()
                            .filter(r -> r.getResponse().getStatus() == 200)
                            .findFirst()
                            .orElseThrow();
            var stale =
                    results.stream()
                            .filter(r -> r.getResponse().getStatus() == 409)
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    "STALE_REVISION",
                    mapper.readTree(stale.getResponse().getContentAsString())
                            .path("code")
                            .asText());
            rejected = mapper.readTree(successful.getResponse().getContentAsString()).path("data");
            assertEquals(
                    submitted.path("revision").asLong() + 1, rejected.path("revision").asLong());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        assertEquals("REJECTED", rejected.path("planStatus").asText());
        assertNotEquals(rejected.path("submittedBy"), rejected.path("reviewedBy"));
        assertEquals("Review fixture rejection", rejected.path("reason").asText());
        mvc.perform(
                        post("/cruise-plans/{id}/approve", id)
                                .header("Authorization", "Bearer " + reviewer)
                                .contentType("application/json")
                                .content(
                                        "{\"expectedRevision\":"
                                                + submitted.path("revision").asLong()
                                                + "}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/cruise-plans/{id}/submit", id)
                                .header("Authorization", "Bearer " + maker)
                                .contentType("application/json")
                                .content(
                                        "{\"expectedRevision\":"
                                                + rejected.path("revision").asLong()
                                                + "}"))
                .andExpect(status().isConflict());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePlanEvent\" WHERE cruise_id=? AND"
                            + " action='WITHDRAW'",
                        Integer.class,
                        id));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePlanEvent\" WHERE cruise_id=? AND"
                            + " action='REJECT'",
                        Integer.class,
                        id));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePlanEvent\" WHERE cruise_id=? AND"
                            + " action='APPROVE'",
                        Integer.class,
                        id));
    }

    @Test
    void scopedExcursionCommercialPatchPreservesFixedSourceFields() throws Exception {
        String crm = token(com.casms.identity.enums.Role.CRUISE_MANAGER),
                ops = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        var source = finalizedSource(crm);
        String body =
                "{\"cruiseName\":\"Commercial tour fixture\",\"sourceVersionId\":\""
                        + source.getVersionId()
                        + "\",\"expectedSourceVersionRevision\":"
                        + source.getRevision()
                        + ",\"itineraryMode\":\"CUSTOM\",\"departureDatetime\":\"2028-03-01T01:00:00Z\"}";
        var a =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        var b =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body.replace("CUSTOM", "FIXED"))),
                        201);
        var aid = java.util.UUID.fromString(a.path("cruiseId").asText());
        var bid = java.util.UUID.fromString(b.path("cruiseId").asText());
        var stopA =
                jdbc.queryForObject(
                        "SELECT s.cruise_stop_id FROM \"CruiseStop\" s JOIN \"Itinerary\" i"
                            + " USING(itinerary_id) WHERE i.cruise_id=?",
                        java.util.UUID.class,
                        aid);
        var stopB =
                jdbc.queryForObject(
                        "SELECT s.cruise_stop_id FROM \"CruiseStop\" s JOIN \"Itinerary\" i"
                            + " USING(itinerary_id) WHERE i.cruise_id=?",
                        java.util.UUID.class,
                        bid);
        var supplier = new com.casms.activity.entity.Supplier();
        supplier.setSupplierName("Isolated tour provider");
        supplier.setContactName("QA fixture contact");
        supplier.setStatus(com.casms.activity.enums.SupplierStatus.ACTIVE);
        supplier.setVerificationStatus(
                com.casms.activity.enums.SupplierVerificationStatus.VERIFIED);
        supplier = suppliers.save(supplier);
        String tour =
                "{\"cruiseStopId\":\""
                        + stopA
                        + "\",\"supplierId\":\""
                        + supplier.getSupplierId()
                        + "\",\"excursionName\":\"Scoped shore"
                        + " program\",\"startTime\":\"2028-03-01T02:00:00Z\",\"plannedReturnTime\":\"2028-03-01T03:00:00Z\",\"meetingPoint\":\"Port"
                        + " gate\",\"maximumCapacity\":10,\"price\":0,\"currencyCode\":\"USD\"}";
        var original =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/excursions", aid)
                                        .header("Authorization", "Bearer " + crm)
                                        .header("If-Match", a.path("revision").asLong())
                                        .contentType("application/json")
                                        .content(tour)),
                        200);
        var fixedTour = java.util.UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " \"ShoreExcursion\"(shore_excursion_id,cruise_stop_id,supplier_id,excursion_code,excursion_name,start_time,planned_return_time,meeting_point,minimum_participants,maximum_capacity,price,currency_code,status,is_mandatory,created_at,updated_at)"
                    + " SELECT"
                    + " ?,?,supplier_id,excursion_code||'-FIXED',excursion_name,start_time,planned_return_time,meeting_point,NULL,maximum_capacity,price,currency_code,status,false,now(),now()"
                    + " FROM \"ShoreExcursion\" WHERE shore_excursion_id=?",
                fixedTour,
                stopB,
                java.util.UUID.fromString(original.path("shoreExcursionId").asText()));
        String commercial = "{\"maximumCapacity\":11,\"price\":0,\"currencyCode\":\"USD\"}";
        var changed =
                response(
                        mvc.perform(
                                patch(
                                                "/cruise-plans/{id}/excursions/{tour}/commercial",
                                                bid,
                                                fixedTour)
                                        .header("Authorization", "Bearer " + crm)
                                        .header("If-Match", b.path("revision").asLong())
                                        .contentType("application/json")
                                        .content(commercial)),
                        200);
        assertEquals(11, changed.path("maximumCapacity").asInt());
        assertEquals(0, changed.path("price").asInt());
        assertEquals(stopB.toString(), changed.path("cruiseStopId").asText());
        assertTrue(changed.path("minimumParticipants").isNull());
        assertEquals(original.path("startTime"), changed.path("startTime"));
        assertEquals(original.path("plannedReturnTime"), changed.path("plannedReturnTime"));
        assertEquals(original.path("supplierId"), changed.path("supplierId"));
        var current =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", aid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        mvc.perform(
                        patch("/cruise-plans/{id}/excursions/{tour}/commercial", aid, fixedTour)
                                .header("Authorization", "Bearer " + crm)
                                .header(
                                        "If-Match",
                                        current.path("windows").path("expectedRevision").asLong())
                                .contentType("application/json")
                                .content(commercial))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/cruise-plans/{id}/excursions/{tour}/commercial", bid, fixedTour)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", b.path("revision").asLong())
                                .contentType("application/json")
                                .content(commercial))
                .andExpect(status().isConflict());
        mvc.perform(
                        patch("/cruise-plans/{id}/excursions/{tour}/commercial", bid, fixedTour)
                                .header("Authorization", "Bearer " + ops)
                                .header("If-Match", 99)
                                .contentType("application/json")
                                .content(commercial))
                .andExpect(status().isForbidden());
        var rows =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}/excursions", bid)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals(11, rows.get(0).path("capacity").asInt());
    }

    @Test
    void crmEditsAndRemovesScopedTourAndServingWindowWithRevisionAndRoleChecks() throws Exception {
        String crm = token(com.casms.identity.enums.Role.CRUISE_MANAGER),
                ops = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
        var source = finalizedSource(crm);
        String body =
                "{\"cruiseName\":\"Scoped CRUD signoff\",\"sourceVersionId\":\""
                        + source.getVersionId()
                        + "\",\"expectedSourceVersionRevision\":"
                        + source.getRevision()
                        + ",\"itineraryMode\":\"CUSTOM\",\"departureDatetime\":\"2030-03-01T01:00:00Z\"}";
        var cruise =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        var id = java.util.UUID.fromString(cruise.path("cruiseId").asText());
        long revision = cruise.path("revision").asLong();
        var stop =
                jdbc.queryForObject(
                        "SELECT s.cruise_stop_id FROM \"CruiseStop\" s JOIN \"Itinerary\" i"
                            + " USING(itinerary_id) WHERE i.cruise_id=?",
                        java.util.UUID.class,
                        id);
        var supplier = new com.casms.activity.entity.Supplier();
        supplier.setSupplierName("Signoff provider");
        supplier.setContactName("QA");
        supplier.setStatus(com.casms.activity.enums.SupplierStatus.ACTIVE);
        supplier.setVerificationStatus(
                com.casms.activity.enums.SupplierVerificationStatus.VERIFIED);
        supplier = suppliers.save(supplier);
        String tour =
                "{\"cruiseStopId\":\""
                        + stop
                        + "\",\"supplierId\":\""
                        + supplier.getSupplierId()
                        + "\",\"excursionName\":\"Editable"
                        + " tour\",\"startTime\":\"2030-03-01T02:00:00Z\",\"plannedReturnTime\":\"2030-03-01T03:00:00Z\",\"meetingPoint\":\"Gate"
                        + " A\",\"maximumCapacity\":10,\"price\":0,\"currencyCode\":\"USD\"}";
        var saved =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/excursions", id)
                                        .header("Authorization", "Bearer " + crm)
                                        .header("If-Match", revision++)
                                        .contentType("application/json")
                                        .content(tour)),
                        200);
        String tourId = saved.path("shoreExcursionId").asText();
        var changed =
                response(
                        mvc.perform(
                                put("/cruise-plans/{id}/excursions/{tour}", id, tourId)
                                        .header("Authorization", "Bearer " + crm)
                                        .header("If-Match", revision++)
                                        .contentType("application/json")
                                        .content(tour.replace("Gate A", "Gate B"))),
                        200);
        assertEquals("Gate B", changed.path("meetingPoint").asText());
        assertEquals(
                "Gate B",
                response(
                                mvc.perform(
                                        get("/cruise-plans/{id}/excursions/{tour}", id, tourId)
                                                .header("Authorization", "Bearer " + crm)),
                                200)
                        .path("meetingPoint")
                        .asText());
        mvc.perform(
                        delete("/cruise-plans/{id}/excursions/{tour}", id, tourId)
                                .header("Authorization", "Bearer " + ops)
                                .header("If-Match", revision))
                .andExpect(status().isForbidden());
        response(
                mvc.perform(
                        delete("/cruise-plans/{id}/excursions/{tour}", id, tourId)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", revision++)),
                200);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"ShoreExcursion\" WHERE shore_excursion_id=?",
                        Integer.class,
                        java.util.UUID.fromString(tourId)));
        var service = new com.casms.servicepackage.entity.Service();
        service.setServiceCode("SIGN-" + java.util.UUID.randomUUID());
        service.setServiceName("Signoff scheduled service");
        service.setServiceType("DINING");
        service.setStatus(com.casms.servicepackage.enums.ServiceStatus.ACTIVE);
        service.setPrice(java.math.BigDecimal.ZERO);
        service.setCurrencyCode("USD");
        service.setRequiresSchedule(true);
        service = services.save(service);
        String window =
                "{\"serviceId\":\""
                        + service.getServiceId()
                        + "\",\"startsAt\":\"2030-03-01T02:00:00Z\",\"endsAt\":\"2030-03-01T03:00:00Z\",\"location\":\"Dining"
                        + " A\",\"capacity\":10}";
        var w =
                response(
                        mvc.perform(
                                post("/cruise-plans/{id}/serving-windows", id)
                                        .header("Authorization", "Bearer " + crm)
                                        .header("If-Match", revision++)
                                        .contentType("application/json")
                                        .content(window)),
                        200);
        String wid = w.path("id").asText();
        mvc.perform(
                        put("/cruise-plans/{id}/serving-windows/{window}", id, wid)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", revision - 1)
                                .contentType("application/json")
                                .content(window))
                .andExpect(status().isConflict());
        response(
                mvc.perform(
                        put("/cruise-plans/{id}/serving-windows/{window}", id, wid)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", revision++)
                                .contentType("application/json")
                                .content(window.replace("Dining A", "Dining B"))),
                200);
        assertEquals(
                "Dining B",
                response(
                                mvc.perform(
                                        get("/cruise-plans/{id}/serving-windows", id)
                                                .header("Authorization", "Bearer " + crm)),
                                200)
                        .get(0)
                        .path("location")
                        .asText());
        response(
                mvc.perform(
                        delete("/cruise-plans/{id}/serving-windows/{window}", id, wid)
                                .header("Authorization", "Bearer " + crm)
                                .header("If-Match", revision)),
                200);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"ServiceSchedule\" WHERE service_schedule_id=?",
                        Integer.class,
                        java.util.UUID.fromString(wid)));
    }

    private com.casms.trip.entity.TripScheduleVersion finalizedSource(String crm) throws Exception {
        return finalizedSource(crm, false);
    }

    @Test
    void pricingReviewUsesDedicatedOpsPermissionImmutableSuccessorsAndMakerChecker()
            throws Exception {
        String
                maker =
                        token(
                                com.casms.identity.enums.Role.CRUISE_MANAGER,
                                com.casms.identity.enums.Role.OPERATIONS_MANAGER),
                ops = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER),
                crm = token(com.casms.identity.enums.Role.CRUISE_MANAGER);
        var source = finalizedSource(maker);
        String body =
                "{\"cruiseName\":\"Pricing review fixture\",\"sourceVersionId\":\""
                        + source.getVersionId()
                        + "\",\"expectedSourceVersionRevision\":"
                        + source.getRevision()
                        + ",\"itineraryMode\":\"CUSTOM\",\"departureDatetime\":\"2028-03-01T01:00:00Z\"}";
        var cruise =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + maker)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        String id = cruise.path("cruiseId").asText();
        var input =
                mapper.readTree(
                        "{\"expectedRevision\":0,\"currency\":\"USD\",\"interiorBaseFare\":10,\"oceanViewBaseFare\":20,\"balconyBaseFare\":30,\"suiteBaseFare\":40,\"portFeePerPassenger\":0,\"taxRate\":0,\"depositPercentage\":0,\"finalPaymentDueDays\":0,\"fullPaymentCutoffDays\":0,\"cancellationPolicy\":\"Explicit"
                            + " test terms\"}");
        mvc.perform(
                        put("/cruise/{id}/pricing-policy", id)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content(input.toString()))
                .andExpect(status().isForbidden());
        var bad = ((com.fasterxml.jackson.databind.node.ObjectNode) input).deepCopy();
        bad.put("currency", "VND");
        mvc.perform(
                        put("/cruise/{id}/pricing-policy", id)
                                .header("Authorization", "Bearer " + maker)
                                .contentType("application/json")
                                .content(bad.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("CURRENCY_USD_REQUIRED"));
        var draft =
                response(
                        mvc.perform(
                                put("/cruise/{id}/pricing-policy", id)
                                        .header("Authorization", "Bearer " + maker)
                                        .contentType("application/json")
                                        .content(input.toString())),
                        200);
        assertEquals("DRAFT", draft.path("status").asText());
        assertEquals(0, draft.path("finalPaymentDueDays").asInt());
        assertEquals(1, draft.path("revision").asLong());
        var pending =
                response(
                        mvc.perform(
                                post("/cruise/{id}/pricing-policy/submit", id)
                                        .header("Authorization", "Bearer " + maker)
                                        .contentType("application/json")
                                        .content("{\"expectedRevision\":1}")),
                        200);
        assertEquals("PENDING_APPROVAL", pending.path("status").asText());
        assertFalse(pending.path("snapshotHash").asText().isBlank());
        for (String denied : new String[] {crm, maker})
            mvc.perform(
                            post("/cruise/{id}/pricing-policy/approve", id)
                                    .header("Authorization", "Bearer " + denied)
                                    .contentType("application/json")
                                    .content("{\"expectedRevision\":2}"))
                    .andExpect(status().isForbidden());
        mvc.perform(
                        post("/cruise/{id}/pricing-policy/return", id)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":2}"))
                .andExpect(status().isUnprocessableEntity());
        var returned =
                response(
                        mvc.perform(
                                post("/cruise/{id}/pricing-policy/return", id)
                                        .header("Authorization", "Bearer " + ops)
                                        .contentType("application/json")
                                        .content(
                                                "{\"expectedRevision\":2,\"reason\":\"Clarify"
                                                    + " cancellation terms\"}")),
                        200);
        assertEquals("RETURNED", returned.path("status").asText());
        assertEquals("Clarify cancellation terms", returned.path("decisionReason").asText());
        ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("expectedRevision", 3);
        var successor =
                response(
                        mvc.perform(
                                put("/cruise/{id}/pricing-policy", id)
                                        .header("Authorization", "Bearer " + maker)
                                        .contentType("application/json")
                                        .content(input.toString())),
                        200);
        assertEquals(2, successor.path("policyNumber").asInt());
        assertNotEquals(returned.path("pricingPolicyId"), successor.path("pricingPolicyId"));
        response(
                mvc.perform(
                        post("/cruise/{id}/pricing-policy/submit", id)
                                .header("Authorization", "Bearer " + maker)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":4}")),
                200);
        var approved =
                response(
                        mvc.perform(
                                post("/cruise/{id}/pricing-policy/approve", id)
                                        .header("Authorization", "Bearer " + ops)
                                        .contentType("application/json")
                                        .content("{\"expectedRevision\":5}")),
                        200);
        assertEquals("APPROVED", approved.path("status").asText());
        assertEquals(6, approved.path("revision").asLong());
        assertTrue(approved.path("reviewedBy").isTextual());
        String approvedId = approved.path("pricingPolicyId").asText(),
                approvedHash = approved.path("snapshotHash").asText();
        long currentRevision = 6;
        for (String field :
                java.util.List.of(
                        "interiorBaseFare",
                        "oceanViewBaseFare",
                        "balconyBaseFare",
                        "suiteBaseFare",
                        "portFeePerPassenger",
                        "taxRate",
                        "depositPercentage",
                        "finalPaymentDueDays",
                        "fullPaymentCutoffDays",
                        "cancellationPolicy")) {
            var edit = ((com.fasterxml.jackson.databind.node.ObjectNode) input).deepCopy();
            edit.put("expectedRevision", currentRevision);
            if (field.equals("cancellationPolicy")) edit.put(field, "Changed terms");
            else
                edit.put(
                        field,
                        field.equals("taxRate") || field.equals("depositPercentage") ? 0.2 : 2);
            var next =
                    response(
                            mvc.perform(
                                    put("/cruise/{id}/pricing-policy", id)
                                            .header("Authorization", "Bearer " + maker)
                                            .contentType("application/json")
                                            .content(edit.toString())),
                            200);
            assertEquals("DRAFT", next.path("status").asText());
            assertNotEquals(approvedId, next.path("pricingPolicyId").asText());
            currentRevision = next.path("revision").asLong();
            response(
                    mvc.perform(
                            post("/cruise/{id}/pricing-policy/submit", id)
                                    .header("Authorization", "Bearer " + maker)
                                    .contentType("application/json")
                                    .content("{\"expectedRevision\":" + currentRevision + "}")),
                    200);
            currentRevision++;
            if (field.equals("cancellationPolicy")) {
                String opsB = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER);
                final long raceRevision = currentRevision;
                var startGate = new java.util.concurrent.CountDownLatch(1);
                var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
                try {
                    var futures =
                            new java.util.ArrayList<
                                    java.util.concurrent.Future<
                                            org.springframework.test.web.servlet.MvcResult>>();
                    for (String reviewer : new String[] {ops, opsB})
                        futures.add(
                                pool.submit(
                                        () -> {
                                            startGate.await();
                                            return mvc.perform(
                                                            post(
                                                                            "/cruise/{id}/pricing-policy/approve",
                                                                            id)
                                                                    .header(
                                                                            "Authorization",
                                                                            "Bearer " + reviewer)
                                                                    .contentType("application/json")
                                                                    .content(
                                                                            "{\"expectedRevision\":"
                                                                                    + raceRevision
                                                                                    + "}"))
                                                    .andReturn();
                                        }));
                    startGate.countDown();
                    var statuses = new java.util.ArrayList<Integer>();
                    for (var f : futures) {
                        var result = f.get(30, java.util.concurrent.TimeUnit.SECONDS);
                        recordHttpResult(result);
                        statuses.add(result.getResponse().getStatus());
                    }
                    java.util.Collections.sort(statuses);
                    assertEquals(java.util.List.of(200, 409), statuses);
                } finally {
                    pool.shutdownNow();
                }
            } else
                response(
                        mvc.perform(
                                post("/cruise/{id}/pricing-policy/approve", id)
                                        .header("Authorization", "Bearer " + ops)
                                        .contentType("application/json")
                                        .content("{\"expectedRevision\":" + currentRevision + "}")),
                        200);
            currentRevision++;
        }
        var unchanged =
                jdbc.queryForMap(
                        "SELECT status,snapshot_hash,interior_base_fare,revision FROM"
                            + " \"CruisePricingPolicy\" WHERE pricing_policy_id=?",
                        java.util.UUID.fromString(approvedId));
        assertEquals("APPROVED", unchanged.get("status"));
        assertEquals(approvedHash, unchanged.get("snapshot_hash"));
        assertEquals(6L, ((Number) unchanged.get("revision")).longValue());
        var history =
                response(
                        mvc.perform(
                                get("/cruise/{id}/pricing-policy/history", id)
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        assertEquals(
                11,
                java.util.stream.StreamSupport.stream(history.spliterator(), false)
                        .filter(e -> "APPROVE".equals(e.path("action").asText()))
                        .count());
    }

    @Test
    void inlineSourceActivityStaysUnconfiguredUntilCrmSuppliesOperationalInputs() throws Exception {
        String crm = token(com.casms.identity.enums.Role.CRUISE_MANAGER);
        var source = finalizedSource(crm, true);
        String sourceHash = source.getContentHash();
        String body =
                "{\"cruiseName\":\"Unknown input fixture\",\"sourceVersionId\":\""
                        + source.getVersionId()
                        + "\",\"expectedSourceVersionRevision\":"
                        + source.getRevision()
                        + ",\"itineraryMode\":\"FIXED\",\"departureDatetime\":\"2028-03-01T01:00:00Z\"}";
        var cruise =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        String id = cruise.path("cruiseId").asText();
        var rows =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}/activities", id)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals(1, rows.size());
        var activity = rows.get(0);
        assertTrue(activity.path("capacity").isNull());
        assertTrue(activity.path("price").isNull());
        assertTrue(activity.path("currencyCode").isNull());
        assertEquals(
                "YES",
                jdbc.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns WHERE"
                            + " table_name='OnboardActivity' AND column_name='capacity'",
                        String.class));
        var plan =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", id)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertTrue(
                plan.path("planBlockers").toString().contains("ACTIVITY_CONFIGURATION_REQUIRED"));
        mvc.perform(
                        post("/cruise-plans/{id}/submit", id)
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content("{\"expectedRevision\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("PLAN_INCOMPLETE"));
        var configured = mapper.createObjectNode();
        configured.put("cruiseId", id);
        configured.put("activityName", activity.path("name").asText());
        configured.set("description", activity.path("description"));
        configured.set("location", activity.path("location"));
        configured.set("startTime", activity.path("startTime"));
        configured.set("endTime", activity.path("endTime"));
        configured.set("deckId", activity.path("deckId"));
        configured.set("cabinId", activity.path("cabinId"));
        configured.put("capacity", 12);
        configured.put("price", 0);
        configured.put("currencyCode", "USD");
        var saved =
                response(
                        mvc.perform(
                                put(
                                                "/cruise-plans/{id}/activities/{activity}",
                                                id,
                                                activity.path("id").asText())
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "If-Match",
                                                plan.path("windows")
                                                        .path("expectedRevision")
                                                        .asLong())
                                        .contentType("application/json")
                                        .content(mapper.writeValueAsString(configured))),
                        200);
        assertEquals(12, saved.path("capacity").asInt());
        assertEquals(0, saved.path("price").asInt());
        assertEquals("USD", saved.path("currencyCode").asText());
        var after =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}", id)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertFalse(
                after.path("planBlockers").toString().contains("ACTIVITY_CONFIGURATION_REQUIRED"));
        assertEquals(
                sourceHash,
                versions.findById(source.getVersionId()).orElseThrow().getContentHash());
    }

    @Test
    void repeatedSourceOccurrencesWithSameTitleStaySeparateAndRetryDoesNotDuplicate()
            throws Exception {
        String crm = token(com.casms.identity.enums.Role.CRUISE_MANAGER);
        var source = finalizedSource(crm, true, true);
        String sourceHash = source.getContentHash();
        String body =
                "{\"cruiseName\":\"Repeated occurrence proof\",\"sourceVersionId\":\""
                        + source.getVersionId()
                        + "\",\"expectedSourceVersionRevision\":"
                        + source.getRevision()
                        + ",\"itineraryMode\":\"CUSTOM\",\"departureDatetime\":\"2028-03-01T01:00:00Z\"}";
        String key = java.util.UUID.randomUUID().toString();
        var created =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header("Idempotency-Key", key)
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        String id = created.path("cruiseId").asText();
        var rows =
                response(
                        mvc.perform(
                                get("/cruise-plans/{id}/activities", id)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        assertEquals(2, rows.size());
        assertEquals(rows.get(0).path("name"), rows.get(1).path("name"));
        assertNotEquals(rows.get(0).path("id"), rows.get(1).path("id"));
        assertNotEquals(rows.get(0).path("startTime"), rows.get(1).path("startTime"));
        var retry =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header("Idempotency-Key", key)
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        assertEquals(id, retry.path("cruiseId").asText());
        mvc.perform(
                        post("/cruise/{id}/sync-operational-programs", id)
                                .header("Authorization", "Bearer " + crm))
                .andExpect(status().isConflict());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"OnboardActivity\" WHERE cruise_id=?",
                        Integer.class,
                        java.util.UUID.fromString(id)));
        assertEquals(
                sourceHash,
                versions.findById(source.getVersionId()).orElseThrow().getContentHash());
    }

    private com.casms.trip.entity.TripScheduleVersion finalizedSource(
            String crm, boolean inlineActivity) throws Exception {
        return finalizedSource(crm, inlineActivity, false);
    }

    @Test
    void crmCoordinatorSubmitsFiveSectionsOpsReviewsAndScopeReassignmentIsAudited()
            throws Exception {
        String crm = token(com.casms.identity.enums.Role.CRUISE_MANAGER),
                otherCrm = token(com.casms.identity.enums.Role.CRUISE_MANAGER),
                ops = token(com.casms.identity.enums.Role.OPERATIONS_MANAGER),
                ship = token(com.casms.identity.enums.Role.SHIP_MANAGER);
        var source = finalizedSource(crm);
        String body =
                "{\"cruiseName\":\"Coordinator scope proof\",\"sourceVersionId\":\""
                        + source.getVersionId()
                        + "\",\"expectedSourceVersionRevision\":"
                        + source.getRevision()
                        + ",\"itineraryMode\":\"CUSTOM\",\"departureDatetime\":\"2029-03-01T01:00:00Z\"}";
        var cruise =
                response(
                        mvc.perform(
                                post("/cruise")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(body)),
                        201);
        String id = cruise.path("cruiseId").asText();
        var dashboard =
                response(
                        mvc.perform(
                                get("/cruise/{id}/preparation", id)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        String originalCoordinator = dashboard.path("coordinatorUserId").asText();
        assertFalse(originalCoordinator.isBlank());
        assertEquals(5, dashboard.path("sections").size());
        for (var section : dashboard.path("sections")) {
            String code = section.path("sectionCode").asText();
            assertTrue(section.path("allowedActions").toString().contains("SUBMIT"));
            assertEquals("CRUISE_MANAGER", section.path("submitterRole").asText());
            assertEquals(
                    code.equals("TECHNICAL_NAUTICAL")
                            ? "SHIP_MANAGER"
                            : code.equals("SAFETY_CREW") ? "OPERATIONS_MANAGER" : "CRUISE_MANAGER",
                    section.path("ownerRole").asText());
            String evidence =
                    "{\"expectedRevision\":"
                            + section.path("revision").asLong()
                            + ",\"evidence\":\"CRM compilation; professional checks remain"
                            + " server-derived\"}";
            String forged =
                    evidence.substring(0, evidence.length() - 1)
                            + ",\"checkItems\":[{\"ready\":true,\"required\":true}],\"status\":\"CONFIRMED\"}";
            mvc.perform(
                            post("/cruise/{id}/preparation/{code}/submit", id, code)
                                    .header("Authorization", "Bearer " + crm)
                                    .contentType("application/json")
                                    .content(forged))
                    .andExpect(status().isBadRequest());
            mvc.perform(
                            post("/cruise/{id}/preparation/{code}/submit", id, code)
                                    .header("Authorization", "Bearer " + ops)
                                    .contentType("application/json")
                                    .content(evidence))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/cruise/{id}/preparation/{code}/submit", id, code)
                                    .header("Authorization", "Bearer " + ship)
                                    .contentType("application/json")
                                    .content(evidence))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/cruise/{id}/preparation/{code}/submit", id, code)
                                    .header("Authorization", "Bearer " + otherCrm)
                                    .contentType("application/json")
                                    .content(evidence))
                    .andExpect(status().isForbidden());
            var submitted =
                    response(
                            mvc.perform(
                                    post("/cruise/{id}/preparation/{code}/submit", id, code)
                                            .header("Authorization", "Bearer " + crm)
                                            .contentType("application/json")
                                            .content(evidence)),
                            200);
            assertEquals("SUBMITTED", submitted.path("status").asText());
            assertTrue(submitted.path("checkItems").toString().contains("\"ready\":false"));
            mvc.perform(
                            post("/cruise/{id}/preparation/{code}/submit", id, code)
                                    .header("Authorization", "Bearer " + crm)
                                    .contentType("application/json")
                                    .content(evidence))
                    .andExpect(status().isConflict());
            String review = "{\"expectedRevision\":" + submitted.path("revision").asLong() + "}";
            mvc.perform(
                            post("/cruise/{id}/preparation/{code}/confirm", id, code)
                                    .header("Authorization", "Bearer " + crm)
                                    .contentType("application/json")
                                    .content(review))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/cruise/{id}/preparation/{code}/confirm", id, code)
                                    .header("Authorization", "Bearer " + ops)
                                    .contentType("application/json")
                                    .content(review))
                    .andExpect(status().isUnprocessableEntity());
            var returned =
                    response(
                            mvc.perform(
                                    post("/cruise/{id}/preparation/{code}/return", id, code)
                                            .header("Authorization", "Bearer " + ops)
                                            .contentType("application/json")
                                            .content(
                                                    "{\"expectedRevision\":"
                                                            + submitted.path("revision").asLong()
                                                            + ",\"reason\":\"Required professional"
                                                            + " conditions missing\"}")),
                            200);
            assertEquals("RETURNED", returned.path("status").asText());
            assertFalse(returned.path("returnedByUserId").isNull());
            assertFalse(returned.path("returnedAt").isNull());
        }
        assertEquals(
                5,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePreparationEvent\" WHERE cruise_id=? AND"
                            + " action='SUBMIT'",
                        Integer.class,
                        java.util.UUID.fromString(id)));
        assertEquals(
                5,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"CruisePreparationSection\" WHERE cruise_id=?",
                        Integer.class,
                        java.util.UUID.fromString(id)));
        var otherView =
                response(
                        mvc.perform(
                                get("/cruise/{id}/preparation", id)
                                        .header("Authorization", "Bearer " + otherCrm)),
                        200);
        for (var section : otherView.path("sections"))
            assertFalse(section.path("allowedActions").toString().contains("SUBMIT"));
        var candidates =
                response(
                        mvc.perform(
                                get("/cruise/{id}/preparation/coordinator-candidates", id)
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        String newId =
                jwt.extractUsername(
                        otherCrm); // token subject is the fixture username, not a trusted request
                                   // actor.
        var replacement = users.findByUsername(newId).orElseThrow();
        assertTrue(candidates.toString().contains(replacement.getUserId().toString()));
        String assignment =
                "{\"coordinatorUserId\":\""
                        + replacement.getUserId()
                        + "\",\"expectedRevision\":"
                        + otherView.path("cruiseRevision").asLong()
                        + "}";
        mvc.perform(
                        put("/cruise/{id}/preparation/coordinator", id)
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content(assignment))
                .andExpect(status().isForbidden());
        var assigned =
                response(
                        mvc.perform(
                                put("/cruise/{id}/preparation/coordinator", id)
                                        .header("Authorization", "Bearer " + ops)
                                        .contentType("application/json")
                                        .content(assignment)),
                        200);
        assertEquals(
                replacement.getUserId().toString(), assigned.path("coordinatorUserId").asText());
        mvc.perform(
                        put("/cruise/{id}/preparation/coordinator", id)
                                .header("Authorization", "Bearer " + ops)
                                .contentType("application/json")
                                .content(assignment))
                .andExpect(status().isConflict());
        var prior =
                response(
                        mvc.perform(
                                get("/cruise/{id}/preparation", id)
                                        .header("Authorization", "Bearer " + crm)),
                        200);
        for (var section : prior.path("sections"))
            assertFalse(section.path("allowedActions").toString().contains("SUBMIT"));
        var replacementView =
                response(
                        mvc.perform(
                                get("/cruise/{id}/preparation", id)
                                        .header("Authorization", "Bearer " + otherCrm)),
                        200);
        for (var section : replacementView.path("sections"))
            assertTrue(section.path("allowedActions").toString().contains("SUBMIT"));
        var events =
                response(
                        mvc.perform(
                                get("/cruise/{id}/preparation/events", id)
                                        .header("Authorization", "Bearer " + ops)),
                        200);
        assertEquals(11, events.size());
        var proof = mapper.createObjectNode();
        proof.put("cruiseId", id);
        proof.put("originalCoordinatorId", originalCoordinator);
        proof.set("afterReassignment", replacementView);
        proof.set("events", events);
        Files.writeString(
                Path.of(
                        "docs/mainflow-alignment/final-audit/preparation-coordinator-readback.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
    }

    private com.casms.trip.entity.TripScheduleVersion finalizedSource(
            String crm, boolean inlineActivity, boolean repeated) throws Exception {
        var p = new com.casms.ship.entity.Port();
        p.setPortCode("V4-" + java.util.UUID.randomUUID());
        p.setPortName("Isolated copy port");
        p.setCountryCode("VN");
        p.setDomesticRegion("BAC_BO");
        p.setTimeZoneId("Asia/Ho_Chi_Minh");
        p.setStatus(com.casms.ship.enums.PortStatus.ACTIVE);
        p = ports.save(p);
        var trip =
                response(
                        mvc.perform(
                                post("/trips")
                                        .header("Authorization", "Bearer " + crm)
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(
                                                "{\"name\":\"Independent copy"
                                                    + " fixture\",\"durationGroup\":\"SHORT\",\"plannedScope\":\"DOMESTIC\",\"domesticRegions\":[\"BAC_BO\"]}")),
                        201);
        var v =
                versions.findByTrip_TripIdOrderByVersionNumberAsc(
                                java.util.UUID.fromString(trip.path("tripId").asText()))
                        .getFirst();
        String content =
                """
{"expectedRevision":%s,"days":[{"dayNumber":1,"dayType":"PORT_DAY","shipTimeZoneId":"Asia/Ho_Chi_Minh"}],"stops":[{"clientKey":"visit-copy","portId":"%s","stopOrder":1,"arrivalDayNumber":1,"departureDayNumber":1,"plannedArrivalTime":"08:00:00","plannedDepartureTime":"18:00:00","allAboardDayNumber":1,"allAboardTime":"17:00:00","isEmbarkation":true,"isDisembarkation":true}],"entries":[]}
"""
                        .formatted(v.getRevision(), p.getPortId());
        if (inlineActivity) {
            var document = mapper.readTree(content);
            ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("entries"))
                    .add(
                            mapper.readTree(
                                    "{\"clientKey\":\"inline-source\",\"dayNumber\":1,\"kind\":\"ONBOARD_ACTIVITY\",\"title\":\"Inline"
                                        + " source"
                                        + " activity\",\"startDayNumber\":1,\"endDayNumber\":1,\"plannedStartTime\":\"10:00:00\",\"plannedEndTime\":\"11:00:00\",\"locationText\":\"Lounge\"}"));
            if (repeated) {
                var first =
                        (com.fasterxml.jackson.databind.node.ObjectNode)
                                document.path("entries").get(0);
                first.put("clientKey", "occurrence!");
                var second = first.deepCopy();
                second.put("clientKey", "occurrence?");
                second.put("plannedStartTime", "11:00:00");
                second.put("plannedEndTime", "12:00:00");
                ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("entries"))
                        .add(second);
            }
            content = mapper.writeValueAsString(document);
        }
        var saved =
                response(
                        mvc.perform(
                                put("/trip-schedule-versions/{id}/content", v.getVersionId())
                                        .header("Authorization", "Bearer " + crm)
                                        .contentType("application/json")
                                        .content(content)),
                        200);
        response(
                mvc.perform(
                        post("/trip-schedule-versions/{id}/finalize", v.getVersionId())
                                .header("Authorization", "Bearer " + crm)
                                .contentType("application/json")
                                .content(
                                        "{\"expectedRevision\":"
                                                + saved.path("revision").asLong()
                                                + "}")),
                200);
        return versions.findById(v.getVersionId()).orElseThrow();
    }
}
