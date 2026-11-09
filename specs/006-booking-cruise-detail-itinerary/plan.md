# Kế hoạch 006 — Chi tiết chuyến và xem lịch trình

**Ngày** 2026-11-09

**Đặc tả** [spec.md](spec.md)

**Checkout khi lập kế hoạch** Backend `codex/customer-booking-redesign-20261108`. Frontend `feat/cruise-schedule-package-updates-20261106` tại `80b4cbf` cùng các thay đổi local của 005. Feature Spec Kit riêng `006-booking-cruise-detail-itinerary`.

## Summary

Sắp lại trang chi tiết chuyến công khai thành tổng quan, lịch trình, tàu và hạng cabin/gói. Tách “Xem lịch trình” khỏi hành động mua. Link từ danh mục mở đúng chuyến và tiêu đề lịch trình theo hash hiện có. Desktop dùng ảnh/thông tin và khối hành động cạnh nhau. Mobile đọc theo một cột với hành động gần thông tin chuyến. Lịch trình trình bày theo ngày, cảng và điểm tham quan trong cùng trang.

## Technical Context

- **Stack** CASMS_WebBooking, TypeScript 6, React 19, React Router 8, Vite 8, Node 24 và npm 11 theo package hiện hành.
- **Dependencies** UI OceVie và CSS Modules, API adapter + decoder, `useReadQuery`, `useAdapterData`, `catalogReturn`. Tái dùng package catalog hiện có.
- **Storage** View model dẫn xuất từ API và vị trí URL. Không cần persistence mới.
- **Testing** Node/CDP harness hiện có cho hồi quy có fixture, typecheck/build/lint và trình duyệt local. Nghiệm thu thật riêng.
- **Targets** Desktop 1366×900, mobile 390×844, native zoom 200%, keyboard và reduced motion.
- **Request budget** Số request ở danh mục như hiện tại. Một owner itinerary theo cruise ID, tái sử dụng DOM khi chuyển anchor; retry có chủ đích. Tất cả tương tác trong feature là đọc.
- **Scope** Route detail công khai, liên kết vào detail từ card và phần trình bày gói/lịch trình. Phần chạy giao dịch 005 tiếp tục qua entry hiện tại.

## Constitution Check — trước nghiên cứu và sau thiết kế

| Cổng | Trước nghiên cứu | Sau thiết kế |
|---|---|---|
| I. Phạm vi và bảo toàn | WebBooking khách công khai | 006 tách tài liệu 005 và dựa trên diff hiện hữu |
| II. Hợp đồng và nghiệp vụ | Giữ API, role, booking và sale gate | Dùng các adapter hiện có, giữ route/query, phân biệt public và transaction gate |
| III. OceVie | Đọc chuẩn và consumer trước thiết kế | Tái dùng component/token, có ca anchor focus, zoom, keyboard và responsive |
| IV. Tái sử dụng | Kiểm tra primitive và style cũ | Composition timeline/detail, CSS cục bộ, dependency hiện có |
| V. Bằng chứng | Xác định baseline và fixture | Kế hoạch nghiệm thu có các ca thật tách biệt. Cổng runtime chờ fixture phù hợp |

## Project Structure

Tài liệu feature gồm [research](research.md), [data model](data-model.md), [UI contract](contracts/ui-flow.md), [API preservation](contracts/api-preservation.md), [quickstart](quickstart.md).

Các path nguồn tính từ repo CASMS_Frontend.

| Bề mặt | Tệp và cách tích hợp |
|---|---|
| Danh mục và entry detail | `CASMS_WebBooking/src/features/booking/components/LiveCruiseCatalog.tsx`. Thêm action trên card, giữ owner tải detail và entry hiện tại. |
| Tổng quan detail | `CASMS_WebBooking/src/features/booking/components/LiveCruiseCatalog.tsx` và module mới `CASMS_WebBooking/src/features/booking/components/CruiseDetail.module.css`. Gom facts, ảnh, action và điều hướng mục. |
| Itinerary và CTA | `CASMS_WebBooking/src/features/booking/components/PublicCruiseInformation.tsx`. Đưa owner đọc itinerary vào phần detail đã tải nếu cần dùng trạng thái từ chối công khai ở cấp trang. Renderer dùng kết quả đọc đã decode. |
| Hash và URL | `CASMS_WebBooking/src/features/booking/model/live-booking-navigation.ts`. Gom đích card/detail hiện có với allowlist hash. Áp effect cuộn/focus trong consumer detail sau khi section mount. |
| Tàu | `CASMS_WebBooking/src/features/booking/components/LiveCruiseCatalog.tsx`. Khối tên tàu từ detail, link đội tàu dùng route `/ships` hiện có, ảnh địa phương chỉ khi phù hợp. |
| Hạng cabin và gói | `CASMS_WebBooking/src/features/booking/components/PublicCruisePackages.tsx` và `model/booking-presentation.ts` để tái dùng `cabinTypeName`. Dùng lưới offer theo hạng, giữ anchor và dữ liệu gốc. |
| Regression | `CASMS_WebBooking/tests/helpers/booking-fixture.mjs`, `CASMS_WebBooking/tests/booking-cruise-detail/browser-detail.mjs` mới, harness cũ `CASMS_WebBooking/tests/booking-redesign/`. |

## Phase 0 — Nghiên cứu

Hai nhánh nghiên cứu đọc hợp đồng public và UI/token. Quyết định về phạm vi, in-page anchor, dữ liệu được phép trình bày, giá và fixture nằm trong [research.md](research.md).

## Phase 1 — Thiết kế

1. **Tổng quan và điều hướng.** Một H1, ảnh hợp lệ, tàu, thời gian, cảng, action và tóm tắt trạng thái. CTA trên thẻ phân biệt mở đầu trang với vào lịch trình. Giữ `from` qua cả hai lối vào và đặt chỗ.
2. **Timeline.** Ngày theo giờ tàu, mỗi ngày có danh sách cảng và điểm tham quan. Giá trị ngày/giờ cảng dùng `Intl.DateTimeFormat` và timezone từ decoder. Giữ tiêu đề anchor trong các trạng thái tải và lỗi.
3. **Tàu, gói và hành động mua.** Dùng khối thông tin tàu và link đội tàu hiện có. Nhóm offer theo hạng cabin, giữ từng giá và quyền lợi, dùng grid thay carousel bắt buộc. Lỗi itinerary cục bộ có thể retry riêng. Khi backend xác nhận chuyến bị rút công khai, ẩn dữ liệu cũ và hiển thị đường về danh mục. `transactionReady` kiểm soát riêng nút đặt chỗ.
4. **Kiểm chứng.** Test fixture bao phủ deep link sau tải, ngày lệch múi giờ, lỗi/empty/retry, public gate, transaction gate, dữ liệu gói và số request. Cổng Backend thật được ghi riêng và cần lịch đã materialize.

Quyết định thực hiện mặc định là timeline trong trang. Bản đồ, số ngày khởi hành thay thế và giá quảng cáo cần feature riêng nếu có hợp đồng dữ liệu và yêu cầu sản phẩm.
