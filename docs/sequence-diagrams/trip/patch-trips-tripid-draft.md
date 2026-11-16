# PATCH /trips/{tripId}/draft

Yêu cầu quyền `TRIP_UPDATE`.

TripService kiểm tra phạm vi sau cập nhật nếu request ghi scope, countryCodes hoặc domesticRegions. Trip chưa khóa có địa lý đã bị tắt bị từ chối trước khi ghi, kể cả khi đổi sang địa lý đang bật. API trả 422 và yêu cầu tạo lại.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
