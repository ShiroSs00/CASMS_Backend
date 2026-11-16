# Phase 1 — Kho phiên bản và quản lý loại tàu

Giữ ShipType, khóa ngoại manifest và mô hình lưu blob PostgreSQL từ baseline `a37c4d5`. Không thêm schema cho luồng này. Flyway đã áp dụng V121/V122 thành công trên PostgreSQL 16.15 QA với 113 migration. Tàu legacy tiếp tục có FK loại tùy chọn.

- Upload nhận multipart `modelKey` và `bundle`, trả `data.assetVersion` cùng số tệp và số byte. Cặp thiết kế được cố định bởi SHA-256 của ZIP. Kho tái sử dụng bản đã lưu khi gửi cùng tệp.
- Preview có tham số `assetVersion`. Asset stream dùng đúng phiên bản và chỉ chấp nhận đường dẫn được manifest khai báo. Request gốc kiểm tra role Admin và authority đọc model. Lượt dispatch async của chính route stream được phép hoàn tất response đã xác thực.
- `POST /ship-types/from-stored` nhận `{"modelKey":"zaandam","assetVersion":"<version từ upload>"}`. Cùng cặp đang gắn trả cùng loại, mã đã dùng với cặp khác trả 409. Khóa transaction PostgreSQL theo mã thiết kế tuần tự hóa request đồng thời.
- `PUT /ship-types/{typeId}/model` nhận cùng DTO. Phiên bản thay thế phải thuộc cùng thiết kế. Thay phiên bản giữ nguyên ShipModel của tàu đã tạo. Hai API multipart quản lý loại cũ tiếp tục dùng được.
- Tên lấy từ `manifest.name`. Các trường `shipManufacturerName` và `engineType` giữ yêu cầu bắt buộc. Thông số dương, giới hạn 8–24 boong hành khách, checksum, tài nguyên tham chiếu, header và JSON GLB, định danh boong/cabin giữa GLB và manifest đều được kiểm tra. Import vật lý tiếp tục kiểm tra các giá trị chi tiết và ràng buộc từng đối tượng.
- Admin đọc và thay đổi toàn bộ loại. Ship Manager chỉ đọc danh mục ACTIVE. Tìm kiếm tên/mã, lọc trạng thái, phân trang, đếm tàu và lấy tàu theo loại đều thực hiện ở PostgreSQL. Số lượng và danh sách theo phạm vi quyền của người gọi.

## Xác minh

Ca HTTP/PostgreSQL dùng hai gói ZIP của cùng thiết kế cùng mã, xác nhận phiên bản ghim, stream GLB Admin, từ chối Ship Manager, kiểm tra retry, xung đột, lọc trạng thái và số Ship giữ nguyên. Các ca kiểm tra ZIP/checksum, manifest, viewer và tương thích kho model chạy trong suite cùng tên. Kết quả tổng hợp ở [Phase 2](CASMS_SHIP_PHASE_2_PROVISIONING.md).

Xem request, response và quyền tại [bàn giao WebAdmin](CASMS_SHIP_BE_HANDOFF_FOR_FE.md).
