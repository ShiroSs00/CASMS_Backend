# POST /trips

Yêu cầu quyền `TRIP_CREATE`.

Controller qua IdempotencyService gọi TripService. Service kiểm tra phạm vi tạo mới bằng danh mục ACTIVE trước khi ghi Trip và lịch nháp trong transaction. Quốc gia hoặc miền không khả dụng trả 422 và yêu cầu tạo lại với phạm vi hợp lệ.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

Trip bounds use inclusive ship-local `HH:mm` values. See the [departure window flow](../TRIP-DEPARTURE-WINDOW.md).
