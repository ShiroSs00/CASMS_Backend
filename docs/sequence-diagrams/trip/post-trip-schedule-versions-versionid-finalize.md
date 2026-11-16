# POST /trip-schedule-versions/{versionId}/finalize

Yêu cầu quyền `ROLE_CRUISE_MANAGER, TRIP_PLAN_WRITE và TRIP_PLAN_VALIDATE`.

TripScheduleVersionService kiểm tra lại role của người chốt, trạng thái, revision và các điều kiện thẩm định trong transaction. Nếu địa lý của bản nháp không khả dụng, trả 422 với violations, rollback và giữ DRAFT.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.
