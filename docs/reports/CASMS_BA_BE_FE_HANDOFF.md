# CASMS Booking Agent — Bàn giao Backend cho Frontend

## API hiện hành

- `GET /cruise/{cruiseId}/sale-readiness` yêu cầu BA được phân công chuyến. BA ngoài phạm vi nhận `403 ACCESS_DENIED`. Operator có quyền vẫn xem được readiness. Catalogue và sellability tiếp tục theo quyền của từng route.
- Khi policy hủy theo case được bật, dùng `POST /booking/{id}/cancellations` với `Idempotency-Key` và tải lại history. Route hủy trực tiếp trả `409 CANCELLATION_CASE_REQUIRED`. Các quyết định hồ sơ dùng `revision` hiện tại.
- Khi kết quả tạo booking chưa rõ, đọc `GET /booking/recovery?idempotencyKey=...` với cùng tài khoản hoặc khóa phiên khách. Dùng đúng idempotency key và payload nếu điều kiện retry đã được xác nhận.
- Trình tự ký hợp đồng và xác nhận thu đều có thể kích hoạt auto-confirm. Dùng booking detail để hiển thị trạng thái và số tiền ròng được đối soát. Một lệnh confirm lặp trên booking đã xác nhận dùng để kiểm tra tính idempotent.

## Đổi cabin cho booking dùng fare

Endpoint `POST /booking/change-cabin` hiện trả `409 FARE_CABIN_CHANGE_NOT_SUPPORTED` cho booking fare. Giữ nguyên hành vi và thông báo nghiệp vụ hiện hành. Legacy vẫn dùng endpoint với `bookingPassengerId`, `newCabinId` và `changeReason`.

Để mở đổi cabin fare, cần xác nhận phạm vi được đổi giữa cùng hạng giá, khác hạng giá và các booking theo nhóm. Cần quyết định thời điểm giữ giá, xử lý chênh lệch tiền, quyền lợi gói và tác động đến hợp đồng đã ký hoặc đã đặt cọc.

Thiết kế sau khi có quyết định nghiệp vụ cần xác minh offer, revision và eligibility của cabin đích, sau đó lock booking và tồn cabin theo thứ tự nhất quán. Cập nhật giữ chỗ, assignment của hành khách, snapshot thương mại và audit trong cùng transaction. Action cần chống replay, kiểm tra quyền theo chuyến và từ chối khi hết chỗ hoặc revision đã cũ. Tiêu chí nghiệm thu gồm hai BA, thao tác đồng thời, rollback khi lỗi và đối chiếu số tiền sau thay đổi. Chưa triển khai các thay đổi đó trong đợt này.

## Điều kiện chạy lại

Cung cấp template PostgreSQL QA có staffing request hợp lệ ở `SENT_TO_HR` cho hai chuyến. Tool có thể kích hoạt tài khoản BA và HR trên clone qua API hiện hành. Hai chuyến fare đã mở bán được. Để nghiệm thu legacy, cần một chuyến đã phát hành có pricing policy legacy được duyệt và hai cabin tương thích. Cần bộ chứng từ ký, thu và hoàn đã đối soát để xác minh luồng thanh toán. Xem [hướng dẫn provisioning](CASMS_BA_QA_DATA_PROVISIONING.md) và [kết quả nghiệm thu](CASMS_BA_FINAL_ACCEPTANCE.md).
