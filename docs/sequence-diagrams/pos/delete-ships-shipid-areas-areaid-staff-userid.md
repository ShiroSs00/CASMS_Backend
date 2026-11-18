# DELETE `/ships/{shipId}/areas/{areaId}/staff/{userId}`


### Ảnh hưởng của luồng bàn giao nhân sự

Khi dữ liệu bố trí theo khu vực thay đổi sau lần gửi HR, trigger PostgreSQL vô hiệu hóa kết quả duyệt đang có của yêu cầu mới và đưa yêu cầu về chờ HR bố trí lại.

[Luồng kiểm tra và bàn giao](../SHIP-STAFFING-HANDOFF.md).

```mermaid
sequenceDiagram
    actor HR as Quản lý nhân sự
    participant API as PosAssignmentController
    participant Service as PosAccessService
    participant DB as PostgreSQL
    HR->>API: DELETE theo chuyến, khu vực và nhân viên
    API->>API: Kiểm tra HR_MANAGER và CRUISE_STAFF_ASSIGN
    API->>Service: Kiểm tra khu vực và phạm vi chuyến
    Service->>DB: Khóa chuyến trước khi cập nhật bố trí
    opt Có phê duyệt nhân sự cần làm lại
        DB->>DB: Đưa yêu cầu về SENT_TO_HR và tăng revision
    end
    Service->>DB: Thu hồi phiên POS liên quan
    API-->>HR: 204
```
