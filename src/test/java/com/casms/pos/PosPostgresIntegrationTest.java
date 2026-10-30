package com.casms.pos;

import com.casms.identity.entity.*;
import com.casms.identity.enums.*;
import com.casms.identity.repository.*;
import com.casms.security.jwt.JwtService;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.casms.booking.entity.*;
import com.casms.booking.enums.*;
import com.casms.booking.repository.*;
import com.casms.servicepackage.enums.ServiceStatus;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT, method authorization, Flyway, JDBC/JPA transactions and PostgreSQL; no mocks. */
@SpringBootTest @AutoConfigureMockMvc
class PosPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired UserRepository users;
    @Autowired PassengerRepository passengers;
    @Autowired ShipRepository ships;
    @Autowired DeckRepository decks;
    @Autowired ShipModelRepository models;
    @Autowired ShipModelObjectRepository objects;
    @Autowired PortRepository ports;
    @Autowired CruiseRepository cruises;
    @Autowired BookingRepository bookings;
    @Autowired BookingPassengerRepository bookingPassengers;
    @Autowired ServiceRepository catalog;
    @Autowired DeckServiceAssignmentRepository deckServices;
    @Autowired PaymentRepository payments;
    @Autowired JwtService jwt;
    UUID ship,deck,model,area,port,cruise,booking,bp,bp2,service,account,terminal;
    String manager,staff,buyer,secret,session;
    final List<UUID> userIds=new ArrayList<>();
    final List<UUID> passengerIds=new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        new TransactionTemplate(tm).executeWithoutResult(status->{
            User owner=user(Role.PASSENGER);User owner2=user(Role.PASSENGER);buyer=token(owner);
            Passenger p=new Passenger();p.setUser(owner);p.setFullName("POS buyer");passengers.saveAndFlush(p);passengerIds.add(p.getPassengerId());
            Passenger p2=new Passenger();p2.setUser(owner2);p2.setFullName("POS group buyer");passengers.saveAndFlush(p2);passengerIds.add(p2.getPassengerId());
            Ship s=new Ship();s.setShipCode(code());s.setShipName("POS test ship");s.setStatus(ShipStatus.ACTIVE);s.setTotalCapacity(100);s.setTotalDecks(1);ships.saveAndFlush(s);ship=s.getShipId();
            Deck d=new Deck();d.setShip(s);d.setDeckNumber(1);d.setDeckName("Spa deck");d.setPriceMultiplier(BigDecimal.ONE);decks.saveAndFlush(d);deck=d.getDeckId();
            ShipModel m=new ShipModel();m.setShip(s);m.setVersion(code());m.setFileUri("/pos-test.glb");m.setFileFormat("glb");m.setStatus(ShipModelStatus.READY);models.saveAndFlush(m);model=m.getShipModelId();
            ShipModelObject o=new ShipModelObject();o.setShipModel(m);o.setDeck(d);o.setNodeKey("spa");o.setDisplayName("Spa");o.setObjectType(ShipModelObjectType.FACILITY);objects.saveAndFlush(o);area=o.getShipModelObjectId();
            var sv=new com.casms.servicepackage.entity.Service();sv.setServiceCode(code());sv.setServiceName("Spa service");sv.setServiceType("SPA");sv.setStatus(ServiceStatus.ACTIVE);sv.setPrice(new BigDecimal("10.0000"));sv.setCurrencyCode("USD");sv.setCapacity(100);catalog.saveAndFlush(sv);service=sv.getServiceId();
            DeckServiceAssignment da=new DeckServiceAssignment();da.setDeck(d);da.setService(sv);deckServices.saveAndFlush(da);
            Port pt=new Port();pt.setPortCode(code());pt.setPortName("POS port");pt.setCountryCode("US");pt.setTimeZoneId("UTC");pt.setStatus(PortStatus.ACTIVE);ports.saveAndFlush(pt);port=pt.getPortId();
            Cruise c=new Cruise();c.setCruiseCode(code());c.setCruiseName("POS cruise");c.setShip(s);c.setCruiseType(CruiseCruiseType.ONE_DAY);c.setStatus(CruiseStatus.IN_PROGRESS);
            c.setDeparturePort(pt);c.setReturnPort(pt);c.setDepartureDatetime(Instant.now().minusSeconds(3600));c.setReturnDatetime(Instant.now().plusSeconds(86400));c=cruises.saveAndFlush(c);cruise=c.getCruiseId();
            Booking b=new Booking();b.setCruise(c);b.setBookedByUser(owner);b.setBookingReference(code());b.setBookingStatus(BookingStatus.Confirmed);b.setAgreedFareAmount(BigDecimal.ZERO);b.setRequiredDepositAmount(BigDecimal.ZERO);b.setCurrency("USD");bookings.saveAndFlush(b);booking=b.getBookingId();
            BookingPassenger a=new BookingPassenger();a.setBooking(b);a.setPassenger(p);a.setParticipationStatus(BookingPassengerParticipationStatus.Active);bookingPassengers.saveAndFlush(a);bp=a.getBookingPassengerId();
            BookingPassenger a2=new BookingPassenger();a2.setBooking(b);a2.setPassenger(p2);a2.setParticipationStatus(BookingPassengerParticipationStatus.Active);bookingPassengers.saveAndFlush(a2);bp2=a2.getBookingPassengerId();
            manager=token(user(Role.OPERATIONS_MANAGER));staff=token(user(Role.ONBOARD_STAFF));
        });
        allocate("100",List.of(Map.of("bookingPassengerId",bp,"amount",new BigDecimal("30"))));
        var configured=perform(put("/ships/{ship}/areas/{area}/services/{service}",ship,area,service),manager,
                Map.of("permissions",List.of(PosPermission.values()),"enabled",true),200);
        assertEquals(service.toString(),configured.path("serviceId").asText());
        var device=perform(post("/ships/{ship}/pos-terminals",ship),manager,
                Map.of("areaId",area,"code",code(),"name","Spa POS","permissions",List.of(PosPermission.values()),"enabled",true),201);
        terminal=UUID.fromString(device.path("terminal").path("terminalId").asText());secret=device.path("deviceSecret").asText();
        session=login(staff,secret);
    }
    @Test void recordsUsageAtServerPriceReducesVirtualAllowanceAndCreatesNoPayment() throws Exception {
        long paymentCount=payments.count();JsonNode sale=sell(bp,2,"one",201);
        assertEquals(0,new BigDecimal("20").compareTo(sale.path("amount").decimalValue()));
        JsonNode budget=statement(bp).path("budget");
        assertEquals(80,budget.path("accountRemaining").asInt());assertEquals(10,budget.path("remainingAmount").asInt());
        assertEquals(paymentCount,payments.count());assertEquals(1,count("Charge"));assertEquals(1,count("OnboardTransaction"));
        assertEquals(1,statement(bp).path("history").size());
        assertEquals("CHARGE",statement(bp).path("history").get(0).path("type").asText());
    }
    @Test void retriesAreIdempotentAndDifferentPayloadCannotReuseKey() throws Exception {
        String first=sell(bp,1,"same",201).path("saleId").asText();
        assertEquals(first,sell(bp,1,"same",201).path("saleId").asText());
        sell(bp,2,"same",409);assertEquals(1,count("PosSale"));assertEquals(1,count("Charge"));
    }
    @Test void memberCapAndSharedAccountLimitAreBothEnforced() throws Exception {
        sell(bp,3,"cap",201);sell(bp,1,"over-cap",422);
        assertEquals(70,statement(bp2).path("budget").path("remainingAmount").asInt());
        sell(bp2,7,"rest",201);sell(bp2,1,"over-group",422);
        assertEquals(0,statement(bp2).path("budget").path("accountRemaining").asInt());assertEquals(2,count("Charge"));
    }
    @Test void personalCapAppliesEvenWhenCruiseStartsBeforePlannedDeparture() throws Exception {
        jdbc.update("UPDATE \"Cruise\" SET departure_datetime=now()+interval '1 hour' WHERE cruise_id=?",cruise);
        allocate("100",List.of(Map.of("bookingPassengerId",bp,"amount",new BigDecimal("10"))));
        sell(bp,2,"early-start-over-cap",422);assertEquals(0,count("Charge"));
        assertEquals(10,statement(bp).path("budget").path("remainingAmount").asInt());
    }
    @Test void rejectedAndCancelledPosRequestsRestoreAllowanceThroughOriginalApi() throws Exception {
        var sale=sell(bp,2,"cancel",201);String request=sale.path("serviceRequestId").asText();
        perform(post("/servicepackage/requests/{id}/cancel",request),buyer,null,200);
        assertEquals(100,statement(bp).path("budget").path("accountRemaining").asInt());
        assertEquals(2,count("OnboardTransaction"));assertEquals(1,count("Charge"));
        perform(put("/servicepackage/requests/{id}",request),staff,Map.of("quantity",1,"note","edit"),409);
        var second=sell(bp,1,"reject",201);
        perform(post("/servicepackage/requests/{id}/actions/reject",second.path("serviceRequestId").asText()),staff,null,200);
        assertEquals(100,statement(bp).path("budget").path("accountRemaining").asInt());assertEquals(4,count("OnboardTransaction"));
    }
    @Test void posChargedQuantitiesCannotBeEditedViaLegacyApi() throws Exception {
        var sale=sell(bp,1,"immutable",201);
        perform(put("/servicepackage/requests/{id}",sale.path("serviceRequestId").asText()),staff,Map.of("quantity",2),422);
        assertEquals(90,statement(bp).path("budget").path("accountRemaining").asInt());
    }
    @Test void groupPassengerDoesNotNeedAnIndividualLoginToUseAllocatedAllowance() throws Exception {
        jdbc.update("UPDATE \"Passenger\" SET user_id=NULL WHERE passenger_id=(SELECT passenger_id FROM \"BookingPassenger\" WHERE booking_passenger_id=?)",bp2);
        sell(bp2,1,"guest",201);
        assertEquals(90,statement(bp2).path("budget").path("accountRemaining").asInt());
    }
    @Test void accessRequiresEmployeePermissionAndDeviceProofAndSessionOwner() throws Exception {
        assertFalse(login(manager,secret).isBlank());
        String finance=new TransactionTemplate(tm).execute(s->token(user(Role.FINANCE_OFFICER)));
        perform(post("/pos-terminals/{id}/sessions",terminal),finance,Map.of("deviceSecret",secret),403);
        perform(post("/pos-terminals/{id}/sessions",terminal),staff,Map.of("deviceSecret","wrong"),403);
        perform(get("/pos/session").header("X-POS-Session",session),manager,null,403);
        perform(post("/ships/{ship}/pos-terminals",ship),staff,Map.of("areaId",area,"code",code(),"name","No","permissions",List.of("SERVICE_READ"),"enabled",true),403);
        perform(put("/pos-terminals/{id}",terminal),manager,Map.of("name","Disabled","permissions",List.of("SERVICE_READ"),"enabled",false),200);
        perform(get("/pos/session").header("X-POS-Session",session),staff,null,403);
    }
    @Test void effectivePermissionsIntersectAreaAndDeviceAndCannotGrantCashPermissions() throws Exception {
        perform(put("/ships/{ship}/areas/{area}/services/{service}",ship,area,service),manager,Map.of("permissions",List.of("SERVICE_READ"),"enabled",true),200);
        sell(bp,1,"denied",403);assertEquals(0,count("PosSale"));
        perform(put("/ships/{ship}/areas/{area}/services/{service}",ship,area,service),manager,Map.of("permissions",List.of("PAYMENT_PROCESS"),"enabled",true),400);
        perform(put("/ships/{ship}/areas/{area}/services/{service}",UUID.randomUUID(),area,service),manager,Map.of("permissions",List.of("SERVICE_READ"),"enabled",true),404);
    }
    @Test void removingDeckOrAreaLinkImmediatelyDeniesUsageWithoutDeletingService() throws Exception {
        perform(delete("/ships/{ship}/areas/{area}/services/{service}",ship,area,service),manager,null,204);
        sell(bp,1,"unlinked",403);assertTrue(catalog.existsById(service));
    }
    @Test void missingDeckAssignmentSuspendedServiceAndUnpublishedAreaCannotBeUsed() throws Exception {
        jdbc.update("DELETE FROM \"DeckServiceAssignment\" WHERE deck_id=? AND service_id=?",deck,service);
        sell(bp,1,"no-deck",403);
        perform(put("/ships/{ship}/areas/{area}/services/{service}",ship,area,service),manager,
                Map.of("permissions",List.of("SERVICE_SALE"),"enabled",true),422);
        jdbc.update("UPDATE \"ShipModel\" SET status='ARCHIVED' WHERE ship_model_id=?",model);
        perform(get("/pos/session").header("X-POS-Session",session),staff,null,422);
    }
    @Test void expiredSessionAndLockedEmployeeCannotUsePos() throws Exception {
        jdbc.update("UPDATE \"PosSession\" SET expires_at=now()-interval '1 second',created_at=now()-interval '1 hour' WHERE token_hash=?",PosService.hash(session));
        sell(bp,1,"expired",403);
        session=login(staff,secret);
        UUID id=jwt.extractUserId(staff);jdbc.update("UPDATE \"User\" SET status='LOCKED' WHERE user_id=?",id);
        sell(bp,1,"locked",403);
    }
    @Test void requestExecutionRespectsAssignedEmployeeAndReturnsNoCashPayment() throws Exception {
        var sale=sell(bp,1,"execute",201);String id=sale.path("serviceRequestId").asText();
        for(String action:List.of("assign","accept","start","complete"))
            perform(post("/pos/requests/{id}/actions/{action}",id,action).header("X-POS-Session",session),staff,null,200);
        assertEquals("COMPLETED",perform(get("/pos/requests").header("X-POS-Session",session),staff,null,200).get(0).path("status").asText());
        assertEquals(90,statement(bp).path("budget").path("accountRemaining").asInt());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM \"Payment\" WHERE onboard_account_id=?",Integer.class,account));
    }
    @Test void allowanceCannotBeLoweredBelowUsedAmountAndRealPaymentDoesNotReplenishIt() throws Exception {
        sell(bp,2,"spent",201);
        perform(put("/booking/{id}/onboard-allowance",booking),manager,Map.of("amount",10,"memberLimits",List.of()),422);
        UUID passengerId=jdbc.queryForObject("SELECT passenger_id FROM \"BookingPassenger\" WHERE booking_passenger_id=?",UUID.class,bp);
        jdbc.update("INSERT INTO \"OnboardTransaction\" (transaction_id,onboard_account_id,passenger_id,transaction_type,amount,currency_code,transaction_time,status) VALUES (?,?,?,'PAYMENT',-20,'USD',now(),'POSTED')",UUID.randomUUID(),account,passengerId);
        assertEquals(80,statement(bp).path("budget").path("accountRemaining").asInt());
        assertEquals(1,statement(bp).path("history").size());
    }
    @Test void simultaneousUseFromTwoDevicesCannotOverspendSharedAllowance() throws Exception {
        allocate("10",List.of());
        UUID otherArea=new TransactionTemplate(tm).execute(status->{
            ShipModelObject o=new ShipModelObject();o.setShipModel(models.findById(model).orElseThrow());o.setDeck(decks.findById(deck).orElseThrow());
            o.setNodeKey("other-pos-area");o.setObjectType(ShipModelObjectType.FACILITY);return objects.saveAndFlush(o).getShipModelObjectId();
        });
        perform(put("/ships/{ship}/areas/{area}/services/{service}",ship,otherArea,service),manager,Map.of("permissions",List.of(PosPermission.values()),"enabled",true),200);
        var d=perform(post("/ships/{ship}/pos-terminals",ship),manager,Map.of("areaId",otherArea,"code",code(),"name","Second","permissions",List.of(PosPermission.values()),"enabled",true),201);
        UUID otherTerminal=UUID.fromString(d.path("terminal").path("terminalId").asText());
        String other=perform(post("/pos-terminals/{id}/sessions",otherTerminal),staff,Map.of("deviceSecret",d.path("deviceSecret").asText()),201).path("sessionToken").asText();
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            var futures=List.of(pool.submit(()->concurrentSell(start,session,bp,"race-1")),pool.submit(()->concurrentSell(start,other,bp2,"race-2")));
            start.countDown();List<Integer> statuses=new ArrayList<>();for(var f:futures)statuses.add(f.get(30,TimeUnit.SECONDS));Collections.sort(statuses);
            assertEquals(List.of(201,422),statuses);assertEquals(1,count("Charge"));assertEquals(0,statement(bp).path("budget").path("accountRemaining").asInt());
        } finally {pool.shutdownNow();}
    }
    @Test void databaseFailureRollsBackRequestChargeAndUsageTogether() throws Exception {
        String function="it_pos_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE FUNCTION "+function+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected POS failure'; END; $$");
        try {
            jdbc.execute("CREATE TRIGGER "+function+" BEFORE INSERT ON \"PosSale\" FOR EACH ROW WHEN (NEW.terminal_id='"+terminal+"'::uuid) EXECUTE FUNCTION "+function+"()");
            sell(bp,1,"rollback",500);assertEquals(0,count("Charge"));assertEquals(0,count("ServiceRequest"));assertEquals(0,count("OnboardTransaction"));
            assertEquals(100,statement(bp).path("budget").path("accountRemaining").asInt());
        } finally {jdbc.execute("DROP TRIGGER IF EXISTS "+function+" ON \"PosSale\"");jdbc.execute("DROP FUNCTION "+function+"()");}
    }
    private int concurrentSell(CountDownLatch start,String sid,UUID passenger,String key) throws Exception {
        start.await();return mvc.perform(post("/pos/sales").header("Authorization","Bearer "+staff).header("X-POS-Session",sid)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(input(passenger,1,key)))).andReturn().getResponse().getStatus();
    }
    private JsonNode sell(UUID passenger,int quantity,String key,int status) throws Exception {
        return perform(post("/pos/sales").header("X-POS-Session",session),staff,input(passenger,quantity,key),status);
    }
    private Map<String,Object> input(UUID passenger,int quantity,String key) {return Map.of("serviceId",service,"bookingPassengerId",passenger,"onboardAccountId",account,"quantity",quantity,"idempotencyKey",key);}
    private JsonNode statement(UUID passenger) throws Exception {return perform(get("/pos/accounts/{account}/passengers/{bp}/statement",account,passenger).header("X-POS-Session",session),staff,null,200);}
    private void allocate(String amount,List<?> caps) throws Exception {account=UUID.fromString(perform(put("/booking/{id}/onboard-allowance",booking),manager,Map.of("amount",new BigDecimal(amount),"memberLimits",caps),200).path("onboardAccountId").asText());}
    private String login(String auth,String deviceSecret) throws Exception {return perform(post("/pos-terminals/{id}/sessions",terminal),auth,Map.of("deviceSecret",deviceSecret),201).path("sessionToken").asText();}
    private JsonNode perform(MockHttpServletRequestBuilder request,String auth,Object body,int status) throws Exception {
        request.header("Authorization","Bearer "+auth);if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var response=mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse();
        return response.getContentAsByteArray().length==0?mapper.createObjectNode():mapper.readTree(response.getContentAsByteArray()).path("data");
    }
    private long count(String table) {
        String sql=switch(table) {
            case "Charge"->"SELECT count(*) FROM \"Charge\" c JOIN \"OnboardTransaction\" t ON t.transaction_id=c.transaction_id WHERE t.onboard_account_id=?";
            case "ServiceRequest"->"SELECT count(*) FROM \"ServiceRequest\" WHERE booking_id=?";
            case "PosSale"->"SELECT count(*) FROM \"PosSale\" WHERE onboard_account_id=?";
            case "OnboardTransaction"->"SELECT count(*) FROM \"OnboardTransaction\" WHERE onboard_account_id=?";
            default->throw new IllegalArgumentException();
        };return jdbc.queryForObject(sql,Long.class,table.equals("ServiceRequest")?booking:account);
    }
    private User user(Role role) {User u=new User();u.setUsername(code());u.setPasswordHash("not-used");u.setRoles(Set.of(role));u.setStatus(UserStatus.ACTIVE);users.saveAndFlush(u);userIds.add(u.getUserId());return u;}
    private String token(User u) {return jwt.generateToken(u.getUsername(),u.getUserId());}
    private static String code() {return "P"+UUID.randomUUID().toString().replace("-","").substring(0,14);}

    @AfterEach void cleanup() {
        if(booking!=null) {
            jdbc.update("DELETE FROM \"PosSale\" WHERE booking_passenger_id IN (SELECT booking_passenger_id FROM \"BookingPassenger\" WHERE booking_id=?)",booking);
            jdbc.update("DELETE FROM \"Charge\" WHERE transaction_id IN (SELECT transaction_id FROM \"OnboardTransaction\" WHERE onboard_account_id IN (SELECT settlement_onboard_account_id FROM \"Booking\" WHERE booking_id=?))",booking);
            jdbc.update("DELETE FROM \"OnboardTransaction\" WHERE onboard_account_id IN (SELECT settlement_onboard_account_id FROM \"Booking\" WHERE booking_id=?)",booking);
            jdbc.update("DELETE FROM \"ServiceRequest\" WHERE booking_id=?",booking);
            if(account!=null) {
                jdbc.update("DELETE FROM \"SpendingLimit\" WHERE account_member_id IN (SELECT account_member_id FROM \"OnboardAccountMember\" WHERE onboard_account_id=?)",account);
                jdbc.update("DELETE FROM \"OnboardAccountMember\" WHERE onboard_account_id=?",account);
                jdbc.update("DELETE FROM \"OnboardAllowanceChange\" WHERE onboard_account_id=?",account);
                jdbc.update("UPDATE \"Booking\" SET settlement_onboard_account_id=NULL WHERE booking_id=?",booking);
                jdbc.update("DELETE FROM \"OnboardAccount\" WHERE onboard_account_id=?",account);
            }
            jdbc.update("DELETE FROM \"BookingPassenger\" WHERE booking_id=?",booking);jdbc.update("DELETE FROM \"Booking\" WHERE booking_id=?",booking);
        }
        if(ship!=null) {
            jdbc.update("DELETE FROM \"PosSession\" WHERE terminal_id IN (SELECT terminal_id FROM \"PosTerminal\" WHERE ship_id=?)",ship);
            jdbc.update("DELETE FROM \"PosTerminal\" WHERE ship_id=?",ship);
            jdbc.update("DELETE FROM \"AreaServiceAssignment\" WHERE area_object_id IN (SELECT ship_model_object_id FROM \"ShipModelObject\" WHERE ship_model_id=?)",model);
            jdbc.update("DELETE FROM \"ShipModelObject\" WHERE ship_model_id=?",model);jdbc.update("DELETE FROM \"ShipModel\" WHERE ship_id=?",ship);
            jdbc.update("DELETE FROM \"DeckServiceAssignment\" WHERE deck_id=?",deck);jdbc.update("DELETE FROM \"Deck\" WHERE ship_id=?",ship);
            jdbc.update("DELETE FROM \"Cruise\" WHERE ship_id=?",ship);jdbc.update("DELETE FROM \"Ship\" WHERE ship_id=?",ship);
        }
        if(service!=null)jdbc.update("DELETE FROM \"Service\" WHERE service_id=?",service);
        if(port!=null)jdbc.update("DELETE FROM \"Port\" WHERE port_id=?",port);
        for(UUID passenger:passengerIds)jdbc.update("DELETE FROM \"Passenger\" WHERE passenger_id=?",passenger);
        for(UUID user:userIds) {jdbc.update("DELETE FROM \"UserRole\" WHERE user_id=?",user);jdbc.update("DELETE FROM \"User\" WHERE user_id=?",user);}
    }
}
