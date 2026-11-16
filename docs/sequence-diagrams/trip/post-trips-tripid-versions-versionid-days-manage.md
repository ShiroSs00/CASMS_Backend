# POST /trips/{tripId}/versions/{versionId}/days/manage

Yêu cầu quyền `TRIP_PLAN_WRITE`.

Endpoint theo Trip ID dùng chung TripService quản lý ngày. Service đọc Trip và phiên bản, kiểm tra địa lý bản nháp trước khi thay đổi ngày. Nếu không khả dụng trả 422.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.
