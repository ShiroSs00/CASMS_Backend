# Phase A Backend — Phân loại lỗi kiểm thử

## Đối chứng

Baseline `65034ee` và snapshot Phase A đầu lượt chạy cùng cấu hình Maven và hai cụm PostgreSQL 16.15 loopback mới. Đối chiếu XML Surefire theo cặp tên class và tên ca. Kết quả lưu tại `target/stabilization-paired-diff.txt`. Baseline 1019 ca gồm 75 FAIL và 30 ERROR. Phase A đầu lượt 1022 ca gồm 66 FAIL và 28 ERROR. Toàn bộ 94 ca lỗi của Phase A đầu lượt có cùng phân loại FAIL/ERROR trên baseline. Baseline có thêm 11 lỗi fixture đã được sửa trong Phase A trước đó và Phase A có 3 ca mới đạt trong snapshot đối chứng.

| Phân loại | Nhóm và bằng chứng |
| --- | --- |
| `REGRESSION_PHASE_A` | So sánh baseline với Phase A đầu lượt không phát hiện ca PASS trên baseline chuyển sang FAIL/ERROR. 52 ca targeted kiểm mã ứng dụng và hợp đồng HTTP đạt. |
| `STALE_TEST_FIXTURE` | POS đã tái hiện 41 lỗi setup HTTP 403 trên cả hai bản. Fixture sau sửa đặt staffing ở `SENT_TO_HR`, bố trí phạm vi ASM và giờ check-in hợp lệ, dọn `ShipCruiseUsage`. POS đạt 41/41 và hai lớp RBAC đạt 13/13 trong retest. RBAC gắn đúng coordinator, mock gọi danh sách theo scope. |
| `STALE_TEST_FIXTURE` | Flyway baseline 5 assertion lỗi do còn kỳ vọng V135 hoặc số migration cũ. Phase A đạt 10/11 (skip ca clone), bao gồm tạo mới và nâng cấp đến V139, checksum lock đạt 3/3. |
| `STALE_TEST_FIXTURE` | Itinerary và mapping gồm CRM của chuyến, ca ASM, thời lượng hoạt động trên 60 phút, nguồn hành trình 3 ngày thuộc nhóm LONG, tour bờ có địa điểm cùng cảng. Fixture lịch sử giao coordinator và dùng entity revision mới cho cleanup. |
| `STALE_TEST_FIXTURE` / `BASELINE_EXISTING` | `CruiseSaleAndHandoffPostgresIntegrationTest` tái hiện 13 lỗi trên cả hai bản đầu. Truy vấn Ready riêng lộ `PORT_TIMEZONE_CHANGED` do itinerary test thiếu snapshot cảng dù cảng có timezone. Fixture cập nhật snapshot, ngày nguồn, currency USD của chuyến legacy và lưu bằng chứng submit/approve giá qua service với CRM và OPS khác người. Ready vẫn là bước riêng trước Open Sale. Kết quả còn lại ghi ở ma trận cuối. |
| `BASELINE_EXISTING` | Readiness và pricing tái hiện cùng nhóm lỗi, gồm fixture USD trên chuyến VND, kỳ vọng fingerprint giá legacy và các gate thương mại hiện hành. Materialization còn lỗi decode kết quả idempotency. |
| `OUT_OF_SCOPE` / `BASELINE_EXISTING` | `SepayServiceTest` tái hiện 8 FAIL và 7 ERROR. Mock webhook thiếu `SepayWebhookIngestionService`; các kỳ vọng checkout/waterfall cũng khác kết quả hiện tại. Giữ nguyên mã và test Payment/SePay. |
| `ENVIRONMENT_BLOCKER` | Bản đầu thiếu asset Zaandam v10. Đã lấy đúng bundle v10 cục bộ vào `target/stabilization-assets/` cho lần cuối. Các ca opt-in còn phụ thuộc DB clone nguồn, fixture OCVVIET V4 hoặc cổng/catalog và cấu hình riêng. E2E Booking Agent thiếu hai plan QA hoàn thiện. |

Lượt opt-in riêng `SaleTermsInboxPostgresIntegrationTest` dùng database `casms_sale_terms_test` đạt 1/5. Bốn ca dừng ở POST yêu cầu setup với 403. Fixture không gắn CRM làm coordinator của các chuyến. Đây là kết quả ngoài số đếm full suite mặc định và chưa có đối chứng opt-in trên baseline. Log ở `target/stabilization-targeted/sale-terms.log`.

Lỗi test được sửa bằng dữ liệu và phạm vi hợp lệ của fixture. Các gate phân quyền, thanh toán, Ready và Open Sale giữ nguyên.

