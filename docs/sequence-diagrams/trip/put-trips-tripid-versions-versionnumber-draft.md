# PUT /trips/{tripId}/versions/{versionNumber}/draft

Yêu cầu quyền `TRIP_PLAN_WRITE`.

Endpoint tương thích dùng cùng luồng lưu nội dung với endpoint theo versionId. Kiểm tra địa lý của nháp và các cảng mới, trả 422 và rollback nếu có lựa chọn không khả dụng.

Trình tự xác thực, service, PostgreSQL và phản hồi nằm trong [luồng danh mục địa lý](../TRIP-GEOGRAPHY-FLOWS.md).

## Shore excursion destination

Shore excursion entries report their persisted destination. A legacy row without one returns an unset destination. Referenced tours must be planned definitions for the same active destination, which must be planned at and served by the selected port call. The save path rejects invalid references before persisting schedule changes. Validation reports issues, and finalize blocks invalid entries. See [the reviewed flow](../ITINERARY-PROGRAM-FLOWS.md#shore-excursion-template-selection).
