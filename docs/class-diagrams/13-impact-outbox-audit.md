# Sơ Đồ Lớp Thiết Kế Chi Tiết: Biến Động Hải Trình, Outbox & Kiểm Toán (Impact, Outbox & Audit Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho xử lý sự cố trễ tàu, mô hình giao vận bảo đảm Transactional Outbox Pattern và nhật ký kiểm toán hệ thống.

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class CruiseDelayController {
        -CruiseDelayService delayService
        -CruiseImpactAssessmentService impactService
        +previewDelayImpact(cruiseId, newEta) ApiResponse
        +recordDepartureDelay(cruiseId, request) ApiResponse
        +listDelayEvents(cruiseId) ApiResponse
    }

    class NotificationController {
        -NotificationService notificationService
        +getMyNotifications(principal) ApiResponse~Page~NotificationResponse~~
        +markAsRead(notificationId) ApiResponse
        +getUnreadCount(principal) ApiResponse~Long~
    }

    class CruiseController {
        -CruiseAuditService auditService
        +getAuditEvents(cruiseId) ApiResponse~List~CruiseAuditEventResponse~~
    }

    %% 2. Service Layer
    class CruiseDelayService {
        -CruiseRepository cruiseRepo
        -CruiseImpactTaskRepository taskRepo
        -ImpactNotificationOutboxRepository outboxRepo
        +applyDelay(cruiseId, delayedMinutes, reason) CruiseImpactTask
        +generateOutboxNotifications(taskId, affectedBookings) void
    }

    class CruiseImpactAssessmentService {
        -CruiseStopRepository stopRepo
        -ItineraryChangeHistoryRepository historyRepo
        +assessImpact(cruiseId, delayDuration) CruiseChangeImpactResponse
        +recalculatePortWindows(cruiseId) void
    }

    class NotificationService {
        -NotificationRepository notificationRepo
        -ImpactNotificationOutboxRepository outboxRepo
        +dispatchPendingOutboxMessages() int
        +deliverNotification(userId, title, content) Notification
        +markNotificationRead(id) void
    }

    class AuditService {
        -AuditLogRepository auditRepo
        +logAction(entityName, entityId, action, username) void
        +queryAuditTrail(entityName, entityId) List~AuditLog~
    }

    %% 3. Repository Layer
    class CruiseImpactTaskRepository {
        +findById(id) Optional~CruiseImpactTask~
        +findByCruiseIdAndStatus(cruiseId, status) List~CruiseImpactTask~
        +save(task) CruiseImpactTask
    }

    class ImpactNotificationOutboxRepository {
        +findByStatusOrderByScheduledSendAtAsc(status) List~ImpactNotificationOutbox~
        +findByTaskId(taskId) List~ImpactNotificationOutbox~
        +save(outbox) ImpactNotificationOutbox
        +saveAll(outboxList) List~ImpactNotificationOutbox~
    }

    class NotificationRepository {
        +findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable) Page~Notification~
        +countByRecipientUserIdAndReadAtIsNull(userId) Long
        +save(notification) Notification
    }

    class AuditLogRepository {
        +findByEntityNameAndEntityId(name, id) List~AuditLog~
        +save(log) AuditLog
    }

    %% 4. Entity Layer
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

    %% Inter-Layer and Domain Relationships
    CruiseDelayController --> CruiseDelayService : delegatesTo
    CruiseDelayController --> CruiseImpactAssessmentService : delegatesTo
    NotificationController --> NotificationService : delegatesTo
    CruiseController --> AuditService : delegatesTo

    CruiseDelayService --> CruiseImpactTaskRepository : accesses
    CruiseDelayService --> ImpactNotificationOutboxRepository : accesses
    NotificationService --> ImpactNotificationOutboxRepository : accesses
    NotificationService --> NotificationRepository : accesses
    AuditService --> AuditLogRepository : accesses

    CruiseImpactTaskRepository ..> CruiseImpactTask : manages
    ImpactNotificationOutboxRepository ..> ImpactNotificationOutbox : manages
    NotificationRepository ..> Notification : manages
    AuditLogRepository ..> AuditLog : manages

    CruiseImpactTask "1" *-- "0..*" ImpactNotificationOutbox : generatesNotifications
    ImpactNotificationOutbox "1" ..> "1" Notification : deliversAs
    CruiseImpactTask "1" o-- "0..*" CruiseAuditEvent : logsEvents
    CruiseImpactTask "1" o-- "0..*" ItineraryChangeHistory : altersItinerary
```
