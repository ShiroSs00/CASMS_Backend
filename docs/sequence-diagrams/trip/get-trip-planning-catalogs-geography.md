# GET /trip-planning/catalogs/geography

Yêu cầu quyền `ROLE_ADMIN`.

Controller gọi TripGeographyCatalog để đọc toàn bộ mục theo thứ tự lưu trong PostgreSQL. Phản hồi 200 gồm kind, code, label, description và status.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
