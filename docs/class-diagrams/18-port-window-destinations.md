# Sơ Đồ Lớp Thiết Kế Chi Tiết: Khung Giờ Cập Cảng & Điểm Đến Du Lịch (Port Windows & Destination Planning Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho việc đăng ký và quản lý khung giờ cập bến (Berth Call Windows) tại các cảng biển và điều phối các chuyến tham quan khám phá điểm đến địa phương (Destination Visits).

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class PortWindowController {
        -PortWindowService windowService
        +getAvailableWindows(portId, dateRange) ApiResponse~List~WindowResponse~~
        +reserveWindow(portId, request) ApiResponse~WindowResponse~
        +recordException(windowId, exceptionDto) ApiResponse
    }

    class DestinationController {
        -DestinationService destinationService
        +listDestinations(countryCode) ApiResponse~List~DestinationResponse~~
        +getDestinationDetail(id) ApiResponse~DestinationResponse~
        +addDestinationVisit(destinationId, visitDto) ApiResponse
    }

    class ItineraryProgramController {
        -ItineraryProgramService programService
        +getProgramsByItinerary(itineraryId) ApiResponse
        +assignDestinationToStop(stopId, destinationId) ApiResponse
    }

    %% 2. Service Layer
    class PortWindowService {
        -PortCallWindowRepository windowRepo
        -PortCallWindowExceptionRepository exceptionRepo
        -PortRepository portRepo
        +checkBerthAvailability(portId, arrival, departure) boolean
        +bookPortWindow(cruiseId, portId, arrival, departure) PortCallWindow
        +handleBerthConflict(windowId, newArrivalTime) PortCallWindowException
    }

    class DestinationService {
        -DestinationRepository destinationRepo
        -DestinationVisitRepository visitRepo
        +findDestinationsByPort(portId) List~Destination~
        +scheduleDestinationVisit(cruiseStopId, destinationId) DestinationVisit
    }

    class ItineraryProgramService {
        -ItineraryRepository itineraryRepo
        -CruiseStopRepository stopRepo
        +mapProgramsToCruise(cruiseId) void
        +validatePortFeasibility(itineraryId) boolean
    }

    %% 3. Repository Layer
    class PortCallWindowRepository {
        +findByPortIdAndStartTimeBetween(portId, start, end) List~PortCallWindow~
        +findByCruiseId(cruiseId) List~PortCallWindow~
        +save(window) PortCallWindow
    }

    class PortCallWindowExceptionRepository {
        +findByPortCallWindowId(windowId) List~PortCallWindowException~
        +save(exception) PortCallWindowException
    }

    class DestinationRepository {
        +findByCountryCode(code) List~Destination~
        +findByDestinationCode(code) Optional~Destination~
        +save(destination) Destination
    }

    class DestinationVisitRepository {
        +findByDestinationId(destId) List~DestinationVisit~
        +save(visit) DestinationVisit
    }

    %% 4. Entity Layer
    class PortCallWindow {
        +UUID windowId
        +UUID portId
        +UUID cruiseId
        +Instant bookedStartTime
        +Instant bookedEndTime
        +String allocatedBerthNumber
        +WindowStatus status
    }

    class PortCallWindowException {
        +UUID exceptionId
        +String exceptionReason
        +Instant originalStartTime
        +Instant revisedStartTime
        +String authorityNoticeRef
    }

    class Destination {
        +UUID destinationId
        +String destinationCode
        +String destinationName
        +String countryCode
        +String description
        +Boolean isActive
    }

    class DestinationVisit {
        +UUID visitId
        +UUID cruiseStopId
        +String excursionSummary
        +BigDecimal estimatedHours
        +Integer minPaxRequired
    }

    class Port {
        +UUID portId
        +String portCode
        +String portName
        +Double latitude
        +Double longitude
    }

    %% Inter-Layer and Domain Relationships
    PortWindowController --> PortWindowService : delegatesTo
    DestinationController --> DestinationService : delegatesTo
    ItineraryProgramController --> ItineraryProgramService : delegatesTo

    PortWindowService --> PortCallWindowRepository : accesses
    PortWindowService --> PortCallWindowExceptionRepository : accesses
    DestinationService --> DestinationRepository : accesses
    DestinationService --> DestinationVisitRepository : accesses

    PortCallWindowRepository ..> PortCallWindow : manages
    PortCallWindowExceptionRepository ..> PortCallWindowException : manages
    DestinationRepository ..> Destination : manages
    DestinationVisitRepository ..> DestinationVisit : manages

    PortCallWindow "1" *-- "0..*" PortCallWindowException : exceptions
    Destination "1" o-- "0..*" DestinationVisit : highlights
    Port "1" o-- "0..*" PortCallWindow : hosts
```
