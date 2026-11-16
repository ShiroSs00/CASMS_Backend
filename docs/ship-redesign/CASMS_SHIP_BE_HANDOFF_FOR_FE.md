# Bàn giao Backend quản lý tàu cho WebAdmin

Baseline Backend `a37c4d5ae763c100f58a9e09abfe9abb4b234e98` và thay đổi Phase 0–2 trong worktree Backend. API dùng tiền tố `/api` và Bearer token. Vai trò và authorities đọc từ `/auth/me`. JSON thành công có dạng `{"success":true,"data":…,"timestamp":"…"}`. Khi giá trị dữ liệu là null, serializer có thể bỏ trường `data`.

## Admin upload một lần, preview và lưu loại

| Method và path | Quyền | Request và response |
| --- | --- | --- |
| POST `/ship-model-assets` | ADMIN và SHIP_MODEL_CREATE | Multipart với `modelKey` (1–80 ký tự `[a-z0-9-]`) và tệp `bundle`. Trả 201. |
| GET `/ship-model-assets/{modelKey}/preview?assetVersion=…` | SHIP_MODEL_CREATE và SHIP_CREATE, tập quyền Admin hiện có | Trả manifest chuẩn đã lưu. Luôn gửi assetVersion trong luồng mới. Bỏ tham số dùng latest theo hành vi legacy. |
| GET `/ship-model-assets/{modelKey}/versions/{assetVersion}/assets/{assetPath}` | ADMIN và SHIP_MODEL_READ | Tải binary `model/gltf-binary` hoặc JSON theo đường dẫn tương đối từ manifest. Gửi Bearer ở các fetch asset. |
| POST `/ship-types/from-stored` | ADMIN | JSON đúng cặp đã preview. Trả 201 với ShipType. |
| PUT `/ship-types/{typeId}/model` | ADMIN | Cùng JSON. Trả 200 và áp dụng phiên bản mới cho tàu tạo sau. |
| POST `/ship-types`, PUT `/ship-types/{typeId}` | ADMIN | Multipart cũ vẫn hỗ trợ, kiểm tra cùng contract metadata. |
| PATCH `/ship-types/{typeId}/status?status=ACTIVE` hoặc `INACTIVE` | ADMIN | Trả 200 và ShipType. |
| DELETE `/ship-types/{typeId}` | ADMIN | 204 nếu loại chưa được dùng, 409 `SHIP_TYPE_IN_USE` nếu đã gắn tàu. |

Ví dụ upload response và body xác nhận. `assetVersion` là định danh của kho, FE lưu và gửi nguyên giá trị.

```json
{"modelKey":"zaandam","fileCount":5,"expandedBytes":2139,"assetVersion":"zip-fdc53b306d58cb06e3319bcfae8527b58019d2aa1eafc5d402eb23fddfb45465"}
```

```json
{"modelKey":"zaandam","assetVersion":"zip-fdc53b306d58cb06e3319bcfae8527b58019d2aa1eafc5d402eb23fddfb45465"}
```

Dùng `manifest.assets.full`, `manifest.assets.exterior` hoặc `manifest.decks[].asset` để dựng URL của viewer. Preview có `modelId`, `name`, `shipManufacturerName`, `engineType`, `passengerCapacity`, `deckCount`, `length`, `beam`, `assets`, `decks` và `cabins` theo manifest. Backend lấy tên loại từ `manifest.name`. Các trường metadata bắt buộc gồm cả nhà sản xuất và động cơ. ZIP thiếu một trong hai cần cập nhật từ nguồn thiết kế. Upload có thể chấp nhận asset đúng cấu trúc để preview, còn xác nhận tạo loại kiểm tra các trường bắt buộc và cấu trúc tham chiếu.

Retry tạo loại với cùng mã thiết kế và đúng bản đang gắn trả về cùng `shipTypeId`. Nếu mã đã gắn phiên bản khác, nhận 409 `SHIP_TYPE_CODE_CONFLICT` và cần mở chi tiết loại để kiểm tra trước khi thay phiên bản. Thay model khác `modelKey` trả 409 `SHIP_TYPE_DESIGN_MISMATCH`. Không đăng ký model cho một Ship khi xác nhận ShipType.

