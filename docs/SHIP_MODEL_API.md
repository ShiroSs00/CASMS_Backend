# Ship model API backend

## Cập nhật import deck và gắn dịch vụ ngày 29 tháng 10 năm 2026

Import hiện lưu cả passenger deck và service deck (ví dụ tầng -1, 0) thành bản ghi `Deck` có UUID thật. Các API danh sách deck trả thêm `serviceLevel` và `modelMetadataJson`; `decksImported` tính cả hai loại tầng. Object dịch vụ và khu vực trong mô hình được bỏ qua, không tự tạo Service.

Gắn dịch vụ đã có bằng `PUT /api/decks/{deckId}/services/{serviceId}` (không có body), xem bằng `GET /api/decks/{deckId}/services`, gỡ bằng `DELETE /api/decks/{deckId}/services/{serviceId}`. Đọc cần `DECK_READ` và `SERVICE_READ`; ghi cần `DECK_READ` và `SERVICE_UPDATE`. Liên kết được giữ khi import lại. Xem [hợp đồng chi tiết và migration V66](DECK_IMPORT_AND_SERVICE_ASSIGNMENTS.md).

Tài liệu mô tả các API backend quản lý model tàu và mapping với dữ liệu CASMS. Response tiếp tục dùng envelope `ApiResponse` (`success`, `data`); các route được gắn tiền tố `/api` từ `server.servlet.context-path`.

## API

| Chức năng | API | Xử lý backend | Quyền |
|---|---|---|---|
| Nạp bộ asset | `POST /ship-model-assets?modelKey=zaandam&assetVersion=v10`, multipart field `bundle` là ZIP | Stream ZIP, xác minh manifest/checksum và lưu file vào PostgreSQL Large Objects | `SHIP_UPDATE` |
| Đăng ký asset cho tàu | `POST /ships/{shipId}/models`, body `{ "modelKey": "zaandam", "assetVersion": "v10" }` | Tạo model `DRAFT` từ bundle đã lưu và xác minh trong DB | `SHIP_UPDATE` |
| Tự tạo/tìm Ship và đăng ký asset | `POST /ship-model-assets/{modelKey}/{assetVersion}/register` | Dùng `manifest.modelId` làm identity ổn định. Tạo Ship khi chưa tồn tại hoặc cập nhật metadata từ manifest cho Ship đã liên kết; sau đó đăng ký model `DRAFT`. | `SHIP_MODEL_CREATE` và `SHIP_CREATE` khi tạo mới, hoặc `SHIP_MODEL_CREATE` và `SHIP_UPDATE` khi cập nhật Ship hiện có |
| Chi tiết model | `GET /ships/{shipId}/models/{modelId}` | Trả metadata, trạng thái, URL asset, số cabin/deck và mapping | `SHIP_READ` |
| Model đang kích hoạt | `GET /ships/{shipId}/model` | Trả model đã kích hoạt; backend lưu bằng trạng thái `READY` hiện có | `SHIP_READ` |
| Phục vụ asset | `GET /ships/{shipId}/models/{modelId}/assets/{assetPath}` | Stream asset từ PostgreSQL Large Objects theo manifest; yêu cầu JWT, cache `private, no-cache` | `SHIP_READ` |
| Mapping deck | `GET/PUT /ships/{shipId}/models/{modelId}/deck-mappings` | PUT thay toàn bộ; kiểm tra ID trong manifest, deck thuộc tàu và số deck; `[]` xóa mapping | Đọc `SHIP_READ`, ghi `SHIP_UPDATE` |
| Mapping cabin | `GET/PUT /ships/{shipId}/models/{modelId}/cabin-mappings` | PUT thay toàn bộ; kiểm tra cabin thuộc tàu và deck đã map; `[]` xóa mapping | Đọc `SHIP_READ`, ghi `SHIP_UPDATE` |
| Kích hoạt model | `POST /ships/{shipId}/models/{modelId}/activate` | Khóa theo tàu, kiểm tra mapping và bảo đảm một model asset được kích hoạt trên mỗi tàu | `SHIP_UPDATE` |

Mapping lưu qua `ShipModel` và `ShipModelObject`. API legacy đăng ký metadata dùng `POST /ships/{shipId}/models/metadata`; API cấu hình 3D cũ vẫn được giữ lại.

