# PATCH /cruise/{cruiseId}/draft

Khi yêu cầu chọn tàu hoặc đổi thời gian của chuyến đã gán tàu, service kiểm tra hạn đăng kiểm của tàu đã khóa theo ngày về hiệu lực tại Việt Nam. Thiếu hạn hoặc hạn trước ngày về trả 422.

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

Voyage departure window validation compares the ship-local departure minute against the template bounds. See the [departure window flow](../TRIP-DEPARTURE-WINDOW.md).

## Shore excursion destination

When applying a schedule or synchronizing its programs, materialization verifies the destination of the exact port call and checks that referenced tours are planned templates tied to that destination. An unlinked, mismatched or ineligible existing template raises a 422 and rolls back the operation. A missing source or supplier produces a failed mapping without a tour. See [the reviewed flow](../ITINERARY-PROGRAM-FLOWS.md#shore-excursion-template-selection).
