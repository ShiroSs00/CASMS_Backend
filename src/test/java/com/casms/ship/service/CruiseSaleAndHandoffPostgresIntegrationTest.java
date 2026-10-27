package com.casms.ship.service;

import com.casms.booking.dto.request.BookingCancelRequest;
import com.casms.booking.dto.request.BookingCreateRequest;
import com.casms.booking.dto.request.PassengerBookingItemRequest;
import com.casms.booking.dto.response.BookingDetailResponse;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.repository.CabinAssignmentRepository;
import com.casms.booking.service.BookingService;
import com.casms.checkin.entity.CruiseCheckin;
import com.casms.checkin.enums.CruiseCheckinStatus;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.PassengerDocumentType;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.enums.TourPackageType;
import com.casms.servicepackage.repository.TourPackageRepository;
import com.casms.payment.entity.Payment;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.repository.PaymentRepository;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingContract;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingContractRepository;
import com.casms.identity.entity.RolePermission;
import com.casms.identity.enums.Permission;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.security.principal.CasmsPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.casms.ship.dto.request.*;
import com.casms.ship.dto.response.*;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripRepository;
import com.casms.trip.repository.TripScheduleVersionRepository;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class CruiseSaleAndHandoffPostgresIntegrationTest {

    @Autowired private CruiseService cruiseService;
    @Autowired private CruiseStaffService cruiseStaffService;
    @Autowired private CruisePackageService cruisePackageService;
    @Autowired private BookingService bookingService;
    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CabinRepository cabinRepository;
    @Autowired private PortRepository portRepository;
    @Autowired private TourPackageRepository tourPackageRepository;
    @Autowired private CruiseTourPackageRepository cruiseTourPackageRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingPassengerRepository bookingPassengerRepository;
    @Autowired private CabinAssignmentRepository cabinAssignmentRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private BookingContractRepository bookingContractRepository;
    @Autowired private CruiseCheckinRepository cruiseCheckinRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripScheduleVersionRepository versionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Ship testShip;
    private Deck testDeck;
    private Cabin testCabinInterior;
    private Cabin testCabinSingleSlot;
    private Port portHph;
    private Port portDan;
    private User testUser;
    private User testStaffCaptain;
    private TourPackage testPackage;
    private TourPackage unassignedPackage;

    @BeforeEach
    void setUp() {
        // Ports
        portHph = new Port();
        portHph.setPortCode("VN-HP" + UUID.randomUUID().toString().substring(0, 4));
        portHph.setPortName("Hai Phong Port Test");
        portHph.setCountryCode("VN");
        portHph.setTimeZoneId("Asia/Ho_Chi_Minh");
        portHph.setStatus(PortStatus.ACTIVE);
        portHph = portRepository.save(portHph);

        portDan = new Port();
        portDan.setPortCode("VN-DA" + UUID.randomUUID().toString().substring(0, 4));
        portDan.setPortName("Da Nang Port Test");
        portDan.setCountryCode("VN");
        portDan.setTimeZoneId("Asia/Ho_Chi_Minh");
        portDan.setStatus(PortStatus.ACTIVE);
        portDan = portRepository.save(portDan);

        // Ship
        String shipCode = "SH-SALE-" + UUID.randomUUID().toString().substring(0, 8);
        testShip = new Ship();
        testShip.setShipCode(shipCode);
        testShip.setShipName("Sale Explorer Test");
        testShip.setStatus(ShipStatus.ACTIVE);
        testShip.setTotalCapacity(200);
        testShip.setTotalDecks(3);
        testShip = shipRepository.save(testShip);

        testDeck = new Deck();
        testDeck.setShip(testShip);
        testDeck.setDeckNumber(1);
        testDeck.setDeckName("Main Deck");
        testDeck = deckRepository.save(testDeck);

        testCabinInterior = new Cabin();
        testCabinInterior.setShip(testShip);
        testCabinInterior.setDeck(testDeck);
        testCabinInterior.setCabinNumber("101");
        testCabinInterior.setCapacity(2);
        testCabinInterior.setCabinType(CabinType.INTERIOR);
        testCabinInterior.setStatus(CabinStatus.AVAILABLE);
        testCabinInterior.setIsAccessible(false);
        testCabinInterior = cabinRepository.save(testCabinInterior);

        testCabinSingleSlot = new Cabin();
        testCabinSingleSlot.setShip(testShip);
        testCabinSingleSlot.setDeck(testDeck);
        testCabinSingleSlot.setCabinNumber("102");
        testCabinSingleSlot.setCapacity(1); // Exactly 1 slot for concurrency grab
        testCabinSingleSlot.setCabinType(CabinType.OCEAN_VIEW);
        testCabinSingleSlot.setStatus(CabinStatus.AVAILABLE);
        testCabinSingleSlot.setIsAccessible(false);
        testCabinSingleSlot = cabinRepository.save(testCabinSingleSlot);

        // Users
        String username = "cust_" + UUID.randomUUID().toString().substring(0, 8);
        testUser = new User();
        testUser.setUsername(username);
        testUser.setPasswordHash("hash123");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setRoles(Set.of(Role.PASSENGER));
        testUser = userRepository.save(testUser);

        String captainName = "capt_" + UUID.randomUUID().toString().substring(0, 8);
        testStaffCaptain = new User();
        testStaffCaptain.setUsername(captainName);
        testStaffCaptain.setPasswordHash("hash123");
        testStaffCaptain.setStatus(UserStatus.ACTIVE);
        testStaffCaptain.setRoles(Set.of(Role.OPERATIONS_MANAGER, Role.BOOKING_AGENT));
        testStaffCaptain = userRepository.save(testStaffCaptain);

        rolePermissionRepository.findByRole(Role.OPERATIONS_MANAGER).ifPresentOrElse(
                rp -> {
                    rp.getPermissions().add(Permission.BOOKING_CONFIRM);
                    rp.getPermissions().add(Permission.BOOKING_READ);
                    rp.getPermissions().add(Permission.BOOKING_CANCEL);
                    rolePermissionRepository.save(rp);
                },
                () -> rolePermissionRepository.save(new RolePermission(Role.OPERATIONS_MANAGER, Set.of(
                        Permission.BOOKING_CONFIRM, Permission.BOOKING_READ, Permission.BOOKING_CANCEL)))
        );
        rolePermissionRepository.findByRole(Role.BOOKING_AGENT).ifPresentOrElse(
                rp -> {
                    rp.getPermissions().add(Permission.BOOKING_CONFIRM);
                    rp.getPermissions().add(Permission.BOOKING_READ);
                    rp.getPermissions().add(Permission.BOOKING_CANCEL);
                    rolePermissionRepository.save(rp);
                },
                () -> rolePermissionRepository.save(new RolePermission(Role.BOOKING_AGENT, Set.of(
                        Permission.BOOKING_CONFIRM, Permission.BOOKING_READ, Permission.BOOKING_CANCEL)))
        );

        // Tour Packages
        testPackage = new TourPackage();
        testPackage.setPackageCode("PKG-" + UUID.randomUUID().toString().substring(0, 6));
        testPackage.setPackageName("Gói Khám Phá Di Sản");
        testPackage.setPackageType(TourPackageType.BASIC);
        testPackage.setPrice(BigDecimal.valueOf(150.00));
        testPackage.setCurrencyCode("USD");
        testPackage.setStatus(TourPackageStatus.ACTIVE);
        testPackage = tourPackageRepository.save(testPackage);

        unassignedPackage = new TourPackage();
        unassignedPackage.setPackageCode("PKG-UN-" + UUID.randomUUID().toString().substring(0, 6));
        unassignedPackage.setPackageName("Gói VIP Không Mở Bán");
        unassignedPackage.setPackageType(TourPackageType.PREMIUM);
        unassignedPackage.setPrice(BigDecimal.valueOf(500.00));
        unassignedPackage.setCurrencyCode("USD");
        unassignedPackage.setStatus(TourPackageStatus.ACTIVE);
        unassignedPackage = tourPackageRepository.save(unassignedPackage);
    }

    private CruiseResponse createReadyCruise(String name, Instant dep, Instant ret) {
        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName(name);
        req.setDepartureDatetime(dep);
        req.setReturnDatetime(ret);
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portDan.getPortId());
        req.setShipId(testShip.getShipId());
        CruiseResponse cruise = cruiseService.create(req);

        // Assign Captain
        CruiseStaffRequest staffReq = new CruiseStaffRequest();
        staffReq.setUserId(testStaffCaptain.getUserId());
        staffReq.setRoleInCruise("CAPTAIN");
        staffReq.setNotes("Thuyền trưởng");
        cruiseStaffService.assignStaff(cruise.getCruiseId(), staffReq, testStaffCaptain.getUserId());

        // Create approved Itinerary for this cruise so it is ready for sale
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
    @DisplayName("BE-07 / E09, E11, E12: Tính toán tồn chỗ (Inventory Summary) và phân biệt sức chứa")
    void test_BE07_inventory_summary_and_capacity_breakdown() {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Inventory Summary Cruise Test", dep, ret);

        // Assign package
        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        // Query inventory summary
        CruiseInventorySummaryResponse inv = cruiseService.getInventorySummary(cruise.getCruiseId());
        assertNotNull(inv);
        assertEquals(cruise.getCruiseId(), inv.getCruiseId());
        assertEquals(testShip.getTotalCapacity(), inv.getPhysicalCapacity());
        assertTrue(inv.getTotalCabins() >= 2);
        assertTrue(inv.getOperationalCabins() >= 2);
        assertEquals(0L, inv.getBookedPassengersCount());
        assertEquals(0L, inv.getHeldPassengersCount());
        assertEquals((long) testShip.getTotalCapacity(), inv.getRemainingCapacity());
        assertEquals(1L, inv.getActivePackagesCount());

        // Check breakdown by cabin type
        assertFalse(inv.getCabinTypeInventory().isEmpty());
        CabinTypeInventoryItem interiorItem = inv.getCabinTypeInventory().stream()
                .filter(i -> i.getCabinType() == CabinType.INTERIOR)
                .findFirst()
                .orElse(null);
        assertNotNull(interiorItem);
        assertEquals(1L, interiorItem.getTotalCabins());
        assertEquals(2L, interiorItem.getTotalSlots());
        assertEquals(0L, interiorItem.getOccupiedSlots());
        assertEquals(2L, interiorItem.getRemainingSlots());
        assertEquals(BigDecimal.valueOf(300), interiorItem.getBaseFare());

        // BE-R02 / Question 5: Open sale and create a Draft booking to verify held vs booked and remaining capacity
        cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest());
        BookingCreateRequest req = new BookingCreateRequest();
        req.setCruiseId(cruise.getCruiseId());
        req.setCurrency("USD");
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setCabinId(testCabinInterior.getCabinId());
        p.setFullName("Held Passenger");
        p.setEmail("held@test.com");
        p.setDocumentType(PassengerDocumentType.Passport);
        p.setDocumentNumber("DOC-HELD-111");
        p.setTourPackageId(testPackage.getTourPackageId());
        req.setPassengers(List.of(p));
        bookingService.createBooking(req, null);

        CruiseInventorySummaryResponse invAfterDraft = cruiseService.getInventorySummary(cruise.getCruiseId());
        assertEquals(0L, invAfterDraft.getBookedPassengersCount(), "Draft bookings must not be counted as confirmed booked");
        assertEquals(1L, invAfterDraft.getHeldPassengersCount(), "Draft bookings must be counted as held");
        assertEquals((long) testShip.getTotalCapacity() - 1, invAfterDraft.getRemainingCapacity(), "Remaining capacity must only subtract held passengers once");

        CruiseSaleReadinessResponse saleReadiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertEquals((long) testShip.getTotalCapacity() - 1, saleReadiness.getRemainingCapacity());
    }

    @Test
    @DisplayName("BE-07 / Concurrency: Hai giao dịch đồng thời đặt suất phòng cuối cùng không thể cùng thành công")
    void test_BE07_concurrency_last_slot_booking() throws Exception {
        Instant dep = Instant.now().plus(45, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Concurrent Booking Cruise", dep, ret);

        // Link package and open sale
        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);
        cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest());

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger overcapacityCount = new AtomicInteger(0);

        Callable<Void> task = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                BookingCreateRequest req = new BookingCreateRequest();
                req.setCruiseId(cruise.getCruiseId());
                req.setCurrency("USD");
                PassengerBookingItemRequest p = new PassengerBookingItemRequest();
                p.setCabinId(testCabinSingleSlot.getCabinId());
                p.setFullName("Passenger " + UUID.randomUUID().toString().substring(0, 5));
                p.setEmail("p_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com");
                p.setDocumentType(PassengerDocumentType.Passport);
                p.setDocumentNumber("DOC-" + UUID.randomUUID().toString().substring(0, 8));
                p.setTourPackageId(testPackage.getTourPackageId());
                req.setPassengers(List.of(p));

                bookingService.createBooking(req, null);
                successCount.incrementAndGet();
            } catch (BusinessException be) {
                if ("CABIN_OVERCAPACITY".equals(be.getCode())) {
                    overcapacityCount.incrementAndGet();
                }
            } catch (Exception ignored) {}
            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 booking must succeed and 1 must be rejected with CABIN_OVERCAPACITY
        assertEquals(1, successCount.get(), "Exactly one concurrent booking on the single available slot must succeed");
        assertEquals(1, overcapacityCount.get(), "The competing booking must be rejected with CABIN_OVERCAPACITY");
    }

    @Test
    @DisplayName("BE-07 / Gate: Chặn đặt chỗ với gói tour chưa được mở bán cho chuyến đi")
    void test_BE07_booking_gate_unassigned_package() {
        Instant dep = Instant.now().plus(50, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Package Gate Cruise", dep, ret);

        // Assign testPackage but NOT unassignedPackage
        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);
        cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest());

        BookingCreateRequest req = new BookingCreateRequest();
        req.setCruiseId(cruise.getCruiseId());
        req.setCurrency("USD");
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setCabinId(testCabinInterior.getCabinId());
        p.setFullName("Hành Khách Test");
        p.setEmail("hk@test.com");
        p.setDocumentType(PassengerDocumentType.Passport);
        p.setDocumentNumber("PASSPORT-123456");
        p.setTourPackageId(unassignedPackage.getTourPackageId()); // Unassigned package!
        req.setPassengers(List.of(p));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                bookingService.createBooking(req, null));
        assertEquals("PACKAGE_NOT_AVAILABLE_FOR_CRUISE", ex.getCode());
    }

    @Test
    @DisplayName("BE-08A / E17, E20: Sale Readiness tách biệt, chỉ đọc và không tự ý mở bán")
    void test_BE08A_sale_readiness_evaluation() {
        Instant dep = Instant.now().plus(60, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Sale Readiness Cruise", dep, ret);

        // 1. Without active package: sale readiness must report blocker
        CruiseSaleReadinessResponse unready = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertFalse(unready.isReady());
        assertTrue(unready.isOperationalReady());
        assertFalse(unready.isPackagesConfigured());
        assertTrue(unready.getBlockers().contains("NO_ACTIVE_PACKAGES"));

        // Verify read-only: cruise status MUST remain DRAFT (Acceptance E17)
        Cruise afterCheck = cruiseRepository.findById(cruise.getCruiseId()).orElseThrow();
        assertEquals(CruiseStatus.DRAFT, afterCheck.getStatus(), "Readiness check must not mutate cruise status");

        // 2. Assign active package: sale readiness must now pass
        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        CruiseSaleReadinessResponse ready = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertTrue(ready.isReady());
        assertTrue(ready.isOperationalReady());
        assertTrue(ready.isPackagesConfigured());
        assertTrue(ready.isInventoryAvailable());
        assertTrue(ready.getBlockers().isEmpty());
        assertNotNull(ready.getEvaluatedAt());
        assertEquals(cruise.getRevision(), ready.getCruiseRevision());
    }

    @Test
    @DisplayName("BE-08B / E18, E24-E27: Mở bán (Open Sale), Tạm dừng (Stop Sale) và Mở lại (Resume Sale)")
    void test_BE08B_open_stop_and_resume_sale() {
        Instant dep = Instant.now().plus(70, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Open Stop Resume Cruise", dep, ret);

        // Assign package
        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        // 1. Stale revision check during open-sale
        CruiseOpenSaleRequest staleReq = CruiseOpenSaleRequest.builder()
                .expectedRevision(cruise.getRevision() + 99L)
                .build();
        BusinessException staleEx = assertThrows(BusinessException.class, () ->
                cruiseService.openSale(cruise.getCruiseId(), staleReq));
        assertEquals("STALE_REVISION", staleEx.getCode());

        // 2. Open sale successfully
        CruiseOpenSaleRequest openReq = CruiseOpenSaleRequest.builder()
                .expectedRevision(cruise.getRevision())
                .build();
        CruiseResponse opened = cruiseService.openSale(cruise.getCruiseId(), openReq);
        assertEquals(CruiseStatus.PUBLISHED, opened.getStatus());

        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).orElseThrow();
        assertEquals(ItineraryStatus.OPEN_FOR_SALE, itinerary.getStatus());

        // 3. Stop sale (SUSPENDED)
        CruiseStopSaleRequest stopReq = CruiseStopSaleRequest.builder()
                .expectedRevision(opened.getRevision())
                .reason("Bão biển đột xuất cần tạm dừng bán")
                .build();
        CruiseResponse stopped = cruiseService.stopSale(cruise.getCruiseId(), stopReq);
        assertEquals(CruiseStatus.PUBLISHED, stopped.getStatus(), "Cruise itself is not cancelled");

        Itinerary stoppedItinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).orElseThrow();
        assertEquals(ItineraryStatus.SUSPENDED, stoppedItinerary.getStatus());

        // 4. Booking while SUSPENDED must fail
        BookingCreateRequest req = new BookingCreateRequest();
        req.setCruiseId(cruise.getCruiseId());
        req.setCurrency("USD");
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setCabinId(testCabinInterior.getCabinId());
        p.setFullName("Khách Hàng Treo");
        p.setEmail("treo@test.com");
        p.setDocumentType(PassengerDocumentType.Passport);
        p.setDocumentNumber("PASS-SUSPENDED-1");
        p.setTourPackageId(testPackage.getTourPackageId());
        req.setPassengers(List.of(p));

        BusinessException suspendEx = assertThrows(BusinessException.class, () ->
                bookingService.createBooking(req, null));
        assertEquals("SALE_SUSPENDED", suspendEx.getCode());

        // 5. Resume sale
        CruiseResumeSaleRequest resumeReq = CruiseResumeSaleRequest.builder()
                .expectedRevision(stopped.getRevision())
                .reason("Thời tiết đã ổn định, mở bán trở lại")
                .build();
        CruiseResponse resumed = cruiseService.resumeSale(cruise.getCruiseId(), resumeReq);

        Itinerary resumedItinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).orElseThrow();
        assertEquals(ItineraryStatus.OPEN_FOR_SALE, resumedItinerary.getStatus());

        // Booking now succeeds!
        BookingDetailResponse booked = bookingService.createBooking(req, null);
        assertNotNull(booked);
        assertNotNull(booked.getBookingReference());
    }

    @Test
    @DisplayName("BE-R00 / D01 Gate: Chặn đặt chỗ khi cruise PUBLISHED nhưng itinerary chưa OPEN_FOR_SALE")
    void test_BE_R00_booking_gate_not_open_for_sale() {
        Instant dep = Instant.now().plus(80, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Not Open For Sale Cruise", dep, ret);

        // Assign package and publish cruise (operational readiness)
        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);
        cruiseService.publish(cruise.getCruiseId());

        // Notice: Cruise is PUBLISHED, but Itinerary is APPROVED (not opened for sale via /open-sale)
        Cruise published = cruiseRepository.findById(cruise.getCruiseId()).orElseThrow();
        assertEquals(CruiseStatus.PUBLISHED, published.getStatus());
        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).orElseThrow();
        assertEquals(ItineraryStatus.APPROVED, itinerary.getStatus());

        BookingCreateRequest req = new BookingCreateRequest();
        req.setCruiseId(cruise.getCruiseId());
        req.setCurrency("USD");
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setCabinId(testCabinInterior.getCabinId());
        p.setFullName("Hành Khách Gate Test");
        p.setEmail("gate_test@test.com");
        p.setDocumentType(PassengerDocumentType.Passport);
        p.setDocumentNumber("DOC-GATE-999");
        p.setTourPackageId(testPackage.getTourPackageId());
        req.setPassengers(List.of(p));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                bookingService.createBooking(req, null));
        assertEquals("CRUISE_NOT_OPEN_FOR_SALE", ex.getCode());
    }

    @Test
    @DisplayName("BE-R01 / Test bắt buộc: Scope A không mở B; retry không thêm scope ngoài ý định; scope mới được cập nhật")
    void test_BE_R01_scope_A_does_not_open_B_and_retry_is_idempotent() {
        Instant dep = Instant.now().plus(90, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Scope Isolation Cruise", dep, ret);

        // Create a second active package B
        TourPackage packageB = new TourPackage();
        packageB.setPackageCode("PKG-B-" + UUID.randomUUID().toString().substring(0, 6));
        packageB.setPackageName("Gói Tour B Biển Đảo");
        packageB.setPackageType(TourPackageType.PREMIUM);
        packageB.setPrice(BigDecimal.valueOf(250.00));
        packageB.setCurrencyCode("USD");
        packageB.setStatus(TourPackageStatus.ACTIVE);
        packageB = tourPackageRepository.save(packageB);

        // Assign both package A and package B to this cruise
        CruiseTourPackageRequest pkgReqA = new CruiseTourPackageRequest();
        pkgReqA.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReqA);

        CruiseTourPackageRequest pkgReqB = new CruiseTourPackageRequest();
        pkgReqB.setTourPackageId(packageB.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReqB);

        // 1. Open sale ONLY for Scope A (testPackage)
        CruiseOpenSaleRequest openScopeA = CruiseOpenSaleRequest.builder()
                .packageIds(List.of(testPackage.getTourPackageId()))
                .build();
        CruiseResponse openedCruise = cruiseService.openSale(cruise.getCruiseId(), openScopeA);
        assertEquals(CruiseStatus.PUBLISHED, openedCruise.getStatus());

        // Verify: Package A is ACTIVE, Package B is NOT ACTIVE (Scope A không mở B)
        CruiseTourPackage ctpA = cruiseTourPackageRepository
                .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruise.getCruiseId(), testPackage.getTourPackageId()).orElseThrow();
        CruiseTourPackage ctpB = cruiseTourPackageRepository
                .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruise.getCruiseId(), packageB.getTourPackageId()).orElseThrow();
        assertTrue(ctpA.isActive(), "Package A must be active");
        assertFalse(ctpB.isActive(), "Scope A must NOT open Package B");

        // Verify booking: Booking Package A succeeds
        BookingCreateRequest reqA = new BookingCreateRequest();
        reqA.setCruiseId(cruise.getCruiseId());
        reqA.setCurrency("USD");
        PassengerBookingItemRequest pA = new PassengerBookingItemRequest();
        pA.setCabinId(testCabinInterior.getCabinId());
        pA.setFullName("Khách Scope A");
        pA.setEmail("scope_a@test.com");
        pA.setDocumentType(PassengerDocumentType.Passport);
        pA.setDocumentNumber("DOC-SCOPE-A");
        pA.setTourPackageId(testPackage.getTourPackageId());
        reqA.setPassengers(List.of(pA));
        BookingDetailResponse bookedA = bookingService.createBooking(reqA, null);
        assertNotNull(bookedA);

        // Booking Package B fails with PACKAGE_NOT_AVAILABLE_FOR_CRUISE
        BookingCreateRequest reqB = new BookingCreateRequest();
        reqB.setCruiseId(cruise.getCruiseId());
        reqB.setCurrency("USD");
        PassengerBookingItemRequest pB = new PassengerBookingItemRequest();
        pB.setCabinId(testCabinInterior.getCabinId());
        pB.setFullName("Khách Scope B");
        pB.setEmail("scope_b@test.com");
        pB.setDocumentType(PassengerDocumentType.Passport);
        pB.setDocumentNumber("DOC-SCOPE-B");
        pB.setTourPackageId(packageB.getTourPackageId());
        reqB.setPassengers(List.of(pB));
        BusinessException exB = assertThrows(BusinessException.class, () ->
                bookingService.createBooking(reqB, null));
        assertEquals("PACKAGE_NOT_AVAILABLE_FOR_CRUISE", exB.getCode());

        // 2. Retry openSale with Scope A: idempotent, does not add Package B
        CruiseResponse retried = cruiseService.openSale(cruise.getCruiseId(), openScopeA);
        assertNotNull(retried);
        ctpB = cruiseTourPackageRepository
                .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruise.getCruiseId(), packageB.getTourPackageId()).orElseThrow();
        assertFalse(ctpB.isActive(), "Retry must not add scope outside intention");

        // 3. Update scope: openSale with Package B (Đã mở bán không được return sớm rồi bỏ qua scope mới)
        CruiseOpenSaleRequest openScopeB = CruiseOpenSaleRequest.builder()
                .packageIds(List.of(packageB.getTourPackageId()))
                .build();
        CruiseResponse scopeBOpened = cruiseService.openSale(cruise.getCruiseId(), openScopeB);
        assertNotNull(scopeBOpened);

        ctpB = cruiseTourPackageRepository
                .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruise.getCruiseId(), packageB.getTourPackageId()).orElseThrow();
        assertTrue(ctpB.isActive(), "Package B must now be active following explicit scope update");

        // Booking Package B now succeeds!
        PassengerBookingItemRequest pBNew = new PassengerBookingItemRequest();
        pBNew.setCabinId(testCabinInterior.getCabinId());
        pBNew.setFullName("Khách Scope B Hợp Lệ");
        pBNew.setEmail("scope_b_new@test.com");
        pBNew.setDocumentType(PassengerDocumentType.Passport);
        pBNew.setDocumentNumber("DOC-SCOPE-B-NEW");
        pBNew.setTourPackageId(packageB.getTourPackageId());
        reqB.setPassengers(List.of(pBNew));
        BookingDetailResponse bookedB = bookingService.createBooking(reqB, null);
        assertNotNull(bookedB);
    }

    @Test
    @DisplayName("BE-R01 / Test bắt buộc: ID khác Cruise/không tồn tại bị từ chối nguyên tử (Atomic rejection)")
    void test_BE_R01_invalid_package_ids_rejected_atomically() {
        Instant dep = Instant.now().plus(100, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Atomic Validation Cruise", dep, ret);

        // Assign testPackage
        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        // 1. Non-existent package ID: throws ResourceNotFoundException
        UUID nonExistentId = UUID.randomUUID();
        CruiseOpenSaleRequest nonExistentReq = CruiseOpenSaleRequest.builder()
                .packageIds(List.of(nonExistentId))
                .build();
        assertThrows(com.casms.common.exception.ResourceNotFoundException.class, () ->
                cruiseService.openSale(cruise.getCruiseId(), nonExistentReq));

        // 2. Package not assigned to this cruise (unassignedPackage): throws PACKAGE_NOT_ASSIGNED_TO_CRUISE
        CruiseOpenSaleRequest unassignedReq = CruiseOpenSaleRequest.builder()
                .packageIds(List.of(unassignedPackage.getTourPackageId()))
                .build();
        BusinessException unassignedEx = assertThrows(BusinessException.class, () ->
                cruiseService.openSale(cruise.getCruiseId(), unassignedReq));
        assertEquals("PACKAGE_NOT_ASSIGNED_TO_CRUISE", unassignedEx.getCode());

        // 3. Inactive package: throws PACKAGE_INACTIVE
        TourPackage inactivePkg = new TourPackage();
        inactivePkg.setPackageCode("PKG-INACT-" + UUID.randomUUID().toString().substring(0, 5));
        inactivePkg.setPackageName("Gói Tạm Ngưng");
        inactivePkg.setPackageType(TourPackageType.BASIC);
        inactivePkg.setPrice(BigDecimal.valueOf(100.00));
        inactivePkg.setCurrencyCode("USD");
        inactivePkg.setStatus(TourPackageStatus.INACTIVE);
        inactivePkg = tourPackageRepository.save(inactivePkg);

        CruiseTourPackage ctpInactive = new CruiseTourPackage();
        ctpInactive.setCruise(cruiseRepository.findById(cruise.getCruiseId()).orElseThrow());
        ctpInactive.setTourPackage(inactivePkg);
        ctpInactive.setActive(false);
        cruiseTourPackageRepository.save(ctpInactive);

        CruiseOpenSaleRequest inactiveReq = CruiseOpenSaleRequest.builder()
                .packageIds(List.of(inactivePkg.getTourPackageId()))
                .build();
        BusinessException inactiveEx = assertThrows(BusinessException.class, () ->
                cruiseService.openSale(cruise.getCruiseId(), inactiveReq));
        assertEquals("PACKAGE_INACTIVE", inactiveEx.getCode());

        // Verify atomicity: cruise remains DRAFT, nothing was half-opened
        Cruise cruiseAfterFailures = cruiseRepository.findById(cruise.getCruiseId()).orElseThrow();
        assertEquals(CruiseStatus.DRAFT, cruiseAfterFailures.getStatus());
    }

    @Test
    @DisplayName("BE-R02 / Xác nhận suất cuối: Xác nhận hold hợp lệ của chính booking thành công, dừng bán không hủy booking")
    void test_BE_R02_confirm_last_slot_preserves_own_hold_and_stop_sale_retains_booking() {
        Instant dep = Instant.now().plus(110, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Last Slot Confirm Cruise", dep, ret);

        // Assign package and open sale
        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);
        cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest());

        // 1. Book the ONLY slot of testCabinSingleSlot (capacity = 1)
        BookingCreateRequest req = new BookingCreateRequest();
        req.setCruiseId(cruise.getCruiseId());
        req.setCurrency("USD");
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setCabinId(testCabinSingleSlot.getCabinId());
        p.setFullName("Hành Khách Suất Cuối");
        p.setEmail("last_slot@test.com");
        p.setDocumentType(PassengerDocumentType.Passport);
        p.setDocumentNumber("DOC-LAST-SLOT");
        p.setTourPackageId(testPackage.getTourPackageId());
        req.setPassengers(List.of(p));

        BookingDetailResponse draftBooking = bookingService.createBooking(req, null);
        assertNotNull(draftBooking);
        assertEquals(BookingStatus.Draft, draftBooking.getBookingStatus());
        UUID bookingId = draftBooking.getBookingId();

        // 2. Competing booking on this now fully-occupied cabin MUST fail with CABIN_OVERCAPACITY
        BookingCreateRequest compReq = new BookingCreateRequest();
        compReq.setCruiseId(cruise.getCruiseId());
        compReq.setCurrency("USD");
        PassengerBookingItemRequest compP = new PassengerBookingItemRequest();
        compP.setCabinId(testCabinSingleSlot.getCabinId());
        compP.setFullName("Khách Cạnh Tranh");
        compP.setEmail("comp@test.com");
        compP.setDocumentType(PassengerDocumentType.Passport);
        compP.setDocumentNumber("DOC-COMP");
        compReq.setPassengers(List.of(compP));
        BusinessException compEx = assertThrows(BusinessException.class, () ->
                bookingService.createBooking(compReq, null));
        assertEquals("CABIN_OVERCAPACITY", compEx.getCode());

        // 3. Accept hard-copy contract for this booking
        BookingContract contract = bookingContractRepository.findByBookingBookingId(bookingId).orElseThrow();
        contract.setStatus(BookingContractStatus.Accepted);
        bookingContractRepository.save(contract);

        // 4. Record verified payment covering full required deposit
        Booking freshBooking = bookingRepository.findById(bookingId).orElseThrow();
        Payment payment = new Payment();
        payment.setBooking(freshBooking);
        payment.setRequestedAmount(freshBooking.getRequiredDepositAmount());
        payment.setConfirmedAmount(freshBooking.getRequiredDepositAmount());
        payment.setRequestedAt(Instant.now());
        payment.setConfirmedAt(Instant.now());
        payment.setStatus(PaymentStatus.Confirmed);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.Cash);
        payment.setCurrency(freshBooking.getCurrency());
        payment.setIdempotencyKey("IDEMP-" + UUID.randomUUID());
        payment.setInternalReceiptReference("RCPT-LAST-SLOT-001");
        paymentRepository.save(payment);

        // 5. Staff with BOOKING_CONFIRM confirms the booking:
        // Verification: Must NOT reject with "insufficient capacity" because the slot is held by THIS booking!
        rolePermissionRepository.findByRole(Role.BOOKING_AGENT).ifPresentOrElse(
                rp -> {
                    rp.getPermissions().add(Permission.BOOKING_CONFIRM);
                    rp.getPermissions().add(Permission.BOOKING_READ);
                    rolePermissionRepository.save(rp);
                },
                () -> rolePermissionRepository.save(new RolePermission(Role.BOOKING_AGENT, Set.of(Permission.BOOKING_CONFIRM, Permission.BOOKING_READ)))
        );
        testStaffCaptain.setRoles(Set.of(Role.BOOKING_AGENT));
        testStaffCaptain = userRepository.save(testStaffCaptain);

        CasmsPrincipal staffPrincipal = new CasmsPrincipal(testStaffCaptain, rolePermissionRepository);

        BookingDetailResponse confirmed = bookingService.confirm(bookingId, staffPrincipal);
        assertEquals(BookingStatus.Confirmed, confirmed.getBookingStatus());

        // 6. Stop sale on the cruise (BE-R02: dừng bán không được tự ý cancel/refund đơn cũ)
        CruiseStopSaleRequest stopReq = CruiseStopSaleRequest.builder()
                .reason("Tạm dừng bán bảo trì")
                .build();
        cruiseService.stopSale(cruise.getCruiseId(), stopReq);

        // Verify existing confirmed booking is PRESERVED, not cancelled, and still accessible
        Booking bookingAfterStop = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.Confirmed, bookingAfterStop.getBookingStatus());
        assertNull(bookingAfterStop.getCancellationReason());

        BookingDetailResponse detailAfterStop = bookingService.detail(bookingId, staffPrincipal);
        assertEquals(BookingStatus.Confirmed, detailAfterStop.getBookingStatus());
    }

    @Test
    @DisplayName("BE-R02: Hai khách tranh suất cuối cho cùng 1 cabin 1 chỗ - đúng 1 khách thành công, 1 khách nhận lỗi")
    void test_BE_R02_two_clients_compete_last_slot_exactly_one_succeeds() throws Exception {
        Instant dep = Instant.now().plus(65, ChronoUnit.DAYS);
        Instant ret = dep.plus(4, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Cruise Last Slot Race", dep, ret);

        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest());

        // Prepare 2 identical booking requests competing for testCabinSingleSlot (capacity = 1)
        BookingCreateRequest req1 = new BookingCreateRequest();
        req1.setCruiseId(cruise.getCruiseId());
        req1.setCurrency("USD");
        PassengerBookingItemRequest p1 = new PassengerBookingItemRequest();
        p1.setCabinId(testCabinSingleSlot.getCabinId());
        p1.setFullName("Khách Đua 1");
        p1.setEmail("race1@test.com");
        p1.setDocumentType(PassengerDocumentType.Passport);
        p1.setDocumentNumber("RACE-001");
        req1.setPassengers(List.of(p1));

        BookingCreateRequest req2 = new BookingCreateRequest();
        req2.setCruiseId(cruise.getCruiseId());
        req2.setCurrency("USD");
        PassengerBookingItemRequest p2 = new PassengerBookingItemRequest();
        p2.setCabinId(testCabinSingleSlot.getCabinId());
        p2.setFullName("Khách Đua 2");
        p2.setEmail("race2@test.com");
        p2.setDocumentType(PassengerDocumentType.Passport);
        p2.setDocumentNumber("RACE-002");
        req2.setPassengers(List.of(p2));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<BookingDetailResponse> f1 = executor.submit(() -> bookingService.createBooking(req1, null));
        Future<BookingDetailResponse> f2 = executor.submit(() -> bookingService.createBooking(req2, null));

        int successes = 0;
        int failures = 0;

        try {
            f1.get();
            successes++;
        } catch (ExecutionException e) {
            failures++;
            assertTrue(e.getCause() instanceof BusinessException);
            BusinessException be = (BusinessException) e.getCause();
            assertEquals("CABIN_OVERCAPACITY", be.getCode());
        }

        try {
            f2.get();
            successes++;
        } catch (ExecutionException e) {
            failures++;
            assertTrue(e.getCause() instanceof BusinessException);
            BusinessException be = (BusinessException) e.getCause();
            assertEquals("CABIN_OVERCAPACITY", be.getCode());
        }

        executor.shutdown();

        assertEquals(1, successes, "Exactly 1 client must secure the single slot");
        assertEquals(1, failures, "Exactly 1 client must be rejected with CABIN_OVERCAPACITY");

        // Verify active assignments on testCabinSingleSlot is exactly 1
        long activeAssignments = cabinAssignmentRepository
                .countActiveAssignmentsByCabinAndCruise(testCabinSingleSlot.getCabinId(), cruise.getCruiseId());
        assertEquals(1, activeAssignments);
    }

    @Test
    @DisplayName("BE-R02: Confirm cạnh tranh stop/invalidation & Quote cũ không vượt được gate khi đã dừng bán")
    void test_BE_R02_confirm_competes_with_stop_sale_and_quote_blocked() {
        Instant dep = Instant.now().plus(70, ChronoUnit.DAYS);
        Instant ret = dep.plus(4, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Cruise Stop Sale Race", dep, ret);

        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest());

        // 1. Client creates a valid Draft booking (holding a slot)
        BookingCreateRequest req = new BookingCreateRequest();
        req.setCruiseId(cruise.getCruiseId());
        req.setCurrency("USD");
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setCabinId(testCabinInterior.getCabinId());
        p.setFullName("Khách Giữ Chỗ");
        p.setEmail("hold@test.com");
        p.setDocumentType(PassengerDocumentType.Passport);
        p.setDocumentNumber("HOLD-001");
        req.setPassengers(List.of(p));
        BookingDetailResponse draft = bookingService.createBooking(req, null);
        UUID bookingId = draft.getBookingId();

        // 2. Accept contract and record full deposit payment
        BookingContract contract = bookingContractRepository.findByBookingBookingId(bookingId).orElseThrow();
        contract.setStatus(BookingContractStatus.Accepted);
        contract.setAcceptedAt(Instant.now());
        bookingContractRepository.save(contract);

        Booking freshBooking = bookingRepository.findById(bookingId).orElseThrow();
        Payment payment = new Payment();
        payment.setBooking(freshBooking);
        payment.setRequestedAmount(freshBooking.getRequiredDepositAmount());
        payment.setConfirmedAmount(freshBooking.getRequiredDepositAmount());
        payment.setRequestedAt(Instant.now());
        payment.setConfirmedAt(Instant.now());
        payment.setStatus(PaymentStatus.Confirmed);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.Cash);
        payment.setCurrency(freshBooking.getCurrency());
        payment.setIdempotencyKey("IDEMP-" + UUID.randomUUID());
        payment.setInternalReceiptReference("RCPT-STOP-001");
        paymentRepository.save(payment);

        // 3. Stop sale occurs (e.g. operational suspension)
        CruiseStopSaleRequest stopReq = CruiseStopSaleRequest.builder()
                .reason("Tạm dừng vận hành đột xuất")
                .build();
        cruiseService.stopSale(cruise.getCruiseId(), stopReq);

        // 4. Verification A: New booking based on old quote MUST fail with SALE_SUSPENDED
        BookingCreateRequest oldQuoteReq = new BookingCreateRequest();
        oldQuoteReq.setCruiseId(cruise.getCruiseId());
        oldQuoteReq.setCurrency("USD");
        PassengerBookingItemRequest pOld = new PassengerBookingItemRequest();
        pOld.setCabinId(testCabinInterior.getCabinId());
        pOld.setFullName("Khách Quote Cũ");
        pOld.setEmail("oldquote@test.com");
        pOld.setDocumentType(PassengerDocumentType.Passport);
        pOld.setDocumentNumber("OLDQUOTE-001");
        oldQuoteReq.setPassengers(List.of(pOld));

        BusinessException createEx = assertThrows(BusinessException.class,
                () -> bookingService.createBooking(oldQuoteReq, null));
        assertEquals("SALE_SUSPENDED", createEx.getCode());

        // 5. Verification B: Confirming the Draft booking while cruise is SUSPENDED is rejected
        CasmsPrincipal staffPrincipal = new CasmsPrincipal(testStaffCaptain, rolePermissionRepository);
        BusinessException confirmEx = assertThrows(BusinessException.class,
                () -> bookingService.confirm(bookingId, staffPrincipal));
        assertEquals("SALE_SUSPENDED", confirmEx.getCode());

        // 6. Resume sale: sale is restored and the existing hold can now be safely confirmed
        CruiseResumeSaleRequest resumeReq = CruiseResumeSaleRequest.builder().build();
        cruiseService.resumeSale(cruise.getCruiseId(), resumeReq);

        BookingDetailResponse confirmed = bookingService.confirm(bookingId, staffPrincipal);
        assertEquals(BookingStatus.Confirmed, confirmed.getBookingStatus());
    }

    @Test
    @DisplayName("BE-R02: Cancel cạnh tranh expiry không nhả lặp (Idempotent cancellation and release)")
    void test_BE_R02_cancel_concurrent_or_repeated_does_not_double_release() {
        Instant dep = Instant.now().plus(75, ChronoUnit.DAYS);
        Instant ret = dep.plus(4, ChronoUnit.DAYS);
        CruiseResponse cruise = createReadyCruise("Cruise Cancel Concurrency", dep, ret);

        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest());

        // Create booking with 1 passenger
        BookingCreateRequest req = new BookingCreateRequest();
        req.setCruiseId(cruise.getCruiseId());
        req.setCurrency("USD");
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setCabinId(testCabinInterior.getCabinId());
        p.setFullName("Khách Huỷ Đơn");
        p.setEmail("cancel@test.com");
        p.setDocumentType(PassengerDocumentType.Passport);
        p.setDocumentNumber("CANCEL-001");
        req.setPassengers(List.of(p));
        BookingDetailResponse booking = bookingService.createBooking(req, null);
        UUID bookingId = booking.getBookingId();

        // Verify initially 1 active assignment
        long activeBefore = cabinAssignmentRepository
                .countActiveAssignmentsByCabinAndCruise(testCabinInterior.getCabinId(), cruise.getCruiseId());
        assertEquals(1, activeBefore);

        // 1st Cancel (e.g., customer or expiry thread)
        BookingCancelRequest cancelReq = new BookingCancelRequest();
        cancelReq.setReason("Hết hạn thanh toán cọc");
        CasmsPrincipal staffPrincipal = new CasmsPrincipal(testStaffCaptain, rolePermissionRepository);

        BookingDetailResponse res1 = bookingService.cancel(bookingId, cancelReq, staffPrincipal);
        assertEquals(BookingStatus.Cancelled, res1.getBookingStatus());

        long activeAfterFirst = cabinAssignmentRepository
                .countActiveAssignmentsByCabinAndCruise(testCabinInterior.getCabinId(), cruise.getCruiseId());
        assertEquals(0, activeAfterFirst);

        // 2nd Cancel (e.g. race with manual cancel or retry)
        BookingDetailResponse res2 = bookingService.cancel(bookingId, cancelReq, staffPrincipal);
        assertEquals(BookingStatus.Cancelled, res2.getBookingStatus());

        long activeAfterSecond = cabinAssignmentRepository
                .countActiveAssignmentsByCabinAndCruise(testCabinInterior.getCabinId(), cruise.getCruiseId());
        assertEquals(0, activeAfterSecond);

        // Verify only 1 cabin assignment row exists for this passenger and validTo is populated
        UUID passengerId = booking.getPassengers().get(0).getBookingPassengerId();
        var assignments = cabinAssignmentRepository.findByBookingPassengerBookingPassengerId(passengerId);
        assertEquals(1, assignments.size());
        assertNotNull(assignments.get(0).getValidTo(), "Cabin assignment must be closed with validTo set");
    }

    @Test
    @DisplayName("BE-09 / C14: Bàn giao dữ liệu cho hệ thống điều hành (Downstream Handoff)")
    void test_BE09_downstream_handoff() {
        // Create source trip & version
        Trip trip = new Trip();
        trip.setTripCode("TR-HANDOFF-" + UUID.randomUUID().toString().substring(0, 6));
        trip.setName("Chuyến Handoff Mẫu");
        trip.setStatus(TripStatus.READY);
        trip.setPlannedScope("DOMESTIC");
        trip.setDurationDays(3);
        trip.setDurationNights(2);
        trip.setDurationGroup(TripDurationGroup.SHORT);
        trip = tripRepository.save(trip);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setTrip(trip);
        version.setVersionNumber(1);
        version.setRevision(1L);
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now());
        version = versionRepository.save(version);

        Instant dep = Instant.now().plus(80, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);

        CruiseCreateRequest createReq = new CruiseCreateRequest();
        createReq.setCruiseName("Downstream Handoff Cruise");
        createReq.setDepartureDatetime(dep);
        createReq.setReturnDatetime(ret);
        createReq.setDeparturePortId(portHph.getPortId());
        createReq.setReturnPortId(portDan.getPortId());
        createReq.setShipId(testShip.getShipId());
        createReq.setSourceVersionId(version.getVersionId());
        CruiseResponse cruise = cruiseService.create(createReq);

        // Assign captain and package
        CruiseStaffRequest staffReq = new CruiseStaffRequest();
        staffReq.setUserId(testStaffCaptain.getUserId());
        staffReq.setRoleInCruise("CAPTAIN");
        staffReq.setNotes("Thuyền trưởng điều hành");
        cruiseStaffService.assignStaff(cruise.getCruiseId(), staffReq, testStaffCaptain.getUserId());

        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        // Approve materialized Itinerary so cruise is ready for sale
        itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).ifPresent(it -> {
            it.setStatus(ItineraryStatus.APPROVED);
            itineraryRepository.save(it);
        });

        cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest());

        // Create a booking
        BookingCreateRequest req = new BookingCreateRequest();
        req.setCruiseId(cruise.getCruiseId());
        req.setCurrency("USD");
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setCabinId(testCabinInterior.getCabinId());
        p.setFullName("Nguyễn Văn Handoff");
        p.setEmail("handoff@test.com");
        p.setDocumentType(PassengerDocumentType.Passport);
        p.setDocumentNumber("PASS-HANDOFF-999");
        p.setTourPackageId(testPackage.getTourPackageId());
        req.setPassengers(List.of(p));
        BookingDetailResponse booking = bookingService.createBooking(req, null);

        // Complete check-in for this passenger
        UUID passengerId = booking.getPassengers().get(0).getBookingPassengerId();
        CruiseCheckin checkin = new CruiseCheckin();
        checkin.setBookingPassenger(bookingPassengerRepository.findById(passengerId).orElseThrow());
        checkin.setCheckinStatus(CruiseCheckinStatus.Completed);
        checkin.setLocationTrackingAllowed(true);
        checkin.setLocationSharingAllowed(true);
        checkin.setCompletedAt(Instant.now());
        checkin.setBoardingReadyAt(Instant.now());
        cruiseCheckinRepository.save(checkin);

        // Query Downstream Handoff
        CruiseDownstreamHandoffResponse handoff = cruiseService.getDownstreamHandoff(cruise.getCruiseId());
        assertNotNull(handoff);
        assertEquals(cruise.getCruiseId(), handoff.getCruiseId());
        assertEquals("Downstream Handoff Cruise", handoff.getCruiseName());
        assertEquals(trip.getTripId(), handoff.getSourceTripId());
        assertEquals(trip.getTripCode(), handoff.getSourceTripCode());
        assertEquals(version.getVersionId(), handoff.getSourceVersionId());
        assertEquals(1, handoff.getSourceVersionNumber());
        assertEquals(testShip.getShipId(), handoff.getShipId());
        assertEquals("Sale Explorer Test", handoff.getShipName());

        // Verify strictly differentiated counts
        assertEquals(1, handoff.getTotalBookingsCount());
        assertEquals(1, handoff.getTotalPassengersCount());
        assertEquals(1, handoff.getCheckedInPassengersCount());
        assertEquals(1, handoff.getBoardedPassengersCount());
        assertTrue(handoff.isCaptainAssigned());
        assertTrue(handoff.isDepartureClearanceReady());
        assertFalse(handoff.getPassengerManifest().isEmpty());
        assertFalse(handoff.getStaffAssignments().isEmpty());

        var manifestItem = handoff.getPassengerManifest().get(0);
        assertEquals("Nguyễn Văn Handoff", manifestItem.getFullName());
        assertEquals("101", manifestItem.getCabinNumber());
        assertEquals("Completed", manifestItem.getCheckinStatus());
    }
}
