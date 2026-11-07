# Research

## Component và CSS

**Decision** Dùng AdminShell, AdminUI và Overlay hiện có.
**Rationale** ActivityUI và CatalogUI đã dựa trên khung và primitive chung. Chênh lệch tập trung ở composition, CSS feature và trạng thái.
**Alternatives considered** Tạo thư viện mới hoặc sao chép màn Quản lý lịch trình sẽ tăng trùng lặp và làm mất bố cục theo nghiệp vụ.

Activities.module.css và Catalog.module.css có rule control 42px, radius 8px. Chưa kết luận các giá trị này thắng cascade trong mọi màn. Baseline computed style phải chạy trước khi sửa.

## Form và validation

**Decision** Dùng Field/FormErrorSummary, tái sử dụng validateActivity và catalog-editor. ActivityCatalogEditor có thông báo lỗi tổng cho tên, sức chứa và giá nên cần tách lỗi tương ứng theo ràng buộc hiện có.
**Rationale** Chuẩn OceVie yêu cầu lỗi ở cạnh trường, giữ bản nhập và semantic ARIA.
**Alternatives considered** Đổi quy tắc hợp lệ để form dễ triển khai sẽ thay đổi nghiệp vụ.

## Modal

**Decision** Dùng footer của Modal cho confirm/cancel.
**Rationale** CatalogActions và ActivityOperations đang có confirm trong body. ActivityOperations có destructive action cần đối chiếu tone theo action hiện hành.
**Alternatives considered** Thay native dialog hoặc tạo Modal riêng sẽ tạo nhiều cơ chế focus và unsaved guard.

## Scope

**Decision** Gói dịch vụ ở /admin/service-packages, đúng catalogConfig.packages. Bao gồm các panel nhúng của trang chi tiết. Các màn standalone products, service-requests, registrations, assignments, Cruise Package và Fare Package templates được giữ ngoài lần triển khai.
**Rationale** Đây là ba nhóm màn user yêu cầu. Generic CatalogList/Form/Detail còn dùng cho products nên phải kiểm tra hồi quy consumer.
**Alternatives considered** Mở rộng toàn bộ workspace dịch vụ vượt quá yêu cầu hiện tại.

## Baseline kỹ thuật

Các lượt lint trước đã ghi lỗi react-hooks/refs tại ActivityCatalogEditor và CatalogFormPage. Khi triển khai phải chạy lại baseline, xác nhận còn tồn tại và xử lý nhỏ trong tệp sửa nếu cần để tệp đạt lint. Chưa chạy lại build/lint trong giai đoạn lập kế hoạch.
