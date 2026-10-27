package com.casms.ship.service;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.RolePermission;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.enums.TourPackageType;
import com.casms.servicepackage.repository.TourPackageRepository;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseOpenSaleRequest;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.dto.request.CruiseTourPackageRequest;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.CruiseSaleReadinessResponse;
import com.casms.ship.dto.response.ReadinessIssueDetail;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
public class CruiseEvidenceReadinessPostgresIntegrationTest {

    @Autowired private CruiseService cruiseService;
    @Autowired private CruiseStaffService cruiseStaffService;
    @Autowired private CruisePackageService cruisePackageService;
    @SpyBean private CruiseRegulationService cruiseRegulationService;

    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CabinRepository cabinRepository;
    @Autowired private PortRepository portRepository;
    @Autowired private TourPackageRepository tourPackageRepository;
    @Autowired private CruiseTourPackageRepository cruiseTourPackageRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private ItineraryDayRepository itineraryDayRepository;
    @Autowired private CruiseStopRepository cruiseStopRepository;
    @Autowired private ShoreExcursionRepository shoreExcursionRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private OperationalRegulationRepository operationalRegulationRepository;
    @Autowired private ShipRegulationRepository shipRegulationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;

    private Ship testShip;
    private Deck testDeck;
    private Cabin testCabin;
    private Port portHph;
    private Port portDan;
    private User testCaptain;
    private TourPackage testPackage;

    @BeforeEach
    void setUp() {
        Mockito.reset(cruiseRegulationService);

        portHph = new Port();
        portHph.setPortCode("VN-EVI-HP" + UUID.randomUUID().toString().substring(0, 4));
        portHph.setPortName("Hai Phong Evidence Port");
        portHph.setCountryCode("VN");
        portHph.setTimeZoneId("Asia/Ho_Chi_Minh");
        portHph.setStatus(PortStatus.ACTIVE);
        portHph = portRepository.save(portHph);

        portDan = new Port();
        portDan.setPortCode("VN-EVI-DA" + UUID.randomUUID().toString().substring(0, 4));
        portDan.setPortName("Da Nang Evidence Port");
        portDan.setCountryCode("VN");
        portDan.setTimeZoneId("Asia/Ho_Chi_Minh");
        portDan.setStatus(PortStatus.ACTIVE);
        portDan = portRepository.save(portDan);

        testShip = new Ship();
        testShip.setShipCode("SH-EVI-" + UUID.randomUUID().toString().substring(0, 8));
        testShip.setShipName("Evidence Explorer");
        testShip.setStatus(ShipStatus.ACTIVE);
        testShip.setTotalCapacity(150);
        testShip.setTotalDecks(3);
        testShip = shipRepository.save(testShip);

        testDeck = new Deck();
        testDeck.setShip(testShip);
        testDeck.setDeckNumber(1);
        testDeck.setDeckName("Deck 1");
        testDeck = deckRepository.save(testDeck);

        testCabin = new Cabin();
        testCabin.setShip(testShip);
        testCabin.setDeck(testDeck);
        testCabin.setCabinNumber("C101");
        testCabin.setCapacity(2);
        testCabin.setCabinType(CabinType.INTERIOR);
        testCabin.setStatus(CabinStatus.AVAILABLE);
        testCabin.setIsAccessible(false);
        testCabin = cabinRepository.save(testCabin);

        testCaptain = new User();
        testCaptain.setUsername("capt_evi_" + UUID.randomUUID().toString().substring(0, 6));
        testCaptain.setPasswordHash("hash123");
        testCaptain.setStatus(UserStatus.ACTIVE);
        testCaptain.setRoles(Set.of(Role.OPERATIONS_MANAGER));
        testCaptain = userRepository.save(testCaptain);

        testPackage = new TourPackage();
        testPackage.setPackageCode("PKG-EVI-" + UUID.randomUUID().toString().substring(0, 6));
        testPackage.setPackageName("Gói Tiêu Chuẩn");
        testPackage.setPackageType(TourPackageType.BASIC);
        testPackage.setPrice(BigDecimal.valueOf(200.00));
        testPackage.setCurrencyCode("USD");
        testPackage.setStatus(TourPackageStatus.ACTIVE);
        testPackage = tourPackageRepository.save(testPackage);
    }

