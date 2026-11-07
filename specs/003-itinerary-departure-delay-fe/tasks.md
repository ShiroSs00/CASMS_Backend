---
description: "Checklist triển khai FE lịch trình và dời giờ khởi hành"
---

# Tasks — Lịch trình và dời giờ khởi hành

**Nguồn** [Đặc tả](spec.md), [kế hoạch](plan.md), [mô hình](data-model.md), [hợp đồng](contracts/web-admin.md), [đối chiếu](research.md) và [kiểm chứng](quickstart.md). Backend đối chiếu ở `7d13ce4`.

**Đường dẫn** Mọi đường dẫn `CASMS_WebAdmin/...` tính từ repo sibling `CASMS_Frontend`. Đường dẫn `specs/...` tính từ repo `CASMS_Backend`. Các tệp FE mới được ghi rõ bên dưới. Dùng toolchain và primitive OceVie hiện có.

## Phase 1 — Setup

- [X] T001 Kiểm tra thay đổi local và baseline typecheck theo `CASMS_WebAdmin/package.json`, đọc hướng dẫn UI tại `CASMS_Frontend/AGENTS.md`, và đối chiếu môi trường kiểm thử cùng bộ dữ liệu yêu cầu tại `specs/003-itinerary-departure-delay-fe/quickstart.md`. Bảo toàn thay đổi local và ghi rõ nếu chưa có môi trường chạy.

## Phase 2 — Nền tảng

- [X] T002 [P] Khai báo quyền GET lịch sử là CRUISE_READ và quyền POST preview/confirm là OPERATIONS_MANAGER kết hợp CRUISE_START cho đường dẫn số nhiều `/cruises/{id}/delays` và `/cruises/{id}/delays/preview` trong `CASMS_WebAdmin/src/shared/auth/api-permissions.json`. Đối chiếu cách registry đánh giá điều kiện ALL trong `CASMS_WebAdmin/src/shared/auth/permission-policy.ts`. Giữ quyền lifecycle và route cũ. (FR-001)
- [X] T003 [P] Tạo `CASMS_WebAdmin/src/features/cruises/model/departure-delay-contract.ts` với kiểu request và decoder Preview, Event, History từ hợp đồng. Áp dụng delayMinutes (số nguyên dương), reason (không rỗng, tối đa 1000 ký tự), category (khớp [A-Z][A-Z0-9_]{0,49}), requestId (UUID) và expectedRevision (số nguyên dương). Xử lý actualDeparture vắng mặt, before là snapshot JSON và after theo CruiseSchedulePreviewResponse. Dùng lại decoder lịch khi shape tương thích. (FR-003, FR-004, FR-005)
- [X] T004 Thêm GET lịch sử và POST preview/confirm vào `CASMS_WebAdmin/src/features/cruises/api/departure-api.ts` sau T002–T003. Dùng đường dẫn delay số nhiều, giữ path helper cũ cho các endpoint hiện có. Gửi UUID và revision trong body, chuyển lỗi ApiError kèm code/details cho bên gọi và hỗ trợ hủy yêu cầu đọc. (FR-003, FR-004, FR-005)

## Phase 3 — US1 · Điều phối và xem trước lịch chuyến

**Mục tiêu** Điều phối viên xem đúng lịch theo mẫu và giờ đã nhập. **Kiểm thử độc lập** Trên tài khoản CRUISE_MANAGER, đổi mẫu hoặc giờ rồi đối chiếu kết quả mới với server. Kiểm tra mẫu legacy và cảng khác múi giờ.

