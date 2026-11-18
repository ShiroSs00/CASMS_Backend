# GET `/cruise-work/inbox`

Hàng chờ ASM nhận các việc do luồng staffing khởi tạo. Công việc SHIP độc lập bị ẩn khi chuyến đang dùng bước kiểm tra tàu trong staffing.

```mermaid
sequenceDiagram
    actor Caller as Người dùng có quyền
    participant API as CruiseWorkHandoffController
    participant Service as CruiseWorkHandoffService
    participant DB as PostgreSQL
    Caller->>API: GET /cruise-work/inbox
    API->>Service: inbox
    Service->>DB: Đọc công việc và kiểm tra phạm vi
    DB-->>Service: Công việc và trạng thái
    Service-->>API: Kết quả trong phạm vi người gọi
    API-->>Caller: ApiResponse
```

[Xem luồng bàn giao đầy đủ](../SHIP-STAFFING-HANDOFF.md).
