package com.casms.servicepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.servicepackage.entity.*;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.enums.*;
import com.casms.servicepackage.repository.*;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.casms.ship.service.CruisePackageService;
import com.casms.ship.dto.request.CruiseTourPackageRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@WithMockUser(authorities={"ROLE_CRUISE_MANAGER","CRUISE_UPDATE_DRAFT","SERVICE_CREATE"})
class CruiseServiceRulesPostgresIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    @Autowired ShipRepository ships; @Autowired DeckRepository decks; @Autowired ServiceRepository services;
    @Autowired PortRepository ports; @Autowired CruiseRepository cruises; @Autowired TourPackageRepository packages;
    @Autowired TourPackageServiceRepository components; @Autowired DeckServiceAssignmentRepository assignments;
    @Autowired CruiseTourPackageRepository cruisePackages; @Autowired CruisePackageService cruisePackageService;
    @Autowired ServicepackageService catalog; @Autowired ShipServiceAvailability availability;
    Ship ship; Deck deck; Service service; Cruise cruise; TourPackage pkg;
    String code(){return UUID.randomUUID().toString().substring(0,8);}
    @BeforeEach void fixture(){
        ship=new Ship();ship.setShipCode("R-"+code());ship.setShipName("Service rules");ship.setStatus(ShipStatus.READY);ship.setTotalCapacity(100);ship.setTotalDecks(1);ship=ships.saveAndFlush(ship);
        deck=new Deck();deck.setShip(ship);deck.setDeckName("Deck 1");deck.setDeckNumber(1);deck=decks.saveAndFlush(deck);
        service=new Service();service.setServiceCode("R-"+code());service.setServiceName("Restaurant");service.setServiceType("DINING");service.setRequiresSchedule(true);service.setStatus(ServiceStatus.ACTIVE);service=services.saveAndFlush(service);
        Port port=new Port();port.setPortCode("R-"+code());port.setPortName("Test port");port.setCountryCode("VN");port.setTimeZoneId("Asia/Ho_Chi_Minh");port.setStatus(PortStatus.ACTIVE);port=ports.saveAndFlush(port);
        cruise=new Cruise();cruise.setCruiseCode("R-"+code());cruise.setCruiseName("Test cruise");cruise.setCruiseType(CruiseCruiseType.MULTI_DAYS);cruise.setShip(ship);cruise.setDeparturePort(port);cruise.setReturnPort(port);cruise.setDepartureDatetime(Instant.now().plusSeconds(86400));cruise.setReturnDatetime(cruise.getDepartureDatetime().plusSeconds(86400));cruise.setStatus(CruiseStatus.DRAFT);cruise=cruises.saveAndFlush(cruise);
        pkg=packageRow();
        var link=new TourPackageService();link.setTourPackage(pkg);link.setService(service);link.setBenefitType(BenefitType.INCLUDED);components.saveAndFlush(link);
    }
    TourPackage packageRow(){var p=new TourPackage();p.setPackageCode("R-"+code());p.setPackageName("Test package");p.setPackageType(TourPackageType.BASIC);p.setPrice(BigDecimal.TEN);p.setCurrencyCode("USD");p.setStatus(TourPackageStatus.ACTIVE);return packages.saveAndFlush(p);}
    void install(){var a=new DeckServiceAssignment();a.setDeck(deck);a.setService(service);assignments.saveAndFlush(a);}
    CruiseTourPackageRequest request(){var r=new CruiseTourPackageRequest();r.setTourPackageId(pkg.getTourPackageId());return r;}
    String schedule() throws Exception {return mapper.writeValueAsString(Map.of("serviceId",service.getServiceId(),"cruiseId",cruise.getCruiseId(),"startsAt",cruise.getDepartureDatetime().plusSeconds(60),"endsAt",cruise.getDepartureDatetime().plusSeconds(3600),"location","Deck 1","capacity",10));}
    @Test void cruiseManagerCannotCreateDefinitionEvenWithCreateAuthority() throws Exception {
        mvc.perform(post("/servicepackage/services").contentType(MediaType.APPLICATION_JSON).content("{\"serviceName\":\"New\",\"serviceType\":\"DINING\",\"price\":10,\"currencyCode\":\"USD\"}" )).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(authorities={"ROLE_ACTIVITY_SERVICE_MANAGER","SERVICE_CREATE"})
    void activityServiceManagerCanCreateDefinition() throws Exception {
        mvc.perform(post("/servicepackage/services").contentType(MediaType.APPLICATION_JSON).content("{\"serviceName\":\"New\",\"serviceType\":\"DINING\",\"price\":10,\"currencyCode\":\"USD\"}" )).andExpect(status().isCreated());
    }
    @Test @WithMockUser(authorities={"ROLE_ACTIVITY_SERVICE_MANAGER","SERVICE_CREATE"})
    void definitionCreatorCannotAttachServiceToCruise() throws Exception {
        mvc.perform(post("/servicepackage/schedules").contentType(MediaType.APPLICATION_JSON).content(schedule())).andExpect(status().isForbidden());
    }
    @Test void cruiseScheduleRejectsServiceMissingOnShip() throws Exception {
        mvc.perform(post("/servicepackage/schedules").contentType(MediaType.APPLICATION_JSON).content(schedule())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SERVICE_NOT_AVAILABLE_ON_SHIP")).andExpect(jsonPath("$.details.missingServices[0].serviceName").value("Restaurant"));
    }
    @Test void cruiseManagerCanScheduleAnExistingInstalledService() throws Exception {
        install();mvc.perform(post("/servicepackage/schedules").contentType(MediaType.APPLICATION_JSON).content(schedule())).andExpect(status().isCreated());
    }
    @Test void attachingPackageFailsAtomicallyWithMissingServiceDetails() throws Exception {
        mvc.perform(post("/cruise/{id}/packages",cruise.getCruiseId()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("tourPackageId",pkg.getTourPackageId())))).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SERVICE_NOT_AVAILABLE_ON_SHIP"));
        assertFalse(cruisePackages.existsByCruise_CruiseIdAndTourPackage_TourPackageId(cruise.getCruiseId(),pkg.getTourPackageId()));
    }
    @Test void attachingPackageSucceedsOnlyOnItsActualShip(){install();assertNotNull(cruisePackageService.assignPackage(cruise.getCruiseId(),request()));}
    @Test void anotherShipsServiceDoesNotSatisfyPackage(){
        Ship other=new Ship();other.setShipCode("R-"+code());other.setShipName("Other ship");other.setStatus(ShipStatus.READY);other.setTotalCapacity(100);other.setTotalDecks(1);other=ships.saveAndFlush(other);
        deck.setShip(other);decks.saveAndFlush(deck);install();
        assertEquals("SERVICE_NOT_AVAILABLE_ON_SHIP",assertThrows(BusinessException.class,()->cruisePackageService.assignPackage(cruise.getCruiseId(),request())).getCode());
    }
    @Test void inheritedParentServiceCannotBypassAvailability(){
        var child=packageRow();child.setParentPackage(pkg);packages.saveAndFlush(child);
        assertEquals("SERVICE_NOT_AVAILABLE_ON_SHIP",assertThrows(BusinessException.class,()->availability.requirePackage(cruise.getCruiseId(),child.getTourPackageId())).getCode());
    }
    @Test void cruiseOwnedDraftCannotAcquireUnavailableService(){
        var draft=packageRow();draft.setStatus(TourPackageStatus.DRAFT);draft.setOwnerCruiseId(cruise.getCruiseId());packages.saveAndFlush(draft);
        assertThrows(BusinessException.class,()->catalog.addPackageService(draft.getTourPackageId(),service.getServiceId(),BenefitType.INCLUDED));
        assertEquals(0,components.countByTourPackage_TourPackageId(draft.getTourPackageId()));
    }
}
