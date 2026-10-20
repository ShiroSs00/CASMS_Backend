# Ship model API backend

Tài liệu mô tả các API backend quản lý model tàu và mapping với dữ liệu CASMS. Response tiếp tục dùng envelope `ApiResponse` (`success`, `data`); các route được gắn tiền tố `/api` từ `server.servlet.context-path`.

## API

| Chức năng | API | Xử lý backend | Quyền |
|---|---|---|---|
| Nạp bộ asset | `POST /ship-model-assets?modelKey=zaandam&assetVersion=v10`, multipart field `bundle` là ZIP | Stream ZIP, xác minh manifest/checksum và lưu file vào PostgreSQL Large Objects | `SHIP_UPDATE` |
| Đăng ký asset cho tàu | `POST /ships/{shipId}/models`, body `{ "modelKey": "zaandam", "assetVersion": "v10" }` | Tạo model `DRAFT` từ bundle đã lưu và xác minh trong DB | `SHIP_UPDATE` |
| Chi tiết model | `GET /ships/{shipId}/models/{modelId}` | Trả metadata, trạng thái, URL asset, số cabin/deck và mapping | `SHIP_READ` |
| Model đang kích hoạt | `GET /ships/{shipId}/model` | Trả model đã kích hoạt; backend lưu bằng trạng thái `READY` hiện có | `SHIP_READ` |
| Phục vụ asset | `GET /ships/{shipId}/models/{modelId}/assets/{assetPath}` | Stream asset từ PostgreSQL Large Objects theo manifest; yêu cầu JWT, cache `private, no-cache` | `SHIP_READ` |
| Mapping deck | `GET/PUT /ships/{shipId}/models/{modelId}/deck-mappings` | PUT thay toàn bộ; kiểm tra ID trong manifest, deck thuộc tàu và số deck; `[]` xóa mapping | Đọc `SHIP_READ`, ghi `SHIP_UPDATE` |
| Mapping cabin | `GET/PUT /ships/{shipId}/models/{modelId}/cabin-mappings` | PUT thay toàn bộ; kiểm tra cabin thuộc tàu và deck đã map; `[]` xóa mapping | Đọc `SHIP_READ`, ghi `SHIP_UPDATE` |
| Kích hoạt model | `POST /ships/{shipId}/models/{modelId}/activate` | Khóa theo tàu, kiểm tra mapping và bảo đảm một model asset được kích hoạt trên mỗi tàu | `SHIP_UPDATE` |

Mapping lưu qua `ShipModel` và `ShipModelObject`. API legacy đăng ký metadata dùng `POST /ships/{shipId}/models/metadata`; API cấu hình 3D cũ vẫn được giữ lại.

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
