---
description: "Triển khai chuẩn hóa UI hoạt động, dịch vụ và gói dịch vụ theo OceVie"
---

# Tasks — OceVie Activity, Service and Package UI

**Input** [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/ui.md](contracts/ui.md), [quickstart.md](quickstart.md).

**Đường dẫn** `CASMS_WebAdmin/...` và `design-systems/...` tính từ repo sibling CASMS_Frontend. `specs/...` tính từ CASMS_Backend. Ghi evidence vào phần kết quả của quickstart.md. Dùng toolchain và dependency hiện có.

## Phase 1 — Setup

- [X] T001 Đọc `CASMS_WebAdmin/AGENTS.md`, `design-systems/ocevie/DESIGN.md` và `design-systems/ocevie/tokens.css`, kiểm tra working diff rồi ghi baseline typecheck/build/lint theo `CASMS_WebAdmin/package.json` vào `specs/004-ocevie-activity-service-ui/quickstart.md`. Ghi riêng lỗi có sẵn và bảo toàn local changes. (FR-001, FR-005, SC-004)
- [X] T002 Lập ma trận nghiệm thu route/state/role và kiểm tra khả năng truy cập bằng tài khoản ACTIVITY_SERVICE_MANAGER cùng tài khoản đọc theo quyền hiện hành trong `CASMS_WebAdmin/src/shared/auth/work-policy.ts` và `CASMS_WebAdmin/src/shared/ui/admin-v3/admin-navigation.ts`. Ghi account fixture, dữ liệu thử nghiệm cô lập và các ca thiếu dữ liệu vào `specs/004-ocevie-activity-service-ui/quickstart.md`. Bao gồm catalog/operational activity, các trạng thái cho phép thao tác, quyền lợi kế thừa và service scheduling. (FR-005, SC-001–SC-003)

## Phase 2 — Nền tảng dùng chung

- [X] T003 Đo computed height/radius/padding ở các form và filter đại diện, đối chiếu `CASMS_WebAdmin/src/features/activities/components/Activities.module.css`, `CASMS_WebAdmin/src/features/service-packages/components/Catalog.module.css` với `CASMS_WebAdmin/src/shared/ui/AdminUI.module.css` và `CASMS_WebAdmin/src/shared/ui/Experience.module.css`. Ghi rõ rule thắng cascade trước khi sửa vào `specs/004-ocevie-activity-service-ui/quickstart.md`. (FR-001, FR-006)
- [X] T004 Sau T003, đổi các rule feature xung đột sang token đã ánh xạ trong `CASMS_WebAdmin/src/features/activities/components/Activities.module.css` và `CASMS_WebAdmin/src/features/service-packages/components/Catalog.module.css`. Dùng control mặc định 44px, compact 36px khi có chủ đích và radius control 6px. Khi thiếu semantic token, bổ sung tại `design-systems/ocevie/tokens.css` và mapping `CASMS_WebAdmin/src/styles/tokens.css`, kiểm tra mapping Booking nếu thêm giá trị dùng chung. Giữ một hệ primitive, tránh global override. (FR-001, FR-006, FR-007)

## Phase 3 — US1 · Tra cứu và đọc thông tin (P1, MVP)

**Mục tiêu** Ba nhóm màn có cùng ngôn ngữ header, filter, bảng và action.
**Kiểm thử độc lập** Dùng bản ghi có sẵn, kiểm tra list/detail của ba nhóm ở trạng thái loading, rỗng, lỗi và dữ liệu dài. Chứng minh tìm/lọc và quyền xem hiện hành hoạt động.

- [X] T005 [P] [US1] Chuẩn hóa filter và action list trong `CASMS_WebAdmin/src/features/activities/pages/ActivityListPage.tsx` bằng Field, ListFilters và primitive phù hợp. Giữ hai view catalog/operational, phạm vi search, status, paging và semantics aria-pressed của toggle nếu giữ dạng toggle. (FR-001, FR-002, FR-005)
- [X] T006 [P] [US1] Rà soát và bổ sung phần còn thiếu ở `CASMS_WebAdmin/src/features/service-packages/pages/CatalogListPage.tsx`, giữ ListFilters/CollectionSummary/DataTable hiện có. Căn action, trạng thái và nhãn filter cho services/packages. Kiểm tra nhánh products trước khi thay đổi component generic. (FR-001, FR-002, FR-007)
- [X] T007 [US1] Chuẩn hóa header, facts, status và nhóm action ở `CASMS_WebAdmin/src/features/activities/pages/ActivityDetailPage.tsx` và `CASMS_WebAdmin/src/features/service-packages/pages/CatalogDetailPage.tsx`. Dùng lại Panel/RecordFacts/Badge hiện có, giữ dữ liệu lịch sử, các panel nhúng và khả năng cuộn vùng bảng. Không sửa lifecycle hoặc nhãn nghiệp vụ đã xác định. (FR-002, FR-005, FR-007)
- [X] T008 [US1] Nghiệm thu ba list và ba detail theo `specs/004-ocevie-activity-service-ui/quickstart.md`. Kiểm tra tìm/lọc/reset, tải lại, empty/error, tên dài và tài khoản đọc. Đối chiếu payload/query, thứ tự dữ liệu và paging trước/sau. (SC-001, FR-002, FR-005)

