# Phase A Backend — Kịch bản nghiệm thu theo vai trò

Dùng một chuyến có dịch vụ onboard và một chuyến chỉ có chương trình ngoài khu vực SERVICE. Tạo chuyến đi bằng dữ liệu test, model READY và model DRAFT cùng tàu. Với chuyến có dịch vụ, chuẩn bị cả cấu hình hợp lệ và một ứng viên thiếu quyền `SERVICE_READ`. Mỗi actor đăng nhập bằng tài khoản riêng đúng vai trò và phạm vi.

| Vai trò | Thao tác | Kết quả cần quan sát |
| --- | --- | --- |
| CRM | Tạo chuyến, giao Quản lý tàu, lưu ngân sách dự kiến và gửi yêu cầu theo revision mới | Thiếu ngân sách trả 422. Lưu xong tạo `PENDING_ASM`; CRM ngoài phạm vi nhận 403. |
| ASM | Nhận việc, mở `program-options`, sắp lịch và hoàn tất | Chỉ thấy khu vực của model READY hiệu lực. Dịch vụ chưa cấu hình boong có thông tin để đề xuất. Hoàn tất tạo `PENDING_SHIP_REVIEW`. |
| Ship | Kiểm tra tàu, chọn khu vực và nhu cầu. Xem lựa chọn cho từng dịch vụ. Thử máy thiếu quyền, tiếp tục với cặp hợp lệ. Tại chuyến không có dịch vụ, lưu hai danh sách rỗng. | Lý do thiếu lần lượt theo cấu hình. Server chặn thao tác không hợp lệ. Hai chuyến đều có thể đến `SHIP_REVIEWED` khi đủ điều kiện áp dụng. Ship ngoài phạm vi không sửa được. |
| Ship và OPS | Trong preparation, Ship nộp `TECHNICAL_NAUTICAL`. OPS khác người nộp xác nhận đúng revision. | `SHIP_CHECKED` chưa nộp section. Sau nộp, kiểm người nộp và revision. Sau duyệt, kiểm người duyệt và revision nộp vẫn được giữ. Chặn tự duyệt. |
| CRM | Đọc lại phương án, gửi HR | `SEND_TO_HR` tạo `SENT_TO_HR`. Revision cũ bị chặn. |
| HR | Gán, gỡ và thay nhân viên; phân ca; thử nhân viên trùng lịch hoặc đã kết thúc membership. Nộp phương án. | `HR_SUBMITTED` cần đủ người và ca còn hiệu lực. Việc gỡ khỏi nhu cầu có thể giữ membership và duty khác. Nhu cầu và ca hiển thị độc lập. |
| CRM | Xác nhận HR hoặc trả Ship với lý do. Thử thu hồi quyền máy trước xác nhận. | `PLANNER_CONFIRMED` khi hợp lệ. Phương án cũ bị chặn dù số assignment đã ghi còn nguyên. Khi trả Ship, đối chiếu số reset báo trước và hiện trạng sau trả. |
| OPS | Duyệt staffing, kiểm các section chuẩn bị, duyệt Cruise Plan, dùng hành động Ready và kiểm Open Sale, mỗi bước theo quyền và điều kiện hiện hành. | `OPS_APPROVED` vẫn là staffing. Các quyết định Plan, hồ sơ, Ready, mở bán và sellability có kết quả riêng. Nếu OPS nộp `SAFETY_CREW`, OPS khác người nộp thực hiện duyệt. |
| Nhân viên POS | Thử vào POS với người đã được HR gán nhưng chưa có quyền khu vực. Cấp quyền ở đúng màn cấu hình QA, thử lại rồi thu hồi hoặc hết hạn. | Sau gán staffing chưa có quyền POS. API POS đánh giá quyền của người dùng, khu vực, máy và thời điểm truy cập. |

## Booking Agent QA

Seeder `scripts/phase_a_qa_budget.py` được gọi từ `scripts/booking_agent_qa.py` khi plan chọn `operatingBudgetVnd` là số nguyên dương đã được cung cấp cho fixture. Runner tạo bản sao ngẫu nhiên từ template local trên cổng 15439, khởi chạy Backend loopback với profile `booking-agent-qa` và dùng CRM phụ trách chuyến. Seeder giữ nguyên giá trị có sẵn và chặn ghi ngoài bản sao QA. Chọn fixture DRAFT phù hợp trước khi tiến tới duyệt Plan hoặc mở bán. Runner vẫn yêu cầu staffing đi qua V2 và đạt `SENT_TO_HR` trước thao tác phân công Booking Agent.

Báo cáo `CASMS_BOOKING_AGENT_REAL_BE_E2E_REPORT.md` và ma trận nghiệm thu ở repo Frontend ghi giao dịch dương còn thiếu chuyến sellable và phân công. Bằng chứng HTTP chi tiết ở Backend tại [Booking Agent V138 staffing blockers](booking-agent-qa-evidence/booking-agent-v138-staffing-blockers-http.json). Chưa chạy lại toàn bộ Booking Agent hoặc chuỗi UI đến Open Sale cho Phase A. Trong checkout có cấu hình QA cục bộ, nhưng thiếu plan hai chuyến có ngân sách và dữ liệu thương mại đã được chỉ định cho lượt nghiệm thu. Các ca service và workflow riêng được ghi theo kết quả thực tế trong [Regression](CASMS_PHASE_A_BE_REGRESSION.md).
