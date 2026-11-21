# Phase A Backend — Hồi quy

Kiểm thử trên JDK 21, Maven 3.9.16 và PostgreSQL 16.15. PostgreSQL dùng hai cụm riêng dưới `target/`, chỉ lắng nghe loopback ở cổng 55475 cho suite rộng và 56559 cho lần xác minh mã cuối. Chỉ đặt datasource và Flyway admin vào cụm cô lập. Không thay đổi migration hoặc checksum đã phát hành.

## Lệnh kiểm thử

- `mvn -o -DskipTests test-compile` — thành công.
- `python -B -m unittest discover -s scripts -p test_booking_agent_qa.py` — **5 đạt**, kiểm tra guard, chạy lại giữ ngân sách có sẵn và dùng revision đang hiệu lực.
- `mvn -o -DforkCount=0 -Dtest=ItineraryProgramsPostgresIntegrationTest,StaffingWorkflowServiceTest,PreparationServiceAuthorizationTest,MigrationChecksumLockTest,PosAccessServicePlacementTest test` — **51 đạt, 0 thất bại, 0 bỏ qua**. Log ở `target/phase-a-regression-r2.log`.
- Kiểm chứng mã cuối trên bản sao source trong `target/phase-a-final-check` với cùng danh sách class và cổng 56559 — **51 đạt, 0 thất bại, 0 bỏ qua**. Log ở `target/phase-a-final-targeted.log`.
- `mvn -o -DforkCount=0 test` — **1.022 ca: 900 đạt, 66 thất bại assertion, 28 lỗi thực thi, 28 bỏ qua**. Log ở `target/phase-a-full-retry.log`. Suite rộng dùng bản build trước khi thêm kiểm tra hoạt động chưa gắn khu vực. Kiểm tra đó có trong lần kiểm chứng mã cuối 51/51.
- `git diff --check` — sạch.

## Phạm vi bằng chứng

- PostgreSQL integration kiểm tra model READY/DRAFT và quyền/phạm vi, tạo yêu cầu sau khi lưu budget, nhận việc ASM, Ship xác nhận cho chuyến không có khu vực SERVICE, CRM gửi HR, HR nộp, CRM xác nhận và OPS duyệt. Kiểm tra TECHNICAL_NAUTICAL nộp và duyệt riêng, gồm chặn cùng người nộp/duyệt và giữ revision. Kiểm tra cuối cũng thêm hoạt động chưa có khu vực sau phê duyệt để xác nhận phương án mất hiệu lực.
- Bộ dữ liệu có dịch vụ lần lượt thêm Deck, Area, terminal và quyền. Test so cặp DTO với kết quả lưu, thay người, kiểm không phát sinh AreaStaffAssignment, thu hồi quyền máy, trả Ship, lưu lại phương án, giữ membership và trả về ASM.
- Hồi quy riêng `CruiseStaffLifecyclePostgresIntegrationTest` chạy 4 test đạt trong lượt đầu, gồm cạnh tranh phân công trùng lịch. `MigrationChecksumLockTest` chạy 3 test đạt. `FlywayMigrationPostgresIntegrationTest` đạt 10 và bỏ qua 1 ca cần cấu hình clone DB nguồn. Flyway tạo schema mới và nâng cấp đến V139.
- Vòng PostgreSQL đầu có test Flyway kỳ vọng V135 và test chương trình dùng fixture lỗi thời về thời lượng, scope và revision. Các kỳ vọng/fixture trong phạm vi đã được sửa. Những lỗi này không phải bằng chứng đạt và kết quả cuối được lấy từ lượt chạy sau.
- Các test service có Spring transaction và dữ liệu thật giúp kiểm chứng truy vấn và gate. Kịch bản nhiều tài khoản qua UI, Open Sale và Booking Agent QA từ đầu đến cuối cần chạy riêng. Xem [E2E](CASMS_PHASE_A_BE_E2E.md).


## Ca còn mở từ suite rộng

| Nhóm | Kết quả quan sát |
| --- | --- |
| `PosPostgresIntegrationTest` | 41 ca dừng ở setup gọi `POST /cruise/{id}/staff` nhận 403. Fixture thiếu `StaffingRequest` ở `SENT_TO_HR`. Cleanup của fixture còn gặp FK `ShipCruiseUsage`. Các ca unit POS mục tiêu đạt và test V2 xác nhận phân công HR không sinh quyền khu vực. |
| `CruiseSaleAndHandoffPostgresIntegrationTest` | 13 ca thất bại, gồm fixture thiếu Ready đã lưu, khác kỳ vọng inventory/sale readiness hoặc không khớp thời lượng nguồn. Một số ca dừng ở gate `READY_CONFIRMATION_REQUIRED`. |
| Readiness, RBAC, pricing và itinerary | Có khác biệt kỳ vọng giá/fingerprint Open Sale, scope HTTP, thời lượng nguồn và dữ liệu lịch trình. Chi tiết lớp và stack trace ở cuối log suite rộng. |
| `SepayServiceTest` | 8 assertion fail và 7 execution error, gồm mock chưa có `SepayWebhookIngestionService`. Module thanh toán nằm ngoài Phase A. |

Những lỗi suite rộng trên được ghi theo lần chạy thực tế. Chưa có lượt đối chứng toàn suite ở HEAD gốc cho mọi lỗi. Kết quả 51/51 chỉ áp dụng cho nhóm kiểm thử mục tiêu của mã cuối. Chưa đủ bằng chứng để xác nhận E2E qua Open Sale và Booking Agent.
