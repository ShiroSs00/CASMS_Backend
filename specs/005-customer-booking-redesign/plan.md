# Implementation Plan: Customer Booking Redesign

**Branch**: `codex/customer-booking-redesign-20261108` (Backend docs) | **Date**: 2026-11-08 | **Spec**: [spec.md](spec.md)

**Input**: Four product decisions recorded in spec: customer Web Booking only; redesign sequence and UI; search through booking reference; independent cabin list plus optional synchronized 3D.

## Summary

Thiết kế lại hành trình OceVie cho khách, từ discovery tới kết quả đã tiếp nhận đặt chỗ. Discovery nằm ngoài stepper; giao dịch gồm bốn bước: Người đi & phòng → Gói & cabin → Thông tin hành khách → Kiểm tra đặt chỗ. Cabin list hoạt động độc lập với mô hình; mở 3D theo yêu cầu và giữ cùng state/eligibility. Price/offer và payload tiếp tục theo từng passenger; không thay thành giá/phòng, không thay Backend hoặc thanh toán.

Giữ một BookingProvider và adapters thật. Tách presentation khỏi RealBookingCreatePage, đưa quote/unknown/lock về boundary sống qua route. Chỉ lập kế hoạch và hợp đồng ở lượt này, chưa đổi code hay tạo tasks.md.

## Technical Context

**Language/Version**: TypeScript ~6.0.2, React ^19.2.8, React Router ^8.4.0, Vite ^8.3.0 theo package hiện có; Node >=24 <25, npm 11.19.
**Primary Dependencies**: Booking Primitives/Overlay, PublicLayout/BookingSessionLayout, React Router, adapters/decoders quote và public cruise. Three.js 0.186 + explorer hiện có chỉ là tùy chọn; không thêm dependency.
**Storage**: In-memory BookingProvider; không schema/migration mới, không durable draft/PII/token storage. UI metadata không gửi Backend.
**Testing**: Toolchain hiện có: npm run typecheck/build, ESLint targeted và browser harness có sẵn nếu dùng được. Test hành vi có giá trị cho mapping/eligibility/quote/guards; runtime riêng với fixture phù hợp. Không thêm test library khi chưa cần.
**Target Platform**: Web Booking desktop/mobile, 1366x900 và 390x844, native browser zoom 200%, keyboard/reduced motion. Giữ guest và authenticated access theo contract hiện có.
**Project Type**: Customer web app với Backend hiện có; các thay đổi sản phẩm/source giới hạn ở CASMS_WebBooking.
**Performance Goals**: Không mount renderer hoặc request manifest/GLB do transaction trước khi Mở 3D; không POST quote/create chỉ vì route render. Giữ public list paging/filter thật, chặn gửi lặp bằng một intent/lock; không đặt SLA Backend chưa được đo.
**Constraints**: OceVie tokens đã nối runtime; giữ routes cũ và sanitize from, thêm party route. Giữ mọi quyền, trạng thái, giá USD, validation, nhóm/capacity, legacy/fare branching và idempotency. Không Admin/Mobile/Backend redesign, không payment processing mới.
**Scale/Scope**: Hai màn discovery, bốn bước giao dịch, kết quả reference và modal 3D; ma trận ít nhất 1 khách, 2 khách chung phòng, 2 nhóm, nhóm mixed-offer, fare-managed và legacy hợp lệ. Không tự đặt max người hay rule tuổi mới.
**Source snapshot**: CASMS_Frontend 80b4cbf trên feat/cruise-schedule-package-updates-20261106; Backend 5df105c. setup-plan trả feature label 005-customer-booking-redesign; tên Git branch thật độc lập như đầu tài liệu. Kiểm tra lại checkout/working diff khi implementation bắt đầu.

## Constitution Check

