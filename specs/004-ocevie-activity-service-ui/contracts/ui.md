# Hợp đồng UI OceVie

## Routes

US1 áp dụng list và detail. US2 áp dụng new/edit. US3 áp dụng các thao tác được nhúng trên detail.

| Nhóm | Routes |
|---|---|
| Hoạt động | /admin/activities, /admin/activities/new, /admin/activities/:activityId, /admin/activities/:activityId/edit |
| Dịch vụ | /admin/services, /admin/services/new, /admin/services/:id, /admin/services/:id/edit |
| Gói dịch vụ | /admin/service-packages, /admin/service-packages/new, /admin/service-packages/:id, /admin/service-packages/:id/edit |

## Components và style

- PageHeader, Panel, ListFilters, CollectionSummary và DataTable phục vụ list/detail theo composition hiện có.
- Field cung cấp label, helper/error, aria-invalid và aria-describedby. Errors summary dùng khi có nhiều lỗi theo primitive hiện hành.
- Button/ButtonLink theo đúng action/navigation. Mỗi nhóm có một primary, secondary cho quay lại/hủy và danger cho destructive.
- Modal/Drawer dùng Overlay với title, body và footer. Pass busy, data-overlay-dismiss cho nút hủy của form modal và giữ protectChanges theo ngữ cảnh.
- Canonical tokens nối qua src/styles/tokens.css. Control mặc định 44px, compact 36px có chủ đích, radius control 6px. Đọc computed style trước khi xác định CSS cần bỏ.
- Layout chỉ xuống cột trên màn hẹp khi nội dung cần. Keyboard focus thấy rõ, đọc lỗi được và giảm chuyển động theo preference.

## Bảo toàn luồng

Danh sách hoạt động có thể chứa catalog và operational view theo implementation hiện tại. Form định tuyến vào editor tương ứng. Giữ API client, method, payload, permissions, lifecycle và returnTo. Chuẩn hóa bảng quyền lợi và lịch dịch vụ ở detail theo dữ liệu hiện hành.

Ngoại lệ payload đã được người dùng duyệt ngày 2026-11-07: serializer service/package không gửi serviceCode/packageCode do DTO Backend không nhận. Không đổi product serializer. Phân biệt editor bằng catalogEntry === true; catalogEntry: false vẫn là hoạt động vận hành.

Phản hồi UI tiếp theo ngày 2026-11-07: nút Xem chi tiết trên list activities/services/products/service-packages mở Modal hiện có, tái sử dụng nội dung detail; direct URL và editor vẫn hoạt động. Giữ filter khi đóng modal, focus về trigger, Escape chỉ đóng modal đang ở trên cùng. Sidebar/landing ASM loại cruises theo yêu cầu; không đổi quyền đọc cruise dùng trong các form.

## Evidence

Mỗi ca nghiệm thu ghi route/record state, role/quyền, viewport/zoom, thao tác, kỳ vọng và kết quả. Ghi riêng lỗi baseline, lỗi UI và ca thiếu dữ liệu.
