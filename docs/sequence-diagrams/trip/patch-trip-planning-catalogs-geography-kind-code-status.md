# PATCH /trip-planning/catalogs/geography/{kind}/{code}/status

Yêu cầu quyền `ROLE_ADMIN`.

Controller kiểm tra kind COUNTRY hoặc REGION và body status ACTIVE hoặc INACTIVE. TripGeographyCatalog cập nhật PostgreSQL trong transaction. Đổi trạng thái Việt Nam hoặc miền lấy khóa mục Việt Nam trước. Tắt Việt Nam tắt cả ba miền trong cùng transaction. Bật miền khi Việt Nam đang tắt trả 409, mã không tồn tại trả 404. Thành công trả 200 và mục vừa sửa.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
