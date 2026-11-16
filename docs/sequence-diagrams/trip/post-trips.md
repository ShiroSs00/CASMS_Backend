# POST /trips

Yêu cầu quyền `TRIP_CREATE`.

Controller qua IdempotencyService gọi TripService. Service kiểm tra phạm vi tạo mới bằng danh mục ACTIVE trước khi ghi Trip và lịch nháp trong transaction. Quốc gia hoặc miền không khả dụng trả 422 và yêu cầu tạo lại với phạm vi hợp lệ.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
