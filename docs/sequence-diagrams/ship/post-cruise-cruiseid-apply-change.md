# POST /cruise/{cruiseId}/apply-change

Khi áp dụng thay đổi tàu hoặc lịch, service dùng ngày về hiệu lực tại Việt Nam và hạn đăng kiểm của tàu đã khóa. Thiếu hạn hoặc hạn trước ngày về trả 422.

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

Voyage departure window validation compares the ship-local departure minute against the template bounds. See the [departure window flow](../TRIP-DEPARTURE-WINDOW.md).
