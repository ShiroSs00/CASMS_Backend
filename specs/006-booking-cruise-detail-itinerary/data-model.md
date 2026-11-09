# Data model — View model công khai

Các trường giữ kiểu và validation của decoder hiện hành. Đây là projection đọc phục vụ UI.

| View | Nguồn | Trường và giới hạn |
|---|---|---|
| CruiseDetail | LiveCruise từ toPublishedCruise | `cruiseId` là UUID trùng route. Tiêu đề, departure/return instants hợp lệ. Ship và cảng tùy chọn, trình bày fallback trung tính. |
| PublicItinerary | decodePublicItinerary | `cruiseId` trùng chuyến. `durationDays`/`durationNights` là số nguyên không âm hoặc null. 0 là giá trị hợp lệ. |
| Day | PublicItinerary.days | `dayNumber` là số nguyên dương. `date` là ngày lịch YYYY-MM-DD hợp lệ theo tàu. `shipTimeZoneId` là Asia/Ho_Chi_Minh. |
| PortCall | Day.portCalls | `sequence` là số nguyên dương. `portName`/`countryCode` tùy chọn. `arrival`/`departure` là instant hợp lệ, format với `portTimeZoneId` hợp lệ. |
| Visit | PortCall.destinationVisits | `sequence` là số nguyên dương. `destinationName` tùy chọn. |
| Sale | publicSaleState và saleState | `publicDisplayEligible` và public label quyết định khả năng xem công khai. `sellable && transactionReady` từ adapter giao dịch quyết định CTA. Giữ tương thích fallback `transactionReady` hiện có của adapter. |
| ShipSummary | LiveCruise đã decode | Tên và shipId tùy chọn. Chỉ hiện link đội tàu khi có cả tên và ID tàu. |
| Offer | Catalog công khai | Nhóm theo `cabinType` thuộc INTERIOR, OCEAN_VIEW, BALCONY, SUITE. Giữ từng offerId, tên gói, logicalCategory, giá USD/người và các quyền lợi, giới hạn, đăng ký riêng đã decode. |
| Navigation | URL + catalogReturn | `cruiseId`, `from` đã lọc và allowlist hash gồm ba anchor hiện tại. Không lưu hồ sơ hành khách trong view. |

Quan hệ một chuyến có nhiều ngày, mỗi ngày có nhiều lần ghé, mỗi lần ghé có nhiều điểm tham quan. Giữ thứ tự từ lịch đã materialize và giữ các lần ghé cùng cảng nếu khác lần. Mảng rỗng không tự suy thành trạng thái chuyến hoặc ngày biển.

Trạng thái đọc cho mỗi tài nguyên gồm loading, data, empty hoặc error. Itinerary có trạng thái nghiệp vụ “chưa sẵn sàng” và “chuyến mất công khai” theo mã đã kiểm chứng. Lần đọc cũ bị bỏ qua khi đổi cruiseId. Hành động xem là read-only. Thao tác mở anchor dùng vị trí URL, không tạo bản sao dữ liệu.
