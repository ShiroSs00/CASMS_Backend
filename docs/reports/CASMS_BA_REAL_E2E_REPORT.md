# CASMS Booking Agent — Báo cáo HTTP và PostgreSQL QA

## Kết quả đã đo

| Kiểm tra | Kết quả | Bằng chứng |
| --- | --- | --- |
| Package Backend | PASS | Maven `BUILD SUCCESS` trên Java 21. |
| Maven regression có chọn lọc | PASS | 66 bài qua hai lượt, 0 failure/error/skipped ở lượt xác nhận cuối của mỗi nhóm. Gồm 57 bài unit, MockMvc contract hoặc checksum và 9 bài dùng PostgreSQL. Xem XML trong `target/surefire-reports`. |
| PostgreSQL integration | PASS | `BookingPostgresIntegrationTest` chạy 6 bài. `BookingAtomicPostgresTest` chạy 3 bài, gồm idempotency đồng thời và rollback của transaction khi lỗi completion được inject. Các bài này không phải probe HTTP qua cổng thật. |
| QA runner validation | PASS | 3 bài Python kiểm tra guard môi trường, template và tùy chọn PostgreSQL read-only. |
| Flyway V137 → V138 | PASS | Validate 129 migrations, áp dụng một migration V138 trên clone. Digest của các bản ghi trước V138 trùng trước/sau. [HTTP JSON từ nguồn V137](booking-agent-qa-evidence/booking-agent-v138-http.json) |
| Flyway V101 → V138 | PASS | Validate 129 migrations, áp dụng 30 migrations trên clone. Digest đến V101 trùng trước/sau. Clone đã được dọn sau khi JVM thoát. [HTTP JSON từ nguồn V101](booking-agent-qa-evidence/booking-agent-v101-http.json) |
| HTTP smoke | PASS | 26 probe HTTP thật từ mỗi nguồn V101 và V137. Các probe kiểm tra login, quyền, danh sách rỗng, route nghiệp vụ với input không tồn tại, và từ chối mở bán. |
| Tái phê duyệt và mở bán fare | PASS | 32 probe HTTP trên clone V137. Hai chuyến dùng fare offer hiện có và trả `sellable=true`. [Receipt tái phê duyệt](booking-agent-qa-evidence/booking-agent-v137-reapproval-http.json) |
| Provisioning BA, HR và điều kiện staffing | BLOCKED | 97 probe HTTP trên clone V138. Tạo BA thứ hai và HR đều `201`, kích hoạt `200`. Cả hai chuyến có catalogue `200` và `transactionReady=true`. CRM gọi tạo staffing với revision hiện hành nhận `422 PREPARATION_BUDGET_REQUIRED` ở cả hai chuyến. [Receipt staffing](booking-agent-qa-evidence/booking-agent-v138-staffing-blockers-http.json) |
| Giao dịch hai Booking Agent | BLOCKED | Đã tạo và kích hoạt BA thứ hai trên clone. Hai chuyến fare đã mở bán. Thiếu hồ sơ staffing hợp lệ ở `SENT_TO_HR`, nên hai BA chưa thể tạo booking trong phạm vi chuyến. |

Maven chạy `DatabaseConfigTest`, `MigrationChecksumLockTest`, `BookingLookupContractTest`, `BookingRecoveryTest`, `BookingServiceTest`, `PaymentServiceTest`, `RefundServiceTest`, `CruiseSaleCatalogTest`, `BookingPostgresIntegrationTest` và `BookingAtomicPostgresTest`. Hai test PostgreSQL dùng clone riêng và kết thúc bằng `DROP DATABASE` cho clone đó. Lượt bổ sung gồm `UxQuoteSnapshotTest` và `CruiseStaffServiceTest`. Hai assertion cũ của quote từng lỗi do giả định chỉ hỗ trợ USD. Test đã cập nhật theo hợp đồng USD/VND hiện hành và lượt chạy lại qua đủ 13 bài.

## Phản hồi mở bán

BA chưa được phân công nhận `403 ACCESS_DENIED` trên `sale-readiness`, `sellability`, `available-cabins` và tạo quote. Operator QA có quyền nhận `200` khi tải readiness. Plan hiển thị `APPROVED` nhưng snapshot phê duyệt hiện tại không còn khớp. `readyStatus=INVALIDATED`, với `readyBlockers=[PLAN_APPROVAL_REQUIRED]`. Readiness trả thêm `READY_CONFIRMATION_REQUIRED` và `FARE_SALE_SCOPE_INELIGIBLE`. Gửi đúng revision/fingerprint đang trả về vẫn nhận `422 READY_CONFIRMATION_REQUIRED`. Public sellability trả `200` cùng `NOT_YET_OPEN`, `sellable=false`, `transactionReady=false`.

Nguồn V137 có 28 chuyến DRAFT. Ba chuyến có bản ghi APPROVED và thời điểm Ready được kiểm tra qua HTTP thật. Cả ba đều trả `saleReady=false`, `READY_CONFIRMATION_REQUIRED`, `PLAN_APPROVAL_REQUIRED` và `FARE_SALE_SCOPE_REQUIRED` khi probe chưa chọn offer cụ thể. Receipt V137 ghi UUID từng chuyến.

Hai chuyến có fare offer đã biết được đưa lại vào review qua `request-revision`. Lượt submit ban đầu trả `422 PREPARATION_REVISIONS_REQUIRED`. Sau khi đọc các revision thật từ dashboard, CRM gửi lại hồ sơ đã có phân hệ `SUBMITTED`. Cả `submit`, `approve`, `ready` và `open-sale` trả `200` trên hai chuyến, với `sellable=true` khi đọc lại.

Hai BA đăng nhập và xác thực quyền qua `/auth/me`. Cả hai được kiểm tra ở trạng thái chưa phân công, nhận `403` theo chuyến. Lượt chuẩn bị xác nhận CRM có quyền đọc và gửi staffing, nhưng hai chuyến đều có `hasOperatingBudget=false` và chưa có staffing request. Vì vậy chưa có bằng chứng 403 chéo giữa hai chuyến đã gán, booking được tạo hoặc cọc thực tế.

## Các kiểm tra giao dịch chưa có dữ liệu chạy

- Hai BA độc lập, list có dữ liệu và 403 chéo chuyến hai chiều.
- Tạo quote và booking, hết hạn quote, revision giá cũ, capacity, GET recovery sau khi mất phản hồi HTTP, replay và đối chiếu transaction. QA runner có thể bỏ phản hồi thành công để kiểm tra đường recovery. Fault injection rollback được xác nhận riêng trong PostgreSQL integration.
- Lookup và detail thành công, ghi nhận hợp đồng QA, đổi cabin legacy, từ chối đổi cabin fare trên booking fare thật, cancellation intake và transition có revision.
- Finance QA ghi nhận thu trước/ký trước, cọc thiếu/cọc đủ, chứng từ trống, refund làm giảm `totalPaidAmount`, auto-confirm và confirm lặp. Chuyển trạng thái manual từ Draft đủ điều kiện cần fixture hợp lệ riêng vì khi ký hoặc thu qua các API hiện hành, Backend cũng gọi auto-confirm.
- Cho phép nâng cấp opt-in đối với lịch sử legacy đã biết. Hai clone hiện có chạy đường migrate chuẩn và không chứa trạng thái V96 cũ để xác minh đường opt-in bằng PostgreSQL thật.

Trạng thái từng feature và endpoint chi tiết nằm trong [bảng nghiệm thu](CASMS_BA_FINAL_ACCEPTANCE.md).
