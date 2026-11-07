# Đặc tả — Chuẩn hóa Hoạt động, Dịch vụ và Gói dịch vụ theo OceVie

Ngày 2026-11-07. Nguồn yêu cầu là chuẩn hóa ba nhóm màn của ACTIVITY_SERVICE_MANAGER dựa trên OceVie và quy chuẩn Quản lý lịch trình hiện có.

## Phạm vi

Web Admin, các route /admin/activities, /admin/services, /admin/service-packages cùng trang chi tiết, tạo và chỉnh sửa. Bao gồm thao tác trạng thái/xóa, quyền lợi gói và lịch dịch vụ được nhúng trong các trang chi tiết này. Hoạt động dùng chung và hoạt động vận hành hiện có đều cần được xem xét trong cùng route editor.

Các trang standalone sản phẩm, đăng ký hoạt động, yêu cầu dịch vụ, phân gói, tour/nhà cung cấp, Cruise Package và Fare Package templates nằm ngoài lần chuẩn hóa này. Shared component cần được kiểm tra hồi quy vì được các trang đó tái sử dụng.

## User stories

### US1 — Tra cứu và đọc thông tin (P1)
Quản lý hoạt động và dịch vụ tra cứu danh sách và mở chi tiết với cùng hệ thống header, bộ lọc, bảng, trạng thái và thao tác như chuẩn Admin OceVie.
Chấp nhận khi cả ba nhóm có label tìm kiếm/bộ lọc, loading/empty/error rõ ràng, hành động hàng dễ nhận biết và dữ liệu dài không phá bố cục.

### US2 — Tạo và chỉnh sửa (P1)
Người có quyền hiện hành điền form có nhóm thông tin rõ ràng, thấy lỗi cạnh trường và giữ được bản nhập khi lỗi hoặc hủy nhầm.
Chấp nhận khi cả form dùng chung và form vận hành áp dụng đúng quyền, quy tắc hợp lệ hiện tại, trạng thái busy và bảo vệ thay đổi chưa lưu.

### US3 — Xác nhận thao tác (P1)
Người có quyền hiện hành xem và xác nhận thay đổi trạng thái, xóa hoặc chỉnh quyền lợi trong hộp thoại thống nhất.
Chấp nhận khi modal có header/body/footer, thao tác hủy rõ, destructive dùng tone danger, chặn bấm lặp, hỗ trợ Escape/focus và không mất bản nhập ngoài ý muốn.

## Functional Requirements

- FR-001 Tái sử dụng AdminShell, AdminUI, Overlay và primitive hiện có. Dùng canonical token qua stylesheet mapping. Chỉ bổ sung semantic token và mapping nếu còn thiếu, tránh override global hoặc primitive thứ hai.
- FR-002 Danh sách và chi tiết của cả ba nhóm có nhóm thao tác nhất quán, một hành động primary trong mỗi nhóm, trạng thái bằng nhãn, filter/search có label và trạng thái dữ liệu loading/empty/error. Giữ phạm vi lọc, sắp xếp và phân trang hiện hành.
- FR-003 Form dùng Field và các section phù hợp. Giữ các ràng buộc và dữ liệu nghiệp vụ hiện hành. Lỗi validation đã biết gắn vào trường, aria-invalid và mô tả lỗi được liên kết. Lỗi hệ thống hoặc kết quả mutation chưa rõ vẫn hiện trong Notice. Không tự suy diễn quy tắc mới từ mong muốn đồng bộ giao diện.
- FR-004 Hộp thoại trạng thái, xóa và quyền lợi dùng Modal/Drawer hiện có với footer riêng, hủy qua data-overlay-dismiss khi có bản nhập, busy khi đang gửi, focus trả về đúng và bảo vệ thay đổi chưa lưu.
- FR-005 Giữ API, payload, permissions, route/query/returnTo, lifecycle, giá, tiền tệ, giới hạn dịch vụ, quyền lợi và các quy tắc đặt chỗ. Bảo toàn thay đổi local.

  Ngoại lệ được người dùng duyệt ngày 2026-11-07: “Đồng ý sửa payload cập nhật”. Serializer service/package bỏ serviceCode/packageCode vì DTO Backend không nhận hai trường này và PUT hiện trả INVALID_REQUEST_BODY. Giữ endpoint, method, các giá trị còn lại và mã do Backend quản lý. Nhánh product không thuộc ngoại lệ.
- FR-006 Điều chỉnh CSS feature để tuân theo token control mặc định 44px, compact 36px khi rõ ngữ cảnh và radius control 6px. Giữ layout Admin. Kiểm tra keyboard, focus, desktop 1366px, mobile 390px và zoom 200%. Bảng có thể cuộn trong vùng bảng, trang không tràn ngang.
- FR-007 Các điều chỉnh shared component hoặc CatalogKind chung phải giữ hành vi trang products và các trang standalone đang dùng cùng CSS/component.

## Success criteria

- SC-001 Kiểm chứng ba danh sách, ba chi tiết và các form tạo/chỉnh sửa bằng ACTIVITY_SERVICE_MANAGER. Thêm tài khoản đọc để chứng minh chỉ hiện thao tác được quyền cho phép.
- SC-002 Với mỗi loại form, nhập sai ít nhất một ràng buộc hiện có, sửa lại, lưu thành công trên dữ liệu thử nghiệm và kiểm tra dữ liệu đọc lại. Lỗi giữ bản nhập và chỉ báo success sau khi server xác nhận.
- SC-003 Kiểm chứng modal trạng thái/xóa và modal quyền lợi có đường hủy, Escape, Tab, trả focus, busy và lỗi server. Kiểm tra desktop/mobile, focus và zoom theo FR-006.
- SC-004 Typecheck, build và lint các tệp đổi đạt. Lỗi baseline phải được nêu riêng. Kiểm tra một trang products và một trang service-requests để phát hiện ảnh hưởng của phần dùng chung.

## Edge cases

Bản ghi lịch sử, trạng thái chỉ đọc, danh mục nguồn lỗi/rỗng, tên dài, lỗi field/server, mutation chưa rõ kết quả, mất quyền giữa phiên, hủy form đã thay đổi và chuyển trực tiếp từ cruiseId/returnTo.
