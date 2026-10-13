package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.DeckCreateRequest;
import com.casms.ship.dto.request.DeckUpdateRequest;
import com.casms.ship.dto.response.CabinSummaryResponse;
import com.casms.ship.dto.response.DeckCapacityResponse;
import com.casms.ship.dto.response.DeckResponse;
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
class DeckServiceTest {

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private CabinRepository cabinRepository;

    @InjectMocks
    private DeckService deckService;

    private Ship testShip;
    private Deck testDeck;
    private UUID shipId;
    private UUID deckId;

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
        testDeck.setDeckName("Promenade Deck");
        testDeck.setCreatedAt(Instant.now());
        testDeck.setUpdatedAt(Instant.now());
    }

    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create deck successfully when within ship limit and not duplicate")
        void create_Success() {
            DeckCreateRequest request = new DeckCreateRequest();
            request.setShipId(shipId);
            request.setDeckNumber(4);
            request.setDeckName("Lido Deck");

            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.existsByShipShipIdAndDeckNumber(shipId, 4)).thenReturn(false);
            when(deckRepository.save(any(Deck.class))).thenAnswer(inv -> {
                Deck d = inv.getArgument(0);
                d.setDeckId(UUID.randomUUID());
                d.setCreatedAt(Instant.now());
                d.setUpdatedAt(Instant.now());
                return d;
            });

            DeckResponse response = deckService.create(request);

            assertThat(response).isNotNull();
            assertThat(response.getShipId()).isEqualTo(shipId);
            assertThat(response.getDeckNumber()).isEqualTo(4);
            assertThat(response.getDeckName()).isEqualTo("Lido Deck");
            verify(deckRepository).save(any(Deck.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when ship not found")
        void create_ThrowsException_WhenShipNotFound() {
            DeckCreateRequest request = new DeckCreateRequest();
            request.setShipId(shipId);
            request.setDeckNumber(1);

            when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deckService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(deckRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessException(INVALID_DECK_NUMBER) when deck number exceeds ship total decks limit")
        void create_ThrowsException_WhenDeckNumberExceedsShipLimit() {
            testShip.setTotalDecks(5);
            DeckCreateRequest request = new DeckCreateRequest();
            request.setShipId(shipId);
            request.setDeckNumber(6);

            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));

            assertThatThrownBy(() -> deckService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Deck number cannot exceed ship's total decks limit");

            verify(deckRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessException(DUPLICATE_DECK) when deck number already exists on ship")
        void create_ThrowsException_WhenDuplicateDeckNumber() {
            DeckCreateRequest request = new DeckCreateRequest();
            request.setShipId(shipId);
            request.setDeckNumber(3);

            when(shipRepository.findById(shipId)).thenReturn(Optional.of(testShip));
            when(deckRepository.existsByShipShipIdAndDeckNumber(shipId, 3)).thenReturn(true);

            assertThatThrownBy(() -> deckService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Deck number already exists on this ship");

            verify(deckRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("list() & detail() Tests")
    class QueryTests {

        @Test
        @DisplayName("list() should return paged decks")
        void list_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Deck> page = new PageImpl<>(List.of(testDeck), pageable, 1);
            when(deckRepository.findAll(pageable)).thenReturn(page);

            Page<DeckResponse> result = deckService.list(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getDeckName()).isEqualTo("Promenade Deck");
        }

        @Test
        @DisplayName("detail() should return deck response when found")
        void detail_Found() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));

            DeckResponse response = deckService.detail(deckId);

            assertThat(response).isNotNull();
            assertThat(response.getDeckId()).isEqualTo(deckId);
            assertThat(response.getDeckNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("detail() should throw ResourceNotFoundException when deck not found")
        void detail_NotFound() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deckService.detail(deckId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update() Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update deck name and number successfully")
        void update_Success() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            when(deckRepository.existsByShipShipIdAndDeckNumber(shipId, 5)).thenReturn(false);
            when(deckRepository.save(any(Deck.class))).thenAnswer(inv -> inv.getArgument(0));

            DeckUpdateRequest request = new DeckUpdateRequest();
            request.setDeckNumber(5);
            request.setDeckName("Sky Deck");

            DeckResponse response = deckService.update(deckId, request);

            assertThat(response.getDeckNumber()).isEqualTo(5);
            assertThat(response.getDeckName()).isEqualTo("Sky Deck");
            verify(deckRepository).save(testDeck);
        }

        @Test
        @DisplayName("Should throw BusinessException(INVALID_DECK_NUMBER) when new deck number exceeds ship limit")
        void update_ThrowsException_WhenNewDeckNumberExceedsLimit() {
            testShip.setTotalDecks(10);
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));

            DeckUpdateRequest request = new DeckUpdateRequest();
            request.setDeckNumber(11);

            assertThatThrownBy(() -> deckService.update(deckId, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Deck number cannot exceed ship's total decks limit");

            verify(deckRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessException(DUPLICATE_DECK) when new deck number already exists on ship")
        void update_ThrowsException_WhenNewDeckNumberDuplicate() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            when(deckRepository.existsByShipShipIdAndDeckNumber(shipId, 4)).thenReturn(true);

            DeckUpdateRequest request = new DeckUpdateRequest();
            request.setDeckNumber(4);

            assertThatThrownBy(() -> deckService.update(deckId, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Deck number already exists on this ship");

            verify(deckRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not check duplicate if deck number remains unchanged")
        void update_SameDeckNumber_OnlyUpdatesName() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            when(deckRepository.save(any(Deck.class))).thenAnswer(inv -> inv.getArgument(0));

            DeckUpdateRequest request = new DeckUpdateRequest();
            request.setDeckNumber(3);
            request.setDeckName("Renamed Deck");

            DeckResponse response = deckService.update(deckId, request);

            assertThat(response.getDeckName()).isEqualTo("Renamed Deck");
            verify(deckRepository, never()).existsByShipShipIdAndDeckNumber(any(), any());
            verify(deckRepository).save(testDeck);
        }
    }

    @Nested
    @DisplayName("deleteUnused() Tests")
    class DeleteUnusedTests {

        @Test
        @DisplayName("Should delete deck when it has no cabins")
        void deleteUnused_Success() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            when(cabinRepository.existsByDeckDeckId(deckId)).thenReturn(false);

            deckService.deleteUnused(deckId);

            verify(deckRepository).delete(testDeck);
        }

        @Test
        @DisplayName("Should throw BusinessException(DECK_IN_USE) when deck contains cabins")
        void deleteUnused_ThrowsException_WhenDeckHasCabins() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            when(cabinRepository.existsByDeckDeckId(deckId)).thenReturn(true);

            assertThatThrownBy(() -> deckService.deleteUnused(deckId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot delete deck because it contains cabins");

            verify(deckRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("listCabinsByDeck() Tests")
    class ListCabinsByDeckTests {

        @Test
        @DisplayName("Should return mapped cabins on deck")
        void listCabinsByDeck_Success() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));

            Cabin cabin = new Cabin();
            cabin.setCabinId(UUID.randomUUID());
            cabin.setShip(testShip);
            cabin.setDeck(testDeck);
            cabin.setCabinNumber("301");
            cabin.setCabinType(CabinType.BALCONY);
            cabin.setCapacity(4);
            cabin.setStatus(CabinStatus.AVAILABLE);
            cabin.setIsAccessible(true);

            when(cabinRepository.findByDeckDeckId(deckId)).thenReturn(List.of(cabin));

            List<CabinSummaryResponse> cabins = deckService.listCabinsByDeck(deckId);

            assertThat(cabins).hasSize(1);
            assertThat(cabins.get(0).getCabinNumber()).isEqualTo("301");
            assertThat(cabins.get(0).getCabinType()).isEqualTo(CabinType.BALCONY);
            assertThat(cabins.get(0).getCapacity()).isEqualTo(4);
            assertThat(cabins.get(0).getIsAccessible()).isTrue();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when deck not found")
        void listCabinsByDeck_NotFound() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deckService.listCabinsByDeck(deckId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("validateBelongsToShip() Tests")
    class ValidateBelongsToShipTests {

        @Test
        @DisplayName("Should return true when deck belongs to ship")
        void validateBelongsToShip_Matches_ReturnsTrue() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));

            boolean matches = deckService.validateBelongsToShip(deckId, shipId);

            assertThat(matches).isTrue();
        }

        @Test
        @DisplayName("Should return false when deck does not belong to ship")
        void validateBelongsToShip_Mismatch_ReturnsFalse() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            UUID otherShipId = UUID.randomUUID();

            boolean matches = deckService.validateBelongsToShip(deckId, otherShipId);

            assertThat(matches).isFalse();
        }
    }

    @Nested
    @DisplayName("validateDeck() Tests")
    class ValidateDeckTests {

        @Test
        @DisplayName("Should throw BusinessException(INVALID_DECK_NUMBER) when deck number exceeds ship total decks limit")
        void validateDeck_ExceedsLimit_ThrowsException() {
            testShip.setTotalDecks(2);
            testDeck.setDeckNumber(3);
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));

            assertThatThrownBy(() -> deckService.validateDeck(deckId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Deck number violates ship limit constraint");
        }

        @Test
        @DisplayName("Should pass validation when deck number within limit")
        void validateDeck_WithinLimit_Passes() {
            testShip.setTotalDecks(10);
            testDeck.setDeckNumber(3);
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));

            deckService.validateDeck(deckId);
        }
    }

    @Nested
    @DisplayName("getDeckCapacity() Tests")
    class GetDeckCapacityTests {

        @Test
        @DisplayName("Should return correct deck capacity metrics")
        void getDeckCapacity_Success() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            when(cabinRepository.countByDeckDeckId(deckId)).thenReturn(50L);
            when(cabinRepository.countByDeckDeckIdAndStatus(deckId, CabinStatus.AVAILABLE)).thenReturn(40L);
            when(cabinRepository.sumCapacityByDeckId(deckId)).thenReturn(150L);

            DeckCapacityResponse capacity = deckService.getDeckCapacity(deckId);

            assertThat(capacity.getDeckId()).isEqualTo(deckId);
            assertThat(capacity.getDeckName()).isEqualTo("Promenade Deck");
            assertThat(capacity.getDeckNumber()).isEqualTo(3);
            assertThat(capacity.getTotalCabins()).isEqualTo(50L);
            assertThat(capacity.getAvailableCabins()).isEqualTo(40L);
            assertThat(capacity.getTotalPassengerCapacity()).isEqualTo(150L);
        }

        @Test
        @DisplayName("Should handle null sum capacity gracefully with zero")
        void getDeckCapacity_NullSum_ReturnsZero() {
            when(deckRepository.findById(deckId)).thenReturn(Optional.of(testDeck));
            when(cabinRepository.countByDeckDeckId(deckId)).thenReturn(0L);
            when(cabinRepository.countByDeckDeckIdAndStatus(deckId, CabinStatus.AVAILABLE)).thenReturn(0L);
            when(cabinRepository.sumCapacityByDeckId(deckId)).thenReturn(null);

            DeckCapacityResponse capacity = deckService.getDeckCapacity(deckId);

            assertThat(capacity.getTotalPassengerCapacity()).isEqualTo(0L);
        }
    }
}
