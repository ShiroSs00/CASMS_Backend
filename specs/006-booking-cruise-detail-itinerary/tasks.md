# Tasks 006 — Chi tiết chuyến và xem lịch trình

Path nguồn tính từ repo CASMS_Frontend. Path `specs/006-booking-cruise-detail-itinerary/` tính từ CASMS_Backend. Tất cả task là công việc triển khai hoặc nghiệm thu dự kiến.

## Phase 1 — Setup

- [X] T001 Rà baseline 005, checkout và runtime token tại `CASMS_WebBooking/src/styles/tokens.css`, đọc root/app AGENTS và OceVie DESIGN/tokens, ghi build/lint/typecheck baseline và cổng Backend tại `specs/006-booking-cruise-detail-itinerary/quickstart.md`. FR-007, SC-005.

## Phase 2 — Foundation

- [X] T002 Xác lập owner đọc theo cruiseId trong `CASMS_WebBooking/src/features/booking/components/LiveCruiseCatalog.tsx` và `CASMS_WebBooking/src/features/booking/components/PublicCruiseInformation.tsx`. Cruise ID là UUID trùng route, bỏ kết quả cũ khi đổi chuyến. Dùng đúng một query itinerary, dùng lại query public display. Giữ adapter/decoder và retry. FR-004, FR-008, SC-002.
- [X] T003 Bổ sung fixture có tên trong `CASMS_WebBooking/tests/helpers/booking-fixture.mjs` cho detail tải chậm, nhiều chuyến/cảng/múi giờ, lỗi quyền công khai, nhóm offer và lỗi giao dịch. Giữ fixture mặc định 005 và thu thập bộ đếm request. FR-004, FR-005, FR-008, SC-001, SC-002, SC-003.

## Phase 3 — US1 Tìm và hiểu chuyến (P1)

**Kiểm chứng độc lập** Từ danh mục có query, hai hành động mở đúng chuyến và anchor hiện hành. Direct URL, reload và back/forward giữ đúng ngữ cảnh.

- [X] T004 [US1] Tạo ca browser cho hai link, from an toàn, hash sau khi detail tải chậm, reload, back/forward và các anchor cũ tại `CASMS_WebBooking/tests/booking-cruise-detail/browser-detail.mjs`. FR-002, SC-001.
- [X] T005 [US1] Chỉnh card và tổng quan trong `CASMS_WebBooking/src/features/booking/components/LiveCruiseCatalog.tsx`, dùng UI sẵn có và `CASMS_WebBooking/src/features/booking/components/CruiseDetail.module.css` mới. Một H1, facts, ảnh có nguồn, link chi tiết và link lịch trình. FR-001, FR-007.
- [X] T006 [US1] Thực hiện đích URL/allowlist và focus/scroll theo navigation key và DOM mount tại `CASMS_WebBooking/src/features/booking/model/live-booking-navigation.ts` và `CASMS_WebBooking/src/features/booking/components/LiveCruiseCatalog.tsx`. Giữ from đã lọc, ba anchor cũ, ScrollRestoration và focus dưới sticky header, không cuộn lại khi retry. FR-002, FR-007, SC-001.

## Phase 4 — US2 Đọc lịch theo ngày (P1)

**Kiểm chứng độc lập** Mở thẳng `#cruise-extras`, đọc lịch nhiều ngày và lỗi/retry mà không bắt đầu đặt.

- [X] T007 [US2] Thêm ca browser ngày lệch múi giờ, nhiều cảng, nhiều điểm, thiếu tên, null/0 duration, ngày rỗng, lịch rỗng, lỗi và retry trong `CASMS_WebBooking/tests/booking-cruise-detail/browser-detail.mjs`. FR-003, FR-004, SC-002.
- [X] T008 [US2] Dựng timeline ngữ nghĩa trong `CASMS_WebBooking/src/features/booking/components/PublicCruiseInformation.tsx` và `CASMS_WebBooking/src/features/booking/components/CruiseDetail.module.css`. `durationDays`/`durationNights` là số nguyên không âm hoặc null. 0 là giá trị hợp lệ. `dayNumber` là số nguyên dương. `date` là ngày lịch YYYY-MM-DD hợp lệ theo tàu. `shipTimeZoneId` là Asia/Ho_Chi_Minh. `sequence` là số nguyên dương. `portName`/`countryCode` tùy chọn. `arrival`/`departure` là instant hợp lệ, format với `portTimeZoneId` hợp lệ. `destinationName` tùy chọn. FR-003, FR-007, SC-002.
- [X] T009 [US2] Hiển thị loading, rỗng, lỗi cục bộ và lịch chưa sẵn sàng qua primitive có sẵn trong `CASMS_WebBooking/src/features/booking/components/PublicCruiseInformation.tsx`. Tích hợp tín hiệu mất công khai ở `CASMS_WebBooking/src/features/booking/components/LiveCruiseCatalog.tsx`, gỡ dữ liệu của chuyến và hiện đường về, ưu tiên tín hiệu public hợp lệ hơn CTA cũ. FR-004, FR-005, FR-008, SC-002.

