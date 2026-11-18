# GET `/cruises/staffing-requests/inbox`

Lọc hàng chờ theo vai trò, trạng thái và phạm vi chuyến. Quản lý tàu thấy cả yêu cầu kiểm tra ban đầu và yêu cầu rà soát HR. Vận hành thấy yêu cầu sau khi HR duyệt.

```mermaid
sequenceDiagram
    actor Caller as Người dùng có quyền
    participant API as StaffingWorkflowController
    participant WF as StaffingWorkflowService
    participant DB as PostgreSQL
    Caller->>API: GET /cruises/staffing-requests/inbox
    API->>WF: inbox với principal hiện hành
    WF->>DB: Đọc và kiểm tra phạm vi dữ liệu
    alt Thiếu quyền hoặc điều kiện xử lý
        WF-->>API: BusinessException
        API-->>Caller: Phản hồi lỗi
    else Đủ điều kiện
        DB-->>WF: Dữ liệu yêu cầu
        WF-->>API: Kết quả
        API-->>Caller: ApiResponse
    end
```

[Xem đầy đủ các bước bàn giao](../SHIP-STAFFING-HANDOFF.md).

### Bàn giao chương trình trước khi kiểm tra tàu

Quản lý tàu không thấy yêu cầu ở PENDING_ASM. Quản lý dịch vụ và hoạt động nhận việc qua GET /cruise-work/inbox.

[Xem luồng đầy đủ](../SHIP-STAFFING-HANDOFF.md).
