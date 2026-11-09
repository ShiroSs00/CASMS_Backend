# Hợp đồng xác minh

- GET /api/booking/lookup thiếu query bắt buộc trả 400 MISSING_REQUIRED_PARAMETER. Giữ xác thực hiện có cho truy vấn đủ tham số.
- GET /api/ships/{id}/current-cruise trả success với chuyến đang chạy hoặc success không có data nếu tàu rảnh. Tàu không tồn tại vẫn trả 404.
- GET/PUT /api/activities/registrations/{id} tiếp tục dùng notes hiện hữu.
- Service request list, service catalog, eligible sources và public benefits giữ payload hiện tại. Không thêm quyền đọc hay tạo thay khách.
