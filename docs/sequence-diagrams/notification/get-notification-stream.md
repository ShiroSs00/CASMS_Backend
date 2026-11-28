# GET /notification/stream

```mermaid
sequenceDiagram
    actor Manager
    participant Security
    participant Controller as NotificationController
    participant Service as NotificationService
    participant Hub as NotificationSseHub
    Manager->>Security: Open notification stream
    Security->>Controller: Authenticated principal
    Controller->>Service: Subscribe with recipient identity
    Service->>Hub: Register emitter by user ID
    Hub-->>Manager: Connection event
    opt Expiry notification transaction has committed
        Service->>Hub: Publish notification and unread count for recipient
        Hub-->>Manager: notification event with SHIP_CLASSIFICATION_EXPIRED
    end
```

Uses the existing recipient-scoped SSE flow. The subscription does not open a database transaction. See [automatic maintenance](../SHIP-TYPE-FLOWS.md#automatic-maintenance-for-classification-expiry) for persistence and deduplication.