| Nguyên tắc | Trước nghiên cứu | Sau thiết kế |
|---|---|---|
| I — Scope/preserve work | PASS: user đã chốt scope và quyền đổi bước; docs mới ở feature 005 | PASS: code scope Web Booking, không commit/push; không ghi đè feature cũ |
| II — Contract/business truth | PASS: redesign không cấp quyền đổi nghiệp vụ | PASS: passenger payload, inventory, quote/readback, idempotency, guest và legacy giữ nguyên; UI group không là booking entity mới |
| III — OceVie | PASS: đọc AGENTS/DESIGN/tokens và primitive | PASS: dùng foundation/runtime mapping, riêng layout customer; kiểm tra responsive/a11y ghi trong quickstart |
| IV — Reuse/bounded changes | PASS: không dependency mới | PASS: compositions domain mới dùng existing Field/Button/Modal; không sửa viewer engine hay primitive thứ hai |
| V — Evidence | PASS: nghiên cứu chỉ là source inspection | PASS: guide phân biệt mock/runtime, ghi baseline/gap; chưa tuyên bố UI hoặc E2E hoàn tất |

Không có vi phạm cần ngoại lệ. Cổng implementation phải chặn khi cần đổi Backend/business rules hoặc public inventory thực tế không đủ để chứng minh ca phụ thuộc. Không mock thành công để đóng nghiệm thu.

## Project Structure

### Documentation (this feature)

- spec.md — yêu cầu và bốn câu trả lời đã chốt.
- checklists/requirements.md — spec quality 16/16; các Notes ban đầu giữ nguyên như lịch sử kiểm tra.
- clarification-report.md — coverage, thay đổi checklist và trạng thái làm rõ.
- research.md — decisions, source evidence và alternatives.
- data-model.md — canonical draft, UI projection và transaction state.
- contracts/ui-flow.md — routes, topology, states, accessibility và FR coverage.
- contracts/api-preservation.md — existing interfaces và invariants.
- quickstart.md — setup/validation/evidence guide.
- tasks.md — chưa tạo; đầu ra của speckit-tasks ở bước tiếp theo.

### Source Code (relative to CASMS_Frontend)

