# Đặc tả FE — Lịch trình và dời giờ khởi hành

Nguồn yêu cầu là đề nghị xác định lại phạm vi FE cho vai trò quản lý lịch trình, đối chiếu với ITINERARY_DEPARTURE_DELAY.md. Các route delay trong tài liệu đã được đối chiếu với source Backend main tại 7d13ce4.

## Kịch bản

- US1 · Điều phối lịch trình (CRUISE_MANAGER) tạo hoặc chỉnh mẫu, chọn mẫu và nhập giờ cho chuyến cụ thể. Xem lịch dự kiến do backend tính và xem lịch vận hành theo quyền đọc.
- US2 · Quản lý vận hành (OPERATIONS_MANAGER có CRUISE_START) xem trước tác động dời giờ, đọc lý do chặn và xác nhận khi backend cho phép.
- US3 · Người có quyền đọc tương ứng xem lịch hiện tại và lịch sử delay từ dữ liệu backend.

## Yêu cầu

- FR-001 Giữ quyền hiện hành của trình chỉnh mẫu, thao tác chuẩn bị và route khởi hành. Chỉ cấp nút preview/confirm delay cho phiên có cả vai trò OPERATIONS_MANAGER và CRUISE_START. CRUISE_MANAGER chỉ có CRUISE_START vẫn giữ thao tác bắt đầu vận hành hiện hành.
- FR-002 Trên form chuyến, dùng kết quả preview backend cho giờ đã nhập. Hiển thị đúng múi giờ được chỉ định, hỗ trợ mẫu cũ và tải lại preview nếu mốc đầu vào thay đổi.
- FR-003 Trên chi tiết chuyến, quản lý vận hành nhập lý do và nhóm lý do theo contract, xem lịch trước/sau, countdown, revision và blockers. Chỉ cho xác nhận mới khi preview không có blockers, đầu vào và revision còn khớp, countdown lớn hơn 0. Nếu điều kiện thay đổi, khóa xác nhận mới và yêu cầu xem trước lại khi chuyến còn đủ điều kiện.
- FR-004 Khi retry cùng payload sau lỗi không rõ kết quả, dùng lại UUID. Lưu nguyên payload trong sessionStorage theo tài khoản và chuyến trước khi gửi. Giữ qua đóng modal và tải lại cùng tab. Khóa yêu cầu mới khi chưa rõ kết quả. Cho kiểm tra lại bằng đúng payload kể cả khi countdown đã hết hoặc revision đổi. Đối soát lịch sử chỉ khi có CRUISE_READ. Phân biệt 400, 409 stale revision, 409 UUID conflict và 422 blockers theo contract thực tế.
- FR-005 Sau khi backend xác nhận, tải lại chi tiết, lịch, lịch sử và Departure Gate. Khi đọc dữ liệu lỗi, hiển thị trạng thái lỗi tương ứng.
- FR-006 Giữ phạm vi Web Admin. Luồng thay đổi lịch hiện hữu xử lý trường hợp delay không thể tự dịch lịch.

## Tiêu chí

- SC-001 Kiểm thử riêng CRUISE_MANAGER có CRUISE_START và OPERATIONS_MANAGER có/thiếu CRUISE_START, gồm truy cập trực tiếp và phiên có nhiều vai trò.
- SC-002 Preview/confirm dùng dữ liệu máy chủ, giữ lịch cũ khi bị chặn và hiển thị lịch mới sau thành công.
- SC-003 Kiểm chứng các ca retry, revision cũ, hai lần delay liên tiếp, múi giờ cảng, mẫu legacy và trường hợp cần đổi ngày.