Route tự đăng ký dùng tên tàu, sức chứa, số deck hành khách, chiều dài và chiều rộng từ manifest. Ship mới nhận `ship_code` theo bộ sinh mã hiện hành; `status` lấy mặc định `ACTIVE`. Khi cập nhật Ship đã có, `ship_code`, `status` và `commissionDate` được giữ nguyên. Nếu `manifest.modelId` đã thuộc một Ship khác, request bị từ chối. `deckCount` là số lượng deck hành khách, không phải số thứ tự deck lớn nhất: Conquest khai báo 13 deck nhưng deck passenger cao nhất mang số 14. Backend lưu riêng `deck_number_limit` để cho phép đúng ordinal này. Deck service levels `-1` và `0` vẫn là model objects, không phải bản ghi Deck nghiệp vụ.

Mỗi phần tử trong `manifest.decks` được upsert thành Deck nghiệp vụ kể cả khi không có cabin; `rooms` trong cấu hình 3D khi đó là danh sách rỗng. Service levels `-1` và `0` không thể thành Deck nghiệp vụ vì `Deck.deck_number` phải dương, nhưng được đưa vào `GET /ships/{shipId}/3d-configuration` như `serviceLevel: true`, kèm node GLB, tên, số tầng và transform để client vẫn hiển thị tầng kỹ thuật/crew.

## Chọn cabin và đặt chỗ qua model 3D

| Bước | API | Ý nghĩa |
|---|---|---|
| Lấy cabin còn chỗ kèm liên kết 3D | `GET /booking/3d-available-cabins?cruiseId={cruiseId}` (có thể lọc `deckId`, `cabinType`, `minCapacity`) | Public read endpoint trả `cabinId`, số cabin, deck, sức chứa còn lại, giá ước tính, `modelId`, `modelNodeKey`, vị trí/góc xoay/kích thước. Cabin không có chỗ không xuất hiện. |
| Gửi đặt chỗ | `POST /booking` | Gửi `cruiseId` và danh sách hành khách; mỗi hành khách dùng `cabinId` nhận từ API trên. Backend vẫn kiểm tra trạng thái cabin và sức chứa trong luồng đặt chỗ hiện có. |

API model-aware ưu tiên model asset `READY` mới nhất và mapping `ShipModelObject`; nếu thiếu mapping transform thì vị trí cabin lấy từ dữ liệu Cabin đã import. `GET /booking/available-cabins` cũ vẫn giữ nguyên response/behavior.

Response data có dạng `{ "shipId": "…", "shipModelId": "…", "shipCreated": true }`. Import deck/cabin vẫn được gọi riêng qua `POST /ships/{shipId}/models/{modelId}/imports` sau khi asset được đăng ký.

## Công việc backend còn lại

| Hạng mục | Tình trạng / việc cần làm |
|---|---|
| Mapping với DB CASMS | DB phát triển hiện chưa có bản ghi `Ship`; sau khi có dữ liệu tàu, deck, cabin thật thì ghi mapping bằng UUID thực. Không tự sinh cabin hoặc dùng UUID null. |
| Migration production | Áp dụng `V7__ship_model_asset_registry_and_mappings.sql` trước khi triển khai production. Flyway hiện tắt; không tự bật khi migration lịch sử còn pending. |
| Asset database | Áp dụng thêm `V8__ship_model_asset_blob_storage.sql`; nạp ZIP qua `POST /ship-model-assets` cho từng model/version. |
| BLE và dẫn đường | Backend hiện lưu RSSI và khoảng cách ước tính. Muốn trả tọa độ thật cần dữ liệu tọa độ anchor và thuật toán định vị phù hợp. |

## Bộ asset v10

| Model | Cabin | Deck khách | Deck dịch vụ |
|---|---:|---:|---|
| Zaandam | 291 | 10 | -1, 0 |
| Conquest | 305 | 13 | -1, 0 |

Asset dùng đơn vị mét, trục lên `+Y`, mũi tàu `+X`, mạn trái `-Z`. Đây là model tham khảo, không chứng minh layout trùng dữ liệu tàu thực trong database. Các checksum SHA-256, GLB header và độ dài file nguồn đã được xác minh; API upload cũng xác minh checksum trước khi lưu vào DB.
