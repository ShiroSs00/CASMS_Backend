package com.casms.ship.service;

import com.casms.booking.dto.request.BookingCreateRequest;
import com.casms.booking.dto.request.BookingQuoteRequest;
import com.casms.booking.dto.request.PassengerBookingItemRequest;
import com.casms.booking.dto.request.PassengerQuoteItem;
import com.casms.booking.dto.response.BookingDetailResponse;
import com.casms.booking.dto.response.BookingQuoteResponse;
import com.casms.booking.entity.BookingQuote;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.PaymentPlan;
import com.casms.booking.repository.BookingQuoteRepository;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseOpenSaleRequest;
import com.casms.ship.dto.request.CruisePricingPolicyRequest;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.dto.response.CabinTypeSellabilityItem;
import com.casms.ship.dto.response.CruisePricingPolicyResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.CruiseSaleReadinessResponse;
import com.casms.ship.dto.response.CruiseSellabilityResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class CruisePricingPolicyIntegrationTest {

    @Autowired private CruiseService cruiseService;
    @Autowired private CruiseStaffService cruiseStaffService;
    @Autowired private CruisePricingPolicyService pricingPolicyService;
    @Autowired private BookingService bookingService;
    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CabinRepository cabinRepository;
    @Autowired private PortRepository portRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private CruisePricingPolicyRepository cruisePricingPolicyRepository;
    @Autowired private BookingQuoteRepository bookingQuoteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Ship testShip;
    private Deck testDeck;
    private Cabin testCabinBalcony;
    private Cabin testCabinInterior;
    private Port portHph;
    private Port portDan;
    private User testOpsManager;
    private User testCaptain;

    @BeforeEach
    void setUp() {
        portHph = new Port();
        portHph.setPortCode("VN-PRC-HP" + UUID.randomUUID().toString().substring(0, 4));
        portHph.setPortName("Hai Phong Pricing Port");
        portHph.setCountryCode("VN");
        portHph.setTimeZoneId("Asia/Ho_Chi_Minh");
        portHph.setStatus(PortStatus.ACTIVE);
        portHph = portRepository.save(portHph);

        portDan = new Port();
        portDan.setPortCode("VN-PRC-DA" + UUID.randomUUID().toString().substring(0, 4));
        portDan.setPortName("Da Nang Pricing Port");
        portDan.setCountryCode("VN");
        portDan.setTimeZoneId("Asia/Ho_Chi_Minh");
        portDan.setStatus(PortStatus.ACTIVE);
        portDan = portRepository.save(portDan);

        testShip = new Ship();
        testShip.setShipCode("SH-PRC-" + UUID.randomUUID().toString().substring(0, 8));
        testShip.setShipName("Pricing Explorer");
        testShip.setStatus(ShipStatus.ACTIVE);
        testShip.setTotalCapacity(100);
        testShip.setTotalDecks(2);
        testShip = shipRepository.save(testShip);

        testDeck = new Deck();
        testDeck.setShip(testShip);
        testDeck.setDeckNumber(1);
        testDeck.setDeckName("Deck 1");
        testDeck = deckRepository.save(testDeck);

        testCabinBalcony = new Cabin();
        testCabinBalcony.setShip(testShip);
        testCabinBalcony.setDeck(testDeck);
        testCabinBalcony.setCabinNumber("B101");
        testCabinBalcony.setCapacity(2);
        testCabinBalcony.setCabinType(CabinType.BALCONY);
        testCabinBalcony.setStatus(CabinStatus.AVAILABLE);
        testCabinBalcony.setIsAccessible(false);
        testCabinBalcony = cabinRepository.save(testCabinBalcony);

        testCabinInterior = new Cabin();
        testCabinInterior.setShip(testShip);
        testCabinInterior.setDeck(testDeck);
        testCabinInterior.setCabinNumber("I102");
        testCabinInterior.setCapacity(2);
        testCabinInterior.setCabinType(CabinType.INTERIOR);
        testCabinInterior.setStatus(CabinStatus.AVAILABLE);
        testCabinInterior.setIsAccessible(false);
        testCabinInterior = cabinRepository.save(testCabinInterior);

        testOpsManager = new User();
        testOpsManager.setUsername("ops_prc_" + UUID.randomUUID().toString().substring(0, 6));
        testOpsManager.setPasswordHash("hash123");
        testOpsManager.setStatus(UserStatus.ACTIVE);
        testOpsManager.setRoles(Set.of(Role.OPERATIONS_MANAGER));
        testOpsManager = userRepository.save(testOpsManager);

        testCaptain = new User();
        testCaptain.setUsername("capt_prc_" + UUID.randomUUID().toString().substring(0, 6));
        testCaptain.setPasswordHash("hash123");
        testCaptain.setStatus(UserStatus.ACTIVE);
        testCaptain.setRoles(Set.of(Role.ONBOARD_STAFF));
        testCaptain = userRepository.save(testCaptain);
    }

    private CruiseResponse createCruiseWithItineraryAndCaptain(String name, Instant dep, Instant ret) {
        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName(name);
        req.setDepartureDatetime(dep);
        req.setReturnDatetime(ret);
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portDan.getPortId());
        req.setShipId(testShip.getShipId());
        CruiseResponse cruise = cruiseService.create(req);

        CruiseStaffRequest staffReq = new CruiseStaffRequest();
        staffReq.setUserId(testCaptain.getUserId());
        staffReq.setRoleInCruise("CAPTAIN");
        staffReq.setNotes("Thuyen truong dieu hanh");
        cruiseStaffService.assignStaff(cruise.getCruiseId(), staffReq, testCaptain.getUserId());

        Itinerary itinerary = new Itinerary();
        itinerary.setCruise(cruiseRepository.findById(cruise.getCruiseId()).orElseThrow());
        itinerary.setItineraryName(name + " Itinerary");
        itinerary.setStartDate(LocalDate.ofInstant(dep, ZoneId.of("UTC")));
        itinerary.setEndDate(LocalDate.ofInstant(ret, ZoneId.of("UTC")));
        itinerary.setItineraryType(ItineraryType.ROUND_TRIP);
        itinerary.setStatus(ItineraryStatus.APPROVED);
        itineraryRepository.save(itinerary);

        return cruise;
    }

    @Test
    @DisplayName("BE-H01.1: Cruise chua co Pricing Policy bi block Sale Readiness va khong the open sale")
    void test_missingPricingPolicy_blocksReadinessAndOpenSale() {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createCruiseWithItineraryAndCaptain("No Price Cruise", dep, ret);

        // 1. Kiem tra readiness gate
        CruiseSaleReadinessResponse readiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertFalse(readiness.isReady(), "Cruise chua co pricing policy khong duoc ready for sale");
        assertTrue(readiness.getBlockers().stream().anyMatch(b -> b.contains("PRICING_POLICY_MISSING")),
                "Phai chua blocker PRICING_POLICY_MISSING");

        // 2. Thu mo ban phai bi chan
        CruiseOpenSaleRequest openReq = CruiseOpenSaleRequest.builder()
                .expectedRevision(cruise.getRevision())
                .build();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                cruiseService.openSale(cruise.getCruiseId(), openReq));
        assertEquals("CRUISE_NOT_SALE_READY", ex.getCode());

        // 3. Public sellability bao cao khong sellable va khong co gia fallback
        CruiseSellabilityResponse sellability = cruiseService.getPublicSellability(cruise.getCruiseId());
        assertFalse(sellability.isSellable());
        assertTrue(sellability.getCabinTypes().isEmpty() ||
                sellability.getCabinTypes().stream().allMatch(c -> c.getBaseFare() == null));
    }

    @Test
    @DisplayName("BE-H01.2: Pricing Policy o trang thai DRAFT bi block Sale Readiness")
    void test_draftPricingPolicy_blocksReadiness() {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createCruiseWithItineraryAndCaptain("Draft Price Cruise", dep, ret);

        // Luu policy DRAFT
        CruisePricingPolicyRequest req = new CruisePricingPolicyRequest();
        req.setCurrency("USD");
        req.setBalconyBaseFare(new BigDecimal("450.00"));
        req.setInteriorBaseFare(new BigDecimal("300.00"));
        req.setTaxRate(new BigDecimal("0.1000"));
        req.setPortFeePerPassenger(new BigDecimal("25.00"));
        req.setDepositPercentage(new BigDecimal("0.3000"));
        req.setFinalPaymentDueDays(14);
        req.setFullPaymentCutoffDays(7);
        pricingPolicyService.savePricingPolicy(cruise.getCruiseId(), req, testOpsManager.getUserId(), false);

        CruiseSaleReadinessResponse readiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertFalse(readiness.isReady());
        assertTrue(readiness.getBlockers().stream().anyMatch(b -> b.contains("PRICING_POLICY_NOT_APPROVED")),
                "Policy DRAFT phai tra blocker PRICING_POLICY_NOT_APPROVED");
    }

    @Test
    @DisplayName("BE-H01.3: Hang phong tren tau chua duoc dinh gia (NULL) bi chan voi PRICING_POLICY_FARE_MISSING")
    void test_unpricedCabinType_blocksReadiness() {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createCruiseWithItineraryAndCaptain("Unpriced Cabin Cruise", dep, ret);

        // Chi dinh gia BALCONY, de NULL INTERIOR du tau co cabin INTERIOR
        CruisePricingPolicy policy = CruisePricingPolicy.builder()
                .cruise(cruiseRepository.findById(cruise.getCruiseId()).orElseThrow())
                .policyVersion("PRC-2026.10-V1")
                .status(PricingPolicyStatus.APPROVED)
                .currency("USD")
                .balconyBaseFare(new BigDecimal("450.00"))
                .interiorBaseFare(null) // NULL!
                .taxRate(new BigDecimal("0.1000"))
                .portFeePerPassenger(new BigDecimal("25.00"))
                .depositPercentage(new BigDecimal("0.3000"))
                .finalPaymentDueDays(14)
                .fullPaymentCutoffDays(7)
                .build();
        cruisePricingPolicyRepository.save(policy);

        CruiseSaleReadinessResponse readiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertFalse(readiness.isReady());
        assertTrue(readiness.getBlockers().stream().anyMatch(b -> b.contains("PRICING_POLICY_FARE_MISSING")),
                "Thieu gia cho INTERIOR phai co blocker PRICING_POLICY_FARE_MISSING");
    }

    @Test
    @DisplayName("BE-H01.4: Gia 0 hop le (dich vu bao gom / mien phi) duoc chap nhan khong bi coi la NULL")
    void test_zeroFare_acceptedAsValidComplimentary() {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createCruiseWithItineraryAndCaptain("Zero Fare Cruise", dep, ret);

        // Dat INTERIOR = 0 (mien phi cho hang phong nay)
        CruisePricingPolicy policy = CruisePricingPolicy.builder()
                .cruise(cruiseRepository.findById(cruise.getCruiseId()).orElseThrow())
                .policyVersion("PRC-2026.10-V1")
                .status(PricingPolicyStatus.APPROVED)
                .currency("USD")
                .balconyBaseFare(new BigDecimal("450.00"))
                .interiorBaseFare(BigDecimal.ZERO) // Explicit 0
                .taxRate(new BigDecimal("0.1000"))
                .portFeePerPassenger(new BigDecimal("25.00"))
                .depositPercentage(new BigDecimal("0.3000"))
                .finalPaymentDueDays(14)
                .fullPaymentCutoffDays(7)
                .build();
        cruisePricingPolicyRepository.save(policy);

        CruiseSaleReadinessResponse readiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertTrue(readiness.getBlockers().stream().noneMatch(b -> b.contains("PRICING_POLICY")),
                "Gia 0 phai duoc chap nhan hop le ma khong tao blocker pricing");
    }

    @Test
    @DisplayName("BE-H01.5: REST API quan ly policy (save -> approve -> get) va mo ban thanh cong")
    void test_pricingPolicy_restApiLifecycle_andOpenSaleSuccess() {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createCruiseWithItineraryAndCaptain("Full Lifecycle Cruise", dep, ret);

        // 1. Save DRAFT via Service
        CruisePricingPolicyRequest saveReq = new CruisePricingPolicyRequest();
        saveReq.setCurrency("USD");
        saveReq.setBalconyBaseFare(new BigDecimal("500.00"));
        saveReq.setInteriorBaseFare(new BigDecimal("320.00"));
        saveReq.setTaxRate(new BigDecimal("0.1000"));
        saveReq.setPortFeePerPassenger(new BigDecimal("30.00"));
        saveReq.setDepositPercentage(new BigDecimal("0.3000"));
        saveReq.setFinalPaymentDueDays(14);
        saveReq.setFullPaymentCutoffDays(7);
        saveReq.setCancellationPolicy("Huy truoc 30 ngay hoan 100%, sau 30 ngay mat coc.");

        CruisePricingPolicyResponse saved = pricingPolicyService.savePricingPolicy(
                cruise.getCruiseId(), saveReq, testOpsManager.getUserId(), false);
        assertEquals(PricingPolicyStatus.DRAFT, saved.getStatus());
        assertEquals("USD", saved.getCurrency());

        // 2. Approve Policy
        CruisePricingPolicyResponse approved = pricingPolicyService.approvePricingPolicy(
                cruise.getCruiseId(), testOpsManager.getUserId());
        assertEquals(PricingPolicyStatus.APPROVED, approved.getStatus());
        assertNotNull(approved.getApprovedAt());
        assertEquals(testOpsManager.getUserId(), approved.getApprovedBy());

        // 3. Get Policy
        CruisePricingPolicyResponse fetched = pricingPolicyService.getPricingPolicy(cruise.getCruiseId());
        assertEquals(PricingPolicyStatus.APPROVED, fetched.getStatus());
        assertEquals(0, new BigDecimal("500.00").compareTo(fetched.getBalconyBaseFare()));

        // 4. Readiness phai PASS va Mo ban thanh cong
        CruiseSaleReadinessResponse readiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertTrue(readiness.isReady(), "Readiness phai PASS sau khi phe duyet policy");

        CruiseOpenSaleRequest openReq = CruiseOpenSaleRequest.builder()
                .expectedRevision(cruise.getRevision())
                .build();
        CruiseResponse opened = cruiseService.openSale(cruise.getCruiseId(), openReq);
        assertEquals(CruiseStatus.PUBLISHED, opened.getStatus());

        // 5. Public sellability authoritative
        CruiseSellabilityResponse sellability = cruiseService.getPublicSellability(cruise.getCruiseId());
        assertTrue(sellability.isSellable());
        assertEquals(approved.getPolicyVersion(), sellability.getPricingPolicyVersion());
        BigDecimal balconyFare = sellability.getCabinTypes().stream()
                .filter(c -> c.getCabinType() == CabinType.BALCONY)
                .map(CabinTypeSellabilityItem::getBaseFare)
                .findFirst().orElse(null);
        assertNotNull(balconyFare);
        assertEquals(0, new BigDecimal("500.00").compareTo(balconyFare));

        BigDecimal interiorFare = sellability.getCabinTypes().stream()
                .filter(c -> c.getCabinType() == CabinType.INTERIOR)
                .map(CabinTypeSellabilityItem::getBaseFare)
                .findFirst().orElse(null);
        assertNotNull(interiorFare);
        assertEquals(0, new BigDecimal("320.00").compareTo(interiorFare));
    }

    @Test
    @DisplayName("BE-H01.6: Quote va Booking nhat quan ve policyVersion, subtotal, tax, port fee, deposit")
    void test_quoteAndBooking_consistencyWithAuthoritativePolicy() {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createCruiseWithItineraryAndCaptain("Quote Booking Sync Cruise", dep, ret);

        // Setup Approved Policy
        CruisePricingPolicy policy = CruisePricingPolicy.builder()
                .cruise(cruiseRepository.findById(cruise.getCruiseId()).orElseThrow())
                .policyVersion("PRC-2026.10-V1")
                .status(PricingPolicyStatus.APPROVED)
                .currency("USD")
                .balconyBaseFare(new BigDecimal("450.00"))
                .interiorBaseFare(new BigDecimal("300.00"))
                .taxRate(new BigDecimal("0.1000")) // 10%
                .portFeePerPassenger(new BigDecimal("25.00")) // 25 USD
                .depositPercentage(new BigDecimal("0.3000")) // 30%
                .finalPaymentDueDays(14)
                .fullPaymentCutoffDays(7)
                .build();
        cruisePricingPolicyRepository.save(policy);

        // Mo ban cruise
        CruiseOpenSaleRequest openReq = CruiseOpenSaleRequest.builder()
                .expectedRevision(cruise.getRevision())
                .build();
        cruiseService.openSale(cruise.getCruiseId(), openReq);

        // 1. Phat hanh Quote cho 2 khach: 1 BALCONY ($450) + 1 INTERIOR ($300)
        BookingQuoteRequest quoteReq = new BookingQuoteRequest();
        quoteReq.setCruiseId(cruise.getCruiseId());
        quoteReq.setPaymentPlan(PaymentPlan.DEPOSIT);
        quoteReq.setPassengers(List.of(
                new PassengerQuoteItem(testCabinBalcony.getCabinId(), null),
                new PassengerQuoteItem(testCabinInterior.getCabinId(), null)
        ));

        CasmsPrincipal guestPrincipal = new CasmsPrincipal(testOpsManager, rolePermissionRepository);
        BookingQuoteResponse quoteRes = bookingService.createQuote(quoteReq, guestPrincipal);

        assertNotNull(quoteRes.getQuoteId());
        assertEquals("PRC-2026.10-V1", quoteRes.getPolicyVersion());

        // Expected financial calculations:
        // Subtotal = 450 + 300 = 750.00
        // Port fees = 2 passengers * 25.00 = 50.00
        // Taxes = 750 * 10% = 75.00
        // Total = 750 + 50 + 75 = 875.00
        // Deposit = 875.00 * 30% = 262.50
        // Remaining = 875.00 - 262.50 = 612.50
        assertEquals(0, new BigDecimal("750.00").compareTo(quoteRes.getSubtotalAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(quoteRes.getPortFeesAmount()));
        assertEquals(0, new BigDecimal("75.00").compareTo(quoteRes.getTaxesAmount()));
        assertEquals(0, new BigDecimal("875.00").compareTo(quoteRes.getTotalAmount()));
        assertEquals(0, new BigDecimal("262.50").compareTo(quoteRes.getDepositAmount()));
        assertEquals(0, new BigDecimal("612.50").compareTo(quoteRes.getRemainingAmount()));

        // 2. Tra cuu quote phai khoi phuc day du line items
        BookingQuoteResponse fetchedQuote = bookingService.getQuote(quoteRes.getQuoteId());
        assertEquals(2, fetchedQuote.getLineItems().size());
        assertEquals("PRC-2026.10-V1", fetchedQuote.getPolicyVersion());

        // 3. Tao Booking truc tiep tu Quote nay
        BookingCreateRequest bookReq = new BookingCreateRequest();
        bookReq.setCruiseId(cruise.getCruiseId());
        bookReq.setQuoteId(quoteRes.getQuoteId());
        bookReq.setPaymentPlan(PaymentPlan.DEPOSIT);

        PassengerBookingItemRequest p1 = new PassengerBookingItemRequest();
        p1.setFullName("Nguyen Van Balcony");
        p1.setCabinId(testCabinBalcony.getCabinId());
        p1.setEmail("balcony@example.com");

        PassengerBookingItemRequest p2 = new PassengerBookingItemRequest();
        p2.setFullName("Tran Thi Interior");
        p2.setCabinId(testCabinInterior.getCabinId());
        p2.setEmail("interior@example.com");

        bookReq.setPassengers(List.of(p1, p2));

        BookingDetailResponse bookingRes = bookingService.createBooking(bookReq, guestPrincipal);
        assertEquals(BookingStatus.Draft, bookingRes.getBookingStatus());
        assertEquals(0, new BigDecimal("875.00").compareTo(bookingRes.getAgreedFareAmount()));
        assertEquals(0, new BigDecimal("262.50").compareTo(bookingRes.getRequiredDepositAmount()));
    }

    @Test
    @DisplayName("BE-H01.7: Cap nhat policy lam vo hieu quote cu (QUOTE_INVALIDATED) va thay doi fingerprint")
    void test_policyUpdate_invalidatesOldQuotesAndChangesFingerprint() {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createCruiseWithItineraryAndCaptain("Policy Invalidation Cruise", dep, ret);

        // 1. Luu va approve V1
        CruisePricingPolicyRequest reqV1 = new CruisePricingPolicyRequest();
        reqV1.setCurrency("USD");
        reqV1.setBalconyBaseFare(new BigDecimal("450.00"));
        reqV1.setInteriorBaseFare(new BigDecimal("300.00"));
        reqV1.setTaxRate(new BigDecimal("0.1000"));
        reqV1.setPortFeePerPassenger(new BigDecimal("25.00"));
        reqV1.setDepositPercentage(new BigDecimal("0.3000"));
        reqV1.setFinalPaymentDueDays(14);
        reqV1.setFullPaymentCutoffDays(7);
        pricingPolicyService.savePricingPolicy(cruise.getCruiseId(), reqV1, testOpsManager.getUserId(), true);
        pricingPolicyService.approvePricingPolicy(cruise.getCruiseId(), testOpsManager.getUserId());

        // Mo ban
        CruiseOpenSaleRequest openReq = CruiseOpenSaleRequest.builder()
                .expectedRevision(cruise.getRevision())
                .build();
        cruiseService.openSale(cruise.getCruiseId(), openReq);

        String fingerprintV1 = cruiseService.validateSaleReadiness(cruise.getCruiseId()).getFingerprint();

        // 2. Phat hanh Quote tren V1
        BookingQuoteRequest quoteReq = new BookingQuoteRequest();
        quoteReq.setCruiseId(cruise.getCruiseId());
        quoteReq.setPaymentPlan(PaymentPlan.FULL);
        quoteReq.setPassengers(List.of(new PassengerQuoteItem(testCabinBalcony.getCabinId(), null)));

        CasmsPrincipal guestPrincipal = new CasmsPrincipal(testOpsManager, rolePermissionRepository);
        BookingQuoteResponse quoteRes = bookingService.createQuote(quoteReq, guestPrincipal);

        // 3. Cap nhat gia (tang gia Balcony tu 450 len 550) -> Auto bump version & Invalidate quotes
        CruisePricingPolicyRequest reqV2 = new CruisePricingPolicyRequest();
        reqV2.setCurrency("USD");
        reqV2.setBalconyBaseFare(new BigDecimal("550.00")); // Doi gia!
        reqV2.setInteriorBaseFare(new BigDecimal("300.00"));
        reqV2.setTaxRate(new BigDecimal("0.1000"));
        reqV2.setPortFeePerPassenger(new BigDecimal("25.00"));
        reqV2.setDepositPercentage(new BigDecimal("0.3000"));
        reqV2.setFinalPaymentDueDays(14);
        reqV2.setFullPaymentCutoffDays(7);
        CruisePricingPolicyResponse updatedPolicy = pricingPolicyService.savePricingPolicy(
                cruise.getCruiseId(), reqV2, testOpsManager.getUserId(), true);

        assertTrue(updatedPolicy.getPolicyVersion().contains("-V2"),
                "Policy version phai duoc tu dong tang len V2 khi sua gia");

        String fingerprintV2 = cruiseService.validateSaleReadiness(cruise.getCruiseId()).getFingerprint();
        assertNotEquals(fingerprintV1, fingerprintV2, "Fingerprint phai thay doi khi policy version thay doi");

        // 4. Quote cu da bi danh dau INVALIDATED trong DB
        BookingQuote oldQuoteInDb = bookingQuoteRepository.findById(quoteRes.getQuoteId()).orElseThrow();
        assertEquals("INVALIDATED", oldQuoteInDb.getStatus());

        // 5. Dung quote cu dat ve phai bi he thong tu choi voi QUOTE_INVALIDATED
        BookingCreateRequest bookReq = new BookingCreateRequest();
        bookReq.setCruiseId(cruise.getCruiseId());
        bookReq.setQuoteId(quoteRes.getQuoteId());
        bookReq.setPaymentPlan(PaymentPlan.FULL);

        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setFullName("Khach Hang Cu");
        p.setCabinId(testCabinBalcony.getCabinId());
        p.setEmail("oldguest@example.com");
        bookReq.setPassengers(List.of(p));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bookingService.createBooking(bookReq, guestPrincipal));
        assertEquals("QUOTE_INVALIDATED", ex.getCode(), "Quote cu da bi vo hieu hoa phai bi he thong tu choi");
    }
}
