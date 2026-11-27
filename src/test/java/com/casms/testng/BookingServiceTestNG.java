package com.casms.testng;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casms.booking.dto.request.BookingCancelRequest;
import com.casms.booking.dto.request.BookingCreateRequest;
import com.casms.booking.dto.request.PassengerBookingItemRequest;
import com.casms.booking.dto.response.BookingDetailResponse;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingContract;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.PaymentPlan;
import com.casms.booking.repository.*;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.PassengerDocumentType;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.notification.repository.NotificationRepository;
import com.casms.notification.service.BookingEmailService;
import com.casms.notification.service.NotificationService;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.RefundRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.repository.BookingPassengerPackageRepository;
import com.casms.servicepackage.repository.TourPackageRepository;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.repository.*;
import com.casms.ship.service.CruiseMainflowService;
import com.casms.testng.listener.CasmsTestListener;
import com.casms.trip.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Listeners(CasmsTestListener.class)
public class BookingServiceTestNG {

    private AutoCloseable mocks;

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingPassengerRepository bookingPassengerRepository;
    @Mock private CabinAssignmentRepository cabinAssignmentRepository;
    @Mock private BookingContractRepository bookingContractRepository;
    @Mock private CruiseRepository cruiseRepository;
    @Mock private CabinRepository cabinRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private TourPackageRepository tourPackageRepository;
    @Mock private BookingPassengerPackageRepository bookingPassengerPackageRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationService notificationService;
    @Mock private BookingEmailService bookingEmailService;
    @Mock private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock private BusinessCodeGenerator businessCodeGenerator;
    @Mock private CruiseTourPackageRepository cruiseTourPackageRepository;
    @Mock private ItineraryRepository itineraryRepository;
    @Mock private CruisePricingPolicyRepository cruisePricingPolicyRepository;
    @Mock private BookingQuoteRepository bookingQuoteRepository;
    @Mock private CruiseMainflowService mainflow;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private BookingService bookingService;

    private UUID cruiseId;
    private UUID cabinId;
    private Ship ship;
    private Cruise cruise;
    private Cabin cabin;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        bookingService = null;
        mocks = MockitoAnnotations.openMocks(this);
        cruiseId = UUID.randomUUID();
        cabinId = UUID.randomUUID();

        ship = new Ship();
        ship.setShipId(UUID.randomUUID());
        ship.setShipName("OceVie Heritage");
        ship.setTotalCapacity(500);

        Deck deck = new Deck();
        deck.setDeckId(UUID.randomUUID());
        deck.setDeckNumber(3);
        deck.setDeckName("Deck 3");
        deck.setShip(ship);

        cabin = new Cabin();
        cabin.setCabinId(cabinId);
        cabin.setCabinNumber("C301");
        cabin.setCabinType(CabinType.BALCONY);
        cabin.setCapacity(2);
        cabin.setStatus(CabinStatus.AVAILABLE);
        cabin.setShip(ship);
        cabin.setDeck(deck);

        cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        cruise.setCruiseCode("CR-2026-NG01");
        cruise.setCruiseName("Halong Heritage Voyage");
        cruise.setStatus(CruiseStatus.PUBLISHED);
        cruise.setOpenedForSaleAt(Instant.now().minus(1, ChronoUnit.DAYS));
        cruise.setDepartureDatetime(Instant.now().plus(30, ChronoUnit.DAYS));
        cruise.setReturnDatetime(Instant.now().plus(33, ChronoUnit.DAYS));
        cruise.setShip(ship);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @DataProvider(name = "invalidBookingRequestData")
    public Object[][] invalidBookingRequestData() {
        return new Object[][] {
            {"EMPTY_EMAIL", "", "P111", "PRIMARY_EMAIL_REQUIRED"},
            {"BLANK_EMAIL", "   ", "P222", "PRIMARY_EMAIL_REQUIRED"},
        };
    }

    @Test(dataProvider = "invalidBookingRequestData", groups = {"smoke", "regression", "boundary"})
    public void testCreateBookingRejectsMissingPrimaryEmail(
            String label, String email, String docNo, String expectedCode) {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setCruiseId(cruiseId);
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setFullName("Guest " + label);
        p.setEmail(email);
        p.setDocumentType(PassengerDocumentType.Passport);
        p.setDocumentNumber(docNo);
        p.setCabinId(cabinId);
        request.setPassengers(List.of(p));

        BusinessException ex =
                Assert.expectThrows(
                        BusinessException.class, () -> bookingService.createBooking(request, null));
        Assert.assertEquals(ex.getCode(), expectedCode);
    }

