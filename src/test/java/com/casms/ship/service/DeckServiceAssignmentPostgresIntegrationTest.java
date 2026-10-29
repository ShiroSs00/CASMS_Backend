package com.casms.ship.service;

import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.enums.ServiceStatus;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual PostgreSQL, MVC, security and repositories; each fixture rolls back after its test. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
@WithMockUser(authorities = {"DECK_READ", "SERVICE_READ", "SERVICE_UPDATE"})
class DeckServiceAssignmentPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ShipRepository ships;
    @Autowired DeckRepository decks;
    @Autowired ServiceRepository services;
    @Autowired DeckServiceAssignmentRepository assignments;
    Ship ship, other;
    Deck deck;
    Service service;

    @BeforeEach void setup() {
        ship = ship(); other = ship();
        deck = new Deck(); deck.setShip(ship); deck.setDeckNumber(1); deck.setDeckName("Physical deck");
        deck.setPriceMultiplier(BigDecimal.ONE); deck = decks.saveAndFlush(deck);
        service = new Service(); service.setServiceCode("C90-"+UUID.randomUUID().toString().substring(0,12));
        service.setServiceName("Manual service"); service.setServiceType("DINING");
        service.setStatus(ServiceStatus.INACTIVE); service = services.saveAndFlush(service);
    }
    private Ship ship() {
        Ship s=new Ship();s.setShipCode("C90-"+UUID.randomUUID().toString().substring(0,12));
        s.setShipName("CASMS 90 test");s.setTotalCapacity(10);s.setTotalDecks(1);s.setStatus(ShipStatus.ACTIVE);
        return ships.saveAndFlush(s);
    }
    private String path(String verb, UUID deckId, UUID serviceId) {
        return "/decks/"+deckId+"/services"+(verb.equals("GET")?"":"/"+serviceId);
    }
    private MockHttpServletRequestBuilder scoped(String verb, UUID deckId, UUID serviceId, UUID shipId) {
        return request(HttpMethod.valueOf(verb),path(verb,deckId,serviceId)).param("shipId",shipId.toString());
    }

    @Test void lifecycleAndCatalogueUpdatesReuseOneRelationship() throws Exception {
        var first=mvc.perform(scoped("PUT",deck.getDeckId(),service.getServiceId(),ship.getShipId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.serviceStatus").value("INACTIVE")).andReturn();
        String id=mapper.readTree(first.getResponse().getContentAsByteArray()).path("data").path("assignmentId").asText();
        service.setServiceName("Updated catalogue name"); services.saveAndFlush(service);
        mvc.perform(scoped("PUT",deck.getDeckId(),service.getServiceId(),ship.getShipId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assignmentId").value(id))
                .andExpect(jsonPath("$.data.serviceName").value("Updated catalogue name"));
        mvc.perform(scoped("GET",deck.getDeckId(),service.getServiceId(),ship.getShipId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get(path("GET",deck.getDeckId(),null))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assignmentId").value(id));
        for(int i=0;i<2;i++)mvc.perform(scoped("DELETE",deck.getDeckId(),service.getServiceId(),ship.getShipId()))
                .andExpect(status().isNoContent());
        assertTrue(services.existsById(service.getServiceId()));
        assertTrue(assignments.findByDeckDeckIdAndServiceServiceId(deck.getDeckId(),service.getServiceId()).isEmpty());
        assertEquals(1,decks.findById(deck.getDeckId()).orElseThrow().getDeckNumber());
    }

    @ParameterizedTest @ValueSource(strings={"GET","PUT","DELETE"})
    void rejectsDeckOutsideRequestedShipWithoutChangingRelationship(String verb) throws Exception {
        var link=new com.casms.ship.entity.DeckServiceAssignment();link.setDeck(deck);link.setService(service);
        UUID id=assignments.saveAndFlush(link).getAssignmentId();
        mvc.perform(scoped(verb,deck.getDeckId(),service.getServiceId(),other.getShipId())).andExpect(status().isNotFound());
        assertEquals(id,assignments.findByDeckDeckIdAndServiceServiceId(deck.getDeckId(),service.getServiceId()).orElseThrow().getAssignmentId());
    }

    @ParameterizedTest @ValueSource(strings={"GET","PUT","DELETE"})
    void rejectsMissingShipOrDeck(String verb) throws Exception {
        mvc.perform(scoped(verb,deck.getDeckId(),service.getServiceId(),UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(scoped(verb,UUID.randomUUID(),service.getServiceId(),ship.getShipId())).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings={"PUT","DELETE"})
    void rejectsMissingService(String verb) throws Exception {
        mvc.perform(scoped(verb,deck.getDeckId(),UUID.randomUUID(),ship.getShipId())).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings={"GET","PUT","DELETE"})
    void rejectsMalformedUuid(String verb) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(verb),path(verb,deck.getDeckId(),service.getServiceId()))
                .param("shipId","not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(request(HttpMethod.valueOf(verb),"/decks/not-a-uuid/services"+(verb.equals("GET")?"":"/"+service.getServiceId())))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings={"GET","PUT","DELETE"})
    @WithMockUser(authorities={"DECK_READ"})
    void deckReadAloneDoesNotAllowServiceAccess(String verb) throws Exception {
        mvc.perform(scoped(verb,deck.getDeckId(),service.getServiceId(),ship.getShipId())).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings={"GET","PUT","DELETE"})
    @WithMockUser(authorities={"SERVICE_READ","SERVICE_UPDATE"})
    void servicePermissionsAloneDoNotAllowDeckAccess(String verb) throws Exception {
        mvc.perform(scoped(verb,deck.getDeckId(),service.getServiceId(),ship.getShipId())).andExpect(status().isForbidden());
    }

    @Test void oneServiceCanBeAssignedToDifferentDecksWithoutChangingItsDefinition() throws Exception {
        Deck second=new Deck();second.setShip(other);second.setDeckNumber(1);second.setDeckName("Other deck");
        second.setPriceMultiplier(BigDecimal.ONE);second=decks.saveAndFlush(second);
        mvc.perform(scoped("PUT",deck.getDeckId(),service.getServiceId(),ship.getShipId())).andExpect(status().isOk());
        mvc.perform(scoped("PUT",second.getDeckId(),service.getServiceId(),other.getShipId())).andExpect(status().isOk());
        assertEquals(1,assignments.findByDeckDeckIdOrderByCreatedAtAscAssignmentIdAsc(deck.getDeckId()).size());
        assertEquals(1,assignments.findByDeckDeckIdOrderByCreatedAtAscAssignmentIdAsc(second.getDeckId()).size());
        assertNull(services.findById(service.getServiceId()).orElseThrow().getPrice());
    }
}
