package com.casms.ship.service;

import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseUpdateDraftRequest;
import com.casms.ship.dto.response.CruiseCapacityValidationResponse;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Port;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private CruiseService cruiseService;

    private UUID cruiseId;
    private UUID shipId;
    private UUID departurePortId;
    private UUID returnPortId;

    private Cruise testCruise;
    private Ship testShip;
    private Port departurePort;
    private Port returnPort;

    private Instant depTime;
    private Instant retTime;

    @BeforeEach
    void setUp() {
        cruiseId = UUID.randomUUID();
        shipId = UUID.randomUUID();
        departurePortId = UUID.randomUUID();
        returnPortId = UUID.randomUUID();

        depTime = Instant.now().plusSeconds(86400);
        retTime = depTime.plusSeconds(86400 * 3);

        testShip = new Ship();
        testShip.setShipId(shipId);
        testShip.setShipCode("SH-001");
        testShip.setShipName("Ocean Voyager");
        testShip.setStatus(ShipStatus.ACTIVE);
        testShip.setTotalCapacity(2000);
        testShip.setTotalDecks(10);

        departurePort = new Port();
        departurePort.setPortId(departurePortId);
        departurePort.setPortName("Ha Long Port");
        departurePort.setStatus(PortStatus.ACTIVE);

        returnPort = new Port();
        returnPort.setPortId(returnPortId);
        returnPort.setPortName("Ha Long Port");
        returnPort.setStatus(PortStatus.ACTIVE);

        testCruise = new Cruise();
        testCruise.setCruiseId(cruiseId);
        testCruise.setCruiseCode("CR-001");
        testCruise.setCruiseName("Ha Long Discovery");
        testCruise.setShip(testShip);
        testCruise.setCruiseType(CruiseCruiseType.MULTI_DAYS);
        testCruise.setDepartureDatetime(depTime);
        testCruise.setReturnDatetime(retTime);
        testCruise.setDeparturePort(departurePort);
        testCruise.setReturnPort(returnPort);
        testCruise.setStatus(CruiseStatus.DRAFT);
        testCruise.setCreatedAt(Instant.now());
        testCruise.setUpdatedAt(Instant.now());
    }

    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create draft cruise with ship successfully")
        void create_WithShip_Success() {
            CruiseCreateRequest request = new CruiseCreateRequest();
            request.setCruiseCode("CR-002");
            request.setCruiseName("Da Nang Voyage");
            request.setShipId(shipId);
            request.setCruiseType(CruiseCruiseType.MULTI_DAYS);
            request.setDepartureDatetime(depTime);
            request.setReturnDatetime(retTime);
            request.setDeparturePortId(departurePortId);
            request.setReturnPortId(returnPortId);

            when(cruiseRepository.existsByCruiseCode("CR-002")).thenReturn(false);
            when(portRepository.findById(departurePortId)).thenReturn(Optional.of(departurePort));
            when(portRepository.findById(returnPortId)).thenReturn(Optional.of(returnPort));
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(cruiseRepository.existsOverlappingCruises(shipId, depTime, retTime)).thenReturn(false);
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> {
                Cruise c = inv.getArgument(0);
                c.setCruiseId(UUID.randomUUID());
                return c;
            });

            CruiseResponse response = cruiseService.create(request);

            assertThat(response).isNotNull();
            assertThat(response.getCruiseCode()).isEqualTo("CR-002");
            assertThat(response.getStatus()).isEqualTo(CruiseStatus.DRAFT);
            assertThat(response.getShipId()).isEqualTo(shipId);
            verify(cruiseRepository).save(any(Cruise.class));
        }

        @Test
        @DisplayName("Should create draft cruise without ship successfully")
        void create_WithoutShip_Success() {
            CruiseCreateRequest request = new CruiseCreateRequest();
            request.setCruiseCode("CR-003");
            request.setCruiseName("Draft Cruise");
            request.setShipId(null);
            request.setCruiseType(CruiseCruiseType.ONE_DAY);
            request.setDepartureDatetime(depTime);
            request.setReturnDatetime(retTime);
            request.setDeparturePortId(departurePortId);
            request.setReturnPortId(returnPortId);

            when(cruiseRepository.existsByCruiseCode("CR-003")).thenReturn(false);
            when(portRepository.findById(departurePortId)).thenReturn(Optional.of(departurePort));
            when(portRepository.findById(returnPortId)).thenReturn(Optional.of(returnPort));
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> {
                Cruise c = inv.getArgument(0);
                c.setCruiseId(UUID.randomUUID());
                return c;
            });

            CruiseResponse response = cruiseService.create(request);

            assertThat(response).isNotNull();
            assertThat(response.getShipId()).isNull();
            assertThat(response.getStatus()).isEqualTo(CruiseStatus.DRAFT);
            verify(shipRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw BusinessException(INVALID_SCHEDULE) when returnDatetime is before departureDatetime")
        void create_InvalidSchedule_ThrowsException() {
            CruiseCreateRequest request = new CruiseCreateRequest();
            request.setDepartureDatetime(retTime);
            request.setReturnDatetime(depTime);

            assertThatThrownBy(() -> cruiseService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Return datetime must be after departure datetime");
        }

        @Test
        @DisplayName("Should throw BusinessException(DUPLICATE_CRUISE_CODE) when code already exists")
        void create_DuplicateCode_ThrowsException() {
            CruiseCreateRequest request = new CruiseCreateRequest();
            request.setCruiseCode("CR-001");
            request.setDepartureDatetime(depTime);
            request.setReturnDatetime(retTime);

            when(cruiseRepository.existsByCruiseCode("CR-001")).thenReturn(true);

            assertThatThrownBy(() -> cruiseService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cruise code already exists");
        }

        @Test
        @DisplayName("Should throw BusinessException(SHIP_NOT_ACTIVE) when assigned ship is inactive")
        void create_ShipInactive_ThrowsException() {
            testShip.setStatus(ShipStatus.MAINTENANCE);

            CruiseCreateRequest request = new CruiseCreateRequest();
            request.setCruiseCode("CR-004");
            request.setShipId(shipId);
            request.setDepartureDatetime(depTime);
            request.setReturnDatetime(retTime);
            request.setDeparturePortId(departurePortId);
            request.setReturnPortId(returnPortId);

            when(cruiseRepository.existsByCruiseCode("CR-004")).thenReturn(false);
            when(portRepository.findById(departurePortId)).thenReturn(Optional.of(departurePort));
            when(portRepository.findById(returnPortId)).thenReturn(Optional.of(returnPort));
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));

            assertThatThrownBy(() -> cruiseService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Assigned ship must be in ACTIVE status");
        }

        @Test
        @DisplayName("Should throw BusinessException(SHIP_SCHEDULE_CONFLICT) when assigned ship has overlapping cruise")
        void create_ShipScheduleConflict_ThrowsException() {
            CruiseCreateRequest request = new CruiseCreateRequest();
            request.setCruiseCode("CR-005");
            request.setShipId(shipId);
            request.setDepartureDatetime(depTime);
            request.setReturnDatetime(retTime);
            request.setDeparturePortId(departurePortId);
            request.setReturnPortId(returnPortId);

            when(cruiseRepository.existsByCruiseCode("CR-005")).thenReturn(false);
            when(portRepository.findById(departurePortId)).thenReturn(Optional.of(departurePort));
            when(portRepository.findById(returnPortId)).thenReturn(Optional.of(returnPort));
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(cruiseRepository.existsOverlappingCruises(shipId, depTime, retTime)).thenReturn(true);

            assertThatThrownBy(() -> cruiseService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Ship already has an overlapping active cruise in this schedule window");
        }
    }

    @Nested
    @DisplayName("list() & detail() Tests")
    class QueryTests {

        @Test
        @DisplayName("list() should return paged cruise responses with filters")
        void list_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Cruise> page = new PageImpl<>(List.of(testCruise), pageable, 1);

            when(cruiseRepository.findAllWithFilters(CruiseStatus.DRAFT, shipId, CruiseCruiseType.MULTI_DAYS, pageable))
                    .thenReturn(page);

            Page<CruiseResponse> result = cruiseService.list(CruiseStatus.DRAFT, shipId, CruiseCruiseType.MULTI_DAYS, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getCruiseCode()).isEqualTo("CR-001");
        }

        @Test
        @DisplayName("detail() should return cruise when found")
        void detail_Found() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            CruiseResponse response = cruiseService.detail(cruiseId);

            assertThat(response).isNotNull();
            assertThat(response.getCruiseId()).isEqualTo(cruiseId);
            assertThat(response.getCruiseName()).isEqualTo("Ha Long Discovery");
        }

        @Test
        @DisplayName("detail() should throw ResourceNotFoundException when cruise not found")
        void detail_NotFound() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cruiseService.detail(cruiseId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateDraft() Tests")
    class UpdateDraftTests {

        @Test
        @DisplayName("Should update draft cruise successfully")
        void updateDraft_Success() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseUpdateDraftRequest request = new CruiseUpdateDraftRequest();
            request.setCruiseName("Updated Ha Long Discovery");
            request.setCruiseType(CruiseCruiseType.ONE_DAY);

            CruiseResponse response = cruiseService.updateDraft(cruiseId, request);

            assertThat(response.getCruiseName()).isEqualTo("Updated Ha Long Discovery");
            assertThat(response.getCruiseType()).isEqualTo(CruiseCruiseType.ONE_DAY);
            verify(cruiseRepository).save(testCruise);
        }

        @Test
        @DisplayName("Should throw BusinessException(CRUISE_NOT_DRAFT) when cruise is not DRAFT")
        void updateDraft_NotDraft_ThrowsException() {
            testCruise.setStatus(CruiseStatus.PUBLISHED);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            CruiseUpdateDraftRequest request = new CruiseUpdateDraftRequest();
            request.setCruiseName("New Name");

            assertThatThrownBy(() -> cruiseService.updateDraft(cruiseId, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only draft cruises can be modified directly");
        }

        @Test
        @DisplayName("Should throw BusinessException(SHIP_SCHEDULE_CONFLICT) when updated schedule conflicts with assigned ship")
        void updateDraft_ScheduleConflict_ThrowsException() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            Instant newDep = depTime.plusSeconds(3600);
            Instant newRet = retTime.plusSeconds(7200);

            when(cruiseRepository.existsOverlappingCruisesExcluding(shipId, cruiseId, newDep, newRet)).thenReturn(true);

            CruiseUpdateDraftRequest request = new CruiseUpdateDraftRequest();
            request.setDepartureDatetime(newDep);
            request.setReturnDatetime(newRet);

            assertThatThrownBy(() -> cruiseService.updateDraft(cruiseId, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Assigned ship has an overlapping active cruise in the updated schedule");
        }
    }

    @Nested
    @DisplayName("deleteDraft() Tests")
    class DeleteDraftTests {

        @Test
        @DisplayName("Should delete draft cruise when no bookings exist")
        void deleteDraft_Success() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(bookingRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(false);

            cruiseService.deleteDraft(cruiseId);

            verify(cruiseRepository).delete(testCruise);
        }

        @Test
        @DisplayName("Should throw BusinessException(CRUISE_NOT_DRAFT) when cruise is not DRAFT")
        void deleteDraft_NotDraft_ThrowsException() {
            testCruise.setStatus(CruiseStatus.PUBLISHED);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            assertThatThrownBy(() -> cruiseService.deleteDraft(cruiseId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only draft cruises can be deleted");
        }

        @Test
        @DisplayName("Should throw BusinessException(CRUISE_HAS_BOOKINGS) when bookings exist")
        void deleteDraft_HasBookings_ThrowsException() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(bookingRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(true);

            assertThatThrownBy(() -> cruiseService.deleteDraft(cruiseId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot delete cruise because bookings already exist");
        }
    }

    @Nested
    @DisplayName("assignShip(), changeAssignedShip(), unassignShip() Tests")
    class ShipAssignmentTests {

        @Test
        @DisplayName("assignShip() should assign ship successfully to cruise without ship")
        void assignShip_Success() {
            testCruise.setShip(null);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(cruiseRepository.existsOverlappingCruisesExcluding(shipId, cruiseId, depTime, retTime)).thenReturn(false);
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseResponse response = cruiseService.assignShip(cruiseId, shipId);

            assertThat(response.getShipId()).isEqualTo(shipId);
            verify(cruiseRepository).save(testCruise);
        }

        @Test
        @DisplayName("assignShip() should no-op if same ship is already assigned")
        void assignShip_SameShip_NoOp() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            CruiseResponse response = cruiseService.assignShip(cruiseId, shipId);

            assertThat(response.getShipId()).isEqualTo(shipId);
            verify(cruiseRepository, never()).save(any());
        }

        @Test
        @DisplayName("assignShip() should throw BusinessException(SHIP_ALREADY_ASSIGNED) when different ship is assigned")
        void assignShip_DifferentShip_ThrowsException() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            UUID otherShipId = UUID.randomUUID();

            assertThatThrownBy(() -> cruiseService.assignShip(cruiseId, otherShipId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cruise already has an assigned ship. Use change-assigned-ship instead.");
        }

        @Test
        @DisplayName("changeAssignedShip() should update ship when capacity and schedule are valid")
        void changeAssignedShip_Success() {
            UUID newShipId = UUID.randomUUID();
            Ship newShip = new Ship();
            newShip.setShipId(newShipId);
            newShip.setStatus(ShipStatus.ACTIVE);
            newShip.setTotalCapacity(2500);

            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(shipRepository.findById(newShipId)).thenReturn(Optional.of(newShip));
            when(cruiseRepository.existsOverlappingCruisesExcluding(newShipId, cruiseId, depTime, retTime)).thenReturn(false);
            when(bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId)).thenReturn(1500L);
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseResponse response = cruiseService.changeAssignedShip(cruiseId, newShipId);

            assertThat(response.getShipId()).isEqualTo(newShipId);
            verify(cruiseRepository).save(testCruise);
        }

        @Test
        @DisplayName("changeAssignedShip() should throw BusinessException(INSUFFICIENT_SHIP_CAPACITY) when new ship capacity is too low")
        void changeAssignedShip_InsufficientCapacity_ThrowsException() {
            UUID newShipId = UUID.randomUUID();
            Ship newShip = new Ship();
            newShip.setShipId(newShipId);
            newShip.setStatus(ShipStatus.ACTIVE);
            newShip.setTotalCapacity(1000);

            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(shipRepository.findById(newShipId)).thenReturn(Optional.of(newShip));
            when(cruiseRepository.existsOverlappingCruisesExcluding(newShipId, cruiseId, depTime, retTime)).thenReturn(false);
            when(bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId)).thenReturn(1500L);

            assertThatThrownBy(() -> cruiseService.changeAssignedShip(cruiseId, newShipId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("is less than current booked passengers");
        }

        @Test
        @DisplayName("unassignShip() should remove ship when in DRAFT and no bookings")
        void unassignShip_Success() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(bookingRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(false);
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseResponse response = cruiseService.unassignShip(cruiseId);

            assertThat(response.getShipId()).isNull();
            verify(cruiseRepository).save(testCruise);
        }

        @Test
        @DisplayName("unassignShip() should throw BusinessException(CRUISE_NOT_DRAFT) when cruise is PUBLISHED")
        void unassignShip_NotDraft_ThrowsException() {
            testCruise.setStatus(CruiseStatus.PUBLISHED);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            assertThatThrownBy(() -> cruiseService.unassignShip(cruiseId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot unassign ship unless cruise is in DRAFT status");
        }

        @Test
        @DisplayName("unassignShip() should throw BusinessException(CRUISE_HAS_BOOKINGS) when bookings exist")
        void unassignShip_HasBookings_ThrowsException() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(bookingRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(true);

            assertThatThrownBy(() -> cruiseService.unassignShip(cruiseId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot unassign ship because bookings already exist");
        }
    }

    @Nested
    @DisplayName("Validation & Readiness Tests")
    class ValidationTests {

        @Test
        @DisplayName("validateShipAvailability() should return true when ship is active and no overlap")
        void validateShipAvailability_Available_ReturnsTrue() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(cruiseRepository.existsOverlappingCruisesExcluding(shipId, cruiseId, depTime, retTime)).thenReturn(false);

            boolean available = cruiseService.validateShipAvailability(cruiseId, shipId);

            assertThat(available).isTrue();
        }

        @Test
        @DisplayName("validateSchedule() should return true when return > departure")
        void validateSchedule_Valid_ReturnsTrue() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            boolean valid = cruiseService.validateSchedule(cruiseId);

            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("validateCapacity() should return correct capacity calculation")
        void validateCapacity_Success() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId)).thenReturn(800L);

            CruiseCapacityValidationResponse res = cruiseService.validateCapacity(cruiseId);

            assertThat(res.getShipTotalCapacity()).isEqualTo(2000);
            assertThat(res.getBookedPassengersCount()).isEqualTo(800L);
            assertThat(res.getAvailableCapacity()).isEqualTo(1200L);
            assertThat(res.isCapacitySufficient()).isTrue();
        }

        @Test
        @DisplayName("validateCapacity() should throw BusinessException(NO_SHIP_ASSIGNED) when no ship is assigned")
        void validateCapacity_NoShip_ThrowsException() {
            testCruise.setShip(null);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            assertThatThrownBy(() -> cruiseService.validateCapacity(cruiseId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No ship is assigned to this cruise");
        }

        @Test
        @DisplayName("validateOperationalReadiness() should return ready when all criteria met")
        void validateOperationalReadiness_Ready_Success() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(deckRepository.countByShipShipId(shipId)).thenReturn(5L);
            when(cabinRepository.countByShipShipId(shipId)).thenReturn(100L);
            when(itineraryRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(true);

            CruiseReadinessResponse readiness = cruiseService.validateOperationalReadiness(cruiseId);

            assertThat(readiness.isReady()).isTrue();
            assertThat(readiness.isShipAssigned()).isTrue();
            assertThat(readiness.isShipActive()).isTrue();
            assertThat(readiness.isShipConfigurationValid()).isTrue();
            assertThat(readiness.isScheduleValid()).isTrue();
            assertThat(readiness.isPortsActive()).isTrue();
            assertThat(readiness.isItineraryConfigured()).isTrue();
            assertThat(readiness.getIssues()).isEmpty();
        }

        @Test
        @DisplayName("validateOperationalReadiness() should return not ready when ship not assigned")
        void validateOperationalReadiness_NoShip_NotReady() {
            testCruise.setShip(null);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(itineraryRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(false);

            CruiseReadinessResponse readiness = cruiseService.validateOperationalReadiness(cruiseId);

            assertThat(readiness.isReady()).isFalse();
            assertThat(readiness.isShipAssigned()).isFalse();
            assertThat(readiness.getIssues()).contains("Chưa gán tàu cho chuyến đi");
        }
    }

    @Nested
    @DisplayName("Lifecycle Transitions (publish, start, complete, cancel) Tests")
    class LifecycleTests {

        @Test
        @DisplayName("publish() should transition DRAFT to PUBLISHED when ready")
        void publish_Success() {
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(deckRepository.countByShipShipId(shipId)).thenReturn(5L);
            when(cabinRepository.countByShipShipId(shipId)).thenReturn(100L);
            when(itineraryRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(true);
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseResponse response = cruiseService.publish(cruiseId);

            assertThat(response.getStatus()).isEqualTo(CruiseStatus.PUBLISHED);
            verify(cruiseRepository).save(testCruise);
        }

        @Test
        @DisplayName("publish() should throw BusinessException(CRUISE_NOT_READY) when readiness check fails")
        void publish_NotReady_ThrowsException() {
            testCruise.setShip(null);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(itineraryRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(false);

            assertThatThrownBy(() -> cruiseService.publish(cruiseId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cruise is not operationally ready to publish");

            verify(cruiseRepository, never()).save(any());
        }

        @Test
        @DisplayName("start() should transition PUBLISHED to IN_PROGRESS")
        void start_Success() {
            testCruise.setStatus(CruiseStatus.PUBLISHED);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseResponse response = cruiseService.start(cruiseId);

            assertThat(response.getStatus()).isEqualTo(CruiseStatus.IN_PROGRESS);
            verify(cruiseRepository).save(testCruise);
        }

        @Test
        @DisplayName("start() should throw BusinessException(CRUISE_NOT_PUBLISHED) when not published")
        void start_NotPublished_ThrowsException() {
            testCruise.setStatus(CruiseStatus.DRAFT);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            assertThatThrownBy(() -> cruiseService.start(cruiseId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only published cruises can be started");
        }

        @Test
        @DisplayName("complete() should transition IN_PROGRESS to COMPLETED")
        void complete_Success() {
            testCruise.setStatus(CruiseStatus.IN_PROGRESS);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseResponse response = cruiseService.complete(cruiseId);

            assertThat(response.getStatus()).isEqualTo(CruiseStatus.COMPLETED);
            verify(cruiseRepository).save(testCruise);
        }

        @Test
        @DisplayName("complete() should throw BusinessException(CRUISE_NOT_IN_PROGRESS) when not in progress")
        void complete_NotInProgress_ThrowsException() {
            testCruise.setStatus(CruiseStatus.PUBLISHED);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            assertThatThrownBy(() -> cruiseService.complete(cruiseId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only in-progress cruises can be completed");
        }

        @Test
        @DisplayName("cancel() should transition to CANCELLED with valid reason")
        void cancel_Success() {
            testCruise.setStatus(CruiseStatus.PUBLISHED);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));
            when(cruiseRepository.save(any(Cruise.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseResponse response = cruiseService.cancel(cruiseId, "Bad weather forecast");

            assertThat(response.getStatus()).isEqualTo(CruiseStatus.CANCELLED);
            verify(cruiseRepository).save(testCruise);
        }

        @Test
        @DisplayName("cancel() should throw BusinessException(CANNOT_CANCEL_COMPLETED) when already completed")
        void cancel_Completed_ThrowsException() {
            testCruise.setStatus(CruiseStatus.COMPLETED);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            assertThatThrownBy(() -> cruiseService.cancel(cruiseId, "Bad weather"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Completed cruises cannot be cancelled");
        }

        @Test
        @DisplayName("cancel() should throw BusinessException(REASON_REQUIRED) when reason is empty")
        void cancel_EmptyReason_ThrowsException() {
            testCruise.setStatus(CruiseStatus.PUBLISHED);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            assertThatThrownBy(() -> cruiseService.cancel(cruiseId, "  "))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cancellation reason is required");
        }

        @Test
        @DisplayName("cancel() should no-op when already cancelled")
        void cancel_AlreadyCancelled_NoOp() {
            testCruise.setStatus(CruiseStatus.CANCELLED);
            when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(testCruise));

            CruiseResponse response = cruiseService.cancel(cruiseId, "Already cancelled");

            assertThat(response.getStatus()).isEqualTo(CruiseStatus.CANCELLED);
            verify(cruiseRepository, never()).save(any());
        }
    }
}
