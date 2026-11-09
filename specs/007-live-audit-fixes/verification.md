# Phân tích và nghiệm thu · 007

## Độ phủ Spec Kit
R01–R10 có task tương ứng T001–T010. T011 bao phủ build và kiểm thử hồi quy. T012 theo dõi nghiệm thu với dữ liệu live. Không phát hiện xung đột nghiêm trọng giữa spec, kế hoạch và danh sách task. Phạm vi xử lý quyền và tên/cabin được ghi rõ trong spec và research.

## Kết quả
| Lớp | Bằng chứng |
| --- | --- |
| Backend Maven | 19 test đạt trong BookingLookupContractTest, ShipServiceTest, ActivityRegistrationWorkflowTest, ServiceRequestServiceTest. BookingLookupContractTest đi qua Spring MVC. ShipServiceTest phân biệt tàu rảnh và tàu không tồn tại. |
| Web build | Admin và Booking đều qua TypeScript + Vite. Có cảnh báo Vite về bundle lớn. |
| Booking Node | 17 test hồi quy đạt. |
| Booking trình duyệt mô phỏng | 390 px và 1366 px. Kiểm tra lỗi bắt buộc, email sai định dạng, focus, liên kết mô tả, giữ đầu vào, 403/404 dùng chung thông báo, kết quả rỗng, xóa bộ lọc, nút mở modal liên hệ và badge đặt lịch. |
| Admin trình duyệt mô phỏng | 768 px và 1366 px. Kiểm tra tìm theo ID, ghi chú, tên dịch vụ không dấu, bộ lọc trạng thái và lỗi danh mục. Kiểm tra focus bằng Enter và không tràn toàn trang. Xác nhận PUT khi sửa số chỗ gửi nguyên ghi chú, đổi bản ghi độc lập và xóa chủ động. Kiểm tra liên kết tới Chuyến đi và phục hồi khi tìm không có nguồn. Kiểm tra giữ ngày giờ khi đổi chuyến và chặn giờ ngoài khoảng trước khi gửi. |
| Backend local HTTP | GET /api/booking/lookup thiếu reference trả 400 MISSING_REQUIRED_PARAMETER, details.parameter=reference. GET /api/cruise?page=0&size=1 trả 200, tổng 0 chuyến công khai. |
| PostgreSQL local | Bản sao đã áp dụng schema V93 và có hai tài khoản được gán ASM. Có 0 đăng ký PENDING mang ghi chú. |

## Còn mở
- T012. Thiếu đăng ký PENDING có ghi chú để kiểm tra vòng đọc, cập nhật và đọc lại với dữ liệu PostgreSQL. Xác minh quyền Admin trên browser bằng fixture. Lần thử đăng nhập bằng bộ tài khoản QA cũ không hoàn tất trên bản sao này.
- API current-cruise đã có test service. Chưa kiểm thử endpoint có phân quyền với tàu rảnh và tàu chạy trên dữ liệu live. Chưa có gói công khai thật để đối chiếu badge.
- Tìm theo tên hành khách, số cabin hoặc mã tham chiếu đặt chỗ cần chốt phạm vi đọc dữ liệu của ASM. Nhân viên tạo yêu cầu thay khách và cấp thêm vai trò Admin là các đề xuất nghiệp vụ/phân quyền riêng.

## Runtime local
- Backend http://127.0.0.1:8080/api dùng bản sao PostgreSQL casms_feature006_20261109.
- Web Admin http://127.0.0.1:5173 và Web Booking http://127.0.0.1:5174 là Vite preview.
