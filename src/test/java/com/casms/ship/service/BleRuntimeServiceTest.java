package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.repository.PassengerBleIdentityRepository;
import com.casms.identity.entity.PassengerBleIdentity;
import com.casms.ship.dto.request.BleReadingRequest;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.LocationDevice;
import com.casms.ship.entity.LocationRecord;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.LocationDeviceStatus;
import com.casms.ship.enums.LocationDeviceType;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.LocationDeviceRepository;
import com.casms.ship.repository.LocationRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BleRuntimeServiceTest {
    @Mock CruiseRepository cruises;
    @Mock LocationDeviceRepository devices;
    @Mock LocationRecordRepository records;
    @Mock PassengerBleIdentityRepository identities;
    @InjectMocks BleRuntimeService service;

    @Test
    void ingestPersistsRssiObservationWithoutInventingCoordinates() {
        UUID cruiseId = UUID.randomUUID(), shipId = UUID.randomUUID(), receiverId = UUID.randomUUID();
        Cruise cruise = inProgressCruise(cruiseId, shipId);
        LocationDevice receiver = installedReceiver(receiverId, shipId);
        when(cruises.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(devices.findByLocationDeviceIdAndShipShipId(receiverId, shipId)).thenReturn(Optional.of(receiver));
        when(identities.findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, "AA:BB:CC:DD:EE:FF")).thenReturn(Optional.of(new PassengerBleIdentity()));
        when(records.findTop7ByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc(receiverId, "AA:BB:CC:DD:EE:FF")).thenReturn(List.of());
        when(records.save(any(LocationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(devices.save(any(LocationDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BleReadingRequest request = reading("AA:BB:CC:DD:EE:FF", -65, Instant.now());

        service.ingest(cruiseId, receiverId, request);

        ArgumentCaptor<LocationRecord> captor = ArgumentCaptor.forClass(LocationRecord.class);
        verify(records).save(captor.capture());
        LocationRecord persisted = captor.getValue();
        assertEquals(-65, persisted.getRssiDbm());
        assertEquals("AA:BB:CC:DD:EE:FF", persisted.getBleIdentifier());
        assertNull(persisted.getLatitude());
        assertNull(persisted.getLongitude());
        assertNotNull(persisted.getEstimatedDistanceMeters());
        assertNotNull(receiver.getLastSeenAt());
    }

    @Test
    void ingestRejectsUnassignedTag() {
        UUID cruiseId = UUID.randomUUID(), shipId = UUID.randomUUID(), receiverId = UUID.randomUUID();
        when(cruises.findById(cruiseId)).thenReturn(Optional.of(inProgressCruise(cruiseId, shipId)));
        when(devices.findByLocationDeviceIdAndShipShipId(receiverId, shipId)).thenReturn(Optional.of(installedReceiver(receiverId, shipId)));
        when(identities.findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, "TAG-1")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.ingest(cruiseId, receiverId, reading("TAG-1", -65, Instant.now())));
        verify(records, never()).save(any());
    }

    @Test
    void snapshotReportsRangeOnlyAndNeverCoordinates() {
        UUID cruiseId = UUID.randomUUID(), shipId = UUID.randomUUID(), receiverId = UUID.randomUUID();
        String id = "AA:BB:CC:DD:EE:FF";
        when(cruises.findById(cruiseId)).thenReturn(Optional.of(inProgressCruise(cruiseId, shipId)));
        when(devices.findByLocationDeviceIdAndShipShipId(receiverId, shipId)).thenReturn(Optional.of(installedReceiver(receiverId, shipId)));
        when(identities.findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, id)).thenReturn(Optional.of(new PassengerBleIdentity()));
        LocationRecord observation = new LocationRecord();
        observation.setBleIdentifier(id); observation.setRssiDbm(-60); observation.setRecordedAt(Instant.now());
        when(records.findTop7ByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc(receiverId, id)).thenReturn(List.of(observation));

        var snapshot = service.snapshot(cruiseId, receiverId, id);

        assertEquals("RANGE_ONLY_NO_DIRECTION", snapshot.positionKind());
        assertNotNull(snapshot.proximity().distanceMeters());
        assertEquals("LIVE", snapshot.proximity().status());
    }

    private static Cruise inProgressCruise(UUID cruiseId, UUID shipId) {
        Ship ship = new Ship(); ship.setShipId(shipId);
        Cruise cruise = new Cruise(); cruise.setCruiseId(cruiseId); cruise.setShip(ship); cruise.setStatus(CruiseStatus.IN_PROGRESS);
        return cruise;
    }

    private static LocationDevice installedReceiver(UUID receiverId, UUID shipId) {
        Ship ship = new Ship(); ship.setShipId(shipId);
        Deck deck = new Deck(); deck.setDeckId(UUID.randomUUID()); deck.setShip(ship); deck.setDeckNumber(4);
        LocationDevice receiver = new LocationDevice();
        receiver.setLocationDeviceId(receiverId); receiver.setShip(ship); receiver.setDeck(deck);
        receiver.setDeviceCode("RX-01"); receiver.setDeviceType(LocationDeviceType.BLE_RECEIVER);
        receiver.setStatus(LocationDeviceStatus.ACTIVE); receiver.setInstalledAt(Instant.now());
        receiver.setModelPositionX(1.0); receiver.setModelPositionY(2.0); receiver.setModelPositionZ(3.0);
        receiver.setModelRotationX(0.0); receiver.setModelRotationY(0.0); receiver.setModelRotationZ(0.0);
        receiver.setTxPowerDbm(-59.0); receiver.setPathLossExponent(2.2); receiver.setModelUnitsPerMeter(1.0);
        return receiver;
    }

    private static BleReadingRequest reading(String id, int rssi, Instant time) {
        BleReadingRequest request = new BleReadingRequest();
        request.setDeviceId(id); request.setDeviceName("CASMS test tag"); request.setRssi(rssi); request.setTimestamp(time);
        return request;
    }
}
