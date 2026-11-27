# GET /trip-planning/catalogs

Yêu cầu quyền `TRIP_READ`.

Controller đọc điểm đến từ DestinationRepository và lấy quốc gia, miền ACTIVE qua TripGeographyCatalog. PostgreSQL sắp xếp theo loại, thứ tự hiển thị rồi mã. Phản hồi giữ các trường countries, domesticRegions và các danh mục khác. Quốc gia mới xuất hiện trong countries ngay sau khi thao tác tạo commit.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
