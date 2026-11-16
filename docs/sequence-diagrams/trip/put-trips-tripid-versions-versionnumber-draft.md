# PUT /trips/{tripId}/versions/{versionNumber}/draft

Yêu cầu quyền `TRIP_PLAN_WRITE`.

Endpoint tương thích dùng cùng luồng lưu nội dung với endpoint theo versionId. Kiểm tra địa lý của nháp và các cảng mới, trả 422 và rollback nếu có lựa chọn không khả dụng.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
