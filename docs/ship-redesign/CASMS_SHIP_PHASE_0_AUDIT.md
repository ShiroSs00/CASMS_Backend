# Phase 0 — Audit Backend quản lý tàu

Baseline GitHub `main` và worktree triển khai cùng ở `a37c4d5ae763c100f58a9e09abfe9abb4b234e98`. Checkout làm việc ban đầu ở `1675573`, có thay đổi QA chưa commit. Các đặc tả trong `specs/ship-types-trip-geography/` và constitution quy định hồ sơ, phạm vi và cổng nghiệm thu. Baseline không có AGENTS.md trong cây nguồn.

| Yêu cầu | Code và mức đáp ứng ở baseline | Sửa, contract và test |
| --- | --- | --- |
| Upload một lần và preview đúng phiên bản | Thiếu. ShipModelAssetStore lấy SHA-256 của ZIP, giữ blob và kiểm tra checksum, nhưng ShipModelAssetUploadResponse ẩn version. Preview dùng bản upload gần nhất. | Trả assetVersion, cho preview và tải asset theo version. Xác nhận bằng JSON từ kho. Test upload xen kẽ hai bản. |
| Tên và metadata của loại | ShipTypeService đọc từ manifest và yêu cầu nhà sản xuất, động cơ, capacity, số boong 8–24, length và beam. Kho kiểm tra asset tham chiếu, GLB đầy đủ được đọc khi import. | Dùng cùng kiểm tra metadata cho cả hai luồng tạo và kiểm tra GLB trước khi ghi loại. Gói Zaandam đã mô tả cần bổ sung metadata thật ở hai trường bắt buộc. |
| Retry tạo loại và trùng mã | Thiếu. type_code unique, lấy từ modelKey khi tạo. PUT hiện có thể đổi modelKey mà giữ code. | Retry cùng cặp đã lưu trả cùng loại, cặp khác trả 409. Tuần tự hóa tạo theo mã thiết kế và giữ định danh thiết kế khi thay version. |
| Version an toàn | ShipType và ShipModel tham chiếu asset riêng, update loại không ghi vào Ship. | Test thay version khi đã có tàu; xác nhận đúng cặp mà admin đã preview. |
| Danh mục và tàu theo loại | ShipTypeRepository có phân trang và lọc active cho Ship Manager. ShipRepository kiểm tra tồn tại theo loại nhưng thiếu truy vấn danh sách và số lượng. | Truy vấn và phân trang trong DB, dùng cùng phạm vi tàu đã có. |
| Provisioning | ShipTypeService khóa loại, tạo Ship DRAFT, đăng ký rồi import và activate ShipModel trong cùng transaction. Thiếu nhận dạng một thao tác khi retry. | Idempotency-Key theo manager, lưu cùng giao dịch và từ chối tái dùng cho yêu cầu khác. |
| Bảo toàn tên và cấu trúc | register và updateImportedBundle kiểm tra manifestModelId trên tàu có loại. Tàu legacy vẫn qua syncShipFromManifest, có thể bị đổi tên. Import lưu cấu trúc theo tàu. | Dùng tên manifest như giá trị dự phòng duy nhất cho tạo legacy nội bộ khi chưa đặt tên. Test re-import và độc lập Deck/Cabin/mapping. |
| Viewer và RBAC | Controller và SecurityConfig áp method security. RolePermissionInitializer cấp SHIP_READ và SHIP_MODEL_READ cho Ship Manager; interceptor giới hạn tàu do mình tạo hoặc được phân công. | Giữ quyền đọc hiện hành, test viewer của tàu do mình tạo và quyền truy cập asset kho theo role Admin. |
| Hồ sơ và trạng thái | ShipOperationalProfile có validation và ràng buộc định danh tại V121. ShipService kiểm tra nhóm chứng nhận, bảo hành, bảo trì khi đổi trạng thái tàu có loại. Hai route tạo legacy công khai trả 410. | Giữ các điều kiện trạng thái; xác minh profile có thể trống khi DRAFT và lỗi khi thiếu nhóm. |
| Migration | V121 thêm FK nullable, JSONB, index và ràng buộc định danh. V122 thuộc danh mục địa lý. QA baseline ghi rõ chưa chạy tích hợp PostgreSQL. | Tái sử dụng IdempotencyRecord và unique key hiện có nên không cần migration mới. Kiểm tra Flyway trên DB cô lập, giữ ID, mã, dữ liệu và liên kết tàu lịch sử. |

Không tìm thấy skill Java, Spring Boot, PostgreSQL, Flyway, REST hay QA Backend phù hợp trong catalog và thư mục skill đã cài. Skill security-best-practices chỉ hỗ trợ Python, JS/TS và Go. Dùng kiến trúc Spring MVC, JPA, Flyway, RBAC và test hiện có.
