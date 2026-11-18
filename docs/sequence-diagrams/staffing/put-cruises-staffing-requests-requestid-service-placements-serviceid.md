# PUT `/cruises/staffing-requests/{requestId}/service-placements/{serviceId}`

Tạo liên kết còn thiếu khu vực–dịch vụ và POS–khu vực–dịch vụ cho dịch vụ trên tàu đang được lên chương trình. Yêu cầu phải đang chờ quản lý tàu và có revision hiện hành. Liên kết được quản trị viên tắt trả lỗi 409.

```mermaid
sequenceDiagram
    actor Ship as Quản lý tàu
    participant API as StaffingWorkflowController
    participant WF as StaffingWorkflowService
    participant POS as PosAccessService
    participant DB as PostgreSQL
    Ship->>API: PUT với expectedRevision
    API->>WF: Xác thực quyền và phạm vi thao tác
    WF->>DB: Khóa chuyến, kiểm tra yêu cầu, POS và khu vực
    alt Sai phạm vi hoặc yêu cầu đã đổi
        WF-->>API: Phản hồi lỗi
    else Đủ điều kiện
        WF->>POS: Dùng lại hoặc tạo liên kết trong phạm vi đã kiểm tra
        POS->>DB: Ghi liên kết mới, giữ cấu hình đã bị tắt
        WF->>DB: Cập nhật bố trí và revision
        WF-->>API: ReviewDetails với revision sau khi lưu
    end
    API-->>Ship: Phản hồi API
```

[Xem luồng bàn giao đầy đủ](../SHIP-STAFFING-HANDOFF.md).
