# Phân Hệ Biến Động Hải Trình, Outbox Thông Báo & Kiểm Toán Hệ Thống (Impact Tasks, Outbox & Audit)

Quản lý các tác vụ xử lý sự cố hải trình (thời tiết biển xấu, tàu trễ giờ cập cảng), mô hình giao vận bất đồng bộ Outbox Pattern để gửi thông báo đẩy đến khách hàng, và nhật ký kiểm toán hệ thống.

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

    class CruiseImpactTask {
        +UUID taskId
        +String taskCode
        +ImpactSeverity severity
        +String description
        +TaskStatus status
        +Instant detectedAt
        +Instant resolvedAt
    }

    class ImpactNotificationOutbox {
        +UUID outboxId
        +String eventIdentity
        +String notificationType
        +String payloadJson
        +OutboxStatus status
        +Integer retryCount
        +Instant scheduledSendAt
        +Instant sentAt
    }

    class Notification {
        +UUID notificationId
        +String notificationType
        +String title
        +String content
        +Instant createdAt
        +Instant readAt
    }

    class CruiseAuditEvent {
        +UUID auditEventId
        +String eventType
        +String triggeredByRole
        +String payloadBefore
        +String payloadAfter
        +Instant recordedAt
    }

    class AuditLog {
        +UUID auditLogId
        +String entityName
        +UUID entityId
        +String action
        +String performedBy
        +Instant timestamp
    }

    class ItineraryChangeHistory {
        +UUID historyId
        +String reason
        +String previousScheduleJson
        +String updatedScheduleJson
        +Instant changedAt
    }

    class ImpactSeverity {
        <<enumeration>>
        MINOR_DELAY
        PORT_SKIPPED
        WEATHER_DIVERT
        EMERGENCY_STOP
    }

    class OutboxStatus {
        <<enumeration>>
        PENDING
        IN_PROGRESS
        SENT
        DEAD_LETTER
    }

    %% Relationships
    CruiseImpactTask "1" *-- "0..*" ImpactNotificationOutbox : generatesNotifications
    ImpactNotificationOutbox "1" ..> "1" Notification : deliversAs
    CruiseImpactTask "1" o-- "0..*" CruiseAuditEvent : logsEvents
    CruiseImpactTask "1" o-- "0..*" ItineraryChangeHistory : altersItinerary
    CruiseImpactTask --> ImpactSeverity : severity
    ImpactNotificationOutbox --> OutboxStatus : outboxState
```

## Giải Thích Quan Hệ & Ràng Buộc
- **Transactional Outbox Pattern**: Khi có sự cố dời giờ tàu, hệ thống lưu task sự cố và thông điệp vào bảng `ImpactNotificationOutbox` trong cùng một Database Transaction. Background worker sẽ đọc từ Outbox và gửi tin nhắn đẩy/email/SMS đến khách, đảm bảo tính bền vững (Guaranteed At-Least-Once Delivery) ngay cả khi mất kết nối mạng ngoài khơi.
- **AuditLog & CruiseAuditEvent**: Cơ chế ghi lại vết thay đổi (Audit Trail) của toàn bộ dữ liệu quan trọng, phục vụ công tác thanh tra hàng hải và kiểm soát gian lận.

