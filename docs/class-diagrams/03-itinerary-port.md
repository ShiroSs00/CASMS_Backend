# Sơ Đồ Lớp Thiết Kế Chi Tiết: Phân Hệ Hải Trình & Cảng Biển (Itinerary & Port Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho lập kế hoạch lộ trình đường biển, các chặng dừng tại cảng và lịch trình chi tiết theo từng ngày của chuyến đi.

## Mã Mermaid UML (Full-Stack 4 Tầng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class CruiseController {
        -CruiseService cruiseService
        +create(request) ApiResponse~CruiseResponse~
        +list(pageable) ApiResponse~Page~CruiseResponse~~
        +detail(cruiseId) ApiResponse~CruiseResponse~
        +assignShip(cruiseId, shipId) ApiResponse
        +publish(cruiseId) ApiResponse
        +start(cruiseId) ApiResponse
        +complete(cruiseId) ApiResponse
    }

    class ItineraryController {
        -ItineraryService itineraryService
        +createItinerary(request) ApiResponse
        +listItineraries() ApiResponse
        +getItineraryDetail(id) ApiResponse
        +publishItinerary(id) ApiResponse
    }

    %% 2. Service Layer
    class CruiseService {
        -CruiseRepository cruiseRepo
        -CruiseStopRepository stopRepo
        -ItineraryRepository itineraryRepo
        +createCruise(dto) CruiseResponse
        +assignShipToCruise(cruiseId, shipId) CruiseResponse
        +startCruise(cruiseId) CruiseResponse
        +completeCruise(cruiseId) CruiseResponse
        +validateScheduleOverlap(shipId, departure, returns) boolean
    }

    class ItineraryService {
        -ItineraryRepository itineraryRepo
        -PortRepository portRepo
        +createItinerary(dto) Itinerary
        +publishItinerary(id) Itinerary
        +calculateTotalDuration(id) int
    }

    %% 3. Repository Layer
    class CruiseRepository {
        <<interface>>
        +findById(id) Optional~Cruise~
        +findByCruiseCode(code) Optional~Cruise~
        +findByShipIdAndStatus(shipId, status) List~Cruise~
        +save(cruise) Cruise
    }

    class ItineraryRepository {
        <<interface>>
        +findById(id) Optional~Itinerary~
        +findByStatus(status) List~Itinerary~
        +save(itinerary) Itinerary
    }

    class CruiseStopRepository {
        <<interface>>
        +findByCruiseIdOrderBySequenceNumber(cruiseId) List~CruiseStop~
        +findByPortId(portId) List~CruiseStop~
        +save(stop) CruiseStop
    }

    class PortRepository {
        <<interface>>
        +findById(id) Optional~Port~
        +findByPortCode(code) Optional~Port~
        +save(port) Port
    }

    %% 4. Entity Layer
    class Cruise {
        +UUID cruiseId
        +String cruiseCode
        +String cruiseName
        +Instant departureDatetime
        +Instant returnDatetime
        +String plannedScope
        +CruiseStatus status
        +Instant publishedAt
    }

    class Itinerary {
        +UUID itineraryId
        +String itineraryCode
        +String itineraryName
        +Integer durationDays
        +Integer durationNights
        +ItineraryStatus status
    }

    class CruiseStop {
        +UUID cruiseStopId
        +Integer sequenceNumber
        +Instant plannedArrivalTime
        +Instant plannedDepartureTime
        +String berthNumber
    }

    class Port {
        +UUID portId
        +String portCode
        +String portName
        +String countryCode
        +Boolean isHomePort
    }

    %% Inter-Layer and Domain Relationships
    CruiseController --> CruiseService : delegatesTo
    ItineraryController --> ItineraryService : delegatesTo

    CruiseService --> CruiseRepository : accesses
    CruiseService --> CruiseStopRepository : accesses
    ItineraryService --> ItineraryRepository : accesses
    ItineraryService --> PortRepository : accesses

    CruiseRepository ..> Cruise : manages
    ItineraryRepository ..> Itinerary : manages
    CruiseStopRepository ..> CruiseStop : manages
    PortRepository ..> Port : manages

    Itinerary "1" o-- "0..*" Cruise : basisFor
    Cruise "1" *-- "2..*" CruiseStop : stopsAt
    CruiseStop "0..*" --> "1" Port : atPort
```
