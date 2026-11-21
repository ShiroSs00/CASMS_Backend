# Phase A Backend — Triển khai

**Baseline:** `main`, HEAD trước và sau `65034ee7be8eb94f31835bebb087801bccd10a13`. Thay đổi nằm trong working tree. Lúc bắt đầu, Backend có `qa/` chưa theo dõi và Frontend có bằng chứng Booking Agent chưa theo dõi. Các dữ liệu đó được giữ nguyên.

Đã đọc hai audit ngày 21-11-2026 trong thư mục workspace cha, README Backend, Constitution trong `.specify/memory`, spec CASMS-75 và các consumer Web Admin. Kiểm kê catalog Codex, các thư mục skill cá nhân và workspace. Các skill được yêu cầu cho Spec Kit, code review, debugging, testing, PostgreSQL và backend architecture không có bản cài phù hợp được tìm thấy. Thực hiện đối chiếu Analyze, chốt phạm vi Clarify theo prompt và Implement bằng spec/plan/tasks hiện có.

| Finding | Phân loại và kết quả |
| --- | --- |
| F01, F11 | **Đã sửa tài liệu.** Spec công bố đầy đủ V2, tách quy trình staffing, preparation, Cruise Plan/Ready và Open Sale. Ghi tiêu chí cũ dưới LEGACY. |
| F02, F09 | **Đã sửa API; xác nhận từ source.** Preparation trả trạng thái dữ liệu, nộp, duyệt và revision nộp riêng. `SHIP_CHECKED` chỉ xử lý handoff tàu. Ship gửi kỹ thuật, CRM gửi hai nhóm của CRM. |
| F05 | **Đã sửa API; cần runtime verification chuyến demo.** Trả tàu và lý do chưa có tàu/boong. Chưa có response cùng fixture demo để xác định nguyên nhân ảnh audit. |
| F06, F13, F25, F26, E13 | **Đã sửa Backend.** Cặp khu vực–terminal theo dịch vụ dùng cùng điều kiện cấu hình với lúc lưu; nêu điều kiện thiếu; đề nghị ASM vẫn có thể chờ cấu hình. Khu vực chương trình và staffing đều lấy từ model READY duy nhất theo `StaffingShipModel`. |
| E08, E09, F24 | **Đã sửa API.** Trả tác động reset dựa trên hàng lưu và loại assignment của phương án stale khỏi số đủ người. BE cho phép danh sách khu vực rỗng nếu không có dịch vụ onboard phải bố trí. FE cần dùng hợp đồng mới để bỏ chặn sai. |
| F27 | **Xác nhận từ source.** V2 chỉ tạo membership khi cần và ghi assignment theo nhu cầu. POS vẫn kiểm tài khoản, terminal, khu vực, thời gian phân công và quyền tại điểm thao tác. |
| F10 | **Xác nhận từ source; chưa có quyết định nghiệp vụ về việc gộp bằng chứng.** Giữ độc lập nhóm an toàn và staffing, cùng quy tắc maker/checker không tự duyệt. |
| F15 | **Cần runtime verification.** Test PostgreSQL và kịch bản kiểm tra theo role nằm trong báo cáo hồi quy/E2E. Check từng mốc trên môi trường có fixture đại diện trước khi chốt nghiệm thu. |
| A6 | **Đã bổ sung seeder; cần runtime verification với fixture Booking Agent tương ứng.** Bằng chứng V138 ghi `422 PREPARATION_BUDGET_REQUIRED`. Gate cần `Cruise.operating_budget_vnd` khác null; đây là số ngân sách vận hành dự kiến CRM lưu ở chuyến, tách với bước duyệt ngân sách. |

## Tệp thay đổi

- `src/main/java/com/casms/staffing/StaffingWorkflowService.java`
- `src/main/java/com/casms/ship/controller/CruiseScopedProgramsController.java`
- `src/main/java/com/casms/ship/service/{CruiseServiceLocationService,ItineraryProgramPolicy,ItineraryProgramService,CruisePreparationService}.java`
- `src/main/java/com/casms/ship/dto/response/CruisePreparationSectionResponse.java`
- `scripts/{booking_agent_qa,phase_a_qa_budget,test_booking_agent_qa}.py` và ngoại lệ hẹp trong `.gitignore` cho bốn báo cáo.
- `src/test/java/com/casms/ship/service/ItineraryProgramsPostgresIntegrationTest.java` và `src/test/java/com/casms/config/flyway/FlywayMigrationPostgresIntegrationTest.java`
- `specs/casms75-ship-staffing-handoff/{spec,plan,tasks}.md` và bốn báo cáo Phase A.

Nhóm kiểm thử mục tiêu đạt 51/51 trên bản mã cuối và QA seeder đạt 5/5. Suite rộng còn lỗi ở fixture và modules nêu trong báo cáo hồi quy. Phase A chưa có nghiệm thu E2E qua Open Sale và Booking Agent.

Flyway hiện hành là V139. Phase A tái sử dụng các view V134–V136 và lịch sử section ở V65. Xem hợp đồng trước/sau trong [API handoff](CASMS_PHASE_A_BE_API_HANDOFF.md), kết quả chạy trong [Regression](CASMS_PHASE_A_BE_REGRESSION.md) và kịch bản theo vai trò trong [E2E](CASMS_PHASE_A_BE_E2E.md).
