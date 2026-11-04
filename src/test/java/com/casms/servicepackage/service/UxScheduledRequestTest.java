package com.casms.servicepackage.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.pos.PosLedgerService;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.entity.ServiceSchedule;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import com.casms.servicepackage.enums.ServiceStatus;
import com.casms.servicepackage.repository.*;
import com.casms.ship.entity.Cruise;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class UxScheduledRequestTest {
    @Mock ServiceRequestRepository requests;
    @Mock ServiceRepository services;
    @Mock BookingPassengerRepository bookingPassengers;
    @Mock PassengerRepository passengers;
    @Mock UserRepository users;
    @Mock ServiceScheduleRepository schedules;
    @Mock PosLedgerService posLedger;
    @InjectMocks ServiceRequestService service;
    final UUID userId = UUID.randomUUID(),
            serviceId = UUID.randomUUID(),
            scheduleId = UUID.randomUUID(),
            bpId = UUID.randomUUID();
    Service item;
    ServiceSchedule schedule;
    Booking booking;
    BookingPassenger bp;
    Passenger passenger;

    @BeforeEach
    void setup() {
        var owner = new User();
        owner.setUserId(userId);
        passenger = new Passenger();
        passenger.setPassengerId(UUID.randomUUID());
        passenger.setUser(owner);
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        booking = new Booking();
        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setCruise(cruise);
        bp = new BookingPassenger();
        bp.setBookingPassengerId(bpId);
        bp.setBooking(booking);
        bp.setPassenger(passenger);
        item = new Service();
        item.setServiceId(serviceId);
        item.setStatus(ServiceStatus.ACTIVE);
        item.setRequiresSchedule(true);
        item.setPrice(new BigDecimal("12.3456"));
        item.setCurrencyCode("USD");
        schedule = new ServiceSchedule();
        schedule.setServiceScheduleId(scheduleId);
        schedule.setService(item);
        schedule.setCruise(cruise);
        schedule.setCapacity(5);
        schedule.setStatus("OPEN");
    }

    void prepareCreate() {
        when(passengers.findByUser_UserId(userId)).thenReturn(Optional.of(passenger));
        when(services.findByIdForUpdate(serviceId)).thenReturn(Optional.of(item));
    }

    void prepareSchedule() {
        when(schedules.lockById(scheduleId)).thenReturn(Optional.of(schedule));
    }

    @Test
    void scheduledCreateRequiresWindow() {
        prepareCreate();
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(userId, serviceId, bpId, null, 1, "", null));
        verify(requests, never()).save(any());
    }

    @Test
    void newRequestCannotSnapshotForeignCurrencyAsUsd() {
        prepareCreate();
        item.setCurrencyCode("VND");
        var error =
                assertThrows(
                        BusinessException.class,
                        () -> service.create(userId, serviceId, bpId, scheduleId, 1, "", null));
        assertEquals("CURRENCY_USD_REQUIRED", error.getCode());
        verify(requests, never()).save(any());
    }

    @Test
    void scheduledCreateRejectsAnotherCruiseWithoutSaving() {
        prepareCreate();
        prepareSchedule();
        var other = new Cruise();
        other.setCruiseId(UUID.randomUUID());
        schedule.setCruise(other);
        when(bookingPassengers.findById(bpId)).thenReturn(Optional.of(bp));
        var error =
                assertThrows(
                        BusinessException.class,
                        () -> service.create(userId, serviceId, bpId, scheduleId, 2, "", null));
        assertEquals("SERVICE_SCHEDULE_CRUISE_MISMATCH", error.getCode());
        verify(requests, never()).save(any());
    }

    @Test
    void scheduledCreateRejectsAnotherService() {
        prepareCreate();
        prepareSchedule();
        var other = new Service();
        other.setServiceId(UUID.randomUUID());
        schedule.setService(other);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(userId, serviceId, bpId, scheduleId, 1, "", null));
        verify(requests, never()).save(any());
    }

    @Test
    void scheduledCreatePersistsSelectedWindowAndPriceSnapshot() {
        prepareCreate();
        prepareSchedule();
        when(bookingPassengers.findById(bpId)).thenReturn(Optional.of(bp));
        when(requests.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved = service.create(userId, serviceId, bpId, scheduleId, 2, "note", null);
        assertSame(schedule, saved.getServiceSchedule());
        assertEquals(2, saved.getQuantity());
        item.setPrice(new BigDecimal("99"));
        assertEquals(new BigDecimal("12.3456"), saved.getUnitPriceSnapshot());
        assertEquals("USD", saved.getCurrencySnapshot());
        assertEquals(BookingStatus.Confirmed, booking.getBookingStatus());
    }

    ServiceRequest prepareUpdate() {
        var r = new ServiceRequest();
        r.setServiceRequestId(UUID.randomUUID());
        r.setService(item);
        r.setServiceSchedule(schedule);
        r.setBooking(booking);
        r.setBookingPassenger(bp);
        r.setPassenger(passenger);
        r.setQuantity(2);
        r.setUnitPriceSnapshot(new BigDecimal("12.3456"));
        r.setCurrencySnapshot("USD");
        r.setStatus(ServiceRequestStatus.REQUESTED);
        when(requests.lockById(r.getServiceRequestId())).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void scheduledEditUsesWindowCapacityRatherThanUnrelatedGlobalCapacity() {
        var r = prepareUpdate();
        when(services.findByIdForUpdate(serviceId)).thenReturn(Optional.of(item));
        prepareSchedule();
        when(requests.sumActiveQuantityForSchedule(eq(scheduleId), any())).thenReturn(4L);
        assertThrows(
                IllegalStateException.class,
                () -> service.updatePending(r.getServiceRequestId(), userId, false, 4, "changed"));
        assertEquals(2, r.getQuantity());
        verify(requests, never()).sumActiveQuantity(any(), any());
        service.updatePending(r.getServiceRequestId(), userId, false, 3, "changed");
        assertEquals(3, r.getQuantity());
        assertEquals(new BigDecimal("12.3456"), r.getUnitPriceSnapshot());
        assertEquals("USD", r.getCurrencySnapshot());
    }

    @Test
    void acceptedCancellationReversesLinkedLedgerWithoutCancellingBooking() {
        var r = prepareUpdate();
        r.setStatus(ServiceRequestStatus.ACCEPTED);
        service.transition(r.getServiceRequestId(), userId, "cancel");
        assertEquals(ServiceRequestStatus.CANCELLED, r.getStatus());
        assertEquals(BookingStatus.Confirmed, booking.getBookingStatus());
        verify(posLedger).reverseForRequest(r.getServiceRequestId(), userId);
    }

    @Test
    void processingCancellationIsRejectedWithoutReversal() {
        var r = prepareUpdate();
        r.setStatus(ServiceRequestStatus.IN_PROGRESS);
        assertThrows(
                IllegalStateException.class,
                () -> service.transition(r.getServiceRequestId(), userId, "cancel"));
        assertEquals(ServiceRequestStatus.IN_PROGRESS, r.getStatus());
        verifyNoInteractions(posLedger);
    }
}