## Đọc loại và danh sách tàu

| Method và path | Quyền | Hành vi |
| --- | --- | --- |
| GET `/ship-types?page=0&size=20&sort=code,asc&status=ACTIVE&q=zaandam` | ADMIN, SHIP_MANAGER | Admin mặc định thấy cả hai trạng thái và có thể lọc. Manager chỉ thấy ACTIVE. q tìm mã hoặc tên, không phân biệt hoa thường. |
| GET `/ship-types/{typeId}` | ADMIN, SHIP_MANAGER | Manager chỉ đọc chi tiết loại ACTIVE. |
| GET `/ship-types/{typeId}/ships?page=0&size=20&sort=shipCode,asc` | ADMIN hoặc SHIP_MANAGER, kèm SHIP_READ | Admin thấy tất cả tàu của loại. Manager chỉ thấy tàu do mình tạo hoặc được phân công theo quy tắc hiện hành. |
| GET `/ships`, GET `/ships/{shipId}` | SHIP_READ | Trang và chi tiết tàu theo phạm vi hiện có. Tàu có loại trả `shipTypeId`, `shipTypeName` và `shipTypeCode`. Tàu legacy không có các trường này. |

API phân trang trả Spring Page trong `data`. Dùng `content`, `number`, `size`, `totalElements` và `totalPages`. `shipCount` tính theo đúng phạm vi quyền xem tàu của caller.

```json
{
  "shipTypeId": "d6bde4b8-82cf-48ed-aa61-286234dc536b",
  "code":"zaandam","name":"MS Zaandam","status":"ACTIVE",
  "modelKey":"zaandam","assetVersion":"zip-fdc53b306d58cb06e3319bcfae8527b58019d2aa1eafc5d402eb23fddfb45465",
  "manufacturerName":"Example Manufacturer","engineType":"Diesel",
  "totalCapacity":582,"totalDecks":10,"lengthMeters":237.7,"beamMeters":32.25,"shipCount":2
}
```

## Quản lý tàu và viewer theo tàu thật

`POST /ship-types/{typeId}/ships` yêu cầu SHIP_MANAGER và header `Idempotency-Key` (UUID do FE tạo một lần cho mỗi lần xác nhận). Body JSON `{"shipName":"Aurora 01","profile":null}` hoặc gửi hồ sơ theo mẫu dưới đây. Server sinh mã `SH`, dựng cấu trúc và trả 201 `ShipResponse` với `shipId`, `shipCode`, `shipName`, `shipTypeId`, `status`, `totalCapacity`, `totalDecks`, `deckNumberLimit` và các trường hồ sơ cơ bản.

Nếu không nhận được phản hồi, giữ nguyên cặp payload và UUID. Cho phép người dùng chọn “Kiểm tra kết quả” bằng `GET /ship-types/{typeId}/ships/provisioning/{requestKey}` (SHIP_MANAGER). Endpoint trả 200 với tàu của chính tài khoản hoặc 404 nếu chưa thấy kết quả đã commit. Nếu cần thử lại, gửi lại đúng POST với UUID cũ. Backend trả cùng tàu nếu request ban đầu đã commit, kể cả khi Admin vừa thay asset của loại. Dùng lại key với dữ liệu khác trả 409 `IDEMPOTENCY_CONFLICT`. Một giao dịch rollback có thể được thử lại bằng key cũ. Tạo tàu khác là thao tác mới có UUID mới.

