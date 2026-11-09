# Hợp đồng dữ liệu công khai

Nguồn FE tại `CASMS_WebBooking/src/features/booking/api/booking-api.ts`, `public-sale-contract.ts`, `live-cruise-contract.ts` và `src/domain/cruise-package` trong CASMS_Frontend. Backend tại `PublicCruiseItineraryController`, `PublicCruiseItineraryService`, `CruiseController`, `CruiseService` trong `src/main/java/com/casms/ship/`.

| Read | Hợp đồng giữ lại |
|---|---|
| `GET /cruise` | PUBLISHED + OPEN_FOR_SALE, cruiseType/page/sort hiện có. Thẻ không gọi chi tiết, lịch, sellability hoặc gói riêng. |
| `GET /cruise/:id?saleState=OPEN_FOR_SALE` | ID hợp lệ và đúng response, projection PUBLISHED. Nếu không có hoặc bị từ chối, dùng trạng thái không xem được và link về danh mục. |
| `GET /cruise/:id/public-itinerary` | Backend yêu cầu public sale visible và lịch materialized. Giữ decoder public, một query owner theo cruiseId. `PUBLIC_ITINERARY_UNAVAILABLE` là trạng thái lịch chưa sẵn sàng. |
| `GET /cruise/:id/sellability` | Dùng cả hai adapter hiện hành cho public display và transaction CTA. Tái bố cục không làm thay đổi cách decode hay nâng quyền. |
| `GET /cruise/:id/cruise-package-offers/available` | Giữ catalog đã decode, giá và benefit đúng chuyến. Lỗi độc lập. |

Khi nhận kết quả `publicDisplayEligible === false` hợp lệ từ adapter public hoặc mã `CRUISE_NOT_OPEN_FOR_SALE` từ itinerary, dừng render nội dung chuyến đã tải và hiện link về danh mục. Tín hiệu áp dụng cho cruiseId của response hiện hành. HTTP lỗi chung, thiếu lịch, gói lỗi và transactionReady false không phải tín hiệu thu hồi công khai. Duy trì chính sách đọc của backend và các chuyển trạng thái hiện có. Trường hợp 005 đã có dữ liệu draft đang mở, link điều hướng giữ hành vi hiện tại.

UI dùng ngày giờ dự kiến đến/rời. Projection FE hiện không có tọa độ, loại ngày hoặc phân loại xuống tàu. Tàu lấy từ detail, link `/ships` dẫn đến trang đội tàu hiện tại. Giá được hiển thị ở từng offer theo catalog. Nhóm cabin là nhóm trình bày, không chứng minh tồn phòng. Tổng giá đặt chỗ vẫn đến từ báo giá ở luồng giao dịch.