    @Test(groups = {"regression", "boundary"})
    public void testCreateBookingRejectsCabinOvercapacity() {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setCruiseId(cruiseId);
        List<PassengerBookingItemRequest> passengers = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            PassengerBookingItemRequest p = new PassengerBookingItemRequest();
            p.setFullName("Guest " + i);
            p.setEmail("guest" + i + "@example.com");
            p.setCabinId(cabinId);
            passengers.add(p);
        }
        request.setPassengers(passengers);

        User account = new User();
        account.setUserId(UUID.randomUUID());
        account.setRoles(EnumSet.of(Role.PASSENGER));
        account.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByNormalizedEmail("guest1@example.com")).thenReturn(Optional.of(account));
        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        Itinerary itinerary = new Itinerary();
        itinerary.setStatus(ItineraryStatus.OPEN_FOR_SALE);
        when(itineraryRepository.findByCruiseCruiseId(cruiseId)).thenReturn(Optional.of(itinerary));
        when(bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId)).thenReturn(0L);
        when(cabinRepository.findByIdForUpdate(cabinId)).thenReturn(Optional.of(cabin));
        when(cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(cabinId, cruiseId))
                .thenReturn(0L);

        BusinessException ex =
                Assert.expectThrows(
                        BusinessException.class, () -> bookingService.createBooking(request, null));
        Assert.assertEquals(ex.getCode(), "CABIN_OVERCAPACITY");
    }

    @Test(groups = {"smoke", "regression", "security"})
    public void testLookupBookingRequiresContactCredential() {
        BusinessException ex =
                Assert.expectThrows(
                        BusinessException.class,
                        () -> bookingService.lookupBooking("BK-0001", " ", null));
        Assert.assertEquals(ex.getCode(), "LOOKUP_CREDENTIAL_REQUIRED");
        verify(bookingRepository, never()).findByBookingReference(any());
    }

    @DataProvider(name = "blockedItineraryStatuses")
    public Object[][] blockedItineraryStatuses() {
        return new Object[][] {
            {ItineraryStatus.APPROVED, "CRUISE_NOT_OPEN_FOR_SALE"},
            {ItineraryStatus.SUSPENDED, "SALE_SUSPENDED"},
        };
    }

    @Test(dataProvider = "blockedItineraryStatuses", groups = {"regression", "boundary"})
    public void testCreateBookingBlockedByItinerarySaleState(
            ItineraryStatus status, String expectedErrorCode) {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setCruiseId(cruiseId);
        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setFullName("Nguyen Van A");
        p.setEmail("guest@example.com");
        p.setCabinId(cabinId);
        request.setPassengers(List.of(p));

        User account = new User();
        account.setUserId(UUID.randomUUID());
        account.setRoles(EnumSet.of(Role.PASSENGER));
        account.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByNormalizedEmail("guest@example.com")).thenReturn(Optional.of(account));
        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        Itinerary itinerary = new Itinerary();
        itinerary.setStatus(status);
        when(itineraryRepository.findByCruiseCruiseId(cruiseId)).thenReturn(Optional.of(itinerary));

        BusinessException ex =
                Assert.expectThrows(
                        BusinessException.class, () -> bookingService.createBooking(request, null));
        Assert.assertEquals(ex.getCode(), expectedErrorCode);
    }

    @Test(groups = {"regression", "boundary"})
    public void testCreateBookingRejectsDuplicatePassengerDocuments() {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setCruiseId(cruiseId);

        PassengerBookingItemRequest p1 = new PassengerBookingItemRequest();
        p1.setFullName("Nguyen Van A");
        p1.setEmail("a@example.com");
        p1.setDocumentType(PassengerDocumentType.Passport);
        p1.setDocumentNumber("P9988776");
        p1.setCabinId(cabinId);

        PassengerBookingItemRequest p2 = new PassengerBookingItemRequest();
        p2.setFullName("Nguyen Van B");
        p2.setEmail("b@example.com");
        p2.setDocumentType(PassengerDocumentType.Passport);
        p2.setDocumentNumber("P9988776");
        p2.setCabinId(cabinId);

        request.setPassengers(List.of(p1, p2));

        BusinessException ex =
                Assert.expectThrows(
                        BusinessException.class, () -> bookingService.createBooking(request, null));
        Assert.assertEquals(ex.getCode(), "DUPLICATE_PASSENGER_DOCUMENT");
    }

    @Test(groups = {"regression", "security"})
    public void testConfirmBookingForbiddenForPassengerAuthority() {
        UUID bookingId = UUID.randomUUID();
        CasmsPrincipal principal = mock(CasmsPrincipal.class);
        when(principal.getUserId()).thenReturn(UUID.randomUUID());
        doReturn(List.of(new SimpleGrantedAuthority("BOOKING_CREATE")))
                .when(principal)
                .getAuthorities();

        BusinessException ex =
                Assert.expectThrows(
                        BusinessException.class, () -> bookingService.confirm(bookingId, principal));
        Assert.assertEquals(ex.getCode(), "BOOKING_CONFIRM_FORBIDDEN");
    }

    @Test(groups = {"smoke", "regression"})
    public void testCancelDraftBookingSucceedsAndRecordsReason() {
        UUID bookingId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = new User();
        owner.setUserId(ownerId);

        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setAgreedFareAmount(new BigDecimal("5000000"));
        booking.setBookedByUser(owner);
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setCruise(cruise);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingPassengerRepository.findByBookingBookingId(bookingId)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        CasmsPrincipal principal = mock(CasmsPrincipal.class);
        when(principal.getUserId()).thenReturn(ownerId);
        when(principal.getAuthorities()).thenReturn(List.of());

        BookingCancelRequest cancelReq = new BookingCancelRequest();
        cancelReq.setReason("Thay doi ke hoach ca nhan");

        BookingDetailResponse response = bookingService.cancel(bookingId, cancelReq, principal);

        SoftAssert soft = new SoftAssert();
        soft.assertEquals(response.getBookingStatus(), BookingStatus.Cancelled);
        soft.assertEquals(response.getCancellationReason(), "Thay doi ke hoach ca nhan");
        soft.assertAll();
    }

    @Test(groups = {"regression"})
    public void testGenerateContractHtmlContainsAllBookingFields() {
        UUID testBookingId = UUID.randomUUID();
        User owner = new User();
        owner.setUserId(UUID.randomUUID());
        owner.setUsername("passenger_demo");
        owner.setEmail("passenger@casms.com");

        Booking booking = new Booking();
        booking.setBookingId(testBookingId);
        booking.setBookingReference("BK-NG-2026");
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setBookedByUser(owner);
        booking.setCruise(cruise);
        booking.setAgreedFareAmount(new BigDecimal("12000000"));
        booking.setCurrency("VND");
        booking.setRequiredDepositAmount(new BigDecimal("3600000"));

        BookingContract contract = new BookingContract();
        contract.setContractReference("CTR-BK-NG-2026");
        contract.setStatus(BookingContractStatus.Draft);

        Passenger passenger = new Passenger();
        passenger.setFullName("Tran Minh Quan");
        passenger.setDateOfBirth(LocalDate.of(1993, 8, 15));
        passenger.setNationality("VN");
        passenger.setDocumentType(PassengerDocumentType.NationalID);
        passenger.setDocumentNumberProtected("001093008888");
        passenger.setEmail("quan@casms.com");
        passenger.setPhoneNumber("0909123456");

        BookingPassenger bp = new BookingPassenger();
        bp.setBookingPassengerId(UUID.randomUUID());
        bp.setBooking(booking);
        bp.setPassenger(passenger);
        bp.setParticipationStatus(BookingPassengerParticipationStatus.Active);
        bp.setRequestedCabinCategory("BALCONY");

        when(bookingRepository.findById(testBookingId)).thenReturn(Optional.of(booking));
        when(bookingContractRepository.findByBookingBookingId(testBookingId))
                .thenReturn(Optional.of(contract));
        when(bookingPassengerRepository.findByBookingBookingId(testBookingId))
                .thenReturn(List.of(bp));
        when(bookingPassengerPackageRepository.findByBookingPassengerBookingBookingId(testBookingId))
                .thenReturn(Collections.emptyList());
        when(cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(
                        bp.getBookingPassengerId()))
                .thenReturn(Optional.empty());
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(testBookingId))
                .thenReturn(Collections.emptyList());

        CasmsPrincipal principal = mock(CasmsPrincipal.class);
        when(principal.getUserId()).thenReturn(owner.getUserId());

        String html = bookingService.generateContractHtml(testBookingId, principal);

        SoftAssert soft = new SoftAssert();
        soft.assertNotNull(html);
        soft.assertTrue(html.contains("BK-NG-2026"), "Missing booking reference");
        soft.assertTrue(html.contains("CTR-BK-NG-2026"), "Missing contract reference");
        soft.assertTrue(html.contains("Tran Minh Quan"), "Missing passenger name");
        soft.assertTrue(html.contains("001093008888"), "Missing document number");
        soft.assertTrue(html.contains("Halong Heritage Voyage"), "Missing cruise name");
        soft.assertTrue(html.contains("OceVie Heritage"), "Missing ship name");
        soft.assertAll();
    }
}
