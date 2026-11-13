package com.casms.activity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.Registration;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.RegistrationStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.payment.entity.OnboardAccount;
import com.casms.payment.entity.OnboardTransaction;
import com.casms.payment.enums.TransactionSyncStatus;
import com.casms.payment.repository.OnboardTransactionRepository;
import com.casms.pos.PosDtos;
import com.casms.pos.PosLedgerService;
import com.casms.servicepackage.entity.BookingPassengerPackage;
import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.repository.BookingPassengerPackageRepository;
import com.casms.servicepackage.repository.TourPackageActivityRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class OnboardOperationServiceTest {

    @Mock OnboardActivityRepository activities;
    @Mock RegistrationRepository registrations;
    @Mock BookingPassengerRepository bookingPassengers;
    @Mock TourPackageActivityRepository packageActivities;
    @Mock BookingPassengerPackageRepository bookingPassengerPackageRepository;
    @Mock PosLedgerService posLedgerService;
    @Mock OnboardTransactionRepository onboardTransactionRepository;
    @Mock NotificationRepository notificationRepository;

    @InjectMocks OnboardOperationService service;

    private UUID bpId;
    private UUID activityId;
    private BookingPassenger bookingPassenger;
    private OnboardActivity activity;
    private User operator;

    @BeforeEach
    void setUp() {
        bpId = UUID.randomUUID();
        activityId = UUID.randomUUID();

        Passenger passenger = new Passenger();
        passenger.setPassengerId(UUID.randomUUID());

        Booking booking = new Booking();
        bookingPassenger = new BookingPassenger();
        bookingPassenger.setBookingPassengerId(bpId);
        bookingPassenger.setPassenger(passenger);
        bookingPassenger.setBooking(booking);

        activity = new OnboardActivity();
        activity.setOnboardActivityId(activityId);
        activity.setActivityCode("AC-TEST-01");
        activity.setActivityName("Sunset Wine Tasting");
        activity.setStatus(OnboardActivityStatus.OPEN);
        activity.setCapacity(10);
        activity.setPrice(BigDecimal.valueOf(50));
        activity.setCurrencyCode("USD");
        activity.setLocation("Sundeck Tầng 10");

        operator = new User();
        operator.setUserId(UUID.randomUUID());
        operator.setUsername("staff01");
    }

    @Test
    void bookActivity_whenIncludedInPackage_chargesZero() {
        when(bookingPassengers.findById(bpId)).thenReturn(Optional.of(bookingPassenger));
        when(registrations.existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(
                eq(bpId), eq(activityId), anyList())).thenReturn(false);
        when(activities.lockActivity(activityId)).thenReturn(Optional.of(activity));
        when(registrations.sumOnboardParticipants(eq(activityId), anyList())).thenReturn(2L);

        TourPackage tourPackage = new TourPackage();
        UUID pkgId = UUID.randomUUID();
        tourPackage.setTourPackageId(pkgId);
        BookingPassengerPackage bpp = new BookingPassengerPackage();
        bpp.setTourPackage(tourPackage);
        when(bookingPassengerPackageRepository.lockByBookingPassengerId(bpId)).thenReturn(Optional.of(bpp));
        when(packageActivities.existsByTourPackage_TourPackageIdAndOnboardActivity_OnboardActivityId(pkgId, activityId))
                .thenReturn(true);

        when(registrations.save(any(Registration.class))).thenAnswer(invocation -> {
            Registration r = invocation.getArgument(0);
            r.setRegistrationId(UUID.randomUUID());
            return r;
        });

        var result = service.bookActivityWithFolio(bpId, activityId, 1, operator);

        assertNotNull(result);
        assertTrue(result.includedInPackage());
        assertEquals(BigDecimal.ZERO, result.chargedAmount());
        assertNull(result.folioTransactionId());
        verify(onboardTransactionRepository, never()).save(any());
    }

    @Test
    void bookActivity_whenAlreadyRegistered_throwsBusinessException() {
        when(bookingPassengers.findById(bpId)).thenReturn(Optional.of(bookingPassenger));
        when(registrations.existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(
                eq(bpId), eq(activityId), anyList())).thenReturn(true);

        assertThrows(BusinessException.class, () ->
                service.bookActivityWithFolio(bpId, activityId, 1, operator));
    }

    @Test
    void bookActivity_whenCapacityExceeded_throwsBusinessException() {
        when(bookingPassengers.findById(bpId)).thenReturn(Optional.of(bookingPassenger));
        when(registrations.existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(
                eq(bpId), eq(activityId), anyList())).thenReturn(false);
        when(activities.lockActivity(activityId)).thenReturn(Optional.of(activity));
        when(registrations.sumOnboardParticipants(eq(activityId), anyList())).thenReturn(10L);

        assertThrows(BusinessException.class, () ->
                service.bookActivityWithFolio(bpId, activityId, 1, operator));
    }

    @Test
    void dynamicWeatherReschedule_updatesLocationAndNotifiesPassenger() {
        when(activities.lockActivity(activityId)).thenReturn(Optional.of(activity));

        User guestUser = new User();
        guestUser.setUserId(UUID.randomUUID());
        bookingPassenger.getPassenger().setUser(guestUser);

        Registration reg = new Registration();
        reg.setStatus(RegistrationStatus.CONFIRMED);
        reg.setBookingPassenger(bookingPassenger);

        when(registrations.findByOnboardActivity_OnboardActivityId(activityId)).thenReturn(List.of(reg));

        var result = service.dynamicWeatherReschedule(
                activityId,
                "Starlight Theater Tầng 5",
                "Cửa đón khách Tầng 5",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                "Mưa bão lớn"
        );

        assertNotNull(result);
        assertEquals("Starlight Theater Tầng 5", result.newLocation());
        assertEquals(1, result.notifiedPassengerCount());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void syncPendingOfflineTransactions_marksSynced() {
        OnboardTransaction tx = new OnboardTransaction();
        tx.setSyncStatus(TransactionSyncStatus.PENDING);

        when(onboardTransactionRepository.findTop100BySyncStatusOrderByTransactionTimeAsc(TransactionSyncStatus.PENDING))
                .thenReturn(List.of(tx));

        var result = service.syncPendingOfflineTransactions(100);

        assertEquals(1, result.totalProcessed());
        assertEquals(1, result.totalSynced());
        assertEquals(TransactionSyncStatus.SYNCED, tx.getSyncStatus());
        assertNotNull(tx.getSyncedAt());
        verify(onboardTransactionRepository, times(1)).saveAll(anyList());
    }
}

