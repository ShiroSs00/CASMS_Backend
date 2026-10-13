package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.ShipCreateRequest;
import com.casms.ship.dto.request.ShipUpdateRequest;
import com.casms.ship.dto.response.CruiseSummaryResponse;
import com.casms.ship.dto.response.ShipCapacitySummaryResponse;
import com.casms.ship.dto.response.ShipResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.ShipRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipServiceTest {

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private CruiseRepository cruiseRepository;

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private CabinRepository cabinRepository;

    @InjectMocks
    private ShipService shipService;

    private Ship testShip;
    private UUID shipId;

    @BeforeEach
    void setUp() {
        shipId = UUID.randomUUID();
        testShip = new Ship();
        testShip.setShipId(shipId);
        testShip.setShipCode("SH-001");
        testShip.setShipName("Ocean Voyager");
        testShip.setStatus(ShipStatus.ACTIVE);
        testShip.setTotalCapacity(2000);
        testShip.setTotalDecks(10);
        testShip.setCommissionDate(LocalDate.of(2020, 1, 15));
        testShip.setCreatedAt(Instant.now());
        testShip.setUpdatedAt(Instant.now());
    }

    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create ship successfully")
        void create_Success() {
            ShipCreateRequest request = new ShipCreateRequest();
            request.setShipCode("SH-002");
            request.setShipName("Sea Explorer");
            request.setStatus(ShipStatus.ACTIVE);
            request.setTotalCapacity(1500);
            request.setTotalDecks(8);
            request.setCommissionDate(LocalDate.of(2022, 5, 10));

            when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> {
                Ship s = invocation.getArgument(0);
                s.setShipId(UUID.randomUUID());
                s.setCreatedAt(Instant.now());
                s.setUpdatedAt(Instant.now());
                return s;
            });

            ShipResponse response = shipService.create(request);

            assertThat(response).isNotNull();
            assertThat(response.getShipCode()).isEqualTo("SH-002");
            assertThat(response.getShipName()).isEqualTo("Sea Explorer");
            assertThat(response.getStatus()).isEqualTo(ShipStatus.ACTIVE);
            assertThat(response.getTotalCapacity()).isEqualTo(1500);
            assertThat(response.getTotalDecks()).isEqualTo(8);
            verify(shipRepository).save(any(Ship.class));
        }
    }

    @Nested
    @DisplayName("list() & detail() Tests")
    class QueryTests {

        @Test
        @DisplayName("list() should return paged ships")
        void list_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Ship> page = new PageImpl<>(List.of(testShip), pageable, 1);
            when(shipRepository.findAll(pageable)).thenReturn(page);

            Page<ShipResponse> result = shipService.list(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getShipCode()).isEqualTo("SH-001");
        }

        @Test
        @DisplayName("detail() should return ship response when found")
        void detail_Found() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));

            ShipResponse response = shipService.detail(shipId);

            assertThat(response).isNotNull();
            assertThat(response.getShipId()).isEqualTo(shipId);
            assertThat(response.getShipName()).isEqualTo("Ocean Voyager");
        }

        @Test
        @DisplayName("detail() should throw ResourceNotFoundException when ship not found")
        void detail_NotFound() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shipService.detail(shipId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update() Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update ship fields and save")
        void update_Success() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

            ShipUpdateRequest updateRequest = new ShipUpdateRequest();
            updateRequest.setShipName("Ocean Voyager II");
            updateRequest.setTotalCapacity(2500);
            updateRequest.setTotalDecks(12);

            ShipResponse response = shipService.update(shipId, updateRequest);

            assertThat(response.getShipName()).isEqualTo("Ocean Voyager II");
            assertThat(response.getTotalCapacity()).isEqualTo(2500);
            assertThat(response.getTotalDecks()).isEqualTo(12);
            verify(shipRepository).save(testShip);
        }

        @Test
        @DisplayName("update() should throw ResourceNotFoundException when ship not found")
        void update_NotFound() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

            ShipUpdateRequest updateRequest = new ShipUpdateRequest();
            updateRequest.setShipName("New Name");

            assertThatThrownBy(() -> shipService.update(shipId, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteUnused() Tests")
    class DeleteUnusedTests {

        @Test
        @DisplayName("Should delete ship when not assigned to any cruise")
        void deleteUnused_Success() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(cruiseRepository.existsByShip(testShip)).thenReturn(false);

            shipService.deleteUnused(shipId);

            verify(shipRepository).delete(testShip);
        }

        @Test
        @DisplayName("Should throw BusinessException(SHIP_IN_USE) when ship is assigned to cruises")
        void deleteUnused_ThrowsException_WhenAssignedToCruise() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(cruiseRepository.existsByShip(testShip)).thenReturn(true);

            assertThatThrownBy(() -> shipService.deleteUnused(shipId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot delete ship because it is assigned to one or more cruises");

            verify(shipRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("changeStatus() Tests")
    class ChangeStatusTests {

        @Test
        @DisplayName("Should return without saving when status unchanged")
        void changeStatus_SameStatus_NoOp() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));

            ShipResponse response = shipService.changeStatus(shipId, ShipStatus.ACTIVE);

            assertThat(response.getStatus()).isEqualTo(ShipStatus.ACTIVE);
            verify(shipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessException(SHIP_ACTIVE_CRUISE) when changing to INACTIVE during active cruise")
        void changeStatus_ThrowsException_WhenActiveCruiseInProgress() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));

            Cruise activeCruise = new Cruise();
            activeCruise.setCruiseId(UUID.randomUUID());
            activeCruise.setStatus(CruiseStatus.IN_PROGRESS);
            when(cruiseRepository.findCurrentActiveCruise(eq(shipId), any(Instant.class)))
                    .thenReturn(Optional.of(activeCruise));

            assertThatThrownBy(() -> shipService.changeStatus(shipId, ShipStatus.INACTIVE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot change status to INACTIVE while ship is on an active cruise");

            verify(shipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessException(SHIP_ACTIVE_CRUISE) when changing to MAINTENANCE during active cruise")
        void changeStatus_ThrowsException_WhenChangingToMaintenanceDuringActiveCruise() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));

            Cruise activeCruise = new Cruise();
            when(cruiseRepository.findCurrentActiveCruise(eq(shipId), any(Instant.class)))
                    .thenReturn(Optional.of(activeCruise));

            assertThatThrownBy(() -> shipService.changeStatus(shipId, ShipStatus.MAINTENANCE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot change status to MAINTENANCE while ship is on an active cruise");
        }

        @Test
        @DisplayName("Should change status to MAINTENANCE when no active cruise")
        void changeStatus_Success_WhenNoActiveCruise() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(cruiseRepository.findCurrentActiveCruise(eq(shipId), any(Instant.class)))
                    .thenReturn(Optional.empty());
            when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

            ShipResponse response = shipService.changeStatus(shipId, ShipStatus.MAINTENANCE);

            assertThat(response.getStatus()).isEqualTo(ShipStatus.MAINTENANCE);
            verify(shipRepository).save(testShip);
        }

        @Test
        @DisplayName("Should change status from INACTIVE to ACTIVE directly")
        void changeStatus_ToActive_Success() {
            testShip.setStatus(ShipStatus.INACTIVE);
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

            ShipResponse response = shipService.changeStatus(shipId, ShipStatus.ACTIVE);

            assertThat(response.getStatus()).isEqualTo(ShipStatus.ACTIVE);
            verify(cruiseRepository, never()).findCurrentActiveCruise(any(), any());
            verify(shipRepository).save(testShip);
        }
    }

    @Nested
    @DisplayName("checkScheduleAvailability() Tests")
    class ScheduleAvailabilityTests {

        @Test
        @DisplayName("Should throw BusinessException(INVALID_DATE_RANGE) when start after end")
        void checkScheduleAvailability_StartAfterEnd_ThrowsException() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            Instant now = Instant.now();
            Instant later = now.plusSeconds(3600);

            assertThatThrownBy(() -> shipService.checkScheduleAvailability(shipId, later, now))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Start time must be before end time");
        }

        @Test
        @DisplayName("Should return true when no overlapping cruises")
        void checkScheduleAvailability_Available() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            Instant start = Instant.now();
            Instant end = start.plusSeconds(86400);
            when(cruiseRepository.existsOverlappingCruises(shipId, start, end)).thenReturn(false);

            boolean available = shipService.checkScheduleAvailability(shipId, start, end);

            assertThat(available).isTrue();
        }

        @Test
        @DisplayName("Should return false when overlapping cruises exist")
        void checkScheduleAvailability_NotAvailable() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            Instant start = Instant.now();
            Instant end = start.plusSeconds(86400);
            when(cruiseRepository.existsOverlappingCruises(shipId, start, end)).thenReturn(true);

            boolean available = shipService.checkScheduleAvailability(shipId, start, end);

            assertThat(available).isFalse();
        }
    }

    @Nested
    @DisplayName("getCapacitySummary() Tests")
    class CapacitySummaryTests {

        @Test
        @DisplayName("Should return aggregate deck and cabin counts")
        void getCapacitySummary_Success() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.countByShipShipId(shipId)).thenReturn(8L);
            when(cabinRepository.countByShipShipId(shipId)).thenReturn(400L);
            when(cabinRepository.countByShipShipIdAndStatus(shipId, CabinStatus.AVAILABLE)).thenReturn(350L);

            ShipCapacitySummaryResponse summary = shipService.getCapacitySummary(shipId);

            assertThat(summary.getShipId()).isEqualTo(shipId);
            assertThat(summary.getShipName()).isEqualTo("Ocean Voyager");
            assertThat(summary.getConfiguredTotalCapacity()).isEqualTo(2000);
            assertThat(summary.getConfiguredTotalDecks()).isEqualTo(10);
            assertThat(summary.getActualDeckCount()).isEqualTo(8L);
            assertThat(summary.getActualCabinCount()).isEqualTo(400L);
            assertThat(summary.getAvailableCabinCount()).isEqualTo(350L);
        }
    }

    @Nested
    @DisplayName("getAffectedCruises() & getCurrentCruise() Tests")
    class CruiseQueryTests {

        @Test
        @DisplayName("getAffectedCruises() should return mapped cruises")
        void getAffectedCruises_Success() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            Instant start = Instant.now();
            Instant end = start.plusSeconds(86400 * 7);

            Cruise cruise = new Cruise();
            cruise.setCruiseId(UUID.randomUUID());
            cruise.setCruiseCode("CR-101");
            cruise.setCruiseName("Bahamas Getaway");
            cruise.setStatus(CruiseStatus.PUBLISHED);
            cruise.setDepartureDatetime(start.plusSeconds(86400));
            cruise.setReturnDatetime(start.plusSeconds(86400 * 4));

            when(cruiseRepository.findAffectedCruises(shipId, start, end)).thenReturn(List.of(cruise));

            List<CruiseSummaryResponse> affected = shipService.getAffectedCruises(shipId, start, end);

            assertThat(affected).hasSize(1);
            assertThat(affected.get(0).getCruiseCode()).isEqualTo("CR-101");
        }

        @Test
        @DisplayName("getCurrentCruise() should return cruise when active")
        void getCurrentCruise_Found() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));

            Cruise cruise = new Cruise();
            cruise.setCruiseId(UUID.randomUUID());
            cruise.setCruiseCode("CR-CURRENT");
            cruise.setStatus(CruiseStatus.IN_PROGRESS);
            cruise.setDepartureDatetime(Instant.now().minusSeconds(3600));
            cruise.setReturnDatetime(Instant.now().plusSeconds(3600));

            when(cruiseRepository.findCurrentActiveCruise(eq(shipId), any(Instant.class)))
                    .thenReturn(Optional.of(cruise));

            CruiseSummaryResponse response = shipService.getCurrentCruise(shipId);

            assertThat(response.getCruiseCode()).isEqualTo("CR-CURRENT");
        }

        @Test
        @DisplayName("getCurrentCruise() should throw ResourceNotFoundException when no active cruise")
        void getCurrentCruise_NotFound() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(cruiseRepository.findCurrentActiveCruise(eq(shipId), any(Instant.class)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> shipService.getCurrentCruise(shipId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No active cruise found for ship currently");
        }
    }

    @Nested
    @DisplayName("validateShip() Tests")
    class ValidateShipTests {

        @Test
        @DisplayName("Should throw BusinessException(SHIP_VALIDATION_FAILED) when actual decks exceed ship total decks limit")
        void validateShip_ActualDecksExceedLimit_ThrowsException() {
            testShip.setTotalDecks(5);
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.countByShipShipId(shipId)).thenReturn(6L);

            assertThatThrownBy(() -> shipService.validateShip(shipId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Actual configured decks exceed ship's total deck limit");
        }

        @Test
        @DisplayName("Should pass validation when actual decks are within limit")
        void validateShip_WithinLimit_Passes() {
            testShip.setTotalDecks(10);
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.countByShipShipId(shipId)).thenReturn(8L);

            shipService.validateShip(shipId);
        }
    }

    @Nested
    @DisplayName("validateOperationalReadiness() Tests")
    class ValidateOperationalReadinessTests {

        @Test
        @DisplayName("Should return false when status is not ACTIVE")
        void validateOperationalReadiness_NotActive_ReturnsFalse() {
            testShip.setStatus(ShipStatus.MAINTENANCE);
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));

            boolean ready = shipService.validateOperationalReadiness(shipId);

            assertThat(ready).isFalse();
            verify(deckRepository, never()).countByShipShipId(any());
        }

        @Test
        @DisplayName("Should return false when ship has no decks")
        void validateOperationalReadiness_ZeroDecks_ReturnsFalse() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.countByShipShipId(shipId)).thenReturn(0L);
            when(cabinRepository.countByShipShipId(shipId)).thenReturn(10L);

            boolean ready = shipService.validateOperationalReadiness(shipId);

            assertThat(ready).isFalse();
        }

        @Test
        @DisplayName("Should return false when ship has no cabins")
        void validateOperationalReadiness_ZeroCabins_ReturnsFalse() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.countByShipShipId(shipId)).thenReturn(5L);
            when(cabinRepository.countByShipShipId(shipId)).thenReturn(0L);

            boolean ready = shipService.validateOperationalReadiness(shipId);

            assertThat(ready).isFalse();
        }

        @Test
        @DisplayName("Should return true when status is ACTIVE and has both decks and cabins")
        void validateOperationalReadiness_Ready_ReturnsTrue() {
            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.countByShipShipId(shipId)).thenReturn(5L);
            when(cabinRepository.countByShipShipId(shipId)).thenReturn(50L);

            boolean ready = shipService.validateOperationalReadiness(shipId);

            assertThat(ready).isTrue();
        }
    }
}
