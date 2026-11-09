# Research — Customer Booking Redesign

Ngày 2026-11-08. Nghiên cứu chỉ đọc source của hai research agent theo speckit-plan; chưa nghiệm thu runtime. Root/app AGENTS, OceVie DESIGN/tokens và Constitution 1.0.0 đã đối chiếu. Các quyết định sản phẩm được người dùng chốt trong spec.md; quyết định kỹ thuật dưới đây không thay nghiệp vụ.

Mọi đường dẫn frontend trong tài liệu tính từ CASMS_Frontend/CASMS_WebBooking/src.

## 1. Nguồn hiện hành

**Decision:** Dựa vào app/router.tsx → DiscoveryPages.tsx/LiveCruiseCatalog.tsx và FlowPages.tsx/RealBookingCreatePage.tsx, real-booking-api.ts, booking-quote-api.ts; không dựa vào demo booking-types.ts có dữ liệu VND hay stepper cũ.
**Rationale:** Đây là các route đang được lắp vào ứng dụng; currency của initialLiveBooking và quote/create hiện là USD.
**Alternatives considered:** Thiết kế từ màn fixture cũ dễ sai giá, capability và luồng thực tế nên bị loại.

## 2. Bốn bước giao dịch, giữ discovery ở ngoài

**Decision:** Tìm chuyến → chi tiết → (1) Người đi & phòng → (2) Gói & cabin → (3) Thông tin hành khách → (4) Kiểm tra đặt chỗ → kết quả có mã. Thêm /booking/party; giữ cabin-experience, travellers và review. Kế hoạch thanh toán nằm trong bước 3, kiểm tra giá và gửi ở bước 4.
**Rationale:** Cho khách xác định số người/nhóm trước, tránh một loạt lựa chọn lặp theo từng hành khách ngay từ đầu. Được người dùng duyệt thay cách chia bước; vẫn giữ deep link và guard tương thích.
**Alternatives considered:** Chỉ đổi màu ba bước cũ không giải quyết nhóm ở chung; gom tất cả vào một trang làm khó kiểm tra dữ liệu và khôi phục lỗi. Không thêm bước thanh toán thực tế ngoài phạm vi.

## 3. Nhóm là cách trình bày của dữ liệu theo hành khách

**Decision:** Tạo projection nhóm/phòng từ passengers[] và cabinGroupKey, cùng khóa UI ổn định cho việc sửa/xóa/thêm. Chuyển lại đúng payload theo hành khách hiện có; không thêm bảng hoặc field Backend, không tạo room price.
**Rationale:** Offer và price revision thuộc từng hành khách. Nhóm chỉ có chung tài nguyên cabin/hạng theo validator hiện tại; không tự áp một gói cho cả nhóm. Có thể hỗ trợ hành động áp cho các thành viên do khách chọn, nhưng không tự chọn thay họ.
**Alternatives considered:** BookingDraft thứ hai theo room hoặc tự chia một nhóm qua nhiều cabin sẽ thay nghĩa dữ liệu và không tương thích hợp đồng.

## 4. Danh sách và 3D dùng chung eligibility

**Decision:** Danh sách cabin độc lập với manifest/GLB. Cùng danh sách ứng viên, selectedCabinId và handler với renderer hiện có. Chỉ mount renderer sau hành động Mở 3D. Giữ PHYSICAL_CABIN và CATEGORY_HOLD.
**Rationale:** AvailableCabin có số cabin, boong, hạng, capacity, remainingCapacity và accessibility đủ cho danh sách. Dùng đúng physicalCabinIds của offer, điều kiện cabin còn nguyên capacity ở nhánh fare-managed và trừ phân bổ trong draft. Không nới điều kiện khi 3D lỗi.
**Alternatives considered:** Dùng danh sách nằm bên trong explorer không phải fallback thật vì vẫn phụ thuộc manifest. Tự thêm viewer/mapping hoặc bắt buộc WebGL trái lựa chọn đã chốt.

