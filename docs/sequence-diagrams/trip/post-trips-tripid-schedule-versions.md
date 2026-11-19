# POST /trips/{tripId}/schedule-versions

Yêu cầu quyền `TRIP_PLAN_WRITE`.

TripService kiểm tra phạm vi Trip và các cảng của lịch nguồn nếu có trước khi tạo phiên bản. Lựa chọn đã tắt trả 422 và không tạo phiên bản.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

## Shore excursion destination

Shore excursion entries report their persisted destination. A legacy row without one returns an unset destination. See [the reviewed flow](../ITINERARY-PROGRAM-FLOWS.md#shore-excursion-template-selection).
