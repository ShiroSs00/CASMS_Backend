# CASMS Booking Agent — Audit Backend

## Phạm vi và phiên bản

- Backend `origin/main` đã fetch tại `0058944298388a03f19f6650fc14482f73bb4ec9`, có `V138__passenger_nfc_card_checkin.sql`. Thay đổi nằm trên nhánh local `codex/booking-agent-e2e-readiness` được tạo từ HEAD đó.
- Frontend `origin/main` đã fetch tại `0a438959e928a7f54644aa392124c9cd5fdde001`. Đối chiếu báo cáo FE `CASMS_BOOKING_AGENT_REAL_BE_E2E_REPORT.md` và `CASMS_BOOKING_AGENT_FINAL_ACCEPTANCE.md` trong workspace. Checkout FE được giữ nguyên.
- Đã đọc cấu hình Flyway, `LegacyChecksumRealigner`, `CruiseService`, `CruiseStaffService`, `BookingService`, `PaymentService`, `CancellationWorkflowService`, controller, DTO và các test liên quan.

## Kết quả theo nhóm

| Nhóm | Phát hiện | Xử lý / bằng chứng |
| --- | --- | --- |
| Migration | Startup tự gọi `repair()` trước `migrate()`, khiến schema history có thể thay đổi chỉ vì khởi động ứng dụng. | Mặc định dùng `migrate()` cùng validation. Luồng legacy chỉ chạy khi đã xác nhận JDBC URL chính xác, schema `public` và history table chuẩn. Realigner giới hạn ở các checksum đã biết, đồng bộ description nếu đổi tên script. Nhánh V96 cũ yêu cầu tên staff script và checksum tương ứng V101. Hai bảng staff legacy chỉ được dọn khi rỗng dưới lock trong một transaction. Các bảng có dữ liệu buộc dừng để operator lập phương án chuyển đổi. |
| Phân quyền | `GET /cruise/{id}/sale-readiness` thiếu kiểm tra phân công đối với Booking Agent có `CRUISE_READ`. `sellability` và `available-cabins` đã kiểm tra. | Áp dụng `BookingAgentCruiseScope` và `requireBookingAgentAssignment` theo pattern hiện có. HTTP trên clone trả `403 ACCESS_DENIED` cho agent chưa phân công và `200` cho operator được cấp quyền. |
| Cấu hình | Profile `dev` có fallback JDBC và thông tin xác thực nhúng trong cấu hình. | Profile `dev` yêu cầu cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Owner cần kiểm tra việc thu hồi hoặc xoay vòng thông tin từng xuất hiện trong lịch sử Git. |
| Dữ liệu QA | Nguồn có một Booking Agent hoạt động, một cruise `PUBLISHED`, không có phân công BA, booking hoặc payment xác nhận. | Tạo thêm BA bằng API nhân sự và kích hoạt qua mailbox loopback trên clone. Tài khoản mới xác thực `200`. |
| Sale readiness | Chuyến QA ban đầu trả `NOT_YET_OPEN`, `sellable=false`. Hai fare đã chọn bị chặn do Ready và phê duyệt stale. | Sau khi CRM đọc revision các phân hệ, nộp lại hồ sơ và Operations phê duyệt, hai chuyến mở bán qua API và trả `sellable=true`. |
| Phân công QA | Operations nhận `403` khi gửi assignment. HR có quyền cần một staffing request đang ở `SENT_TO_HR`. Nguồn QA không có request hoặc ngân sách vận hành dự kiến cho các chuyến. | Cần CRM lưu ngân sách dự kiến hợp lệ và hồ sơ đi đúng các bước của staffing workflow. |
| Thanh toán | `PaymentService` xác nhận khoản thu qua API hiện có và gọi auto-confirm. `BookingService.acceptContract` cũng đánh giá lại điều kiện confirm. | E2E cần ký hợp đồng và chứng từ thu/hoàn hợp lệ tại QA. Kiểm tra `totalPaidAmount` sau hoàn và thời điểm xác nhận trên booking readback. |
| Tính năng chưa hỗ trợ | Đổi cabin của booking fare trả `FARE_CABIN_CHANGE_NOT_SUPPORTED`. | Giữ nguyên rule. Xem [BE–FE handoff](CASMS_BA_BE_FE_HANDOFF.md) để phân tích nghiệp vụ cần chốt trước khi triển khai. |
| Regression test | Hai assertion `UxQuoteSnapshotTest` vẫn giả định chỉ hỗ trợ USD. | Đồng bộ test với hợp đồng hiện hành: EUR bị từ chối, và USD bị từ chối đối với policy VND. Giữ nguyên production currency rules. |

## Ràng buộc vận hành

Chạy migration và kiểm thử giao dịch trên clone có tên sinh tự động ở `127.0.0.1:15439`. Không có sửa migration đã áp dụng, chỉnh checksum/schema history thủ công, phân công trực tiếp bằng SQL hoặc tạo khoản thu `Confirmed` bằng SQL. Hai bản nguồn chỉ được truy vấn kiểm kê và dùng làm template trong PostgreSQL QA.
