# POST /trips/{tripId}/versions/new-draft

Yêu cầu quyền `TRIP_PLAN_WRITE`.

TripScheduleVersionService đọc lịch đã phát hành gần nhất và kiểm tra cả phạm vi chuyến lẫn các cảng sao chép trước khi ghi bản nháp. Lựa chọn đã tắt trả 422.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Shore excursion destination

Shore excursion entries report their persisted destination. A legacy row without one returns an unset destination. See [the reviewed flow](../ITINERARY-PROGRAM-FLOWS.md#shore-excursion-template-selection).