    private CruiseResponse createBasicReadyCruise(String name, Instant dep, Instant ret) {
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
        staffReq.setNotes("Thuyền trưởng điều hành");
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
    @DisplayName("BE-R03.1: Thiếu giá hoặc thiếu tiền tệ trên gói kích hoạt phải bị chặn mở bán có bằng chứng")
    void test_BE_R03_missing_price_or_currency_blocks_sale_readiness() {
        Instant dep = Instant.now().plus(30, ChronoUnit.DAYS);
        Instant ret = dep.plus(4, ChronoUnit.DAYS);
        CruiseResponse cruise = createBasicReadyCruise("Test Price Currency Readiness", dep, ret);

        // Tạo gói bị thiếu giá (price <= 0)
        TourPackage unpricedPkg = new TourPackage();
        unpricedPkg.setPackageCode("PKG-NOPRICE-" + UUID.randomUUID().toString().substring(0, 6));
        unpricedPkg.setPackageName("Gói Chưa Đặt Giá");
        unpricedPkg.setPackageType(TourPackageType.BASIC);
        unpricedPkg.setPrice(BigDecimal.ZERO);
        unpricedPkg.setCurrencyCode("USD");
        unpricedPkg.setStatus(TourPackageStatus.ACTIVE);
        unpricedPkg = tourPackageRepository.save(unpricedPkg);

        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(unpricedPkg.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        CruiseSaleReadinessResponse saleReadiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertNotNull(saleReadiness);
        assertFalse(saleReadiness.isReady());
        assertFalse(saleReadiness.isPricingConfigured());
        assertEquals(ReadinessStatus.NOT_READY, saleReadiness.getStatus());
        assertTrue(saleReadiness.getBlockers().contains("PACKAGE_PRICE_MISSING"));

        ReadinessIssueDetail priceIssue = saleReadiness.getDetails().stream()
                .filter(d -> "PACKAGE_PRICE_MISSING".equals(d.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(priceIssue);
        assertEquals("PACKAGE", priceIssue.getTargetType());
        assertEquals(unpricedPkg.getTourPackageId(), priceIssue.getTargetId());
        assertEquals("PACKAGE", priceIssue.getScope());
        assertEquals(ReadinessSeverity.BLOCKER, priceIssue.getSeverity());
        assertNotNull(priceIssue.getEvidence());
        assertNotNull(priceIssue.getAllowedAction());
        assertEquals("TOUR_PACKAGE_MANAGE", priceIssue.getRequiredPermission());

        // Kiểm tra openSale bị chặn
        assertThrows(BusinessException.class, () -> cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest()));

        // Sửa giá nhưng để trống tiền tệ (currencyCode = "")
        unpricedPkg.setPrice(BigDecimal.valueOf(100.00));
        unpricedPkg.setCurrencyCode("");
        tourPackageRepository.save(unpricedPkg);

        saleReadiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertFalse(saleReadiness.isReady());
        assertFalse(saleReadiness.isPricingConfigured());
        assertTrue(saleReadiness.getBlockers().contains("PACKAGE_CURRENCY_MISSING"));

        ReadinessIssueDetail currIssue = saleReadiness.getDetails().stream()
                .filter(d -> "PACKAGE_CURRENCY_MISSING".equals(d.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(currIssue);
        assertEquals("PACKAGE_CURRENCY_MISSING", currIssue.getCode());
        assertEquals("PACKAGE", currIssue.getScope());
    }

    @Test
    @DisplayName("BE-R03.2: Lỗi hạ tầng / Timeout dependency trả về INDETERMINATE và KHÔNG làm đổi business state của tàu")
    void test_BE_R03_dependency_timeout_returns_indeterminate_and_preserves_ship_active() {
        Instant dep = Instant.now().plus(25, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createBasicReadyCruise("Test Dependency Timeout", dep, ret);

        // Gán gói hợp lệ
        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        try {
            // Mô phỏng timeout / lỗi kết nối tới CruiseRegulationService
            doThrow(new RuntimeException("Gateway timeout while connecting to regulation repository"))
                    .when(cruiseRegulationService).getEffectiveSnapshot(any(), any());

            // 1. Kiểm tra Operational Readiness
            CruiseReadinessResponse opReadiness = cruiseService.validateOperationalReadiness(cruise.getCruiseId());
            assertNotNull(opReadiness);
            assertFalse(opReadiness.isReady(), "Chưa đủ cơ sở sẵn sàng do dependency timeout");
            assertEquals(ReadinessStatus.INDETERMINATE, opReadiness.getStatus(), "Trạng thái phải là INDETERMINATE, không gộp thành boolean giả");

            // QUAN TRỌNG: Lỗi hạ tầng không tự ghi business state 'tàu mất khả dụng'
            assertTrue(opReadiness.isShipActive(), "Tàu vật lý vẫn đang ACTIVE, không bị ghi sai trạng thái");
            assertTrue(opReadiness.isShipConfigurationValid(), "Cấu hình tàu vẫn hợp lệ");

            // Kiểm tra detail chứa DEPENDENCY_TIMEOUT với severity INFRASTRUCTURE_ERROR
            ReadinessIssueDetail infraDetail = opReadiness.getDetails().stream()
                    .filter(d -> "DEPENDENCY_TIMEOUT".equals(d.getCode()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(infraDetail);
            assertEquals("REGULATION", infraDetail.getTargetType());
            assertEquals("GLOBAL", infraDetail.getScope());
            assertEquals(ReadinessSeverity.INFRASTRUCTURE_ERROR, infraDetail.getSeverity());
            assertTrue(infraDetail.getEvidence().contains("RuntimeException"));

            // 2. Kiểm tra Sale Readiness cũng phản ánh INDETERMINATE
            CruiseSaleReadinessResponse saleReadiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
            assertEquals(ReadinessStatus.INDETERMINATE, saleReadiness.getStatus());
            assertFalse(saleReadiness.isReady());

            // 3. Mở bán bị từ chối
            assertThrows(BusinessException.class, () -> cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest()));
        } finally {
            Mockito.reset(cruiseRegulationService);
        }
    }

    @Test
    @DisplayName("BE-R03.3: Quy định tính đúng hiệu lực tại mốc D07 (khởi hành), gồm GLOBAL/SHIP/CRUISE, không ép có quy định riêng của Cruise")
    void test_BE_R03_regulations_effective_at_milestone_D07_departure_datetime() {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createBasicReadyCruise("Test Milestone D07 Regulations", dep, ret);

        // Case A: Có quy định GLOBAL hiệu lực bao trùm ngày khởi hành D07
        OperationalRegulation globalReg = new OperationalRegulation();
        globalReg.setRegulationCode("REG-GLO-" + UUID.randomUUID().toString().substring(0, 6));
        globalReg.setName("Quy tắc an toàn hàng hải toàn cầu");
        globalReg.setDescription("Quy định chung cho mọi tàu");
        globalReg.setRegulationType(RegulationType.SAFETY);
        globalReg.setScope(RegulationScope.GLOBAL);
        globalReg.setValue("STANDARD_MARITIME_SAFETY");
        globalReg.setUnit(RegulationUnit.NONE);
        globalReg.setStatus(RegulationStatus.ACTIVE);
        globalReg.setEffectiveFrom(Instant.now().minus(10, ChronoUnit.DAYS));
        globalReg.setEffectiveTo(dep.plus(100, ChronoUnit.DAYS)); // Còn hiệu lực tại D07
        operationalRegulationRepository.save(globalReg);

        // Đánh giá operational readiness: Cruise KHÔNG có quy định riêng nhưng vẫn thừa hưởng GLOBAL
        CruiseReadinessResponse opRes = cruiseService.validateOperationalReadiness(cruise.getCruiseId());
        assertTrue(opRes.isRegulationsCompliant());
        assertFalse(opRes.getWarnings().contains("NO_EFFECTIVE_REGULATIONS"),
                "Không ép phải có quy định riêng của Cruise nếu Global/Ship đã đáp ứng");

        // Case B: Tạo một chuyến đi khởi hành sau khi quy định hết hạn
        Instant futureDep = dep.plus(200, ChronoUnit.DAYS); // Đã vượt quá effectiveTo (100 days)
        Instant futureRet = futureDep.plus(3, ChronoUnit.DAYS);
        CruiseResponse expiredCruise = createBasicReadyCruise("Test Expired Reg Cruise", futureDep, futureRet);

        // Tại mốc D07 (futureDep), quy định globalReg đã hết hiệu lực!
        var snapshot = cruiseRegulationService.getEffectiveSnapshot(expiredCruise.getCruiseId(), futureDep);
        assertNotNull(snapshot);
        boolean hasGlobalAtFuture = snapshot.getInherited().stream()
                .anyMatch(i -> i.getRegulation().getRegulationCode().equals(globalReg.getRegulationCode()));
        assertFalse(hasGlobalAtFuture, "Quy định đã hết hạn trước mốc D07 không được coi là có hiệu lực");
    }

    @Test
    @DisplayName("BE-R03.4: Tour bắt buộc có nhà cung cấp chưa xác minh/bị từ chối chặn toàn bộ Cruise")
    void test_BE_R03_mandatory_excursion_supplier_unverified_or_rejected_blocks_cruise() {
        Instant dep = Instant.now().plus(50, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createBasicReadyCruise("Mandatory Excursion Cruise", dep, ret);

        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).orElseThrow();

        // Tạo ItineraryDay và CruiseStop trực tiếp
        ItineraryDay day = new ItineraryDay();
        day.setItinerary(itinerary);
        day.setDayNumber(1);
        day.setDate(itinerary.getStartDate());
        day.setDayType(ItineraryDayType.PORT_DAY);
        day = itineraryDayRepository.save(day);

        CruiseStop stop = new CruiseStop();
        stop.setItinerary(itinerary);
        stop.setArrivalItineraryDay(day);
        stop.setDepartureItineraryDay(day);
        stop.setPort(portHph);
        stop.setStopOrder(1);
        stop.setPlannedArrival(dep.plus(1, ChronoUnit.DAYS));
        stop.setPlannedDeparture(dep.plus(1, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS));
        stop.setCallType(CruiseStopCallType.NORMAL);
        stop.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop.setStatus(CruiseStopStatus.PLANNED);
        stop = cruiseStopRepository.save(stop);

        // Tạo nhà cung cấp bị REJECTED
        Supplier rejectedSupplier = new Supplier();
        rejectedSupplier.setSupplierName("Công ty Tour Bị Từ Chối");
        rejectedSupplier.setContactName("Nguyễn Văn A");
        rejectedSupplier.setStatus(SupplierStatus.ACTIVE);
        rejectedSupplier.setVerificationStatus(SupplierVerificationStatus.REJECTED);
        rejectedSupplier = supplierRepository.save(rejectedSupplier);

        // Gán tour bắt buộc (price = 0)
        ShoreExcursion mandatoryExcursion = new ShoreExcursion();
        mandatoryExcursion.setCruiseStop(stop);
        mandatoryExcursion.setPort(stop.getPort());
        mandatoryExcursion.setExcursionCode("EXC-MAND-" + UUID.randomUUID().toString().substring(0, 5));
        mandatoryExcursion.setExcursionName("Tham quan vịnh bắt buộc");
        mandatoryExcursion.setSupplier(rejectedSupplier);
        mandatoryExcursion.setStartTime(dep.plus(1, ChronoUnit.DAYS));
        mandatoryExcursion.setPlannedReturnTime(dep.plus(1, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS));
        mandatoryExcursion.setMaximumCapacity(50);
        mandatoryExcursion.setPrice(BigDecimal.ZERO); // Tour bắt buộc kèm chuyến
        mandatoryExcursion.setStatus(ShoreExcursionStatus.OPEN);
        mandatoryExcursion.setMeetingPoint("Sảnh chính tầng 1");
        shoreExcursionRepository.save(mandatoryExcursion);

        // Đánh giá Sale Readiness
        CruiseSaleReadinessResponse saleReadiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertNotNull(saleReadiness);
        assertFalse(saleReadiness.isReady(), "Tour bắt buộc có supplier bị từ chối phải chặn mở bán");
        assertEquals(ReadinessStatus.NOT_READY, saleReadiness.getStatus());
        assertTrue(saleReadiness.getBlockers().contains("MANDATORY_EXCURSION_SUPPLIER_REJECTED"));

        ReadinessIssueDetail issue = saleReadiness.getDetails().stream()
                .filter(d -> "SUPPLIER_REJECTED".equals(d.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(issue);
        assertEquals("CRUISE", issue.getScope(), "Phạm vi ảnh hưởng của tour bắt buộc phải là CRUISE");
        assertEquals(ReadinessSeverity.BLOCKER, issue.getSeverity());

        // Cố tình mở bán sẽ bị chặn
        assertThrows(BusinessException.class, () -> cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest()));
    }

    @Test
    @DisplayName("BE-R03.5: Tour tự chọn (Optional) lỗi supplier chỉ chặn scope EXCURSION, không tự chặn cả Cruise")
    void test_BE_R03_optional_excursion_unready_blocks_only_excursion_scope_not_cruise() {
        Instant dep = Instant.now().plus(60, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createBasicReadyCruise("Optional Excursion Cruise", dep, ret);

        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).orElseThrow();

        ItineraryDay day = new ItineraryDay();
        day.setItinerary(itinerary);
        day.setDayNumber(1);
        day.setDate(itinerary.getStartDate());
        day.setDayType(ItineraryDayType.PORT_DAY);
        day = itineraryDayRepository.save(day);

        CruiseStop stop = new CruiseStop();
        stop.setItinerary(itinerary);
        stop.setArrivalItineraryDay(day);
        stop.setDepartureItineraryDay(day);
        stop.setPort(portHph);
        stop.setStopOrder(1);
        stop.setPlannedArrival(dep.plus(1, ChronoUnit.DAYS));
        stop.setPlannedDeparture(dep.plus(1, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS));
        stop.setCallType(CruiseStopCallType.NORMAL);
        stop.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop.setStatus(CruiseStopStatus.PLANNED);
        stop = cruiseStopRepository.save(stop);

        // Tạo nhà cung cấp chưa xác minh (PENDING)
        Supplier unverifiedSup = new Supplier();
        unverifiedSup.setSupplierName("Công ty Cano Lặn Biển");
        unverifiedSup.setContactName("Trần Văn B");
        unverifiedSup.setStatus(SupplierStatus.ACTIVE);
        unverifiedSup.setVerificationStatus(SupplierVerificationStatus.PENDING);
        unverifiedSup = supplierRepository.save(unverifiedSup);

        // Gán tour TỰ CHỌN (price > 0)
        ShoreExcursion optionalExcursion = new ShoreExcursion();
        optionalExcursion.setCruiseStop(stop);
        optionalExcursion.setPort(stop.getPort());
        optionalExcursion.setExcursionCode("EXC-OPT-" + UUID.randomUUID().toString().substring(0, 5));
        optionalExcursion.setExcursionName("Lặn ngắm san hô (Tự chọn)");
        optionalExcursion.setSupplier(unverifiedSup);
        optionalExcursion.setStartTime(dep.plus(1, ChronoUnit.DAYS));
        optionalExcursion.setPlannedReturnTime(dep.plus(1, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS));
        optionalExcursion.setMaximumCapacity(20);
        optionalExcursion.setPrice(BigDecimal.valueOf(45.00)); // Tour tự chọn tính phí
        optionalExcursion.setStatus(ShoreExcursionStatus.OPEN);
        optionalExcursion.setMeetingPoint("Bến thuyền số 2");
        shoreExcursionRepository.save(optionalExcursion);

        // Đánh giá Sale Readiness
        CruiseSaleReadinessResponse saleReadiness = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertNotNull(saleReadiness);

        // QUAN TRỌNG: Tour tự chọn lỗi KHÔNG được chặn cả Cruise!
        assertTrue(saleReadiness.isReady(), "Cruise tổng thể vẫn sẵn sàng mở bán");
        assertEquals(ReadinessStatus.WARNING, saleReadiness.getStatus());
        assertFalse(saleReadiness.getBlockers().contains("MANDATORY_EXCURSION_SUPPLIER_UNVERIFIED"));

        // Detail phải chỉ rõ scope là EXCURSION, mức độ WARNING
        ReadinessIssueDetail issue = saleReadiness.getDetails().stream()
                .filter(d -> "SUPPLIER_NOT_VERIFIED".equals(d.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(issue);
        assertEquals("EXCURSION", issue.getScope(), "Phạm vi phải được phân lập đúng là EXCURSION, không tự chặn Cruise");
        assertEquals(ReadinessSeverity.WARNING, issue.getSeverity());

        // Cruise vẫn mở bán thành công
        assertDoesNotThrow(() -> cruiseService.openSale(cruise.getCruiseId(), new CruiseOpenSaleRequest()));
    }

    @Test
    @DisplayName("BE-R03.6: Nguồn lực độc quyền (Tàu/Thuyền trưởng) có kiểm tra xung đột khác với tài nguyên chia sẻ")
    void test_BE_R03_personnel_and_exclusive_resource_conflict() {
        Instant dep = Instant.now().plus(70, ChronoUnit.DAYS);
        Instant ret = dep.plus(4, ChronoUnit.DAYS);

        // 1. Tạo Cruise A sử dụng testShip và testCaptain
        CruiseResponse cruiseA = createBasicReadyCruise("Cruise Exclusive A", dep, ret);

        // 2. Cố tình tạo Cruise B trùng lịch với testShip qua API -> Bị chặn ngay từ tầng khởi tạo
        CruiseCreateRequest reqB = new CruiseCreateRequest();
        reqB.setCruiseName("Cruise Exclusive B (Trùng Tàu)");
        reqB.setDepartureDatetime(dep.plus(1, ChronoUnit.DAYS)); // Trùng khoảng [dep, ret]
        reqB.setReturnDatetime(ret.plus(1, ChronoUnit.DAYS));
        reqB.setDeparturePortId(portHph.getPortId());
        reqB.setReturnPortId(portDan.getPortId());
        reqB.setShipId(testShip.getShipId()); // Cùng tàu độc quyền

        BusinessException ex = assertThrows(BusinessException.class, () -> cruiseService.create(reqB));
        assertEquals("SHIP_SCHEDULE_CONFLICT", ex.getCode());

        // 3. Tạo Cruise B không gán tàu ban đầu, sau đó gán tàu trực tiếp để kiểm tra Operational Readiness gate
        reqB.setShipId(null);
        CruiseResponse cruiseB = cruiseService.create(reqB);

        // Gán tàu bị trùng lịch trực tiếp vào Cruise B
        Cruise entityB = cruiseRepository.findById(cruiseB.getCruiseId()).orElseThrow();
        entityB.setShip(testShip);
        cruiseRepository.save(entityB);

        // Đánh giá Cruise B -> Bị xung đột độc quyền tàu (SHIP_SCHEDULE_CONFLICT) và thiếu thuyền trưởng
        CruiseReadinessResponse opResB = cruiseService.validateOperationalReadiness(cruiseB.getCruiseId());
        assertFalse(opResB.isReady());
        assertTrue(opResB.getBlockers().contains("SHIP_SCHEDULE_CONFLICT"));

        ReadinessIssueDetail shipConflictDetail = opResB.getDetails().stream()
                .filter(d -> "SHIP_SCHEDULE_CONFLICT".equals(d.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(shipConflictDetail);
        assertEquals("SHIP", shipConflictDetail.getTargetType());
        assertEquals("SHIP", shipConflictDetail.getScope());
        assertEquals(ReadinessSeverity.BLOCKER, shipConflictDetail.getSeverity());
        assertEquals("CRUISE_CHANGE_SHIP", shipConflictDetail.getRequiredPermission());

        // Thiếu thuyền trưởng (Cruise B chưa gán thuyền trưởng)
        assertTrue(opResB.getBlockers().contains("MISSING_CAPTAIN"));
        ReadinessIssueDetail captainDetail = opResB.getDetails().stream()
                .filter(d -> "MISSING_CAPTAIN".equals(d.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(captainDetail);
        assertEquals("CRUISE", captainDetail.getTargetType());
        assertEquals(ReadinessSeverity.BLOCKER, captainDetail.getSeverity());
    }

    @Test
    @DisplayName("BE-R03.7: Thay đổi dữ liệu phụ thuộc ngoài Cruise làm đổi fingerprint mà không cần bump revision của Cruise")
    void test_BE_R03_external_dependency_change_updates_fingerprint_without_cruise_revision_bump() {
        Instant dep = Instant.now().plus(90, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createBasicReadyCruise("Fingerprint Freshness Cruise", dep, ret);

        TourPackage pkg = new TourPackage();
        pkg.setPackageCode("PKG-FING-" + UUID.randomUUID().toString().substring(0, 6));
        pkg.setPackageName("Gói Khám Phá");
        pkg.setPackageType(TourPackageType.BASIC);
        pkg.setPrice(BigDecimal.valueOf(150.00));
        pkg.setCurrencyCode("USD");
        pkg.setStatus(TourPackageStatus.ACTIVE);
        pkg = tourPackageRepository.save(pkg);

        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(pkg.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        // 1. Đánh giá sale readiness ban đầu
        CruiseSaleReadinessResponse initialRes = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        String initialFingerprint = initialRes.getFingerprint();
        assertNotNull(initialFingerprint);
        Long initialRevision = cruiseRepository.findById(cruise.getCruiseId()).orElseThrow().getRevision();

        // 2. Không sửa Cruise (không bump revision). Sửa dữ liệu ngoài: Đổi giá của TourPackage từ 150 -> 250
        pkg.setPrice(BigDecimal.valueOf(250.00));
        tourPackageRepository.save(pkg);

        // 3. Đánh giá lại Sale Readiness
        CruiseSaleReadinessResponse refreshedRes = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        String refreshedFingerprint = refreshedRes.getFingerprint();
        Long refreshedRevision = cruiseRepository.findById(cruise.getCruiseId()).orElseThrow().getRevision();

        // Revision của Cruise không đổi
        assertEquals(initialRevision, refreshedRevision, "Cruise entity không bị sửa nên revision giữ nguyên");

        // Nhưng Fingerprint PHẢI đổi vì phụ thuộc bên ngoài đã thay đổi!
        assertNotEquals(initialFingerprint, refreshedFingerprint,
                "Fingerprint phải phản ánh sự thay đổi của dữ liệu ngoài (giá package) để phát hiện stale cache");
    }

    @Test
    @DisplayName("BE-R03.8: C09 (/sale-readiness) là API tra cứu đánh giá, tuyệt đối không tự mở bán Cruise")
    void test_BE_R03_C09_sale_readiness_does_not_open_sale() {
        Instant dep = Instant.now().plus(100, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        CruiseResponse cruise = createBasicReadyCruise("C09 Read-Only Cruise", dep, ret);

        CruiseTourPackageRequest pkgReq = new CruiseTourPackageRequest();
        pkgReq.setTourPackageId(testPackage.getTourPackageId());
        cruisePackageService.assignPackage(cruise.getCruiseId(), pkgReq);

        // Gọi C09 nhiều lần
        CruiseSaleReadinessResponse r1 = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertTrue(r1.isReady());

        CruiseSaleReadinessResponse r2 = cruiseService.validateSaleReadiness(cruise.getCruiseId());
        assertTrue(r2.isReady());

        // Kiểm tra Cruise vẫn ở trạng thái DRAFT
        Cruise currentCruise = cruiseRepository.findById(cruise.getCruiseId()).orElseThrow();
        assertEquals(CruiseStatus.DRAFT, currentCruise.getStatus(), "C09 không được tự động chuyển Cruise sang PUBLISHED");

        // Kiểm tra Itinerary vẫn ở trạng thái APPROVED (chưa phải OPEN_FOR_SALE)
        Itinerary currentItinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).orElseThrow();
        assertEquals(ItineraryStatus.APPROVED, currentItinerary.getStatus(), "C09 không được tự động chuyển Itinerary sang OPEN_FOR_SALE");
    }
}