## Phase 4 — US2 · Tạo và chỉnh sửa (P1)

**Mục tiêu** Form có nhóm thông tin và validation cạnh trường, bảo vệ bản nhập.
**Kiểm thử độc lập** Kiểm tra tạo/chỉnh catalog activity, chỉnh operational activity, tạo/chỉnh service và package. Một lỗi hiện hành ở mỗi loại form có thông báo tại trường, sửa đúng và lưu/readback thành công.

- [X] T009 [P] [US2] Tách lỗi tên/sức chứa/giá hiện đang gộp trong `CASMS_WebAdmin/src/features/activities/pages/ActivityCatalogEditor.tsx` thành lỗi Field theo đúng các ràng buộc đang có, thêm FormErrorSummary khi phù hợp và nhóm action lưu/quay lại. Giữ draft sau lỗi, readback và khóa khi kết quả chưa rõ. Nếu baseline ref lint còn tồn tại, thay cách lưu baseline bằng state phù hợp để đạt lint mà giữ unsaved semantics. (FR-003, FR-005)
- [X] T010 [P] [US2] Chuẩn hóa nhóm trường, hiển thị validation và busy trong `CASMS_WebAdmin/src/features/activities/pages/ActivityFormPage.tsx`. Tái sử dụng `CASMS_WebAdmin/src/features/activities/model/activity.ts`, giữ lựa chọn catalog/operational editor, giờ tàu, venue, route cruiseId/returnTo và các giới hạn hiện có. (FR-003, FR-005)
- [X] T011 [US2] Chuẩn hóa form service/package trong `CASMS_WebAdmin/src/features/service-packages/pages/CatalogFormPage.tsx`, tái sử dụng Field/FormSection/FormErrorSummary và validation tại `CASMS_WebAdmin/src/features/service-packages/model/catalog-editor.ts`. Giữ giá/tiền tệ, usage policy, timezone, scheduling, inherited values, dirty guard và unknown-outcome lock hiện có. Xử lý baseline ref lint nhỏ trong tệp sửa nếu còn. Kiểm tra nhánh products do dùng chung editor. (FR-003, FR-005, FR-007)
- [X] T012 [US2] Kiểm chứng form theo `specs/004-ocevie-activity-service-ui/quickstart.md`, gồm lỗi tại trường/ARIA/focus, giữ draft sau lỗi server, hủy bản nhập đổi, direct URL, cruiseId/returnTo, lost permission và kết quả mutation chưa rõ. Ghi request trước/sau và đối chiếu dữ liệu sau lưu trên Backend thử nghiệm. (SC-001, SC-002, FR-003, FR-005)

## Phase 5 — US3 · Xác nhận thao tác (P1)

**Mục tiêu** Modal trạng thái/xóa/quyền lợi và các form nhúng dùng anatomy và hành vi OceVie.
**Kiểm thử độc lập** Với bản ghi ở trạng thái cho phép thao tác, kiểm tra modal bằng keyboard, hủy form đổi, server error và busy. Giao diện chỉ hiển thị/gửi action theo quyền/lifecycle hiện hành.

- [X] T013 [P] [US3] Đưa action của modal hoạt động vào footer trong `CASMS_WebAdmin/src/features/activities/components/ActivityOperations.tsx`, dùng secondary cho hủy và danger cho destructive theo action hiện có. Giữ lock, eligibility và quyền tại điểm gửi. Rà panel nhúng `CASMS_WebAdmin/src/features/activities/components/RegistrationPanel.tsx` để chỉ chỉnh presentation nếu lệch contract, giữ nguyên nghiệp vụ đăng ký. (FR-001, FR-004, FR-005)
- [X] T014 [P] [US3] Chuẩn hóa Modal footer và nhóm action trong `CASMS_WebAdmin/src/features/service-packages/components/CatalogActions.tsx`. Giữ package DRAFT-only edit/delete, catalogPermission và các giới hạn action hiện hành. Pass busy và bảo đảm nút hủy hoạt động đúng. (FR-004, FR-005, FR-007)
- [X] T015 [US3] Chuẩn hóa form/confirmation modal và nhóm thao tác nhúng tại `CASMS_WebAdmin/src/features/service-packages/components/PackageBenefitsEditor.tsx` và `CASMS_WebAdmin/src/features/service-packages/components/ServiceSchedules.tsx`. Dùng footer của Overlay, data-overlay-dismiss khi có draft và protectChanges phù hợp. Giữ quyền PACKAGE_COMPONENT_MANAGE, quyền lợi kế thừa, mapping đối tượng, scheduling và quy tắc hiện hành. (FR-003, FR-004, FR-005)
- [X] T016 [US3] Chạy ca modal trạng thái, xóa, quyền lợi và lịch dịch vụ trong `specs/004-ocevie-activity-service-ui/quickstart.md`. Kiểm tra Tab/Shift+Tab, Escape, focus return, hủy draft, busy, lỗi server và chặn click lặp. Đối chiếu request/action theo role và lifecycle. (SC-003, FR-004, FR-005)

