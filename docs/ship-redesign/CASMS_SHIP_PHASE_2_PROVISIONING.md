# Phase 2 — Tạo tàu vật lý và hồ sơ vận hành

`POST /ship-types/{typeId}/ships` nhận `Idempotency-Key` UUID và body `{"shipName":"Aurora 01","profile":null}`. UUID nhận dạng một lần xác nhận của một Ship Manager. Hash gắn với loại, tên đã trim và profile đã được deserialize/chuẩn hóa. Dùng bảng `IdempotencyRecord` hiện có với operation `PROVISION_SHIP`. Ràng buộc duy nhất PostgreSQL chờ request đồng thời rồi replay đúng kết quả hoặc trả 409 nếu payload khác. Reservation và ID kết quả được ghi trong transaction tạo tàu. Không cần migration mới.

`GET /ship-types/{typeId}/ships/provisioning/{requestKey}` đọc kết quả đã commit của chính manager. 404 có thể nghĩa là transaction đầu tiên chưa commit hoặc đã rollback. Người dùng có thể chủ động thử lại với cùng UUID và payload. Replay trả snapshot hiện tại của đúng tàu, kể cả sau khi phiên bản loại đổi.

Ship giữ tên do manager nhập, sinh mã SH và bắt đầu DRAFT. Backend khóa loại, lấy bản asset đã gắn, đăng ký model, dựng Deck/Cabin/mapping theo ID tàu và kích hoạt ShipModel trong cùng giao dịch. ShipModel READY phục vụ viewer ngay khi Ship còn DRAFT. Khi import lỗi, Ship, ShipModel, cấu trúc, job và reservation rollback cùng nhau. Các lỗi chi tiết của importer trả trong response.

Ship Manager đổi tên tàu có loại qua `PATCH /ships/{shipId}/name`, theo phạm vi đang sở hữu hoặc được phân công. Route PATCH chung giữ authority SHIP_UPDATE. Register/re-import giữ tên vật lý. Manifest chỉ cung cấp tên dự phòng cho phương thức tạo legacy nội bộ khi tên vật lý chưa có. Hai API tạo legacy công khai vẫn trả 410.

Profile là tùy chọn khi tạo. PUT thay toàn bộ profile. Điều kiện đổi trạng thái hiện hành giữ đủ ba nhóm chứng nhận, bảo hành và bảo trì, cùng guard chuyến đang hoạt động. Không có điều kiện pháp lý mới. V121 giữ uniqueness của IMO và cặp quốc gia/số đăng ký. Sáu trạng thái Ship và giới hạn boong được giữ.

## Xác minh

Các ca PostgreSQL kiểm tra hai tàu cùng loại chia sẻ asset nhưng có UUID/mã riêng, Deck/Cabin/model riêng, tên cập nhật độc lập, quyền đọc/phạm vi viewer và profile, version loại thay đổi giữ nguyên tàu cũ, retry đồng thời, xung đột Idempotency-Key, khôi phục kết quả và rollback. Kiểm tra V121 giữ tàu legacy của V34 và chạy kiểm tra đọc/sửa tàu legacy. Dữ liệu QA dùng PostgreSQL địa phương cô lập, tách khỏi các database làm việc.

Xem contract [bàn giao WebAdmin](CASMS_SHIP_BE_HANDOFF_FOR_FE.md).

### Kết quả Maven trên PostgreSQL 16.15

| Suite | Pass |
| --- | ---: |
| ShipModelImportPostgresIntegrationTest | 33 |
| ShipTypeServiceTest | 4 |
| ShipServiceTest | 11 |
| ShipReadinessAssessmentTest | 2 |
| StoredModelCatalogTest | 2 |
| PublicShipModelTest | 3 |
| BaselineRoleAuthorizationTest | 6 |

Tổng 61 test đạt. Gói Zaandam v10 được giải nén vào thư mục QA và truyền qua `casms.test.zaandamBundle`. Bộ test dùng dữ liệu thật trong PostgreSQL cô lập và Spring MVC với JWT, ngoài các unit test Mockito. Flyway áp dụng 113 migration đến V122 rồi validate đủ 113 migration khi chạy lại.

Lần chạy rộng phát hiện một ca nền ngoài phạm vi, `BaselineRoleAuthorizationTest.crmCanStillReadDraftsWhilePublicCatalogUsesSaleFilter`. Mock chờ `CruiseService.list`, controller baseline gọi `listVisibleForStaff`. Lần chạy nghiệm thu loại riêng ca này. Các file Cruise và test Cruise liên quan giữ nguyên.

Zaandam v12 draft có tên MS Zaandam và 10 boong. Manifest nguồn thiếu `shipManufacturerName` và `engineType`, do đó chưa đủ điều kiện lưu loại. Ca test metadata dùng các fixture QA. Không có thay đổi vào ZIP nguồn hoặc các migration đã áp dụng.

Maven package kết thúc thành công. Kiểm tra inventory toàn repo còn báo 73 route và 610 diagram ngoài luồng này chưa được lập tài liệu theo chuẩn scanner. Không có route cũ bị lệch và không có lỗi cấu trúc Mermaid. Sáu route được bổ sung có inventory và diagram theo vị trí parser mong đợi.
