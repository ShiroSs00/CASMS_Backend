# CASMS-75: Hoàn thiện hoạt động lịch trình theo từng bước

**Branch**: `feat/CASMS-75-itinerary-management`
**Ngày**: 2026-11-10
**Nguồn**: Yêu cầu người dùng trong phiên làm việc; đã đồng bộ `origin/main` tại `238abe7`.

## User Scenarios & Testing

### US1 — Bản nháp và chuyển hồ sơ từng bộ phận (P1)

CRM tạo lịch trình trước khi biết đầy đủ hoạt động. Hồ sơ kỹ thuật tàu, khách sạn và nhân sự vẫn chuyển theo quy trình, quyền, coordinator và revision hiện có.

- AC1: Tạo/sửa bản nháp không yêu cầu gán hết danh mục hoặc hoàn thiện mọi hoạt động.
- AC2: Gửi từng phân hệ chuẩn bị không gọi kiểm tra đầy đủ hoạt động tổng thể.
- AC3: Gửi tổng thể cho vận hành, duyệt và xuất bản phải kiểm tra lại dữ liệu hiện hành; thiếu dữ liệu không chuyển trạng thái.

### US2 — Gán và bổ sung lịch hoạt động (P1)

- AC4: Một yêu cầu gán sao chép các định nghĩa đã chọn thành occurrence thuộc chuyến; giữ nguồn, không tạo ngày giờ giả. Danh mục nguồn không bị sửa.
- AC5: API tiếp theo bổ sung thời gian, vị trí, sức chứa và giá rõ ràng (có thể bằng 0 USD). Thời gian bắt đầu < kết thúc, toàn bộ trong khoảng chuyến.
- AC6: `If-Match` dùng revision của Cruise; chỉnh sửa tăng revision, sai revision trả 409. Chỉ CRM có quyền chỉnh bản nháp; hồ sơ bị freeze không được sửa.
- AC7: Có thể bỏ occurrence PLANNED chưa có đăng ký, kể cả chưa có thời gian. Không bỏ occurrence có đăng ký còn hiệu lực.

### US3 — Đúng khu vực và đúng lần ghé cảng (P1)

- AC8: Chỉ FACILITY thuộc đúng tàu, có deck vật lý, không có cabin, được phân loại ACTIVITY_SPACE và không có dịch vụ đang gán mới được dùng tổ chức hoạt động.
- AC9: Khu vực cho phép nhiều hoạt động cùng giờ. Không kiểm tra xung đột chỉ vì trùng tên địa điểm/cảng. Giới hạn đăng ký, supplier, giờ tàu/tender vẫn giữ.
- AC10: Tham quan chỉ gán vào lần ghé cảng trong chính lịch trình, tại Destination ACTIVE được đặt trực tiếp trên stop hoặc trong CruiseDestinationVisit của stop đó. Nếu định nghĩa khóa port/destination, phải khớp.
- AC11: Tham quan bắt đầu sau khi tàu đến cộng thời gian tender; kết thúc trước all-aboard/rời cảng và nằm trong chuyến. Mọi stop hoạt động có giờ đến/rời tăng dần, không chồng lấn; NORMAL nằm trong chuyến. Giữ quy ước call đặc biệt của repository.
- AC12: Phân loại OTHER/SERVICE/ACTIVITY_SPACE được quản lý rõ ràng; không tự đoán khu vực cũ từ tên. DB chặn gán dịch vụ vào ACTIVITY_SPACE và đổi loại khi còn hoạt động hiệu lực.

## Decisions / Scope

“Đầy đủ” được triển khai là hoàn thiện mọi hoạt động **đã chọn**, không bắt gán toàn bộ danh mục, không ép mỗi ngày/lần ghé có cả hai loại. Phân loại mới bổ sung cho `metadata_json.kind` vốn chỉ mô tả facility. Các API lịch trình/cảng hiện có tiếp tục phụ trách chỉnh lịch tàu và danh sách điểm tham quan.

Không thay đổi công thức giá, tiền tệ USD của dự án, quyền chuyên môn, điều kiện tự duyệt, xác nhận hồ sơ hay đưa migration lên DB dùng chung. Không thay frontend trong yêu cầu này.

## Success Criteria

Kiểm thử được luồng gán thiếu thông tin → hoàn thiện → đủ điều kiện, chặn phạm vi/revision/quyền/khu vực sai, kiểm chứng migration và trigger trên PostgreSQL độc lập; route inventory/sequence coverage hợp lệ và delta nghiệp vụ được rà soát.
