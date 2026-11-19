# PUT /trip-schedule-versions/{versionId}/content

Yêu cầu quyền `TRIP_PLAN_WRITE`.

Sau kiểm tra trạng thái và revision, TripScheduleVersionService kiểm tra địa lý của bản nháp hiện có và các cảng mới trong request. Nếu không khả dụng trả 422 và yêu cầu tạo lại. Không ghi nội dung hoặc revision khi lỗi.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

## Shore excursion destination

Shore excursion entries report their persisted destination. A legacy row without one returns an unset destination. Referenced tours must be planned definitions for the same active destination, which must be planned at and served by the selected port call. The save path rejects invalid references before persisting schedule changes. Validation reports issues, and finalize blocks invalid entries. See [the reviewed flow](../ITINERARY-PROGRAM-FLOWS.md#shore-excursion-template-selection).
