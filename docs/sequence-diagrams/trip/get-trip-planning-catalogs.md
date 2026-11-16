# GET /trip-planning/catalogs

Yêu cầu quyền `TRIP_READ`.

Controller đọc điểm đến từ DestinationRepository và lấy quốc gia, miền ACTIVE qua TripGeographyCatalog. PostgreSQL sắp xếp theo thứ tự cấu hình. Phản hồi giữ các trường countries, domesticRegions và các danh mục khác.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
