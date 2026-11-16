# PUT /trip-schedule-versions/{versionId}/content

Yêu cầu quyền `TRIP_PLAN_WRITE`.

Sau kiểm tra trạng thái và revision, TripScheduleVersionService kiểm tra địa lý của bản nháp hiện có và các cảng mới trong request. Nếu không khả dụng trả 422 và yêu cầu tạo lại. Không ghi nội dung hoặc revision khi lỗi.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
