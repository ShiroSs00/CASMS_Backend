# POST /trips/{tripId}/schedule-versions

Yêu cầu quyền `TRIP_PLAN_WRITE`.

TripService kiểm tra phạm vi Trip và các cảng của lịch nguồn nếu có trước khi tạo phiên bản. Lựa chọn đã tắt trả 422 và không tạo phiên bản.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
