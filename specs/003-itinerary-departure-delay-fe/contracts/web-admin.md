# Hợp đồng UI và API cần xác minh

Đã đối chiếu với Backend main tại 7d13ce4. Ba endpoint delay chỉ dùng /cruises. API bao dữ liệu thành công trong data.

| Giao diện | Hợp đồng |
|---|---|
| Gate | GET /cruises/{id}/departure-readiness, CRUISE_READ hoặc CRUISE_VALIDATE |
| Lịch vận hành | GET /cruises/{id}/schedule, theo quyền đọc của endpoint |
| Lịch sử | GET /cruises/{id}/delays, CRUISE_READ |
| Preview | POST /cruises/{id}/delays/preview, OPERATIONS_MANAGER và CRUISE_START |
| Confirm | POST /cruises/{id}/delays, cùng điều kiện vai trò và quyền. Gửi revision và UUID theo wire contract |

Preview và confirm cùng gửi delayMinutes (số nguyên dương), reason (không rỗng, tối đa 1000 ký tự), category (khớp [A-Z][A-Z0-9_]{0,49}), requestId (UUID) và expectedRevision (số nguyên dương). Retry confirm cùng intent giữ nguyên payload, gồm UUID và revision gốc.

Preview trả revision, evaluatedAt, secondsUntilDeparture, blockers, portWindowChecks, before và after. before là snapshot JSON, after là CruiseSchedulePreviewResponse. Confirm trả sự kiện gồm eventId, requestId, cruiseId, createdBy, createdAt, delayMinutes, category, reason, status, revision, before và after. Lịch sử trả originalDeparture, expectedDeparture, actualDeparture nếu có, totalDelayMinutes và events.

Lỗi dùng code và details. Với 409 STALE_CRUISE_REVISION, tải lại chuyến rồi xem trước. Với 409 DELAY_REQUEST_REUSED, đối soát intent và lịch sử. Với 422 CRUISE_DELAY_BLOCKED, đọc details.blockers và details.portWindowChecks. Với 422 DEPARTURE_TIME_REACHED, tải lại chuyến và đóng khả năng xác nhận. Hiển thị lỗi validation 400 tại trường khi có thông tin tương ứng.
