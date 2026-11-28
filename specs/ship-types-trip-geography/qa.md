# Bằng chứng QA BE-01 đến BE-08

## Kết quả đã chạy

Checkout nền `43b19e2`, Java 21.0.11 và Maven 3.9.16. Lệnh từ PowerShell:

```powershell
mvn -o "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" -q "-Dtest=ShipTypeServiceTest,ShipServiceTest,StoredModelCatalogTest,PublicShipModelTest,TripGeographyCatalogTest,TripPlanningEnhancementTest,TripAndCruiseP0PortValidationTest" test
```

Surefire ghi 80 test, 0 failures, 0 errors, 0 skipped.

| Suite | Số test | Phạm vi liên quan |
| --- | ---: | --- |
| ShipTypeServiceTest | 2 | Gói thiếu trường và xác định tên tàu, phiên bản 3D khi tạo |
| ShipServiceTest | 11 | Điều kiện đổi trạng thái và hồi quy trạng thái tàu |
| StoredModelCatalogTest | 2 | Hồi quy danh mục model |
| PublicShipModelTest | 3 | Hồi quy API đọc mô hình |
| TripGeographyCatalogTest | 4 | Tắt Việt Nam, khóa miền, phạm vi chuyến và cảng trong lịch nháp |
| TripPlanningEnhancementTest | 42 | Hồi quy lập chuyến, gồm yêu cầu tạo lại lịch nháp khi miền bị tắt |
| TripAndCruiseP0PortValidationTest | 16 | Hồi quy lập chuyến và cảng |

`git diff --check` trả 0. `python scripts/sequence_docs.py --check` trả 1 do thiếu 73 dòng inventory và 610 sơ đồ trong phần nền, với 0 dòng lỗi thời và 0 lỗi cấu trúc Mermaid. Nền `origin/main` có 74 dòng inventory thiếu và 627 sơ đồ thiếu theo cùng quy tắc quét. Các mapping thêm và các route cập nhật có tài liệu trong inventory.

## Chưa xác minh trên PostgreSQL

Ca tích hợp `shipTypeProvisioningSharesAssetsAndGatesStatusOnScopedProfile` đã biên dịch, chờ chạy trên PostgreSQL cô lập. Docker daemon đang dừng và môi trường chưa có PostgreSQL cô lập được duyệt. Migration V121/V122, rollback trên database, trùng định danh đồng thời và bảo toàn dữ liệu lịch sử khi nâng cấp vẫn chờ bằng chứng thực thi.
# Kiểm chứng bổ sung BE-09

- `mvn.cmd -B -ntp '-Dtest=TripGeographyCatalogTest,TripPlanningContractTest' test` hoàn thành ngày 2026-11-27. Chạy 30 test, 0 lỗi kiểm thử, 0 lỗi thực thi, 0 bỏ qua. Trong đó có 7 test service danh mục và 23 test hợp đồng API.
- `python scripts/sequence_docs.py --check` ghi nhận route và sơ đồ BE-09. Kiểm tra toàn kho vẫn báo 94 route và 101 sơ đồ cũ còn thiếu, không có lỗi cấu trúc Mermaid.
- Chưa kiểm thử đồng thời trên PostgreSQL. API dùng khóa chính hiện có và câu lệnh `INSERT ... ON CONFLICT` để xử lý mã trùng.

## Kiểm chứng ngày đăng kiểm gần nhất

- `mvn.cmd -B -ntp '-Dtest=ShipOperationalProfileTest,ShipServiceTest,ShipTypeServiceTest' test` chạy 18 test, 0 lỗi kiểm thử, 0 lỗi thực thi và 0 bỏ qua. Các ca mới kiểm tra hồ sơ cũ, ánh xạ khi tạo và thay hồ sơ, ngày tương lai và thứ tự ngày hết hạn.
- `python scripts/sequence_docs.py --check` có đủ tài liệu cho 3 route chịu ảnh hưởng. Kiểm tra toàn kho vẫn báo 94 route và 101 sơ đồ cũ còn thiếu, 0 lỗi cấu trúc Mermaid.

## Kiểm chứng tạo tàu READY và hết hạn đăng kiểm

- `mvn.cmd -B -ntp '-Dtest=ShipClassificationExpiryServiceTest,ShipTypeServiceTest,ShipServiceTest,CruiseServiceTest' test` chạy 35 ca với 0 lỗi kiểm thử, 0 lỗi thực thi và 0 bỏ qua.
- `git diff --check` đạt. `python scripts/sequence_docs.py --check` ghi nhận các route đã sửa, còn 92 route và 99 sơ đồ nền thiếu, 0 route lỗi thời và 0 lỗi cấu trúc Mermaid.
- Chưa áp dụng V145 hoặc chạy bài tích hợp PostgreSQL, nên chưa có bằng chứng runtime từ scheduler, giao dịch gửi thông báo và SSE.

## Đồng bộ trạng thái tàu khi khởi hành và đóng chuyến

- Chạy `CruiseServiceTest,ShipClassificationExpiryServiceTest` có 23 ca, 1 lỗi từ test cũ `publishCruise_whenReady_success`. Nền `main` đã thay luồng publish trực tiếp bằng phản hồi `LEGACY_PUBLISH_DISABLED`.
- Chạy lại hai lớp, loại riêng ca publish cũ. Kết quả 22 ca đạt, 0 lỗi và 0 bỏ qua. Các ca mới bao gồm khởi hành, đóng chuyến cuối, chuyến khác đang chạy, hết hạn, bảo toàn trạng thái tạm ngưng và hủy chuyến đang chạy.
- `python scripts/sequence_docs.py --check` ghi nhận tài liệu start, complete và cancel. Nền còn 98 route và 105 sơ đồ thiếu, 0 lỗi cấu trúc Mermaid.