## Phase 5 — US3 Tàu, hạng cabin và đi đặt (P1)

**Kiểm chứng độc lập** Mở chi tiết trực tiếp và kiểm tra tàu, hai offer cùng hạng, quyền lợi, CTA hợp lệ và CTA bị chặn với itinerary/gói tải độc lập.

- [X] T010 [US3] Thêm ca browser cho ship đầy đủ/thiếu, offer cùng hạng, giá/quyền lợi, publicDisplayEligible, transactionReady false, lỗi gói và URL đặt chỗ trong `CASMS_WebBooking/tests/booking-cruise-detail/browser-detail.mjs`. FR-005, FR-006, FR-009, SC-003.
- [X] T011 [US3] Thêm “Tàu của chuyến” tại `CASMS_WebBooking/src/features/booking/components/LiveCruiseCatalog.tsx`. Tên và shipId tùy chọn. Chỉ hiện link đội tàu khi có cả tên và ID tàu. Giữ asset địa phương có nguồn và link `/ships`. FR-009, SC-003.
- [X] T012 [US3] Tái bố cục `CASMS_WebBooking/src/features/booking/components/PublicCruisePackages.tsx` bằng `cabinTypeName` từ `CASMS_WebBooking/src/features/booking/model/booking-presentation.ts` và CSS module detail. Nhóm theo `cabinType` thuộc INTERIOR, OCEAN_VIEW, BALCONY, SUITE. Giữ từng offerId, tên gói, logicalCategory, giá USD/người và các quyền lợi, giới hạn, đăng ký riêng đã decode. FR-006, FR-007, SC-003.
- [X] T013 [US3] Đặt lại khối sale/CTA trong `CASMS_WebBooking/src/features/booking/components/PublicCruiseInformation.tsx` và `CASMS_WebBooking/src/features/booking/components/LiveCruiseCatalog.tsx`. Giữ `sellable && transactionReady` từ adapter, fallback hiện hành và `/booking/party` với đúng ID/from. FR-005, FR-002, SC-003.

## Phase 6 — Polish & nghiệm thu

- [X] T014 Kiểm tra desktop, mobile, zoom 200%, keyboard, focus sau anchor và reduced motion. Ghi bằng chứng tại `specs/006-booking-cruise-detail-itinerary/quickstart.md`, sửa CSS cục bộ trong `CASMS_WebBooking/src/features/booking/components/CruiseDetail.module.css` nếu cần. FR-007, SC-004.
- [X] T015 Chạy ca browser mới, smoke 005, typecheck/build/lint, rà diff API và số lượt đọc của list/detail/retry, xác nhận 0 mutation trong scope xem. Ghi kết quả tại `specs/006-booking-cruise-detail-itinerary/quickstart.md`. FR-008, SC-001, SC-002, SC-003.
- [ ] T016 Đọc thử detail, lịch đã materialize, gói, trạng thái và CTA trên Backend local tương thích. Ghi riêng bằng chứng runtime tại `specs/006-booking-cruise-detail-itinerary/quickstart.md`. Giữ mở nếu chưa có fixture/môi trường phù hợp. SC-005.

## Thứ tự và cách chia

T001 → T002 → T003 là nền tảng chung. US1 chạy T004 → T005 → T006. US2 chạy T007 → T008 → T009. US3 chạy T010 → T011 → T012 → T013. Các story kiểm chứng độc lập qua route hiện tại nhưng dùng chung một số tệp, vì vậy triển khai tuần tự. Có thể chuẩn bị fixture ca US2 cùng đánh giá visual US1 khi hai người không sửa cùng tệp. T014/T015 chạy sau các story. T016 có thể chuẩn bị môi trường sau T001, thực thi trên bản đã tích hợp.

MVP là thẻ danh mục mở đúng detail/anchor, tổng quan mới và timeline thật theo contract. Sau đó bổ sung tàu, so sánh gói, rà hồi quy và nghiệm thu thật. Không dùng fixture để đóng SC-005.
