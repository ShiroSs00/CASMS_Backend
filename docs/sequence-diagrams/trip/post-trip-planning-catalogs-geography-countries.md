# POST /trip-planning/catalogs/geography/countries

```mermaid
sequenceDiagram
    actor Admin
    participant Security as JWT + method authorization
    participant Controller as TripCatalogController
    participant Catalog as TripGeographyCatalog
    participant JDBC as JdbcTemplate
    participant DB as PostgreSQL
    Admin->>Security: POST code và label
    alt Thiếu xác thực hoặc quyền ROLE_ADMIN
        Security-->>Admin: 401 hoặc 403
    else Có quyền
        Security->>Controller: Kiểm tra body
        alt Body thiếu dữ liệu bắt buộc hoặc sai cấu trúc
            Controller-->>Admin: 400
        else Body hợp lệ
            Controller->>Catalog: createCountry trong transaction
            Catalog->>Catalog: Cắt khoảng trắng, chuẩn hóa mã và xác thực ISO
            alt Mã hoặc tên không hợp lệ
                Catalog-->>Controller: Rollback, lỗi 400
                Controller-->>Admin: 400 INVALID_COUNTRY_CODE hoặc INVALID_COUNTRY_LABEL
            else Mã và tên hợp lệ
                Catalog->>JDBC: Bind mã và tên cho INSERT ACTIVE
                JDBC->>DB: INSERT với thứ tự kế tiếp và ON CONFLICT DO NOTHING
                alt Khóa chính kind và code đã tồn tại
                    DB-->>Catalog: 0 dòng được thêm
                    Catalog-->>Controller: Rollback, lỗi 409
                    Controller-->>Admin: 409 COUNTRY_ALREADY_EXISTS
                else Thêm thành công
                    DB-->>Catalog: 1 dòng được thêm
                    Catalog-->>Controller: Commit, mục COUNTRY ACTIVE
                    Controller-->>Admin: 201 ApiResponse với mục vừa tạo
                end
            end
        end
    end
```

Quốc gia đã tồn tại giữ nguyên tên và trạng thái khi yêu cầu tạo trùng. Quốc gia mới xuất hiện trong danh mục hoạt động sau khi commit. Xem [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md) để đối chiếu các API đọc và thay đổi trạng thái.
