# Kiểm chứng FE

Điều kiện: có backend đích với các route đã xác minh, tài khoản theo vai trò, một chuyến PUBLISHED chưa xuất phát và dữ liệu ca bị chặn.

Từ CASMS_Frontend/CASMS_WebAdmin, chạy npm run typecheck và npm run dev. Mở chi tiết chuyến ở /admin/cruises/{id}.

1. Dùng CRUISE_MANAGER có CRUISE_START. Kiểm tra trình chỉnh mẫu và preview theo giờ nhập, thao tác bắt đầu vận hành theo điều kiện hiện tại, cùng quyền xem lịch.
2. Dùng OPERATIONS_MANAGER có và thiếu CRUISE_START. Kiểm tra thao tác delay, quyền truy cập trực tiếp và tài khoản nhiều vai trò.
3. Preview lịch trước/sau. Kiểm tra ngày qua nửa đêm, lịch cố định, múi giờ cảng và mẫu legacy thiếu giờ mốc. Ca bị chặn giữ lịch hiện hành.
4. Xác nhận delay hợp lệ. Chi tiết, lịch, gate và lịch sử phản ánh dữ liệu mới. Delay thứ hai lấy lịch mới làm gốc.
5. Kiểm tra revision cũ và lỗi mất phản hồi bằng môi trường thử nghiệm. Retry cùng intent giữ UUID, tránh ghi hai lần. Thay dữ liệu yêu cầu preview mới.


6. Mô phỏng mất phản hồi confirm, đóng modal rồi reload cùng tab. Kiểm tra chỉ có thể gửi lại nguyên yêu cầu cũ, kể cả khi countdown hết hoặc tài khoản thiếu CRUISE_READ. Tài khoản có CRUISE_READ đối soát theo requestId.
7. Giữ modal preview mở tới khi countdown bằng 0 hoặc tải lại chi tiết với revision mới. Xác nhận mới bị khóa, gửi lại intent chưa rõ kết quả vẫn khả dụng.


## Kết quả thực thi local — 2026-11-07

### Môi trường

- Web Admin chạy tại http://127.0.0.1:5174, proxy tới Backend http://127.0.0.1:8080/api.
- Backend main 7d13ce4, Java 21, PostgreSQL 16 trong container riêng casms-delay-check-20261107, DB casms_delay trên 127.0.0.1:55432. Các dữ liệu tạo và chỉnh phục vụ kiểm thử nằm trong DB cô lập.
- Đã đọc các DB kiểm thử local hiện có, sao chép ocv_mf02_e2e_test sang DB casms_readiness trong container cô lập. Bản gốc được giữ nguyên. Clone gặp V91 trùng bảng và thiếu cột Ship của V93. Đã thử chạy clone với Flyway tắt, áp dụng V93 nguồn vào clone và giữ Hibernate validate để chỉ đánh giá dữ liệu cũ. Kết quả này không chứng nhận migration của DB cũ.

### Đạt

- HTTP 200 ở Web Admin, API docs Backend và API docs qua proxy FE.
- Chromium/Edge headless đăng nhập OPS và CRM thật qua FE. CRM xem danh sách chuyến và lịch sử, POST preview delay nhận 403 và tab thao tác OPS không mở được.
- OPS mở modal delay, preview bị chặn hiển thị lý do và khóa xác nhận. Preview revision cũ trả 409 STALE_CRUISE_REVISION. Confirm bị chặn trả 422.
- Đọc lịch sử thật của chuyến có hai sự kiện, tổng 120 phút; chuyến chưa delay trả danh sách rỗng; chuyến có giờ thực tế trả actualDeparture. UI hiển thị lịch sử.
- Replay sự kiện fixture với đúng actor và UUID qua UI. Sau khi Backend trả 200, trình duyệt mô phỏng mất phản hồi. Reload cùng tab giữ nguyên payload, lần retry trả 200, không thêm sự kiện và xóa pending intent. Fixture audit actor được gắn tài khoản local trên DB cô lập để kiểm chứng đường replay.
- Form tạo chuyến đọc mẫu legacy thật qua preview API. Đổi ngày/giờ hủy preview đã áp dụng. Giờ 00:30 Việt Nam được gửi thành 17:30Z ngày trước. Dialog lịch không gây tràn ngang trang ở chiều rộng 1366 và 390 px.
- Fixture legacy được sửa nhóm thời lượng từ SHORT thành LONG để phù hợp ba ngày. Preview legacy trả 200. Đổi timezone cảng thử nghiệm sang Asia/Tokyo trả 09:00 địa phương thành 00:00Z. Thêm mốc khởi hành 08:00 cho fixture rồi đổi giờ qua nửa đêm: lịch cảng dịch đúng 16,5 giờ từ server.
- Backend tests đạt: CruiseDelayServiceTest 4, BaselineRoleAuthorizationTest 7, ScheduleMaterializationServiceTest 14, TripPlanningContractTest 22 và CruiseDelayPostgresIntegrationTest 1. Tổng 48 ca. Integration test delay dùng PostgreSQL thật nhưng giả lập gate/window, kiểm tra concurrent retry, hai delay nối tiếp, stale revision và rollback.
- Web Admin build và ESLint các tệp TypeScript đổi đạt. Sửa decoder FE để chấp nhận message null hợp lệ trong portWindowChecks.

### Chưa đạt hoặc chưa đủ bằng chứng

- CruiseDraftAndMaterializationPostgresIntegrationTest lượt đầy đủ có 10 lỗi trên 12 ca, ban đầu lỗi lazy proxy Trip không có Session. Lượt chẩn đoán E02 bật lazy loading ngoài transaction đi tiếp và bị validation thời lượng do fixture SHORT có ba ngày. Các lỗi nằm ở test/backend hiện tại, chưa sửa nguồn Backend trong công việc FE này.
- Năm chuyến PUBLISHED trong clone dữ liệu cũ đều trả PLAN_APPROVAL_REQUIRED. Kiểm tra kế hoạch của một chuyến cho thấy WINDOW_NOT_VERIFIED và PRICING_POLICY_REQUIRED. Chưa tìm được chuyến đạt gate thực để xác nhận delay mới end-to-end.
- Ma trận browser đầy đủ cho OPS thiếu CRUISE_START và tài khoản nhiều vai trò còn thiếu fixture runtime. Logic phân quyền đã có kiểm chứng mô phỏng ở lượt trước.
- T011 và T015 tiếp tục để mở cho các ca trên. Không dùng kết quả mock gate để đánh dấu đạt điều kiện vận hành thật.
