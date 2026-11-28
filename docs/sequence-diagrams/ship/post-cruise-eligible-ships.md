# POST /cruise/eligible-ships

Tính ngày về từ lịch nguồn và giờ khởi hành đã chọn theo múi giờ `Asia/Ho_Chi_Minh`. Kết quả chỉ giữ tàu có `classificationExpiryDate` bằng hoặc sau ngày đó. Tàu thiếu hạn đăng kiểm được loại khỏi danh sách.

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

Voyage departure window validation compares the ship-local departure minute against the template bounds. See the [departure window flow](../TRIP-DEPARTURE-WINDOW.md).