| Khu vực | Tệp hiện có / hướng thay đổi |
|---|---|
| Composition/routes | CASMS_WebBooking/src/app/router.tsx, CustomerShipPages.tsx, layouts/BookingSessionLayout.tsx; thêm party route, giữ lazy explorer |
| Discovery | features/booking/pages/DiscoveryPages.tsx, components/LiveCruiseCatalog.tsx, PublicCruiseInformation.tsx, PublicCruisePackages.tsx; chỉnh hierarchy/filter/copy, không giả capability |
| Session/controller | components/BookingProvider.tsx, model/live-booking-context.ts; giữ một draft, quản lý quote/unknown/lock ở boundary |
| Navigation/validation | model/live-booking-navigation.ts, booking-progress.ts; mapping 4 stage và guards, không đổi chữ ký step 1/2/3 của business validator một cách máy móc |
| Presentation | pages/FlowPages.tsx, RealBookingCreatePage.tsx; rút step composition và success view, bỏ review-modal lặp |
| Proposed domain compositions | BookingPartyStep, BookingCabinChoices, BookingJourneyProgress trong features/booking/components; tên/chia file có thể điều chỉnh khi tasks, không tạo primitive |
| Cabin/3D | CruisePackageCabinSelection.tsx, BookingShipModel.tsx, BookingModelIntegration.tsx; list independent, shared eligibility/handler, model-open state |
| Passenger/review | LiveBookingFields.tsx, BookingExperience.tsx, RealBookingUI.tsx, BookingRecord trong RealBookingPages.tsx; tái dùng field/result thật |
| Styles/foundation | Booking.module.css, LiveBooking.module.css và scoped styles cần thiết; giữ shared/ui/Primitives.tsx, Overlay.tsx và canonical tokens |
| Contract adapters | api/booking-api.ts, real-booking-api.ts, booking-quote-api.ts, quote-contract.ts, domain/cruise-package/* — giữ endpoint/payload/decoders; chỉ đổi ownership khi cần và có regression |

Đường dẫn features/... và model/... trong bảng tính từ CASMS_WebBooking/src. Không dùng demo booking-types.ts hoặc stepper fixture làm nguồn live contract.

## Phase 0 — Research outcome

Hai research agent đã kiểm tra độc lập contracts/state và UX/routes/components, chỉ đọc source. Tổng hợp tại research.md. Không còn unknown kỹ thuật cần người dùng quyết định. Các vấn đề dữ liệu/runtime là điều kiện kiểm chứng, không là lý do bịa endpoint/giá/filter.

## Phase 1 — Design and sequencing

1. **Foundation/state**: baseline diff/toolchain; xác định consumer trước khi tách controller; metadata ổn định cho người/nhóm, không duplicate draft. Hoist quote/unknown/lock; bất kỳ busy/unknown nào cũng không được tự mất qua route.
2. **Journey**: party groups + four-stage routes, progressive guard và backward navigation. Stage 1 tạo/điều chỉnh slot từng người, không bulk cấp phát mảng vô hạn hoặc tự sinh nhóm qua sức chứa. Stage 2 giữ validator allocation; stage 3 giữ validator identities/payment; stage 4 chạy đầy đủ trước quote/create. Các enum bước nghiệp vụ cũ không được hiểu thành số bước UI mới.
3. **Selection**: cùng selector tính candidates cho list và 3D, lọc từng offer và giao tập khi thao tác theo nhóm. Giữ branch legacy; lựa chọn CATEGORY_HOLD không dùng cabinId. Thay nhóm/offer phải cập nhật đúng người và vô hiệu quote liên quan, không tự đồng nhất offer.
4. **Presentation**: discovery đọc được; summary thật; một heading/progress, không /03 cố định. Loại hero/ảnh/subtext lặp khỏi giao dịch, label ngắn và đủ nghĩa. Không thay PublicLayout toàn bộ hoặc trang home ngoài consumer cần thiết.
5. **Review/result**: Kiểm tra giá → quote readback/expiry → Xác nhận đặt chỗ; một primary theo state. Reference receipt trong /booking/review; dùng renderer/result hiện có ở chế độ phù hợp, không kéo cả workspace sau đặt chỗ vào scope.
6. **Validation**: theo quickstart, từ focused tests đến runtime/data readback và regression ngoài flow. Thiếu fixture hoặc runtime mismatch phải ghi rõ, giữ gate liên quan mở.

## Complexity Tracking

Không thêm project/library/store hoặc schema. Một route party và các domain composition là chi phí tối thiểu để phân nhóm trước allocation. Ownership quote được nâng lên Provider để bảo toàn guard khi tách route, không là một kho dữ liệu mới.

## Status

Phase 0 và Phase 1 design hoàn tất; spec quality 16/16. Các artifact là kế hoạch, chưa là bằng chứng implementation. Không tạo task triển khai, commit, push hoặc đổi source FE/BE ở lượt này. Bước tiếp theo: speckit-tasks, sau đó speckit-analyze.


## Constitution gate — implementation review 2026-11-08

- I Scope/local work: PASS. Chỉ Web Booking và docs feature; không commit/push, không đổi checkout, giữ untracked local files.
- II Contracts/business: PASS theo diff và fixture regression; adapters/serializers/domain/permissions không sửa. Runtime Backend gate OPEN vì DB test thiếu catalog_entry, không thay schema hoặc nghiệp vụ để vượt.
- III OceVie/accessibility: PASS trên browser fixture desktop/mobile/native 200%, control/token metrics, keyboard/modal và review ảnh. Model published thật vẫn thuộc T024 OPEN.
- IV Reuse/bounded change: PASS. Tái dùng Primitives/Overlay/renderer; component mới chỉ là domain composition; không dependency mới hoặc thay shared primitive.
- V Evidence: OPEN cho nghiệm thu toàn feature. Phân biệt 17 unit/response tests, browser/renderer fixtures, visual và Backend startup failure; T012/T024/T033 không đánh dấu đạt; T007 đã bổ sung smoke checkpoint ba bước dựng lại riêng, không backdate thời điểm kiểm tra. Chi tiết tại quickstart.md.
