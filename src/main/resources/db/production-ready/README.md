# Danh mục tiếng Việt cho CASMS-75

V110 và V111 chỉ tạo schema. Trên PostgreSQL thử nghiệm đã migrate, chạy
`catalog.sql`, rồi `package-quotas-draft.sql` và `branch75_catalog_assertions.sql`.
Các script seed chỉ chạy khi được gọi chủ động. Hồ sơ nhân viên dùng địa chỉ
`example.invalid` và chỉ vào staging. Tour bắt đầu ở trạng thái DRAFT.
Tour VERIFIED vẫn là hồ sơ staging. View `OperationalVerifiedTour` kiểm tra lại
cảng và nhà cung cấp theo trạng thái hiện tại.

Quyền lợi và định mức trong SQL là đề xuất cho gói DRAFT. `package-benefits-review.csv`
ghi lại các cặp để đối chiếu từ gói đầu vào. Dữ liệu đang chờ phê duyệt thương mại
và kiểm thử việc áp dụng hạn mức lúc mua hoặc sử dụng quyền lợi.

Để chạy bộ kiểm thử PostgreSQL, dùng một máy chủ PostgreSQL cục bộ dành riêng cho
test với vai trò `postgres` và cấu hình xác thực cục bộ cho môi trường test.
Tạo database rỗng `casms_catalog_api` rồi đặt `CASMS_CATALOG_TEST_PG_PORT` theo
cổng loopback. Test migration tạo và xóa database tạm để chạy cả hai kịch bản
fresh và V95. Test API dùng Spring Boot, MockMvc và PostgreSQL, rồi rollback
fixture phát sinh trong từng ca.

```powershell
mvn -B "-Dtest=CatalogBranch75*Test" test
```
