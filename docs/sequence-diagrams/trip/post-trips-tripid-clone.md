# POST /trips/{tripId}/clone

Yêu cầu quyền `TRIP_CREATE`.

Controller qua IdempotencyService gọi TripService. Service kiểm tra phạm vi Trip, cảng khởi hành và các cảng dừng của bản nguồn trước khi sao chép qua repository. 422 khi phát hiện địa lý không khả dụng.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

Trip bounds use inclusive ship-local `HH:mm` values. See the [departure window flow](../TRIP-DEPARTURE-WINDOW.md).
