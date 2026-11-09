# Contract preservation — Booking integration

Nguồn frontend: CASMS_WebBooking/src/features/booking/api/booking-api.ts, real-booking-api.ts, booking-quote-api.ts, quote-contract.ts và domain/cruise-package/api.ts. Đây là inventory hợp đồng đã đọc, không là đề xuất endpoint mới. Prefix /api do client/config hiện có xử lý.

| Interface | Mục đích và điều phải giữ |
|---|---|
| GET /cruise | Public list: giữ query sale/type/page theo liveCruiseCatalogApi; không bỏ paging khi filter |
| GET /cruise/:id | Chi tiết public theo adapter, giữ query saleState đang dùng |
| GET /cruise/:id/sellability | Nguồn sale/eligibility hiện hành, không suy từ status đơn lẻ |
| GET /cruise/:id/public-itinerary | Public itinerary; không thay bằng dữ liệu preparation nội bộ |
| GET /cruise/:id/cruise-package-offers/available | Offer/price/category/cabin mapping hiện được phép mua; giữ decodeCruisePackageCatalog |
| GET /booking/available-cabins | Giữ cruiseId và filters được adapter hỗ trợ; dữ liệu selection không là hold |
| GET /cruise/:id/packages | Assignment links của nhánh legacy mà adapter hiện hỗ trợ; không đủ để tự dựng catalog/giá |
| GET /servicepackage/packages | Catalog legacy được packagesForCruise ghép với assignment links, validate số tiền/currency và lọc ACTIVE; không gửi tourPackageId legacy vào fare-managed selection |
| POST /booking/quotes | quoteSelection hiện có: cruiseId, USD, paymentPlan, passenger selections; không thêm PII vào economic quote payload |
| GET /booking/quotes/:id | Guest token option hiện có; readback identity/fingerprint/policy/amount/line selections và expiry |
| POST /booking | serializeBooking hiện có + quoteId/quoteGuestToken; giữ Idempotency-Key của intent, decode và verify saved identity/currency/agreedFareAmount |
| GET /booking/lookup | Interface recovery hiện có, ngoài redesign; không ghi reference/email/phone hoặc token ra log công khai |

## Invariants

1. Một canonical BookingDraft và một session controller. New group/person keys, progress và model-open là UI state, không thêm field network hay đổi thứ tự passenger ngoài thao tác khách đã xác nhận.
2. Không định nghĩa tổng giá mới. Fare-managed quote line đã gồm cabin entitlement; không cộng estimatedPrice/priceMultiplier của cabin vào tổng đó. Nhánh legacy dùng chính quote hiện tại của nó.
3. Danh sách và 3D bắt buộc cùng physicalCabinIds/remainingCapacity/group rules, không chỉ dùng validation cabinId tồn tại. Mixed-offer group không được âm thầm gộp offer hoặc nới điều kiện.
4. Quote selection signature chứa cruise, payment plan, passenger order và các allocation. Signature đổi hoặc expiry tới hạn thì quote cũ không dùng được. Thông tin cá nhân vẫn validate cuối, không đưa secret/PII vào signature log.
5. Một create intent có một idempotencyKey; không tạo UUID mới theo mỗi lần click. Pending/unknown không tự reset vì unmount route; không đổi key để vượt lock. Không hứa chống lặp xuyên reload khi identity không được lưu bền.
6. Giữ guest email requirement và guest-token ownership. Không chuyển khách qua staff login để giải quyết lỗi public.
7. Success là response đã qua decoder và guard, có reference. Booking/payment/contract status hiển thị riêng theo server. Lỗi 400/401/403/404/409/422 và nhóm unknown giữ classifier hiện có.
8. Không đổi Backend/permissions/schema, viewer API, policy thanh toán hoặc timeout hợp đồng. Nếu source/contract runtime lệch, ghi baseline và dừng phần bị phụ thuộc; không mock thành công rồi coi là E2E.

## Kiểm chứng tương thích

Chụp request đã che PII/token và đọc lại dữ liệu test: chuỗi quote→readback→create, guest và authenticated nếu có fixture; fare-managed và legacy đủ điều kiện; mapping từng passenger đúng sau thêm/xóa/chuyển nhóm. Đối chiếu signature/headers trong harness không log giá trị secret. Theo dõi request count cho double click và không tải model trước mở. Các mẫu giả lập lỗi chỉ chứng minh UI/recovery path và phải gắn nhãn mock.
