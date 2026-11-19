# PUT /trips/{tripId}

Yêu cầu quyền `TRIP_UPDATE`.

TripService chuyển yêu cầu sang nhánh cập nhật draft và kiểm tra phạm vi nếu request ghi scope, countryCodes hoặc domesticRegions. Trip chưa khóa có địa lý đã bị tắt bị từ chối trước khi ghi, kể cả khi đổi sang địa lý đang bật. API trả 422 và yêu cầu tạo lại.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

Trip bounds use inclusive ship-local `HH:mm` values. See the [departure window flow](../TRIP-DEPARTURE-WINDOW.md).
