# POST /cruise/{cruiseId}/assign-ship

Gán tàu kiểm tra hạn đăng kiểm của tàu đã khóa theo ngày về của chuyến tại Việt Nam. Thiếu hạn hoặc hạn trước ngày về trả 422.

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.
