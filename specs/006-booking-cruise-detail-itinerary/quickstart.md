# Hướng dẫn nghiệm thu 006

Từ `CASMS_Frontend/CASMS_WebBooking`, dùng Node 24/npm 11 và dependency theo lockfile. Chạy `npm run typecheck`, `npm run build` và `npm run lint`. Để đo lượt đọc của bản production, chạy `npm run preview -- --port 5174` sau build. Dùng Edge headless với CDP cổng 9335 và profile kiểm thử riêng.

```powershell
$env:BOOKING_TEST_ORIGIN = 'http://127.0.0.1:5174'
$env:BOOKING_CDP_PORT = '9335'
node tests/booking-cruise-detail/browser-detail.mjs
node tests/booking-redesign/browser-smoke.mjs
```

Ca native zoom dùng profile Edge 200%, cổng CDP riêng và `BOOKING_NATIVE_ZOOM=1`. Script xác nhận DPR 2, CSS zoom 1 và visualViewport scale 1. Khi dùng cổng FE khác, đặt `BOOKING_TEST_ORIGIN` trùng cổng đó.

| Ca | Kết quả cần kiểm |
|---|---|
| Danh mục có query | Mở 2 link cùng chuyến. Detail giữ query và hash hợp lệ, back trả đúng page/type. Kiểm thử cả from độc hại và ID sai. |
| Slow detail, anchor | Direct URL/reload/back/forward tới ba anchor. Đến heading khi DOM mount, focus đọc được, header không che, retry không cuộn lại. |
| Lịch trình | Nhiều ngày, nhiều cảng/điểm, giờ địa phương lệch ngày của tàu, null/0 duration, thiếu tên và ngày rỗng. So với payload fixture. |
| Lỗi và quyền | 403/404 detail, 409 chưa materialize, 409 mất công khai, sellability xác nhận không public, lỗi mạng, payload lỗi, đổi ID nhanh. Không render dữ liệu cũ. Retry lỗi itinerary cục bộ chỉ đọc itinerary. |
| Tàu, hạng cabin và mở bán | Có/thiếu tên và ID tàu cho kết quả phù hợp. Nhiều offer cùng hạng giữ nguyên từng giá và quyền lợi. Hai cờ cho phép thì CTA giữ đúng URL. Cờ không cho phép hoặc lỗi giao dịch thì không hiện CTA. So giá/quyền lợi với catalog và xác nhận lỗi itinerary độc lập. |
| Request | Theo dõi list nhiều thẻ, detail đổi hash, retry và đổi chuyến. Một query itinerary owner cho chuyến, không phát sinh đọc theo card hoặc mutation. |
| Visual và keyboard | Desktop 1366×900, mobile 390×844, native zoom 200%, Tab/Enter, reduced motion. Kiểm tên chuyến/cảng dài và tương phản token. |
| Backend thật | Khởi chạy Backend local trên DB kiểm thử có schema phù hợp và một chuyến đang mở bán với schedule materialized. Xác nhận detail, lịch, sale, gói và CTA đúng response. Chỉ cần thao tác đọc và mở entry. |

Ghi riêng bằng chứng tĩnh, fixture browser và Backend thật. 005 từng gặp thiếu `OnboardActivity.catalog_entry` khi khởi động local, cần xác minh lại trước nghiệm thu 006. Khi môi trường thật chưa đạt, giữ cổng nghiệm thu thật mở và ghi nguyên nhân. Không dùng kết quả feature 006 để tự đánh dấu các task 005 còn mở.

## Baseline 2026-11-09

Frontend `feat/cruise-schedule-package-updates-20261106` tại `80b4cbf` với diff local 005. Typecheck, build và lint đạt trước khi sửa. Cảnh báo kích thước chunk đã có trong baseline. Runtime token import OceVie canonical. Git và ESLint đã loại trừ dependency/build/env theo cấu hình hiện hành.

## Kết quả 2026-11-09

| Loại kiểm chứng | Kết quả |
|---|---|
| Build và tĩnh | TypeScript, Vite build, lint và diff check đạt. Vite vẫn báo kích thước chunk như baseline. |
| Fixture 006 | Production preview 5174. Kiểm tra hai link card, Tab/Enter, from, reload/back/forward, ba anchor, đổi chuyến, ngày lệch múi giờ, null/0 thời lượng, nhóm cabin, giá/quyền lợi, sale/transaction gate và lỗi/retry. |
| Request fixture | Danh mục hai thẻ phát sinh một lượt đọc danh mục. Detail mở một lượt đọc itinerary. Đổi hash không đọc thêm. Retry itinerary gọi đúng endpoint đó. Thao tác xem gửi 0 mutation. |
| Visual | Edge 1366×900, 390×844 và native zoom 200% đạt. Profile zoom đo DPR 2, innerWidth 670, visualViewport.scale 1 và CSS zoom 1. Focus heading sau hash và reduced motion được kiểm tra. Ảnh fixture nằm trong [evidence/](evidence/). |
| Hồi quy 005 | 17 test Node cùng `browser-smoke.mjs` và `browser-states.mjs` đạt. Smoke ghi lại ảnh ở thư mục evidence mặc định của 005. Ca hết chỗ kiểm tra trạng thái không còn trong danh mục theo 006. |
| Runtime thật | T016 mở. Các DB local đã kiểm tra thiếu `OnboardActivity.catalog_entry` hoặc chưa có chuyến đã công bố phù hợp. Bản sao `casms_feature006_20261109` của DB E2E chạy V90 thành công, V91 dừng vì `CruisePlanPackageSelection` đã tồn tại. Hai bảng V91 có dữ liệu, lần lượt 5 và 7 hàng. Backend dừng ở bước khởi động, chưa có bằng chứng đọc itinerary thật. |

Thư mục evidence dùng fixture synthetic. Việc khởi động Backend thử nghiệm chỉ chạy migration trên bản sao E2E.

Sau lượt triển khai, FE chạy ở cổng 5174 và Backend chạy ở cổng 8080. Trên bản sao E2E, hai bảng trùng đã được chuyển tạm vào schema lưu trữ để Flyway chạy đến V93, sau đó nạp lại đủ 5 và 7 hàng vào bảng mới. API danh mục và proxy FE trả thành công với 0 chuyến đang mở bán, nên T016 vẫn mở.
