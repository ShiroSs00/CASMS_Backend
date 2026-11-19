# PUT /trip-schedule-versions/{versionId}/days/{dayNumber}/idea

Yêu cầu quyền `ROLE_CRUISE_MANAGER và TRIP_PLAN_WRITE`.

Sau kiểm tra quyền, trạng thái và revision, TripScheduleVersionService kiểm tra địa lý của bản nháp trước khi cập nhật ý tưởng. Nếu không khả dụng trả 422.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

## Shore excursion destination

Shore excursion entries report their persisted destination. A legacy row without one returns an unset destination. See [the reviewed flow](../ITINERARY-PROGRAM-FLOWS.md#shore-excursion-template-selection).
