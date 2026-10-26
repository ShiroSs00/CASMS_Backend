package com.casms.ship.service;

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
    @Autowired private CruiseCheckinRepository cruiseCheckinRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripScheduleVersionRepository versionRepository;
    @Autowired private UserRepository userRepository;
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
        testStaffCaptain.setRoles(Set.of(Role.OPERATIONS_MANAGER));
        testStaffCaptain = userRepository.save(testStaffCaptain);

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
