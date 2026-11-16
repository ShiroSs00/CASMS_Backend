# GET /trip-planning/catalogs/sources/ports

Yêu cầu quyền `TRIP_READ`.

Controller đọc Trip và Port qua repository theo bộ lọc hiện có. Controller lấy quốc gia, miền ACTIVE qua TripGeographyCatalog, lọc danh sách cảng trước khi trả 200. Quốc gia hoặc miền tắt không còn xuất hiện trong dữ liệu chọn cảng.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
