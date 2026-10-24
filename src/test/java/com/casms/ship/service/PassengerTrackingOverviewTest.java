package com.casms.ship.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.PassengerBleIdentity;
import com.casms.identity.enums.PassengerBleIdentityStatus;
import com.casms.identity.repository.PassengerBleIdentityRepository;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PassengerTrackingOverviewTest {
    @Mock CruiseRepository cruises;
    @Mock LocationDeviceRepository devices;
    @Mock LocationRecordRepository records;
    @Mock PassengerBleIdentityRepository identities;
    @InjectMocks BleRuntimeService service;

    @Test void overviewUsesOneBatchAndChoosesFreshNearestRangeWithoutPassengerCoordinates() {
        Cruise cruise = cruise(); var near=receiver(cruise.getShip(),"near"); var far=receiver(cruise.getShip(),"far");
        var identity=identity(cruise,Instant.now().minusSeconds(30));
        when(cruises.findById(cruise.getCruiseId())).thenReturn(Optional.of(cruise));
        when(devices.findByShipShipId(cruise.getShip().getShipId())).thenReturn(List.of(near,far));
        when(identities.findByCruiseCruiseIdAndStatusOrderByAssignedAtDesc(cruise.getCruiseId(),PassengerBleIdentityStatus.ACTIVE)).thenReturn(List.of(identity));
        when(records.findByShipShipIdAndRecordedAtBetweenOrderByRecordedAtAsc(eq(cruise.getShip().getShipId()),any(),any())).thenReturn(List.of(reading(far,-80,Instant.now().minusSeconds(1)),reading(near,-60,Instant.now().minusSeconds(1))));
        var result=service.passengerOverview(cruise.getCruiseId());
        assertEquals(cruise.getShip().getShipId(),result.shipId());assertEquals("RANGE_ONLY_NO_DIRECTION",result.positionKind());
        var passenger=result.passengers().getFirst();assertEquals("Unit Passenger",passenger.fullName());assertEquals("LIVE",passenger.status());assertEquals(near.getLocationDeviceId(),passenger.receiver().anchorId());assertTrue(passenger.proximity().distanceMeters()>0);
        verify(records,times(1)).findByShipShipIdAndRecordedAtBetweenOrderByRecordedAtAsc(any(),any(),any());
        verify(records,never()).findTop7ByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc(any(),any());
        assertFalse(Arrays.stream(passenger.getClass().getRecordComponents()).anyMatch(c->Set.of("position","identificationNumber","bleIdentifier","phone","email").contains(c.getName())));
    }
    @Test void readingsBeforeAssignmentAreNotReusedAndOldReadingsHaveNoRadius() {
        Cruise cruise=cruise();var receiver=receiver(cruise.getShip(),"receiver");var identity=identity(cruise,Instant.now().minusSeconds(10));
        when(cruises.findById(cruise.getCruiseId())).thenReturn(Optional.of(cruise));when(devices.findByShipShipId(any())).thenReturn(List.of(receiver));when(identities.findByCruiseCruiseIdAndStatusOrderByAssignedAtDesc(any(),any())).thenReturn(List.of(identity));
        when(records.findByShipShipIdAndRecordedAtBetweenOrderByRecordedAtAsc(any(),any(),any())).thenReturn(List.of(reading(receiver,-60,Instant.now().minusSeconds(20))));
        assertEquals("NO_RECENT_SIGNAL",service.passengerOverview(cruise.getCruiseId()).passengers().getFirst().status());
        identity.setAssignedAt(Instant.now().minusSeconds(40));var stale=service.passengerOverview(cruise.getCruiseId()).passengers().getFirst();assertEquals("OFFLINE",stale.status());assertNull(stale.proximity().distanceMeters());
    }
    @Test void noTrackingOutsideAnInProgressCruise() {var cruise=cruise();cruise.setStatus(CruiseStatus.DRAFT);when(cruises.findById(cruise.getCruiseId())).thenReturn(Optional.of(cruise));assertThrows(com.casms.common.exception.BusinessException.class,()->service.passengerOverview(cruise.getCruiseId()));verifyNoInteractions(identities,devices,records);}
    private Cruise cruise(){var ship=new Ship();ship.setShipId(UUID.randomUUID());var cruise=new Cruise();cruise.setCruiseId(UUID.randomUUID());cruise.setShip(ship);cruise.setStatus(CruiseStatus.IN_PROGRESS);cruise.setDepartureDatetime(Instant.now().minusSeconds(100));return cruise;}
    private PassengerBleIdentity identity(Cruise cruise,Instant assigned){var passenger=new Passenger();passenger.setFullName("Unit Passenger");var bp=new BookingPassenger();bp.setBookingPassengerId(UUID.randomUUID());bp.setPassenger(passenger);var identity=new PassengerBleIdentity();identity.setPassengerBleIdentityId(UUID.randomUUID());identity.setBookingPassenger(bp);identity.setCruise(cruise);identity.setBleIdentifier("UNIT-TAG");identity.setAssignedAt(assigned);return identity;}
    private LocationDevice receiver(Ship ship,String code){var deck=new Deck();deck.setDeckId(UUID.randomUUID());deck.setDeckNumber(1);deck.setShip(ship);var d=new LocationDevice();d.setLocationDeviceId(UUID.randomUUID());d.setShip(ship);d.setDeck(deck);d.setDeviceCode(code);d.setDeviceType(LocationDeviceType.BLE_RECEIVER);d.setStatus(LocationDeviceStatus.ACTIVE);d.setInstalledAt(Instant.now().minusSeconds(100));d.setModelPositionX(1d);d.setModelPositionY(2d);d.setModelPositionZ(3d);d.setTxPowerDbm(-59d);d.setPathLossExponent(2.2d);d.setModelUnitsPerMeter(1d);return d;}
    private LocationRecord reading(LocationDevice device,int rssi,Instant at){var r=new LocationRecord();r.setDevice(device);r.setBleIdentifier("UNIT-TAG");r.setRssiDbm(rssi);r.setRecordedAt(at);return r;}
}
