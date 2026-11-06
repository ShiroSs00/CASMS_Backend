# Hồ sơ cơ bản của Ship

`manufacturerName`, `origin`, `productionYear` và `engineType` tùy chọn. Giới hạn chuỗi lần lượt là 150, 100 và 100 ký tự. Năm là số nguyên từ 1 đến 9999. V93 thêm bốn cột nullable trên Ship và CHECK cho năm. Các trường không tham gia đồng bộ manifest.

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant C as ShipController / ShipModelController
    participant S as ShipService / ShipModelAssetService
    participant R as ShipRepository / ShipModelRepository
    participant DB as PostgreSQL
    Client->>Auth: POST hoặc PATCH kèm hồ sơ
    Auth->>C: SHIP_CREATE hoặc SHIP_UPDATE, thêm SHIP_MODEL_CREATE khi tạo từ model
    C->>C: Validate DTO
    alt Payload sai
        C-->>Client: 400 ApiResponse với lỗi field
    else Payload hợp lệ
        C->>S: Gọi service trong transaction
        alt Tạo thủ công
            S->>R: Lưu Ship và bốn trường
            R->>DB: INSERT Ship
            S-->>C: ShipResponse
        else Tạo từ model
            S->>R: Lưu Ship từ manifest và bốn trường từ request
            R->>DB: INSERT Ship và DRAFT ShipModel trong cùng transaction
            S-->>C: ShipResponse hoặc ID đăng ký
        else PATCH Ship
            S->>R: Đọc Ship theo UUID và kiểm tra các giới hạn cấu hình nếu thay đổi
            alt Ship không tồn tại
                S-->>C: 404
            else Ship tồn tại và cập nhật hợp lệ
                S->>R: Áp dụng trường khác null
                R->>DB: UPDATE cột thay đổi
                S-->>C: ShipResponse
            end
        end
        C-->>Client: 201 tạo hoặc 200 cập nhật khi thành công
    end
```

Danh sách và chi tiết yêu cầu SHIP_READ, đọc qua ShipRepository trong transaction chỉ đọc và ánh xạ sang ShipResponse. Thay đổi trạng thái yêu cầu SHIP_STATUS_CHANGE, chặn trạng thái phi vận hành khi có chuyến đang hoạt động, và ánh xạ cùng ShipResponse. Hibernate chỉ ghi cột thay đổi khi UPDATE Ship để các đường cập nhật trạng thái hay model không ghi lại bốn cột hồ sơ từ bản đọc cũ. Các mã lỗi, quyền và điều kiện nghiệp vụ hiện có tiếp tục áp dụng.