## Ma trận lần cuối

| Nhóm | Baseline FAIL/ERROR | Phase A đầu lượt FAIL/ERROR | Full suite sau sửa fixture FAIL/ERROR |
| --- | ---: | ---: | ---: |
| `FlywayMigrationPostgresIntegrationTest` | 5/0 | 0/0 | 0/0 |
| `SepayServiceTest` | 8/7 | 8/7 | 8/7 |
| `PosPostgresIntegrationTest` | 41/0 | 41/0 | 0/0 |
| `BaselineRoleAuthorizationTest` | 1/0 | 1/0 | 0/0 |
| `CruiseRbacAuthorizationIntegrationTest` | 2/0 | 2/0 | 0/0 |
| `ItineraryChangeHistoryPostgresIntegrationTest` | 2/0 | 2/0 | 0/0 |
| `CruiseDraftAndMaterializationPostgresIntegrationTest` | 0/1 | 0/1 | 0/1 |
| `CruiseEvidenceReadinessPostgresIntegrationTest` | 5/0 | 5/0 | 5/0 |
| `CruiseOperationalReadinessPostgresIntegrationTest` | 0/1 | 0/1 | 0/1 |
| `CruisePricingPolicyIntegrationTest` | 2/4 | 2/4 | 2/4 |
| `CruiseSaleAndHandoffPostgresIntegrationTest` | 4/9 | 4/9 | 6/6 |
| `CruiseTimezoneAndSchedulePostgresIntegrationTest` | 0/4 | 0/4 | 0/0 |
| `ItineraryProgramsPostgresIntegrationTest` | 4/2 | 0/0 | 0/0 |
| `OperationalProgramUnknownInputTest` | 0/1 | 0/1 | 0/0 |
| `ShipModelImportPostgresIntegrationTest` | 0/1 | 0/1 | 0/0 |
| `TripPlanningContractTest` | 1/0 | 1/0 | 0/0 |

Chạy lại riêng hai lớp fixture sau cập nhật cửa sổ booking và tuyến cảng: **6 PASS, 4 FAIL, 8 ERROR, 0 SKIP**. Log ở `target/stabilization-last-fixtures/full.log`.

Kiểm chứng lần cuối 4 ca hợp đồng, log `target/stabilization-final-contract-cases/full.log`: **4 PASS, 0 FAIL, 0 ERROR, 0 SKIP**.

Các kết quả riêng không được cộng vào số đếm full suite. Hai tệp test bổ sung sau snapshot là `CruiseOperationalReadinessPostgresIntegrationTest.java` và `CruiseSaleAndHandoffPostgresIntegrationTest.java`. Mã ứng dụng trong snapshot và working tree giống nhau.
- `test_BE08A_sale_readiness_evaluation` — PASS trong lượt kiểm tra 4 ca cuối.
- `test_BE08B_open_stop_and_resume_sale` — PASS trong lượt kiểm tra 4 ca cuối.
- `test_BE_R01_invalid_package_ids_rejected_atomically` — PASS trong lượt kiểm tra 4 ca cuối.
- `test_E06_and_E30_operational_program_mapping` — PASS trong lượt kiểm tra 4 ca cuối.


## Lỗi còn mở và giới hạn

- 40 ca lỗi trong full suite sau sửa fixture đều có lỗi trên baseline. Một số ca đổi loại FAIL/ERROR vì điều kiện fixture hợp lệ đã đưa test đến gate tiếp theo.
- Lượt chạy 18 ca cho riêng sale và itinerary cho thấy sau khi sửa cửa sổ booking, các test booking lịch sử dừng ở `QUOTE_REQUIRED`. Bốn ca hợp đồng được chạy lại riêng đều đạt. Luồng Booking Agent với tạo báo giá qua API và dữ liệu phân công thực tế vẫn cần nghiệm thu.
- `ShipModelImportPostgresIntegrationTest` đạt 37/37 khi dùng đúng asset v10. `CruiseTimezoneAndSchedulePostgresIntegrationTest`, `ItineraryChangeHistoryPostgresIntegrationTest` và `TripPlanningContractTest` hết nhóm lỗi baseline trong full suite.
- Các ca pricing/operational readiness còn lỗi trong ma trận dùng fixture và kỳ vọng lịch sử. Kiểm thử này ghi rõ điểm dừng và giữ các quy tắc nghiệp vụ hiện hành. Không có bằng chứng ca mới phát sinh lỗi từ mã ứng dụng Phase A trong đối chứng.