- [X] T005 [US1] Rà soát và bổ sung nếu thiếu việc làm mất hiệu lực preview cũ khi đổi sourceVersionId hoặc departureDatetime, bỏ qua phản hồi tới muộn và xử lý lỗi/tải lại tại `CASMS_WebAdmin/src/features/cruises/pages/CruiseFormPage.tsx`. Dùng preview server, giữ flow tạo và chỉnh hiện có. (FR-002)
- [X] T006 [US1] Kiểm tra và chỉnh nếu cần việc hiển thị mốc lịch theo múi giờ tàu/cảng trong `CASMS_WebAdmin/src/features/cruises/components/DepartureSchedulePreview.tsx`, đối chiếu decoder tại `CASMS_WebAdmin/src/features/cruises/model/departure-contract.ts`. Giữ dữ liệu mẫu legacy do server trả và kiểm tra ca khác múi giờ, qua ngày theo `specs/003-itinerary-departure-delay-fe/quickstart.md`. (FR-002, SC-003)
- [X] T007 [US1] Chạy hồi quy trình chỉnh mẫu và thao tác bắt đầu vận hành theo `specs/003-itinerary-departure-delay-fe/quickstart.md`, dùng `CASMS_WebAdmin/src/features/trip-planning/pages/TripSchedulePage.tsx` và `CASMS_WebAdmin/src/features/cruises/pages/CruiseDetailPage.tsx` làm điểm kiểm tra. (FR-001, SC-001)

## Phase 4 — US2 · Xem trước và xác nhận dời giờ

**Mục tiêu** Quản lý vận hành quyết định từ dữ liệu server. **Kiểm thử độc lập** Chuyến PUBLISHED chưa xuất phát có thể được preview và xác nhận khi đủ điều kiện. Ca bị chặn hoặc revision cũ giữ lịch hiện hành. Mất phản hồi có thể gửi lại đúng intent.

- [X] T008 [P] [US2] Tạo `CASMS_WebAdmin/src/features/cruises/model/useDepartureDelay.ts` quản lý draft, preview, countdown theo evaluatedAt/secondsUntilDeparture và intent gắn theo chuyến và người dùng. Khóa xác nhận mới khi đổi đầu vào, revision hoặc countdown về 0. Lưu nguyên intent theo tài khoản và chuyến trong sessionStorage trước khi gửi, qua đóng modal và reload cùng tab. Retry sau lỗi không rõ kết quả giữ nguyên cả payload, requestId và expectedRevision, kể cả khi revision đổi, countdown hết hoặc không có CRUISE_READ. Giữ intent chưa rõ kết quả để đối soát trước khi mở yêu cầu khác, chặn gửi trùng khi đang xử lý và tránh retry mutation tự động. (FR-003, FR-004)
- [X] T009 [US2] Tạo `CASMS_WebAdmin/src/features/cruises/components/DepartureDelay.tsx` dùng Field, Button, Modal, Loading và Notice cho nhập liệu, lịch trước/sau, cửa sổ cảng, blockers và xác nhận. Liên kết lỗi 400 với trường nếu server có chi tiết. Với STALE_CRUISE_REVISION, tải lại và preview lại. Với DELAY_REQUEST_REUSED, hiển thị trạng thái cần đối soát. Với CRUISE_DELAY_BLOCKED hoặc DEPARTURE_TIME_REACHED, vô hiệu xác nhận và hiển thị lý do phù hợp. Dẫn về luồng đổi lịch hiện có khi cần phân bổ lại ngày hoặc xử lý khung cố định. (FR-003, FR-004, FR-006)
- [X] T010 [US2] Gắn DepartureDelay vào khu Vận hành trong `CASMS_WebAdmin/src/features/cruises/pages/CruiseDetailPage.tsx` với điều kiện đồng thời role OPERATIONS_MANAGER và quyền CRUISE_START từ session. Kiểm tra điều kiện này tại điểm mở và điểm gửi. Dùng trạng thái chuyến để hướng dẫn mở preview và để server quyết định điều kiện chặn. Nối refresh chi tiết, lịch và Departure Gate sau confirm, giữ nguyên nút Start. (FR-001, FR-003, FR-005)
- [ ] T011 [US2] Thực hiện các ca phân quyền và lỗi tích hợp trong `specs/003-itinerary-departure-delay-fe/quickstart.md`, gồm CRUISE_MANAGER có CRUISE_START, OPS có/thiếu quyền, tài khoản nhiều vai trò, truy cập trực tiếp, preview bị chặn, revision cũ, mất phản hồi, reload cùng tab, retry khi đã hết countdown hoặc không có CRUISE_READ và hai lần delay nối tiếp. Dùng chuyến thử nghiệm. (SC-001, SC-002, SC-003)

## Phase 5 — US3 · Đọc lịch sử dời giờ

