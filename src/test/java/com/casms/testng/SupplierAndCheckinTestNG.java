package com.casms.testng;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import com.casms.activity.service.SupplierService;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingContract;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingContractRepository;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.checkin.entity.CruiseCheckin;
import com.casms.checkin.enums.CruiseCheckinStatus;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.checkin.service.CheckinService;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.ship.entity.Cruise;
import com.casms.testng.listener.CasmsTestListener;

import jakarta.persistence.EntityNotFoundException;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.testng.Assert;
import org.testng.annotations.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Listeners(CasmsTestListener.class)
public class SupplierAndCheckinTestNG {

    private AutoCloseable mocks;

    @Mock private SupplierRepository supplierRepository;
    @Mock private ShoreExcursionRepository shoreExcursionRepository;
    @InjectMocks private SupplierService supplierService;

    @Mock private CruiseCheckinRepository checkinRepository;
    @Mock private BookingPassengerRepository bookingPassengerRepository;
    @Mock private BookingContractRepository bookingContractRepository;
    @InjectMocks private CheckinService checkinService;

    private UUID supplierId;
    private Supplier supplier;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        supplierService = null;
        checkinService = null;
        mocks = MockitoAnnotations.openMocks(this);
        supplierId = UUID.randomUUID();
        supplier = new Supplier();
        supplier.setSupplierName("Ha Long Excursion Co.");
        supplier.setContactName("Le Minh");
        supplier.setStatus(SupplierStatus.INACTIVE);
        supplier.setVerificationStatus(SupplierVerificationStatus.PENDING);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test(groups = {"smoke", "regression"})
    public void testActivateVerifiedSupplierTransitionsToActive() {
        supplier.setVerificationStatus(SupplierVerificationStatus.VERIFIED);
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(supplier)).thenReturn(supplier);

        Supplier result = supplierService.activate(supplierId);

        Assert.assertEquals(result.getStatus(), SupplierStatus.ACTIVE);
        verify(supplierRepository).save(supplier);
    }

    @Test(groups = {"regression", "boundary"}, expectedExceptions = IllegalStateException.class)
    public void testActivateUnverifiedSupplierThrowsIllegalState() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        supplierService.activate(supplierId);
    }

    @Test(groups = {"regression"}, expectedExceptions = EntityNotFoundException.class)
    public void testDetailMissingSupplierThrowsEntityNotFound() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.empty());
        supplierService.detail(supplierId);
    }

    @Test(groups = {"regression", "boundary"})
    public void testDeleteUnusedRejectsSupplierReferencedByExcursions() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(shoreExcursionRepository.existsBySupplier_SupplierId(supplierId)).thenReturn(true);

        Assert.expectThrows(IllegalStateException.class, () -> supplierService.deleteUnused(supplierId));
        verify(supplierRepository, never()).delete(any(Supplier.class));
    }

    @DataProvider(name = "checkinWindowSecondsData")
    public Object[][] checkinWindowSecondsData() {
        return new Object[][] {
            {10801L, false},
            {10800L, true},
            {10799L, true},
            {3600L, true},
            {0L, false},
        };
    }

    @Test(dataProvider = "checkinWindowSecondsData", groups = {"smoke", "regression", "boundary"})
    public void testOnlineCheckinRespectsDepartureWindow(
            long secondsBeforeDeparture, boolean expectedOpen) {
        UUID ownerId = UUID.randomUUID();
        BookingPassenger bp = buildBookingPassenger(ownerId);
        UUID bpId = bp.getBookingPassengerId();
        Instant now = Instant.parse("2030-02-01T09:00:00Z");
        bp.getBooking().getCruise().setDepartureDatetime(now.plusSeconds(secondsBeforeDeparture));

        when(bookingPassengerRepository.lockById(bpId)).thenReturn(Optional.of(bp));
        CruiseCheckin checkin = new CruiseCheckin();
        checkin.setBookingPassenger(bp);
        checkin.setCheckinStatus(CruiseCheckinStatus.InProgress);
        checkin.setEmergencyContactName("Nguyen Emergency");
        checkin.setEmergencyContactPhone("0900000000");
        when(checkinRepository.findByBookingPassenger_BookingPassengerId(bpId))
                .thenReturn(Optional.of(checkin));

        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(now);
            if (!expectedOpen) {
                BusinessException ex =
                        Assert.expectThrows(
                                BusinessException.class,
                                () -> checkinService.startCheckin(bpId, null, null, ownerId, false));
                Assert.assertEquals(ex.getCode(), "CHECKIN_WINDOW_CLOSED");
            } else {
                BookingContract contract = new BookingContract();
                contract.setStatus(BookingContractStatus.Accepted);
                when(bookingContractRepository.findByBookingBookingId(bp.getBooking().getBookingId()))
                        .thenReturn(Optional.of(contract));
                when(checkinRepository.save(checkin)).thenReturn(checkin);

                CruiseCheckin started = checkinService.startCheckin(bpId, null, null, ownerId, false);
                Assert.assertEquals(started.getCheckinStatus(), CruiseCheckinStatus.InProgress);
                CruiseCheckin completed = checkinService.completeCheckin(bpId, ownerId, false);
                Assert.assertEquals(completed.getCheckinStatus(), CruiseCheckinStatus.BoardingReady);
            }
        }
    }

    @Test(groups = {"regression", "security"}, expectedExceptions = AccessDeniedException.class)
    public void testPassengerCannotStartAnotherPassengersCheckin() {
        BookingPassenger bp = buildBookingPassenger(UUID.randomUUID());
        when(bookingPassengerRepository.lockById(bp.getBookingPassengerId()))
                .thenReturn(Optional.of(bp));

        checkinService.startCheckin(bp.getBookingPassengerId(), null, null, UUID.randomUUID(), false);
    }

    private BookingPassenger buildBookingPassenger(UUID ownerId) {
        User owner = new User();
        owner.setUserId(ownerId);
        Passenger passenger = new Passenger();
        passenger.setPassengerId(UUID.randomUUID());
        passenger.setUser(owner);

        Cruise cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setDepartureDatetime(Instant.now().plusSeconds(3600));

        Booking booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setBookedByUser(owner);
        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setCruise(cruise);

        BookingPassenger bp = new BookingPassenger();
        bp.setBookingPassengerId(UUID.randomUUID());
        bp.setBooking(booking);
        bp.setPassenger(passenger);
        bp.setParticipationStatus(BookingPassengerParticipationStatus.Active);
        return bp;
    }
}