Nguồn: features/booking/components/CruisePackageCabinSelection.tsx, BookingShipModel.tsx, BookingModelIntegration.tsx; app/CustomerShipPages.tsx; domain/cruise-package/purchase-contract.ts.

## 5. Giá, báo giá, gửi và trạng thái thành công

**Decision:** Giữ quoteSelection, requestBookingQuote và createQuotedBooking. Review ban đầu có hành động Kiểm tra giá; sau quote hợp lệ mới có Xác nhận đặt chỗ. Không lặp toàn bộ review trong một modal xác nhận thứ hai.
**Rationale:** Quote phải ACTIVE/chưa hết hạn, đúng từng passenger, fingerprint/policy/amount và được readback. Create dùng cùng Idempotency-Key của intent và đọc lại quote trước gửi. Thay selections/payment plan/passenger order làm quote cũ vô hiệu; sửa thông tin cá nhân vẫn phải validate trước gửi nhưng không bịa thêm điều kiện giá.
**Alternatives considered:** Tự tính tổng, cộng cabin vào fare đã gồm cabin, tự submit ngay sau khi có quote hoặc tạo UUID mới khi bấm lại đều bị loại.

Success copy là “Đã tiếp nhận đặt chỗ”, kèm reference và trạng thái thực tế; không suy thành Confirmed/đã thanh toán. Nhánh legacy non-fare-managed đang được adapter hỗ trợ vẫn giữ theo dữ liệu; không nhầm với demo fixture cũ.

## 6. Vòng đời state và phục hồi

**Decision:** BookingProvider là boundary duy nhất cho draft, quote intent, quoteUnknown, booking uncertainty, lock và saved result qua các route. Giữ in-memory lifetime; không thêm localStorage/sessionStorage chứa PII, guest token hoặc quote secret.
**Rationale:** Quote intent hiện nằm trong RealBookingCreatePage, dễ mất khi đổi route. Hoist ownership để đổi bước không vô tình mở lại mutation. Quote unknown và booking unknown phải là hai trạng thái riêng, không tự retry.
**Alternatives considered:** Thêm kho draft thứ hai hoặc durable PII persistence không được chấp thuận. Reload không được hứa khôi phục toàn bộ phiên; chuyển về bước hợp lệ và đường lookup hiện có, không tự POST. Bảo vệ idempotency áp dụng cho cùng intent còn tồn tại; không tuyên bố chống lặp xuyên reload khi chưa có recovery contract.

## 7. Discovery và giới hạn dữ liệu

**Decision:** Chỉ thiết kế bộ lọc server đang hỗ trợ: loại chuyến và phân trang; không thêm price/date/destination filter giả hoặc giá “từ” tự suy từ một trang dữ liệu.
**Rationale:** liveCruiseCatalogApi trong features/booking/api/booking-api.ts hiện có cruiseType/page; public list không có lead fare đáng tin cậy. Sale state và offer đủ điều kiện được đọc riêng ở detail.
**Alternatives considered:** Client-filter một trang rồi quảng cáo là tìm toàn bộ, hoặc lấy giá cabin làm giá hành trình, đều gây hiểu nhầm.

## 8. OceVie và phạm vi kỹ thuật

**Decision:** Giữ React 19/TypeScript 6/Vite/React Router và Primitives/Overlay hiện có. CSS theo phạm vi Booking; không dependency mới, không thay brand, viewer engine, Admin, Backend hoặc Mobile.
**Rationale:** Canonical tokens đã được import tại styles/tokens.css. Booking dùng control/CTA theo mapping, một tiêu đề/progress và summary có giá đúng trạng thái; bỏ /03 cố định cùng ảnh trang trí đẩy nội dung giao dịch xuống.
**Alternatives considered:** Thêm UI library/primitive thứ hai hoặc copy layout Admin không đáp ứng Constitution.

## Kết luận nghiên cứu

Không còn unknown kỹ thuật ngăn lập plan. Runtime dữ liệu mở bán, fare/legacy, category hold, nhóm trộn offer và lỗi quote/booking phải được xác minh trong implementation; không coi nghiên cứu source là bằng chứng E2E.
