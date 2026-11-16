# POST /trip-schedule-versions/{versionId}/validate

Yêu cầu quyền `TRIP_PLAN_VALIDATE`.

TripScheduleVersionService kiểm tra địa lý nếu phiên bản đang DRAFT. Khi có lựa chọn không khả dụng, phản hồi 200 ValidationReport với validForSubmit=false và lỗi TRIP_GEOGRAPHY_UNAVAILABLE, actionHint RECREATE_DRAFT.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.
