# Sơ Đồ Lớp Thiết Kế Chi Tiết: Phân Hệ Cầu Tàu & An Toàn SOLAS (Check-in & Safety Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho thủ tục check-in tại nhà ga cảng, kiểm soát qua cầu tàu Gangway bằng NFC/RFID và điểm danh diễn tập an toàn bắt buộc SOLAS (Muster Drill).

## Mã Mermaid UML (Full-Stack 4 Tầng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class CheckinController {
        -CheckinService checkinService
        +startCheckin(bookingId) ApiResponse~CheckinResponse~
        +completeCheckin(checkinId) ApiResponse~CheckinResponse~
        +getStatus(bookingId) ApiResponse~CheckinResponse~
    }

    class GuidanceSessionController {
        -GuidanceSessionService guidanceService
        +createSession(request) ApiResponse
        +markAttendance(sessionId, participantId) ApiResponse
        +getSessionRoster(sessionId) ApiResponse
    }

    %% 2. Service Layer
    class CheckinService {
        -CruiseCheckinRepository checkinRepo
        -GangwayEventRepository gangwayRepo
        -BookingRepository bookingRepo
        +initiateCheckin(bookingId) CruiseCheckin
        +processGangwayScan(tagId, direction, gate) GangwayEvent
        +finalizeCheckin(checkinId) CruiseCheckin
        +getPersonsOnBoardCount(cruiseId) int
    }

    class GuidanceSessionService {
        -GuidanceSessionRepository sessionRepo
        -MusterStationRepository stationRepo
        +scheduleMusterDrill(cruiseId, stationId) GuidanceSession
        +recordAttendance(sessionId, passengerId, signature) boolean
        +verifySolasCompliance(cruiseId) boolean
    }

    %% 3. Repository Layer
    class CruiseCheckinRepository {
        <<interface>>
        +findById(id) Optional~CruiseCheckin~
        +findByBookingId(bookingId) Optional~CruiseCheckin~
        +save(checkin) CruiseCheckin
    }

    class GangwayEventRepository {
        <<interface>>
        +findByCruiseIdOrderByEventTimeDesc(cruiseId) List~GangwayEvent~
        +countByCruiseIdAndDirection(cruiseId, direction) Long
        +save(event) GangwayEvent
    }

    class GuidanceSessionRepository {
        <<interface>>
        +findByCruiseId(cruiseId) List~GuidanceSession~
        +save(session) GuidanceSession
    }

    class MusterStationRepository {
        <<interface>>
        +findByShipId(shipId) List~MusterStation~
        +save(station) MusterStation
    }

    %% 4. Entity Layer
    class CruiseCheckin {
        +UUID checkinId
        +Instant checkinTime
        +String terminalGate
        +CheckinStatus status
    }

    class CheckinStep {
        +UUID stepId
        +String stepName
        +Integer sequenceOrder
        +Boolean isCompleted
    }

    class GangwayEvent {
        +UUID eventId
        +Instant eventTime
        +GangwayDirection direction
        +String scannerDeviceId
        +Boolean accessGranted
    }

    class MusterStation {
        +UUID stationId
        +String stationCode
        +String locationDescription
        +Integer maxCapacityPlaque
    }

    class GuidanceSession {
        +UUID sessionId
        +String sessionTitle
        +GuidanceType guidanceType
        +Instant scheduledTime
        +Boolean isSolasMandatory
    }

    %% Inter-Layer and Domain Relationships
    CheckinController --> CheckinService : delegatesTo
    GuidanceSessionController --> GuidanceSessionService : delegatesTo

    CheckinService --> CruiseCheckinRepository : accesses
    CheckinService --> GangwayEventRepository : accesses
    GuidanceSessionService --> GuidanceSessionRepository : accesses
    GuidanceSessionService --> MusterStationRepository : accesses

    CruiseCheckinRepository ..> CruiseCheckin : manages
    GangwayEventRepository ..> GangwayEvent : manages
    GuidanceSessionRepository ..> GuidanceSession : manages
    MusterStationRepository ..> MusterStation : manages

    CruiseCheckin "1" *-- "1..*" CheckinStep : pipelineSteps
    CruiseCheckin "1" o-- "0..*" GangwayEvent : entryEvents
    MusterStation "1" o-- "0..*" GuidanceSession : hostedAt
```