**Mục tiêu** Người đọc thấy các mốc hiện tại và sự kiện đã ghi nhận. **Kiểm thử độc lập** Mở một chuyến có delay từ trước với tài khoản chỉ có CRUISE_READ. Kiểm tra mốc ban đầu, mốc dự kiến, mốc thực tế nếu có, tổng phút, danh sách sự kiện và trạng thái rỗng/lỗi.

- [X] T012 [P] [US3] Tạo `CASMS_WebAdmin/src/features/cruises/components/DepartureDelayHistory.tsx` đọc lịch sử theo cruiseId và refresh key. Hiển thị mốc giờ đúng ngữ cảnh, lý do, nhóm và số phút từ server. Phân biệt chưa có sự kiện với lỗi tải và hỗ trợ tải lại. (FR-005, SC-002)
- [X] T013 [US3] Gắn DepartureDelayHistory trên chi tiết chuyến cho người có CRUISE_READ trong `CASMS_WebAdmin/src/features/cruises/pages/CruiseDetailPage.tsx`, tại phần đọc lịch hiện có. Nối refresh key sau confirm và đối soát requestId khi hỗ trợ phục hồi intent. Chạy sau T010 và T012 vì cùng sửa trang chi tiết. (FR-004, FR-005)
- [X] T014 [US3] Kiểm tra lịch sử ở chuyến chưa delay, chuyến đã delay và chuyến có giờ thực tế theo `specs/003-itinerary-departure-delay-fe/quickstart.md`. Xác nhận lịch sử đổi ngay sau confirm, đối soát theo requestId sau khi nối T013 và tài khoản thiếu CRUISE_READ không gửi GET lịch sử. (FR-005, SC-001, SC-002)

## Phase 6 — Kiểm chứng chung

- [ ] T015 Chạy typecheck, build, lint các tệp đổi theo `CASMS_WebAdmin/package.json` và chạy bộ kịch bản trong `specs/003-itinerary-departure-delay-fe/quickstart.md`. Ghi riêng baseline, kết quả runtime và ca chưa kiểm chứng. Kiểm tra diff chỉ gồm Web Admin và tài liệu feature. (FR-006, SC-001–SC-003)

## Thứ tự và song song

- T001 → T002 và T003 song song → T004 → US2 và US3. US1 có thể bắt đầu sau T001. T015 chạy sau khi hoàn tất cả ba story.
- US1 chạy T005 → T006 → T007. Phần form của US1 có thể chạy cùng T008 hoặc T012.
- US2 chạy T008 → T009 → T010 → T011. T008 và T012 có thể chạy cùng lúc sau T004.
- US3 chạy T012 → T013 → T014. T012 có thể chạy khi US2 đang xây dialog. T013 chờ T010 để tránh xung đột tại trang chi tiết. Tiêu chí đọc của US3 dùng chuyến có lịch sử sẵn nên có thể kiểm tra độc lập với thao tác tạo delay mới.

## Chiến lược triển khai

MVP gồm nền tảng và US1 để xác nhận vai trò điều phối viên và preview theo giờ nhập. Tiếp theo đưa US2 vào khu Vận hành, rồi nối US3 và kiểm chứng dữ liệu tải lại sau confirm. Dùng các kịch bản nghiệm thu có trong đặc tả và quickstart với toolchain hiện hành.


## Trạng thái kiểm chứng

Nghiệm thu local ngày 2026-11-07. Đã hoàn tất 13/15 task. Xem quickstart.md để biết bằng chứng, các giới hạn và môi trường.

T011 còn mở cho xác nhận delay mới qua UI trên chuyến đạt Departure Gate thật, cùng ma trận đầy đủ OPS thiếu quyền và tài khoản nhiều vai trò ở môi trường runtime. Retry UUID sau mất phản hồi/reload đã kiểm chứng bằng trình duyệt và Backend thật. Hai lần delay nối tiếp được kiểm chứng bằng integration test PostgreSQL dùng mock readiness và cửa sổ cảng.

T015 còn mở vì chưa đạt toàn bộ nghiệm thu runtime và bộ hồi quy materialization có lỗi fixture/backend. Build và lint các tệp FE đổi đã đạt.
