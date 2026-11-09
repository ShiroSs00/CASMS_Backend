# Research — Chi tiết chuyến và lịch trình

## R1. Phạm vi từ hình tham chiếu

**Decision** Dùng ảnh danh mục để tách hành động “Xem lịch trình”. Dùng hai ảnh bổ sung và bản [Western Mediterranean Cruise cùng chuyến, vùng Iceland](https://www.royalcaribbean.com/itinerary/7-night-western-mediterranean-cruise-from-barcelona-on-legend-LE07M870?country=ISL&groupId=LE07BCN-1495935368&packageCode=LE07M870&sailDate=2027-10-17) để tham chiếu thứ tự tổng quan, lịch trình, tàu và hạng cabin/gói. URL Guam gốc không đọc được qua công cụ web. Hình thức nhóm card cabin được đối chiếu trực tiếp từ ảnh người dùng cung cấp.

**Rationale** Hình minh họa bước tìm chuyến. `DiscoveryPages.tsx` dẫn đến `LiveCruiseDetail` tại `/cruises/:cruiseId`. `PublicBookingRecord` thuộc kết quả đặt và tra cứu, là consumer khác. Ảnh chỉ cung cấp tham chiếu hình thức. Nội dung, màu sắc, hình ảnh, nhãn và luồng mua dùng chuẩn dự án.

**Alternatives considered** Redesign hồ sơ đã đặt, sao chép bộ lọc, bản đồ, lịch khởi hành nhiều ngày và giá trung bình. Các phần này không thuộc mục tiêu hoặc thiếu hợp đồng công khai phù hợp.

## R2. Xem lịch trình trên cùng trang

**Decision** Link phụ từ card và tổng quan chi tiết dẫn đến `#cruise-extras`. Mục timeline giữ tiêu đề ổn định trong loading/error/empty, hỗ trợ hash bất đồng bộ và quay lại.

**Rationale** `PublicCruiseItinerary` hiện đọc lịch inline. Dữ liệu dài và nhiều cấp phù hợp với mục nội dung cuộn tự nhiên. `PublicLayout` có ScrollRestoration và scroll margin desktop. Xử lý hash sau mount trong phạm vi detail, phối hợp cuộn của layout và đo header ở mobile/zoom.

**Alternatives considered** Modal hoặc route mới. Modal giảm không gian đọc và cần thêm lifecycle focus. Route mới tăng đường quay lại, phải tái quản lý context và dữ liệu.

## R3. Hợp đồng, múi giờ và trạng thái

**Decision** Giữ `liveCruiseCatalogApi`, decoder và catalog. Render ngày tàu từ chuỗi date-only, format thời điểm theo múi giờ cảng với ngày đầy đủ. Một owner đọc itinerary cho mỗi trang chi tiết.

**Rationale** `PublicCruiseItineraryService` gọi `requirePublicSaleVisible` rồi kiểm tra schedule materialized. Decoder chỉ cung cấp dữ liệu public đã chọn. `useReadQuery` giữ trạng thái theo mount/key, abort lần đọc cũ và không cache chéo component. Lỗi từ chối bán rõ ràng khác lỗi riêng của lịch. Cờ `transactionReady` có thể false trên chuyến công khai, ví dụ nhánh unified hiện hành.

**Alternatives considered** Suy ngày biển từ portCalls rỗng, suy múi giờ từ trình duyệt, lấy DTO vận hành, lấy giá ước tính hoặc gọi itinerary cho từng thẻ. Các cách này sai nghĩa dữ liệu hoặc làm tăng request.

## R4. UI và kiểm chứng

**Decision** Tái dùng PublicLayout, ButtonLink/ActionLink, Panel, Badge, Notice, Loading, Empty, PublicCruisePackages và mapping token runtime. Timeline dùng danh sách có thứ tự và heading ngữ nghĩa. Giữ CSS mới trong module detail; phần card dùng selector cục bộ.

**Rationale** Root/app AGENTS và OceVie DESIGN/tokens đã được đối chiếu. Có thể dùng asset địa phương đã được ghi nguồn. Ảnh không khớp chuyến chỉ dùng dưới dạng minh họa có nhãn hoặc bỏ ảnh. Harness Node/CDP của 005 có sẵn nhưng itinerary fixture hiện trả days rỗng. Ca thật cần môi trường Backend phù hợp.

**Alternatives considered** Thêm thư viện timeline/map, primitive và asset ngoài repo. Các phần này không cần cho phạm vi hiện tại.

## R5. Áp dụng Ship highlights và Available Rooms vào CASMS

**Decision** Mục tàu dùng tên/ID trong detail và route đội tàu có sẵn. Mục hạng cabin dùng offer đã decode theo `cabinType`, nhãn hiện có và thẻ giá/quyền lợi theo từng gói. Các điểm nổi bật có nguồn từ quyền lợi được đặt trong card gói tương ứng.

**Rationale** `LiveCruise` có dữ liệu tên/ID tàu. `PublicCruisePackages` có `templateName`, `logicalCategory`, `cabinType`, `amount`, `benefits`. Dữ liệu này phù hợp để so sánh offer. Nó không cung cấp bộ ảnh phòng, danh sách tiện ích toàn tàu hoặc số phòng đang trống. `/ships` đã có trang đội tàu, mở theo hành động khách.

**Alternatives considered** Nhúng gallery/carousel hoặc 3D vào detail, đặt ngay từ thẻ offer và giữ cabin sớm. Các cách này cần dữ liệu và hành vi vượt quá bước xem.