## Phase 6 — Kiểm chứng chung

- [X] T017 Kiểm tra ở 1366x900, 390x844 và zoom 200% các list/detail/form thuộc `specs/004-ocevie-activity-service-ui/contracts/ui.md`, cùng một modal đại diện của từng nhóm và mọi modal có nội dung dài. Đo computed style và kiểm tra keyboard/focus/ARIA, reduced motion, control 44px hoặc compact 36px, radius 6px và cuộn bảng không làm tràn trang. Ghi evidence vào `specs/004-ocevie-activity-service-ui/quickstart.md`. (FR-006, SC-001, SC-003)
- [X] T018 Kiểm tra hồi quy list/detail/form products và một list/detail service-requests đang dùng chung `CASMS_WebAdmin/src/features/service-packages/pages/CatalogListPage.tsx`, `CASMS_WebAdmin/src/features/service-packages/pages/CatalogDetailPage.tsx`, `CASMS_WebAdmin/src/features/service-packages/pages/CatalogFormPage.tsx` và `CASMS_WebAdmin/src/features/service-packages/components/Catalog.module.css`. Kiểm tra thêm consumer khi diff shared mở rộng, ghi kết quả vào `specs/004-ocevie-activity-service-ui/quickstart.md`. (FR-007, SC-004)
- [X] T019 Chạy typecheck/build và lint các tệp đổi theo `CASMS_WebAdmin/package.json`, rà diff API/permissions/route để bảo đảm FR-005, kiểm tra từng SC-001–SC-004 có evidence đạt trong `specs/004-ocevie-activity-service-ui/quickstart.md`. Giữ task nghiệm thu chưa đủ bằng chứng ở trạng thái mở và ghi rõ nguyên nhân. (FR-005, SC-004)

## Dependencies và thứ tự thực hiện

T001 → T002 → T003 → T004 là nền tảng. Sau đó mỗi story có thể được triển khai và kiểm chứng trên dữ liệu có sẵn mà không cần story khác tạo dữ liệu.

- US1 chạy T005 và T006 độc lập, rồi T007 → T008.
- US2 chạy T009 và T010 độc lập, rồi T011 → T012. Các tệp editor khác tệp list/detail của US1 nên có thể làm song song sau T004.
- US3 chạy T013 và T014 độc lập, rồi T015 → T016. Các component này khác các editor của US2, nhưng mọi sửa shared primitive/CSS phải hoàn tất hoặc được phối hợp trước.
- T017 → T018 → T019 sau các story. Không song song mutation trên cùng record thử nghiệm. Khi thiếu dữ liệu, tiếp tục phần độc lập và giữ các ca runtime chưa chạy ở trạng thái mở.

## Ví dụ song song

US1 có thể xử lý ActivityListPage ở T005 cùng CatalogListPage ở T006.
US2 có thể xử lý ActivityCatalogEditor ở T009 cùng ActivityFormPage ở T010.
US3 có thể xử lý ActivityOperations ở T013 cùng CatalogActions ở T014.
Các ví dụ mô tả phụ thuộc tệp, không yêu cầu tạo agent hoặc chạy mutation đồng thời.

## Chiến lược triển khai

MVP gồm nền tảng và US1. Kiểm chứng đọc/tìm/lọc trước, sau đó triển khai US2 và US3 từng nhóm màn. Dùng primitive có sẵn, chỉnh phần thiếu và giữ những phần đã đạt. Kết thúc bằng evidence kỹ thuật, trình duyệt và hồi quy consumer ngoài scope được nêu ở trên.


## Ngoại lệ được duyệt trong implementation

- [X] T020 Theo đồng ý của người dùng ngày 2026-11-07, bỏ serviceCode/packageCode khỏi serializer service/package trong CASMS_WebAdmin/src/features/service-packages/model/catalog-editor.ts để khớp DTO Backend; kiểm tra PUT và readback thật của cả hai. Giữ product serializer, endpoint/method/permissions. Cập nhật spec.md, plan.md, contracts/ui.md và quickstart.md.

## Cổng nghiệm thu đã đóng — 2026-11-07

T002/T008: account shore_manager với quyền đọc thực trên Backend 8082/DB bản sao riêng đã được kiểm tra; xem activities/services được, ghi bị Backend trả 403, package admin/DRAFT detail bị chặn theo policy hiện hành. Ma trận ASM ba nhóm, loading/error/empty/retry, tên 200 ký tự, query và thứ tự dữ liệu đạt. Quyền lợi kế thừa dùng response fixture rõ ràng. Fixture permission đã phục hồi nguyên trạng.
T017: native Edge zoom 200% đã xác nhận bằng metrics và ảnh; 14 route, modal ba nhóm, footer nội dung dài, keyboard/focus đạt. Desktop/mobile và reduced motion đã có evidence.
T019: typecheck/build/lint, diff API/permissions/route và SC-001–SC-004 đạt; không còn task mở. Chi tiết loại bằng chứng và giới hạn của response fixture nằm trong quickstart.md.
