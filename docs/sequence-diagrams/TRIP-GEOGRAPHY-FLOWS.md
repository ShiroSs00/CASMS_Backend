# Danh mục địa lý lập chuyến

## Danh mục chọn và cấu hình

Flyway V122 khởi tạo năm quốc gia và ba miền theo thứ tự đã công bố. Endpoint lập kế hoạch giữ cấu trúc `countries` và `domesticRegions`. Admin đọc cả trạng thái ACTIVE và INACTIVE từ endpoint cấu hình. Bật lại Việt Nam cho phép bật từng miền riêng.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT + method authorization
    participant Controller as TripCatalogController
    participant Catalog as TripGeographyCatalog
    participant DB as PostgreSQL
    Client->>Security: GET danh mục hoặc PATCH trạng thái
    alt Thiếu xác thực hoặc quyền
        Security-->>Client: 401 hoặc 403
    else Được phép
        Security->>Controller: Kiểm tra path và body
        alt GET danh mục
            Controller->>Catalog: list(activeOnly)
            Catalog->>DB: SELECT theo trạng thái và thứ tự
            DB-->>Catalog: Quốc gia và miền
            Controller-->>Client: 200 danh mục
        else PATCH trạng thái
            Controller->>Catalog: changeStatus trong transaction
            opt Mục Việt Nam hoặc miền
                Catalog->>DB: Khóa mục VN bằng SELECT FOR UPDATE
            end
            alt Yêu cầu bật miền khi VN tắt
                Catalog-->>Client: 409 PARENT_COUNTRY_INACTIVE
            else Trạng thái hợp lệ
                Catalog->>DB: UPDATE theo kind và code
                alt Không tìm thấy mục
                    Catalog-->>Client: 404 GEOGRAPHY_NOT_FOUND
                else Mục tồn tại
                    opt Tắt Việt Nam
                        Catalog->>DB: UPDATE cả ba miền thành INACTIVE
                    end
                    DB-->>Catalog: Trạng thái mới
                    Catalog-->>Controller: Commit
                    Controller-->>Client: 200 mục vừa cập nhật
                end
            end
        end
    end
```

## Tạo chuyến và lịch nháp

Việc đánh giá địa lý áp dụng cho thao tác lập kế hoạch mới và lịch nháp. Lịch đã phát hành tiếp tục đọc từ dữ liệu hiện có. Danh mục cảng dùng các bộ lọc cũ và thêm điều kiện quốc gia, miền đang hoạt động.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT + method authorization
    participant Controller as Trip or Schedule Controller
    participant Service as TripService or TripScheduleVersionService
    participant Repository as Trip, version and port repositories
    participant Catalog as TripGeographyCatalog
    participant DB as PostgreSQL
    Client->>Security: Tạo, sửa, nhân bản hoặc thẩm định
    Security->>Controller: Request đã được cấp quyền
    Controller->>Service: Thực hiện nghiệp vụ trong transaction
    opt Có lịch hoặc chuyến nguồn
        Service->>Repository: Đọc Trip, lịch và các cảng
        Repository->>DB: SELECT dữ liệu phạm vi và cảng
    end
    Service->>Catalog: Đọc tập quốc gia và miền ACTIVE
    Catalog->>DB: SELECT danh mục hoạt động
    alt Chọn quốc gia hoặc miền không khả dụng
        alt API validate
            Service-->>Controller: ValidationReport có lỗi RECREATE_DRAFT
            Controller-->>Client: 200 validForSubmit=false
        else API mutation
            Service-->>Controller: Rollback yêu cầu
            Controller-->>Client: 422 TRIP_GEOGRAPHY_UNAVAILABLE
        end
    else Phạm vi hợp lệ
        opt API mutation và các điều kiện hiện có hợp lệ
            Service->>Repository: Lưu chuyến hoặc lịch
            Repository->>DB: Ghi dữ liệu
        end
        Service-->>Controller: Phản hồi nghiệp vụ
        Controller-->>Client: Kết quả theo hợp đồng hiện có
    end
```
