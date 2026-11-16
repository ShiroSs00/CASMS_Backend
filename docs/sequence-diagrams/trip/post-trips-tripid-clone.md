# POST /trips/{tripId}/clone

Yêu cầu quyền `TRIP_CREATE`.

Controller qua IdempotencyService gọi TripService. Service kiểm tra phạm vi Trip, cảng khởi hành và các cảng dừng của bản nguồn trước khi sao chép qua repository. 422 khi phát hiện địa lý không khả dụng.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).
