# GET `/cruise-work/{cruiseId}/program-context`

Người được giao ASM đọc ngữ cảnh chương trình. Chỉ được chỉnh khi nhiệm vụ ở IN_PROGRESS theo kiểm tra mayConfigurePrograms.

```mermaid
sequenceDiagram
    actor Caller as Người dùng có quyền
    participant API as CruiseWorkHandoffController
    participant Service as CruiseWorkHandoffService
    participant DB as PostgreSQL
    Caller->>API: GET /cruise-work/{cruiseId}/program-context
    API->>Service: programContext
    Service->>DB: Đọc công việc và kiểm tra phạm vi
    DB-->>Service: Công việc và trạng thái
    Service-->>API: Kết quả trong phạm vi người gọi
    API-->>Caller: ApiResponse
```

[Xem luồng bàn giao đầy đủ](../SHIP-STAFFING-HANDOFF.md).
