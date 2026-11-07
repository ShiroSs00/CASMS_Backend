# Implementation Plan — OceVie Activity, Service and Package UI

**Feature** 004-ocevie-activity-service-ui. **Date** 2026-11-07.
**Git checkouts** Backend main, Frontend feat/cruise-schedule-package-updates-20261106.
**Input** [spec.md](spec.md).

## Summary

Chuẩn hóa UI hiện có của ACTIVITY_SERVICE_MANAGER theo OceVie và cách dùng primitive trong Quản lý lịch trình. Ba nhóm route là activities, services và service-packages. Giữ các bề mặt nghiệp vụ hiện có và bổ sung trạng thái giao diện còn thiếu theo contracts/ui.md.

## Technical Context

- Language/version — TypeScript 6, React 19, React Router, Vite, CSS Modules theo package.json của Web Admin.
- Primary dependencies — AdminUI, Overlay, AdminShell, Field, Button/ButtonLink, FormSection, FormErrorSummary, Notice, ListFilters, CollectionSummary và DataTable hiện có.
- Storage — Draft và state giao diện hiện hành. Giữ cơ chế unsaved guard và mutation recovery đang được dùng.
- Testing — npm run typecheck, npm run build, ESLint từng tệp đổi. Kiểm chứng browser với dữ liệu local theo quickstart.md.
- Target — Web Admin desktop/mobile, keyboard và zoom 200%.
- Performance — Không thêm dependency. Khi lưu quyền lợi, chỉ refresh component và panel quyền lợi liên quan thay vì tháo toàn bộ chi tiết gói; giữ focus vào trigger sau khi tải lại.
- Scale — Ba nhóm danh sách/chi tiết/tạo/sửa cùng các component được nhúng trên trang chi tiết.
- Constraints — Giữ payload, quyền, route/returnTo, lifecycle và toàn bộ validation nghiệp vụ. Mọi thay đổi shared phải có kiểm tra hồi quy.

## Constitution Check

Đã đối chiếu Constitution v1.0.0 trước triển khai. Nguyên tắc I được đáp ứng bằng scope ba nhóm màn và bảo toàn working diff. Nguyên tắc II giữ API, quyền, validation và lifecycle. Nguyên tắc III dùng OceVie và kiểm chứng UI. Nguyên tắc IV tái sử dụng AdminUI/Overlay và kiểm tra generic consumer. Nguyên tắc V yêu cầu evidence cho task và giữ mở ca chưa đủ dữ liệu. Không có ngoại lệ cần duyệt. Đối chiếu lại cùng năm nguyên tắc khi kết thúc implementation.

Kiểm tra sau triển khai: người dùng đã duyệt ngoại lệ sửa serializer service/package để khớp DTO Backend (spec FR-005). Backend, permissions và API endpoints không sửa. Các thay đổi khác của working tree được giữ nguyên. Control dùng mapping canonical có sẵn, không thêm primitive hoặc token. Các task nghiệm thu thiếu bằng chứng được giữ mở theo nguyên tắc V; xem quickstart.md.

Nghiệm thu bổ sung ngày 2026-11-07 đã đóng đủ T002/T008/T017/T019: account đọc trên Backend/DB bản sao riêng, native Edge zoom 200%, ma trận đọc và kiểm tra kỹ thuật đạt. Fixture permission đã phục hồi khớp bản gốc, Backend phụ đã dừng. 20/20 task hoàn tất; không còn cổng bằng chứng mở. Phân biệt các ca response fixture và dữ liệu thật tại quickstart.md.

## Project Structure

Tài liệu ở specs/004-ocevie-activity-service-ui gồm spec.md, plan.md, research.md, data-model.md, contracts/ui.md và quickstart.md. tasks.md thuộc bước speckit-tasks tiếp theo.

Nguồn ở repo sibling CASMS_Frontend/CASMS_WebAdmin/src.

| Khu vực | Entry points hiện có |
|---|---|
| Hoạt động | features/activities/pages/ActivityListPage.tsx, ActivityDetailPage.tsx, ActivityFormPage.tsx, ActivityCatalogEditor.tsx |
| Component hoạt động | features/activities/components/ActivityUI.tsx, ActivityOperations.tsx, RegistrationPanel.tsx, Activities.module.css |
| Danh mục dịch vụ/gói | features/service-packages/pages/CatalogListPage.tsx, CatalogDetailPage.tsx, CatalogFormPage.tsx |
| Component dịch vụ/gói | features/service-packages/components/CatalogUI.tsx, CatalogActions.tsx, ServiceSchedules.tsx, PackageBenefitsEditor.tsx, Catalog.module.css |
| Dùng chung | shared/ui/AdminUI.tsx, Overlay.tsx, Overlay.module.css, Experience.module.css, ListControls.tsx và shared/ui/index.ts |
| Token | CASMS_WebAdmin/src/styles/tokens.css và design-systems/ocevie/tokens.css tính từ repo Frontend |

## Phase 0 — Research

Xem research.md. Dùng incumbent OceVie, từng bước chỉnh CSS feature và composition của primitive. CSS 42px/8px tồn tại trong feature nhưng phải kiểm tra computed style do cascade có thể đã chuẩn hóa control. Không tạo một bộ component mới.

## Phase 1 — Design

1. Baseline các màn trong quickstart, ghi computed size/radius, trạng thái filter, action và modal.
2. US1 chuẩn hóa list/detail qua primitive hiện có. Chuyển filter hoạt động sang Field/ListFilters nếu chưa dùng. Chỉ chỉnh CSS nằm trong scope hoặc kiểm tra hồi quy các consumer.
3. US2 phân nhóm form, Field errors, summary và trạng thái busy. ActivityFormPage chọn editor theo dữ liệu catalog/operational nên cần nghiệm thu cả hai nhánh. Tái sử dụng validators đang có.
4. US3 đưa nút xác nhận/hủy vào footer Modal, dùng danger đúng thao tác destructive, bảo vệ form modal chưa lưu và giữ phục hồi lỗi hiện tại. Kiểm tra RegistrationPanel, ServiceSchedules, PackageBenefitsEditor trong ngữ cảnh được nhúng.
5. Kiểm chứng kỹ thuật và browser theo quickstart. Chỉ đánh dấu đạt runtime khi có tài khoản/dữ liệu tương ứng.

## Complexity Tracking

Không đề xuất abstraction, thư viện hoặc package mới. Phần generic catalog tiếp tục phục vụ products, vì vậy điều chỉnh theo variant hoặc kiểm tra consumer trước khi đổi dùng chung.