| API | Role/authority | Ghi chú |
| --- | --- | --- |
| PATCH `/ships/{shipId}` | SHIP_UPDATE | Contract cập nhật cơ bản hiện có. |
| PATCH `/ships/{shipId}/name` | SHIP_MANAGER và SHIP_READ | JSON `{"shipName":"Aurora Renewed"}`. Áp dụng tàu có loại trong phạm vi manager. |
| GET, PUT `/ships/{shipId}/operational-profile` | SHIP_MANAGER | Theo phạm vi tàu. PUT thay hồ sơ toàn bộ. Tàu legacy trả 409 `SHIP_TYPE_REQUIRED`. |
| POST `/ships/{shipId}/status?status=READY` | SHIP_STATUS_CHANGE | Trạng thái vận hành tàu tách khỏi READY của ShipModel. |
| GET `/ships/{shipId}/model` | SHIP_READ | Model đang hoạt động của tàu. Dùng `id`, `baseUrl` và `manifestUrl` trong data. |
| GET `/ships/{shipId}/models` | SHIP_MODEL_READ | Danh sách model của tàu. |
| GET `/ships/{shipId}/models/{modelId}/deck-mappings`, `cabin-mappings`, `assets/{assetPath}` | SHIP_READ | Dùng đúng shipId và modelId đã trả về. Không cần authority ghi để xem. |

DTO hồ sơ vận hành. Mọi trường có thể bỏ trống khi tạo DRAFT. Ngày dùng ISO `YYYY-MM-DD`, IMO là chuỗi 7 chữ số, quốc gia là mã 2 chữ cái, số đăng ký đi cùng quốc gia đăng ký.

```json
{
  "manufacturerShipCode":"BUILD-01","imoNumber":"1234567",
  "shipyard":"Example Yard","buildCountry":"VN","buildYear":2020,"deliveryDate":"2020-01-15",
  "registrationCountry":"VN","registrationNumber":"REG-01",
  "classificationSociety":"Example Society","classificationExpiryDate":"2099-01-15",
  "certificateReferences":["certificate-1"],"warrantyReferences":["warranty-1"],
  "maintenanceReferences":["maintenance-1"]
}
```

Các trạng thái Ship là DRAFT, READY, IN_SERVICE, MAINTENANCE, SUSPENDED và RETIRED. Một yêu cầu đổi trạng thái tàu có loại thiếu hồ sơ trả 422 `SHIP_OPERATIONAL_PROFILE_INCOMPLETE` với `details.missingGroups`. Giá trị nhóm là `certification` (đơn vị, ngày hết hạn và tham chiếu), `warranty` và `maintenance`. Sau khi hồ sơ đủ, guard chuyến đang hoạt động tiếp tục áp dụng.

## Xử lý lỗi và tương thích

- 400 `BAD_REQUEST` cho tên, metadata hoặc gói sai. Chuỗi `error` chỉ ra trường manifest khi validation ShipType từ chối.
- 400 `VALIDATION_FAILED` cho DTO hoặc header bị thiếu, `INVALID_PARAMETER` cho UUID/query sai kiểu.
- 403 `ACCESS_DENIED` cho thao tác ngoài vai trò hoặc phạm vi. 404 `RESOURCE_NOT_FOUND` hoặc `NOT_FOUND` cho loại/asset không tìm thấy.
- 409 `SHIP_TYPE_INACTIVE`, `SHIP_TYPE_CODE_CONFLICT`, `SHIP_TYPE_DESIGN_MISMATCH`, `SHIP_TYPE_IN_USE`, `IDEMPOTENCY_CONFLICT`, và `DUPLICATE_RESOURCE` cho trùng IMO hoặc số đăng ký.
- 422 `SHIP_MODEL_IMPORT_REJECTED` có `details.issues`. Lưu ý provisioning rollback Ship, model, cấu trúc và job khi có lỗi.
- `POST /ships` và `POST /ship-model-assets/{modelKey}/register` trả 410 cho người có quyền gọi route. Giữ các URL đọc viewer hiện hữu.
- Contract dùng chung đã có 6 trạng thái tàu và giới hạn 8–24 boong hành khách. Số hiệu cao nhất vẫn xác định từ các boong trong manifest, có thể lớn hơn số lượng boong.

Kết quả thực thi và giới hạn QA được ghi ở báo cáo [Phase 1](CASMS_SHIP_PHASE_1_SHIPTYPE.md) và [Phase 2](CASMS_SHIP_PHASE_2_PROVISIONING.md).
