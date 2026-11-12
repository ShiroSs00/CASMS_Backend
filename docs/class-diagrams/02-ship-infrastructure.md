# Phân Hệ Hạ Tầng Kỹ Thuật Tàu, Boong & Buồng Phòng (Ship Infrastructure)

Quản lý thực thể vật lý du thuyền (`Ship`), các tầng boong (`Deck`), buồng phòng (`Cabin`), phân loại hạng phòng (`CabinCategory`), khu chức năng công cộng (`Area`) và checklist nghiệm thu kỹ thuật an toàn trước hải trình (`CruisePreparationSection`).

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

    class Ship {
        +UUID shipId
        +String shipCode
        +String shipName
        +Integer grossTonnage
        +BigDecimal lengthMeters
        +BigDecimal beamMeters
        +Integer totalDecks
        +Integer passengerCapacity
        +Integer crewCapacity
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
        +String roomName
        +Integer maxOccupancy
        +BigDecimal areaSquareMeters
        +Boolean hasBalcony
        +Boolean isAccessible
        +CabinStatus status
    }

    class CabinCategory {
        +UUID categoryId
        +String categoryCode
        +String categoryName
        +String description
        +BigDecimal standardFareRate
    }

    class Area {
        +UUID areaId
        +String areaCode
        +String areaName
        +AreaType areaType
        +Integer maxOccupancy
        +Boolean isRestricted
    }

    class ShipRegulation {
        +UUID regulationId
        +String regulationCode
        +String title
        +String contentText
        +String authorityBody
        +Boolean isMandatory
    }

    class CruisePreparationSection {
        +UUID sectionId
        +String sectionCode
        +String sectionTitle
        +PreparationStatus status
        +Instant inspectedAt
        +String inspectedBy
    }

    class PreparationItem {
        +UUID itemId
        +String itemName
        +Boolean isPassed
        +String note
    }

    %% Relationships
    Ship "1" *-- "1..*" Deck : hasDecks
    Deck "1" *-- "0..*" Cabin : locatesCabins
    Deck "1" *-- "0..*" Area : locatesAreas
    Cabin "0..*" --> "1" CabinCategory : classifiedAs
    Ship "1" o-- "0..*" ShipRegulation : governedBy
    Ship "1" *-- "0..*" CruisePreparationSection : readinessChecklist
    CruisePreparationSection "1" *-- "1..*" PreparationItem : containsChecks
```

## Giải Thích Quan Hệ & Ràng Buộc
- **Ship - Deck**: Quan hệ Composition (`1 *-- 1..*`), một con tàu bắt buộc phải có ít nhất 1 boong tàu. Khi xóa tàu, các boong tàu thuộc về tàu đó sẽ bị xóa theo.
- **Deck - Cabin / Area**: Boong tàu là không gian vật lý chứa các buồng phòng lưu trú (`Cabin`) và các khu chức năng công cộng (`Area` như nhà hàng, nhà hát, hồ bơi).
- **Cabin - CabinCategory**: Mỗi buồng phòng thuộc một hạng phòng cụ thể (Interior, Ocean View, Balcony, Suite) với định mức giá vé chuẩn.
- **Ship - CruisePreparationSection - PreparationItem**: Checklist kiểm định kỹ thuật (máy chính, phao cứu sinh, hệ thống PCCC) trước khi tàu đủ điều kiện xuất bến.

