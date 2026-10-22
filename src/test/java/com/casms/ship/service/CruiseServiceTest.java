package com.casms.ship.service;

import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CruiseServiceTest {

    @Mock
    private CruiseRepository cruiseRepository;
    @Mock
    private ShipRepository shipRepository;
    @Mock
    private PortRepository portRepository;
    @Mock
    private DeckRepository deckRepository;
    @Mock
    private CabinRepository cabinRepository;
    @Mock
    private ItineraryRepository itineraryRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingPassengerRepository bookingPassengerRepository;
    @Mock
    private BusinessCodeGenerator businessCodeGenerator;

    @InjectMocks
    private CruiseService cruiseService;

    private Ship testShip;
    private Port depPort;
    private Port retPort;
    private Cruise testCruise;
    private UUID cruiseId;
    private UUID shipId;

    @BeforeEach
    void setUp() {
        cruiseId = UUID.randomUUID();
        shipId = UUID.randomUUID();

        testShip = new Ship();
        testShip.setShipId(shipId);
        testShip.setShipCode("SH-01");
        testShip.setShipName("Queen of the Ocean");
        testShip.setStatus(ShipStatus.ACTIVE);
        testShip.setTotalCapacity(1000);
        testShip.setTotalDecks(10);

        depPort = new Port();
        depPort.setPortId(UUID.randomUUID());
        depPort.setPortName("Halong Port");
        depPort.setStatus(PortStatus.ACTIVE);

        retPort = new Port();
        retPort.setPortId(UUID.randomUUID());
        retPort.setPortName("Halong Port");
        retPort.setStatus(PortStatus.ACTIVE);

        testCruise = new Cruise();
        testCruise.setCruiseId(cruiseId);
        testCruise.setCruiseCode("CR-001");
        testCruise.setCruiseName("Halong 3D2N Tour");
        testCruise.setDepartureDatetime(Instant.now().plus(10, ChronoUnit.DAYS));
        testCruise.setReturnDatetime(Instant.now().plus(13, ChronoUnit.DAYS));
        testCruise.setDeparturePort(depPort);
        testCruise.setReturnPort(retPort);
        testCruise.setStatus(CruiseStatus.DRAFT);
    }

    @Test
    @DisplayName("Tạo cruise bản nháp thành công")
    void createCruise_success() {
        CruiseCreateRequest request = new CruiseCreateRequest();
        request.setCruiseName("Halong 3D2N Tour");
        request.setCruiseType(CruiseCruiseType.MULTI_DAYS);
        request.setDepartureDatetime(Instant.now().plus(5, ChronoUnit.DAYS));
        request.setReturnDatetime(Instant.now().plus(8, ChronoUnit.DAYS));
        request.setDeparturePortId(depPort.getPortId());
        request.setReturnPortId(retPort.getPortId());

        when(businessCodeGenerator.next("CR")).thenReturn("CR-0001");
        when(portRepository.findById(depPort.getPortId())).thenReturn(Optional.of(depPort));
        when(portRepository.findById(retPort.getPortId())).thenReturn(Optional.of(retPort));
        when(cruiseRepository.save(any(Cruise.class))).thenAnswer(i -> {
            Cruise c = i.getArgument(0);
            c.setCruiseId(cruiseId);
            return c;
        });

        CruiseResponse response = cruiseService.create(request);

        assertNotNull(response);
        assertEquals("CR-0001", response.getCruiseCode());
        assertEquals(CruiseStatus.DRAFT, response.getStatus());
        verify(cruiseRepository).save(any(Cruise.class));
    }

    @Test
    @DisplayName("Tạo cruise thất bại khi thời gian kết thúc trước thời gian khởi hành")
    void createCruise_invalidSchedule_throwsException() {
        CruiseCreateRequest request = new CruiseCreateRequest();
        request.setDepartureDatetime(Instant.now().plus(10, ChronoUnit.DAYS));
        request.setReturnDatetime(Instant.now().plus(5, ChronoUnit.DAYS)); // Return before departure

        BusinessException ex = assertThrows(BusinessException.class, () -> cruiseService.create(request));
        assertEquals("INVALID_SCHEDULE", ex.getCode());
    }

    @Test
    @DisplayName("Gán tàu cho cruise thành công khi tàu ACTIVE và không trùng lịch")
    void assignShip_success() {
        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
        when(cruiseRepository.existsOverlappingCruisesExcluding(eq(shipId), eq(cruiseId), any(), any())).thenReturn(false);
        when(cruiseRepository.save(any(Cruise.class))).thenAnswer(i -> i.getArgument(0));

        CruiseResponse response = cruiseService.assignShip(cruiseId, shipId);

        assertNotNull(response);
        assertEquals(shipId, response.getShipId());
        verify(cruiseRepository).save(any(Cruise.class));
    }

    @Test
    @DisplayName("Gán tàu thất bại khi tàu không ở trạng thái ACTIVE")
    void assignShip_shipNotActive_throwsException() {
        testShip.setStatus(ShipStatus.MAINTENANCE);
        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));

        BusinessException ex = assertThrows(BusinessException.class, () -> cruiseService.assignShip(cruiseId, shipId));
        assertEquals("SHIP_NOT_ACTIVE", ex.getCode());
    }

    @Test
    @DisplayName("Kiểm tra sẵn sàng vận hành: Không sẵn sàng khi chưa có hải trình đã phê duyệt")
    void validateOperationalReadiness_missingApprovedItinerary_notReady() {
        testCruise.setShip(testShip);
        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
        when(deckRepository.countByShipShipId(shipId)).thenReturn(5L);
        when(cabinRepository.countByShipShipId(shipId)).thenReturn(50L);
        when(itineraryRepository.findByCruiseCruiseId(cruiseId)).thenReturn(Optional.empty()); // No itinerary

        CruiseReadinessResponse response = cruiseService.validateOperationalReadiness(cruiseId);

        assertFalse(response.isReady());
        assertFalse(response.getIssues().isEmpty());
    }

    @Test
    @DisplayName("Kiểm tra sẵn sàng vận hành & Publish thành công khi đầy đủ điều kiện")
    void publishCruise_whenReady_success() {
        testCruise.setShip(testShip);
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(UUID.randomUUID());
        itinerary.setStatus(ItineraryStatus.APPROVED);

        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
        when(deckRepository.countByShipShipId(shipId)).thenReturn(5L);
        when(cabinRepository.countByShipShipId(shipId)).thenReturn(50L);
        when(itineraryRepository.findByCruiseCruiseId(cruiseId)).thenReturn(Optional.of(itinerary));
        when(cruiseRepository.save(any(Cruise.class))).thenAnswer(i -> i.getArgument(0));

        CruiseResponse response = cruiseService.publish(cruiseId);

        assertEquals(CruiseStatus.PUBLISHED, response.getStatus());
        verify(cruiseRepository).save(testCruise);
    }
}
