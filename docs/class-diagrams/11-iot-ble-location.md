# Phân Hệ Định Vị Trong Nhà Thời Gian Thực IoT BLE & Theo Dõi Hành Khách (Indoor Location & BLE Tracking)

Quản lý hạ tầng thiết bị định vị (Beacon, Scanner BLE), nhật ký tín hiệu di chuyển thời gian thực, và liên kết mã định danh BLE với hành khách trên du thuyền.

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

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

    class LocationDeviceType {
        <<enumeration>>
        FIXED_ANCHOR_BEACON
        GANGWAY_GATEWAY_SCANNER
        MOBILE_CREW_HANDHELD
    }

    class LocationDeviceStatus {
        <<enumeration>>
        ONLINE
        OFFLINE
        CALIBRATING
        MAINTENANCE
    }

    %% Relationships
    LocationDevice "1" ..> "0..*" LocationRecord : picksUpSignals
    PassengerBleIdentity "1" ..> "0..*" LocationRecord : trackedBy
    PassengerIdentifier "1" o-- "0..1" PassengerBleIdentity : bindsBleTag
    LocationDevice --> LocationDeviceType : type
    LocationDevice --> LocationDeviceStatus : status
```

## Giải Thích Quan Hệ & Ràng Buộc
- **LocationDevice**: Các trạm thu phát sóng Bluetooth Low Energy (BLE) cố định tại các góc boong tàu, hành lang, nhà hàng, hồ bơi.
- **LocationRecord**: Dòng dữ liệu time-series lưu trữ tọa độ tính toán (X, Y, Z) và cường độ sóng (`signalRssi`) của vòng đeo tay thông minh.
- **PassengerBleIdentity & PassengerIdentifier**: Liên kết vòng tay RFID/BLE với hồ sơ hành khách (`Passenger`), cho phép xác định vị trí hành khách trong các tình huống khẩn cấp (diễn tập cứu hộ, tìm trẻ em đi lạc, cảnh báo khu vực cấm).
