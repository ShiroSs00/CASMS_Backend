# Sơ Đồ Lớp Thiết Kế Chi Tiết: Định Vị Trong Nhà Thời Gian Thực IoT BLE (Indoor Location & BLE Tracking Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho quản lý trạm phát sóng Anchor Beacon, dòng dữ liệu tọa độ thời gian thực (Real-time telemetry) và giám sát vị trí hành khách mang thẻ/vòng tay BLE.

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class BleAnchorController {
        -BleAnchorService anchorService
        +registerAnchor(request) ApiResponse~BleAnchorResponse~
        +updateAnchor(anchorId, request) ApiResponse~BleAnchorResponse~
        +listAnchorsByShip(shipId) ApiResponse~List~BleAnchorResponse~~
        +calibrateAnchor(anchorId, rssiOffset) ApiResponse
    }

    class PassengerTrackingController {
        -PassengerBleIdentityService trackingService
        +getPassengerLastKnownLocation(passengerId) ApiResponse~PassengerTrackingResponse~
        +getDeckHeatmap(deckId) ApiResponse
        +triggerMissingChildAlert(passengerId) ApiResponse
    }

    class BleRuntimeController {
        -BleRuntimeService runtimeService
        +ingestTelemetryBatch(telemetryList) ApiResponse
        +getDeviceHealthStatus(shipId) ApiResponse
    }

    %% 2. Service Layer
    class BleAnchorService {
        -LocationDeviceRepository deviceRepo
        -ShipRepository shipRepo
        +registerDevice(dto) LocationDevice
        +updateDeviceStatus(deviceId, status) LocationDevice
        +findOnlineDevices(shipId) List~LocationDevice~
    }

    class BleRuntimeService {
        -LocationRecordRepository recordRepo
        -LocationDeviceRepository deviceRepo
        +processBeaconSignals(signals) void
        +triangulateCoordinates(macAddress, signalList) LocationRecord
        +cleanOldRecords(beforeInstant) int
    }

    class PassengerBleIdentityService {
        -PassengerBleIdentityRepository identityRepo
        -LocationRecordRepository recordRepo
        +pairWristband(passengerId, macAddress) PassengerBleIdentity
        +unpairWristband(passengerId) void
        +locatePassenger(passengerId) Optional~LocationRecord~
    }

    %% 3. Repository Layer
    class LocationDeviceRepository {
        +findById(id) Optional~LocationDevice~
        +findByDeviceCode(code) Optional~LocationDevice~
        +findByShipIdAndStatus(shipId, status) List~LocationDevice~
        +findByBleIdentifier(bleId) Optional~LocationDevice~
        +save(device) LocationDevice
    }

    class LocationRecordRepository {
        +findLatestByTagBleIdentifier(tagId) Optional~LocationRecord~
        +findByDetectedDeckIdAndRecordedAtAfter(deckId, since) List~LocationRecord~
        +save(record) LocationRecord
        +saveAll(records) List~LocationRecord~
    }

    class PassengerBleIdentityRepository {
        +findByPassengerIdAndIsActiveTrue(passengerId) Optional~PassengerBleIdentity~
        +findByBleMacAddress(mac) Optional~PassengerBleIdentity~
        +save(identity) PassengerBleIdentity
    }

    %% 4. Entity Layer
    class LocationDevice {
        +UUID locationDeviceId
        +String deviceCode
        +String deviceName
        +String bleIdentifier
        +String modelNodeKey
        +LocationDeviceType deviceType
        +LocationDeviceStatus status
        +Instant installedAt
    }

    class LocationRecord {
        +UUID recordId
        +String tagBleIdentifier
        +Double coordinateX
        +Double coordinateY
        +Double coordinateZ
        +Integer signalRssi
        +Instant recordedAt
        +UUID detectedDeckId
        +UUID detectedAreaId
    }

    class PassengerBleIdentity {
        +UUID identityId
        +String bleMacAddress
        +String majorMinorHex
        +Boolean isActive
        +Instant pairedAt
        +Instant unpairedAt
    }

    class PassengerIdentifier {
        +UUID identifierId
        +String identifierType
        +String identifierValue
        +Boolean isPrimary
        +Instant issuedAt
    }

    %% Inter-Layer and Domain Relationships
    BleAnchorController --> BleAnchorService : delegatesTo
    PassengerTrackingController --> PassengerBleIdentityService : delegatesTo
    BleRuntimeController --> BleRuntimeService : delegatesTo

    BleAnchorService --> LocationDeviceRepository : accesses
    BleRuntimeService --> LocationRecordRepository : accesses
    BleRuntimeService --> LocationDeviceRepository : accesses
    PassengerBleIdentityService --> PassengerBleIdentityRepository : accesses
    PassengerBleIdentityService --> LocationRecordRepository : accesses

    LocationDeviceRepository ..> LocationDevice : manages
    LocationRecordRepository ..> LocationRecord : manages
    PassengerBleIdentityRepository ..> PassengerBleIdentity : manages

    LocationDevice "1" ..> "0..*" LocationRecord : picksUpSignals
    PassengerBleIdentity "1" ..> "0..*" LocationRecord : trackedBy
    PassengerIdentifier "1" o-- "0..1" PassengerBleIdentity : bindsBleTag
```
