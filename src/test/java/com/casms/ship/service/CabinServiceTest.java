package com.casms.ship.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.entity.CabinAssignment;
import com.casms.booking.repository.CabinAssignmentRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.ship.dto.request.CabinCreateRequest;
import com.casms.ship.dto.request.CabinSearchRequest;
import com.casms.ship.dto.request.CabinUpdateRequest;
import com.casms.ship.dto.response.CabinAssignmentHistoryResponse;
import com.casms.ship.dto.response.CabinSummaryResponse;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.CabinRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CabinServiceTest {

    @Mock
    private CabinRepository cabinRepository;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private CabinAssignmentRepository cabinAssignmentRepository;

    @InjectMocks
    private CabinService cabinService;

    private Ship testShip;
    private Deck testDeck;
    private Cabin testCabin;
    private UUID shipId;
    private UUID deckId;
    private UUID cabinId;

    @BeforeEach
    void setUp() {
        shipId = UUID.randomUUID();
        testShip = new Ship();
        testShip.setShipId(shipId);
        testShip.setShipCode("SH-001");
        testShip.setShipName("Ocean Star");
        testShip.setStatus(ShipStatus.ACTIVE);
        testShip.setTotalDecks(10);
        testShip.setTotalCapacity(2000);

        deckId = UUID.randomUUID();
        testDeck = new Deck();
        testDeck.setDeckId(deckId);
        testDeck.setShip(testShip);
        testDeck.setDeckNumber(3);
        testDeck.setDeckName("Deck 3");

        cabinId = UUID.randomUUID();
        testCabin = new Cabin();
        testCabin.setCabinId(cabinId);
        testCabin.setShip(testShip);
        testCabin.setDeck(testDeck);
        testCabin.setCabinNumber("301");
        testCabin.setCabinType(CabinType.BALCONY);
        testCabin.setCapacity(4);
        testCabin.setStatus(CabinStatus.AVAILABLE);
        testCabin.setIsAccessible(true);
        testCabin.setCreatedAt(Instant.now());
        testCabin.setUpdatedAt(Instant.now());
    }

    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create cabin successfully when valid")
        void create_Success() {
            CabinCreateRequest request = new CabinCreateRequest();
            request.setShipId(shipId);
            request.setDeckId(deckId);
            request.setCabinNumber("302");
            request.setCabinType(CabinType.SUITE);
            request.setCapacity(2);
            request.setStatus(CabinStatus.AVAILABLE);
            request.setIsAccessible(false);

            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            when(cabinRepository.existsByShipShipIdAndCabinNumber(shipId, "302")).thenReturn(false);
            when(cabinRepository.save(any(Cabin.class))).thenAnswer(inv -> {
                Cabin c = inv.getArgument(0);
                c.setCabinId(UUID.randomUUID());
                return c;
            });

            CabinSummaryResponse response = cabinService.create(request);

            assertThat(response).isNotNull();
            assertThat(response.getCabinNumber()).isEqualTo("302");
            assertThat(response.getCabinType()).isEqualTo(CabinType.SUITE);
            assertThat(response.getCapacity()).isEqualTo(2);
            assertThat(response.getStatus()).isEqualTo(CabinStatus.AVAILABLE);
            assertThat(response.getIsAccessible()).isFalse();
            verify(cabinRepository).save(any(Cabin.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when ship not found")
        void create_ThrowsException_WhenShipNotFound() {
            CabinCreateRequest request = new CabinCreateRequest();
            request.setShipId(shipId);
            request.setDeckId(deckId);

            when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cabinService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(cabinRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when deck not found")
        void create_ThrowsException_WhenDeckNotFound() {
            CabinCreateRequest request = new CabinCreateRequest();
            request.setShipId(shipId);
            request.setDeckId(deckId);

            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.findById(deckId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cabinService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(cabinRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessException(DECK_SHIP_MISMATCH) when deck belongs to a different ship")
        void create_ThrowsException_WhenDeckShipMismatch() {
            Ship otherShip = new Ship();
            otherShip.setShipId(UUID.randomUUID());
            testDeck.setShip(otherShip);

            CabinCreateRequest request = new CabinCreateRequest();
            request.setShipId(shipId);
            request.setDeckId(deckId);

            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));

            assertThatThrownBy(() -> cabinService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("The deck does not belong to the specified ship");

            verify(cabinRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessException(DUPLICATE_CABIN) when cabin number already exists on ship")
        void create_ThrowsException_WhenDuplicateCabinNumber() {
            CabinCreateRequest request = new CabinCreateRequest();
            request.setShipId(shipId);
            request.setDeckId(deckId);
            request.setCabinNumber("301");

            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            when(cabinRepository.existsByShipShipIdAndCabinNumber(shipId, "301")).thenReturn(true);

            assertThatThrownBy(() -> cabinService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cabin number already exists on this ship");

            verify(cabinRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("list() & detail() Tests")
    class QueryTests {

        @Test
        @DisplayName("list() should return paged cabins")
        void list_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Cabin> page = new PageImpl<>(List.of(testCabin), pageable, 1);
            when(cabinRepository.findAll(pageable)).thenReturn(page);

            Page<CabinSummaryResponse> result = cabinService.list(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getCabinNumber()).isEqualTo("301");
        }

        @Test
        @DisplayName("detail() should return cabin response when found")
        void detail_Found() {
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            CabinSummaryResponse response = cabinService.detail(cabinId);

            assertThat(response).isNotNull();
            assertThat(response.getCabinId()).isEqualTo(cabinId);
            assertThat(response.getCabinNumber()).isEqualTo("301");
        }

        @Test
        @DisplayName("detail() should throw ResourceNotFoundException when cabin not found")
        void detail_NotFound() {
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cabinService.detail(cabinId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update() Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update cabin fields and save")
        void update_Success() {
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));
            when(cabinRepository.save(any(Cabin.class))).thenAnswer(inv -> inv.getArgument(0));

            CabinUpdateRequest request = new CabinUpdateRequest();
            request.setCabinType(CabinType.SUITE);
            request.setCapacity(6);
            request.setIsAccessible(false);

            CabinSummaryResponse response = cabinService.update(cabinId, request);

            assertThat(response.getCabinType()).isEqualTo(CabinType.SUITE);
            assertThat(response.getCapacity()).isEqualTo(6);
            assertThat(response.getIsAccessible()).isFalse();
            verify(cabinRepository).save(testCabin);
        }
    }

    @Nested
    @DisplayName("deleteUnused() Tests")
    class DeleteUnusedTests {

        @Test
        @DisplayName("Should delete cabin when not assigned to bookings")
        void deleteUnused_Success() {
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));
            when(cabinAssignmentRepository.existsByCabinCabinId(cabinId)).thenReturn(false);

            cabinService.deleteUnused(cabinId);

            verify(cabinRepository).delete(testCabin);
        }

        @Test
        @DisplayName("Should throw BusinessException(CABIN_IN_USE) when cabin has assignments")
        void deleteUnused_ThrowsException_WhenCabinAssigned() {
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));
            when(cabinAssignmentRepository.existsByCabinCabinId(cabinId)).thenReturn(true);

            assertThatThrownBy(() -> cabinService.deleteUnused(cabinId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot delete cabin because it has been assigned to bookings");

            verify(cabinRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("searchAvailable() Tests")
    class SearchAvailableTests {

        @Test
        @DisplayName("Should return available cabins matching criteria")
        void searchAvailable_Success() {
            CabinSearchRequest request = new CabinSearchRequest();
            request.setShipId(shipId);
            request.setDeckId(deckId);
            request.setCabinType(CabinType.BALCONY);
            request.setMinCapacity(2);
            request.setIsAccessible(true);

            when(cabinRepository.searchAvailable(shipId, deckId, CabinType.BALCONY, 2, true))
                    .thenReturn(List.of(testCabin));

            List<CabinSummaryResponse> results = cabinService.searchAvailable(request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getCabinNumber()).isEqualTo("301");
        }
    }

    @Nested
    @DisplayName("checkCapacity() & checkCompatibility() Tests")
    class CapacityAndCompatibilityTests {

        @Test
        @DisplayName("checkCapacity() should return true when capacity is sufficient")
        void checkCapacity_Sufficient_ReturnsTrue() {
            testCabin.setCapacity(4);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            assertThat(cabinService.checkCapacity(cabinId, 3)).isTrue();
            assertThat(cabinService.checkCapacity(cabinId, 4)).isTrue();
        }

        @Test
        @DisplayName("checkCapacity() should return false when capacity is insufficient")
        void checkCapacity_Insufficient_ReturnsFalse() {
            testCabin.setCapacity(2);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            assertThat(cabinService.checkCapacity(cabinId, 3)).isFalse();
        }

        @Test
        @DisplayName("checkCompatibility() should return true when accessibility required and cabin is accessible")
        void checkCompatibility_RequiresAccessible_Matches_ReturnsTrue() {
            testCabin.setIsAccessible(true);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            assertThat(cabinService.checkCompatibility(cabinId, true)).isTrue();
        }

        @Test
        @DisplayName("checkCompatibility() should return false when accessibility required but cabin is not accessible")
        void checkCompatibility_RequiresAccessible_Mismatch_ReturnsFalse() {
            testCabin.setIsAccessible(false);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            assertThat(cabinService.checkCompatibility(cabinId, true)).isFalse();
        }

        @Test
        @DisplayName("checkCompatibility() should return true when accessibility is not required")
        void checkCompatibility_AccessibleNotRequired_ReturnsTrue() {
            testCabin.setIsAccessible(false);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            assertThat(cabinService.checkCompatibility(cabinId, false)).isTrue();
            assertThat(cabinService.checkCompatibility(cabinId, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("changeOperationalStatus() Tests")
    class ChangeOperationalStatusTests {

        @Test
        @DisplayName("Should throw BusinessException(CABIN_OCCUPIED) when changing OCCUPIED cabin to MAINTENANCE")
        void changeOperationalStatus_OccupiedToMaintenance_ThrowsException() {
            testCabin.setStatus(CabinStatus.OCCUPIED);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            assertThatThrownBy(() -> cabinService.changeOperationalStatus(cabinId, CabinStatus.MAINTENANCE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot change status to MAINTENANCE because cabin is occupied");

            verify(cabinRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessException(CABIN_OCCUPIED) when changing OCCUPIED cabin to OUT_OF_SERVICE")
        void changeOperationalStatus_OccupiedToOutOfService_ThrowsException() {
            testCabin.setStatus(CabinStatus.OCCUPIED);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            assertThatThrownBy(() -> cabinService.changeOperationalStatus(cabinId, CabinStatus.OUT_OF_SERVICE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot change status to OUT_OF_SERVICE because cabin is occupied");

            verify(cabinRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should change status successfully when not blocked by occupancy rule")
        void changeOperationalStatus_Success() {
            testCabin.setStatus(CabinStatus.AVAILABLE);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));
            when(cabinRepository.save(any(Cabin.class))).thenAnswer(inv -> inv.getArgument(0));

            CabinSummaryResponse response = cabinService.changeOperationalStatus(cabinId, CabinStatus.MAINTENANCE);

            assertThat(response.getStatus()).isEqualTo(CabinStatus.MAINTENANCE);
            verify(cabinRepository).save(testCabin);
        }
    }

    @Nested
    @DisplayName("getAssignmentHistory() Tests")
    class AssignmentHistoryTests {

        @Test
        @DisplayName("Should return mapped cabin assignment history")
        void getAssignmentHistory_Success() {
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            UUID assignmentId = UUID.randomUUID();
            UUID passengerId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            BookingPassenger bp = new BookingPassenger();
            bp.setBookingPassengerId(passengerId);

            User user = new User();
            user.setUserId(userId);

            CabinAssignment assignment = new CabinAssignment();
            assignment.setCabinAssignmentId(assignmentId);
            assignment.setCabin(testCabin);
            assignment.setBookingPassenger(bp);
            assignment.setAssignedByUser(user);
            assignment.setValidFrom(Instant.now().minusSeconds(86400));
            assignment.setValidTo(Instant.now());
            assignment.setChangeReason("Room upgrade");

            when(cabinAssignmentRepository.findByCabinCabinIdOrderByValidFromDesc(cabinId))
                    .thenReturn(List.of(assignment));

            List<CabinAssignmentHistoryResponse> history = cabinService.getAssignmentHistory(cabinId);

            assertThat(history).hasSize(1);
            assertThat(history.get(0).getCabinAssignmentId()).isEqualTo(assignmentId);
            assertThat(history.get(0).getBookingPassengerId()).isEqualTo(passengerId);
            assertThat(history.get(0).getAssignedByUserId()).isEqualTo(userId);
            assertThat(history.get(0).getChangeReason()).isEqualTo("Room upgrade");
        }
    }

    @Nested
    @DisplayName("validateCabin() Tests")
    class ValidateCabinTests {

        @Test
        @DisplayName("Should throw BusinessException(DATA_INTEGRITY_ERROR) when cabin deck ship doesn't match cabin ship")
        void validateCabin_Mismatch_ThrowsException() {
            Ship otherShip = new Ship();
            otherShip.setShipId(UUID.randomUUID());
            testDeck.setShip(otherShip);

            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            assertThatThrownBy(() -> cabinService.validateCabin(cabinId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cabin's deck does not belong to the cabin's assigned ship");
        }

        @Test
        @DisplayName("Should pass validation when consistent")
        void validateCabin_Consistent_Passes() {
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            cabinService.validateCabin(cabinId);
        }
    }

    @Nested
    @DisplayName("checkAvailability(), blockCabin(), releaseCabin() Tests")
    class AvailabilityAndLockingTests {

        @Test
        @DisplayName("checkAvailability() should return true when AVAILABLE, false otherwise")
        void checkAvailability_Tests() {
            testCabin.setStatus(CabinStatus.AVAILABLE);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));
            assertThat(cabinService.checkAvailability(cabinId)).isTrue();

            testCabin.setStatus(CabinStatus.OCCUPIED);
            assertThat(cabinService.checkAvailability(cabinId)).isFalse();
        }

        @Test
        @DisplayName("blockCabin() should throw BusinessException(INVALID_BLOCK_REASON) when reason is not OCCUPIED or MAINTENANCE")
        void blockCabin_InvalidReasonStatus_ThrowsException() {
            assertThatThrownBy(() -> cabinService.blockCabin(cabinId, CabinStatus.AVAILABLE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cabin can only be blocked as OCCUPIED or MAINTENANCE");

            verify(cabinRepository, never()).save(any());
        }

        @Test
        @DisplayName("blockCabin() should throw BusinessException(CABIN_NOT_AVAILABLE) when cabin is not AVAILABLE")
        void blockCabin_NotAvailable_ThrowsException() {
            testCabin.setStatus(CabinStatus.MAINTENANCE);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            assertThatThrownBy(() -> cabinService.blockCabin(cabinId, CabinStatus.OCCUPIED))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cabin is not available to be blocked");

            verify(cabinRepository, never()).save(any());
        }

        @Test
        @DisplayName("blockCabin() should successfully block available cabin")
        void blockCabin_Success() {
            testCabin.setStatus(CabinStatus.AVAILABLE);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));
            when(cabinRepository.save(any(Cabin.class))).thenAnswer(inv -> inv.getArgument(0));

            CabinSummaryResponse response = cabinService.blockCabin(cabinId, CabinStatus.MAINTENANCE);

            assertThat(response.getStatus()).isEqualTo(CabinStatus.MAINTENANCE);
            verify(cabinRepository).save(testCabin);
        }

        @Test
        @DisplayName("releaseCabin() should release occupied/maintenance cabin to AVAILABLE")
        void releaseCabin_Success() {
            testCabin.setStatus(CabinStatus.MAINTENANCE);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));
            when(cabinRepository.save(any(Cabin.class))).thenAnswer(inv -> inv.getArgument(0));

            CabinSummaryResponse response = cabinService.releaseCabin(cabinId);

            assertThat(response.getStatus()).isEqualTo(CabinStatus.AVAILABLE);
            verify(cabinRepository).save(testCabin);
        }

        @Test
        @DisplayName("releaseCabin() should do nothing and return if already AVAILABLE")
        void releaseCabin_AlreadyAvailable_NoOp() {
            testCabin.setStatus(CabinStatus.AVAILABLE);
            when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(testCabin));

            CabinSummaryResponse response = cabinService.releaseCabin(cabinId);

            assertThat(response.getStatus()).isEqualTo(CabinStatus.AVAILABLE);
            verify(cabinRepository, never()).save(any());
        }
    }
}
