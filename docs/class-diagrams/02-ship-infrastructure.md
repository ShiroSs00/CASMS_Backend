# Sơ Đồ Lớp Thiết Kế Chi Tiết: Phân Hệ Hạ Tầng Tàu & Buồng Phòng (Ship Infrastructure Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho quản lý thông số kỹ thuật tàu, các tầng boong, buồng phòng và khu vực công cộng.

## Mã Mermaid UML (Full-Stack 4 Tầng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class ShipController {
        -ShipService shipService
        +create(request) ApiResponse~ShipResponse~
        +list(pageable) ApiResponse~Page~ShipResponse~~
        +detail(shipId) ApiResponse~ShipResponse~
        +update(shipId, request) ApiResponse~ShipResponse~
        +changeStatus(shipId, newStatus) ApiResponse
        +assessOperationalReadiness(shipId) ApiResponse
    }

    class CabinController {
        -CabinService cabinService
        +listCabinsByShip(shipId) ApiResponse~List~CabinResponse~~
        +getCabinDetail(cabinId) ApiResponse~CabinResponse~
        +updateCabinStatus(cabinId, status) ApiResponse
    }

    class DeckController {
        -DeckService deckService
        +listDecksByShip(shipId) ApiResponse~List~DeckResponse~~
        +getDeckLayout(deckId) ApiResponse
    }

    %% 2. Service Layer
    class ShipService {
        -ShipRepository shipRepo
        -DeckRepository deckRepo
        -CabinRepository cabinRepo
        +createShip(dto) ShipResponse
        +updateShip(shipId, dto) ShipResponse
        +getShipDetail(shipId) ShipResponse
        +checkOperationalReadiness(shipId) boolean
    }

    class CabinService {
        -CabinRepository cabinRepo
        -CabinCategoryRepository categoryRepo
        +findCabinsByShip(shipId) List~Cabin~
        +updateStatus(cabinId, status) Cabin
        +checkCapacity(cabinId, guestCount) boolean
    }

    class DeckService {
        -DeckRepository deckRepo
        -AreaRepository areaRepo
        +findDecksByShip(shipId) List~Deck~
        +getFloorPlan(deckId) String
    }

    %% 3. Repository Layer
    class ShipRepository {
        <<interface>>
        +findById(id) Optional~Ship~
        +findByShipCode(code) Optional~Ship~
        +findAllByStatus(status) List~Ship~
        +save(ship) Ship
    }

    class CabinRepository {
        <<interface>>
        +findByDeckId(deckId) List~Cabin~
        +findByCabinNumberAndShipId(number, shipId) Optional~Cabin~
        +save(cabin) Cabin
    }

    class DeckRepository {
        <<interface>>
        +findByShipIdOrderByDeckNumber(shipId) List~Deck~
        +save(deck) Deck
    }

    class AreaRepository {
        <<interface>>
        +findByDeckId(deckId) List~Area~
        +save(area) Area
    }

    %% 4. Entity Layer
    class Ship {
        +UUID shipId
        +String shipCode
        +String shipName
        +Integer passengerCapacity
        +Integer totalDecks
        +ShipStatus status
        +Instant yearBuilt
    }

    class Deck {
        +UUID deckId
        +Integer deckNumber
        +String deckName
        +DeckType deckType
        +String floorPlanUrl
    }

    class Cabin {
        +UUID cabinId
        +String cabinNumber
        +String cabinType
        +Integer maxOccupancy
        +BigDecimal areaSquareMeters
        +Boolean hasBalcony
        +CabinStatus status
    }

    class CabinCategory {
        +UUID categoryId
        +String categoryCode
        +String categoryName
        +BigDecimal standardFareRate
    }

    class Area {
        +UUID areaId
        +String areaCode
        +String areaName
        +AreaType areaType
        +Integer maxOccupancy
    }

    %% Inter-Layer and Domain Relationships
    ShipController --> ShipService : delegatesTo
    CabinController --> CabinService : delegatesTo
    DeckController --> DeckService : delegatesTo

    ShipService --> ShipRepository : accesses
    ShipService --> DeckRepository : accesses
    ShipService --> CabinRepository : accesses
    CabinService --> CabinRepository : accesses
    DeckService --> DeckRepository : accesses
    DeckService --> AreaRepository : accesses

    ShipRepository ..> Ship : manages
    DeckRepository ..> Deck : manages
    CabinRepository ..> Cabin : manages
    AreaRepository ..> Area : manages

    Ship "1" *-- "1..*" Deck : hasDecks
    Deck "1" *-- "0..*" Cabin : locatesCabins
    Deck "1" *-- "0..*" Area : locatesAreas
    Cabin "0..*" --> "1" CabinCategory : classifiedAs
```
