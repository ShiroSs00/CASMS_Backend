# Phase A Backend — Nghiệm thu ổn định

**Kiểm thử trọng tâm đạt trên mã ứng dụng Phase A.** Nghiệm thu toàn bộ Phase A còn mở do suite rộng và kịch bản E2E còn thiếu bằng chứng.

## Phiên bản và phạm vi

- Backend `main`, HEAD trước và sau `65034ee7be8eb94f31835bebb087801bccd10a13`. Baseline lấy bằng `git archive 65034ee` vào bản sao riêng. Phase A và bản sau sửa fixture chạy ở các bản sao độc lập. SHA-256 của 1.129 tệp trong `src/` khớp giữa working tree và bản nghiệm thu tại thời điểm chuẩn bị full suite.
- Đã tải `origin/main` của cả hai repo. Backend khớp `65034ee`, Frontend khớp `31249fd`. Patch local và dữ liệu QA hiện có được bảo toàn.
- Đã đọc bốn báo cáo Phase A, hướng dẫn AGENTS của workspace và constitution Backend. Không tìm thấy AGENTS trong checkout Backend hoặc skill chuyên biệt đã cài cho code review, debugging, testing, PostgreSQL và Spec Kit. Rà soát dùng source, JUnit, XML Surefire và PostgreSQL thực tế.

## Bằng chứng kiểm thử

| Lần chạy | Tổng | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: | ---: |
| Baseline `65034ee` | 1019 | 886 | 75 | 30 | 28 |
| Phase A đầu lượt, full suite | 1022 | 900 | 66 | 28 | 28 |
| Phase A sau sửa fixture, full suite | 1023 | 955 | 21 | 19 | 28 |
| Targeted API/workflow | 52 | 52 | 0 | 0 | 0 |
| Hai lớp fixture chạy lại cuối | 18 | 6 | 4 | 8 | 0 |
| Bốn ca hợp đồng chạy lại cuối | 4 | 4 | 0 | 0 | 0 |
| Python QA seeder | 5 | 5 | 0 | 0 | 0 |

- `mvn -o -DforkCount=0 test` dùng JDK 21, Maven 3.9.16, PostgreSQL 16.15 loopback với database riêng. Lượt cuối truyền thêm `-Dcasms.test.zaandamBundle=<thư mục test>` từ bộ asset Zaandam v10 sẵn có.
- Targeted dùng `mvn -o -DforkCount=0 -Dtest=ItineraryProgramsPostgresIntegrationTest,StaffingWorkflowServiceTest,PreparationServiceAuthorizationTest,MigrationChecksumLockTest,PosAccessServicePlacementTest test`. Python dùng `python -B -m unittest discover -s scripts -p test_booking_agent_qa.py`.
- `mvn -o -DskipTests package` thành công. JAR `target/casms-0.0.1-SNAPSHOT.jar` chứa `StaffingWorkflowService` và Flyway V139. `git diff --check` sạch.
- Log Maven và XML ở `target/stabilization-{baseline,current,targeted,acceptance}/`. Full suite dùng mã ứng dụng cuối. Hai tệp fixture cập nhật sau khi suite đã biên dịch được kiểm thử riêng theo hai dòng kết quả ở bảng. So sánh theo class và tên ca. Mỗi ca FAIL/ERROR trong Phase A đầu lượt đều tái hiện ở baseline. Xem [triage](CASMS_PHASE_A_BE_TEST_FAILURE_TRIAGE.md).

## Workflow và hợp đồng Frontend đã xác minh

| Hợp đồng | Bằng chứng |
| --- | --- |
| `eligiblePlacements` | PostgreSQL và HTTP `review-details` trả đúng ID khu vực và máy được chọn. Dịch vụ thiếu Deck, Area, POS hoặc `SERVICE_READ` bị lọc. Thu hồi quyền làm phương án stale. |
| `shipPlanReady`, `planResetImpact` | HTTP serialize đúng các trường. Ship tiến đến `SHIP_REVIEWED` với hai danh sách rỗng khi chuyến không cần khu SERVICE. Test xác minh số phân công và việc xóa phân công khi trả Ship. |
| Model READY, `deckBlocker` | HTTP `program-options` trả model READY hiệu lực và chuyển sang model mới khi model đó READY. HTTP service-locations kiểm có boong, `SHIP_DECK_REQUIRED` và `SHIP_REQUIRED`. |
| `TECHNICAL_NAUTICAL` | Test PostgreSQL xác nhận Ship nộp riêng sau bước kiểm tàu, từ chối tự duyệt và cho OPS độc lập duyệt. HTTP dashboard hiển thị đúng `approvalStatus` và giữ `submittedRevision`. |
| Ngân sách vận hành và V2 | HTTP trả `PREPARATION_BUDGET_REQUIRED` khi thiếu, CRM lưu VND qua revision đương thời rồi tạo `PENDING_ASM`. Test PostgreSQL tiếp tục qua ASM, Ship, CRM gửi HR, HR nộp, CRM xác nhận và OPS duyệt. Python kiểm tra seeder giữ ngân sách hiện có và giới hạn database QA. |

Web Admin tiếp tục tích hợp các trường ở bảng trên, gồm dùng `shipPlanReady` cho chuyến không cần khu SERVICE và hiển thị trạng thái kỹ thuật riêng. [API handoff Phase A](CASMS_PHASE_A_BE_API_HANDOFF.md).

## Open Sale và điều kiện còn thiếu để nghiệm thu E2E

Bốn ca chạy riêng sau full suite đều đạt. Fixture legacy dùng hai tài khoản test riêng để nộp và duyệt giá qua service, thiết lập cửa sổ booking hợp lệ và kiểm tra Ready, mở/tạm dừng/mở lại bán, từ chối phạm vi gói sai và cổng bookable sau khi mở lại. Ca itinerary xác minh chương trình được tạo khi materialize và từ chối ghi đè bản đã thuộc chuyến. Đây là bằng chứng tích hợp riêng cho từng gate.

Cần kế hoạch QA chỉ định hai chuyến đại diện, ngân sách đã xác nhận, tài khoản CRM/ASM/Ship/HR/OPS và hai Booking Agent, giá và phạm vi bán hợp lệ, bằng chứng preparation/Ready và phân công Agent đúng chuyến. Chạy theo [kịch bản E2E](CASMS_PHASE_A_BE_E2E.md) trên bản sao QA loopback để xác nhận Open Sale, sellability và thao tác Booking Agent cùng chuỗi. Bằng chứng test API hiện tại kiểm từng gate và các đoạn workflow.

## Tệp của working tree Phase A

- `.gitignore`
- `scripts/booking_agent_qa.py`
- `scripts/test_booking_agent_qa.py`
- `specs/casms75-ship-staffing-handoff/plan.md`
- `specs/casms75-ship-staffing-handoff/spec.md`
- `specs/casms75-ship-staffing-handoff/tasks.md`
- `src/main/java/com/casms/ship/controller/CruiseScopedProgramsController.java`
- `src/main/java/com/casms/ship/dto/response/CruisePreparationSectionResponse.java`
- `src/main/java/com/casms/ship/service/CruisePreparationService.java`
- `src/main/java/com/casms/ship/service/CruiseServiceLocationService.java`
- `src/main/java/com/casms/ship/service/ItineraryProgramPolicy.java`
- `src/main/java/com/casms/ship/service/ItineraryProgramService.java`
- `src/main/java/com/casms/staffing/StaffingWorkflowService.java`
- `src/test/java/com/casms/config/flyway/FlywayMigrationPostgresIntegrationTest.java`
- `src/test/java/com/casms/pos/PosPostgresIntegrationTest.java`
- `src/test/java/com/casms/ship/controller/BaselineRoleAuthorizationTest.java`
- `src/test/java/com/casms/ship/controller/CruiseRbacAuthorizationIntegrationTest.java`
- `src/test/java/com/casms/ship/repository/ItineraryChangeHistoryPostgresIntegrationTest.java`
- `src/test/java/com/casms/ship/service/CruiseOperationalReadinessPostgresIntegrationTest.java`
- `src/test/java/com/casms/ship/service/CruiseSaleAndHandoffPostgresIntegrationTest.java`
- `src/test/java/com/casms/ship/service/CruiseTimezoneAndSchedulePostgresIntegrationTest.java`
- `src/test/java/com/casms/ship/service/ItineraryProgramsPostgresIntegrationTest.java`
- `src/test/java/com/casms/ship/service/OperationalProgramUnknownInputTest.java`
- `src/test/java/com/casms/trip/contract/TripPlanningContractTest.java`
- `scripts/phase_a_qa_budget.py`
- `docs/reports/CASMS_PHASE_A_BE_IMPLEMENTATION.md`
- `docs/reports/CASMS_PHASE_A_BE_REGRESSION.md`
- `docs/reports/CASMS_PHASE_A_BE_E2E.md`
- `docs/reports/CASMS_PHASE_A_BE_API_HANDOFF.md`
- `docs/reports/CASMS_PHASE_A_BE_FINAL_ACCEPTANCE.md`
- `docs/reports/CASMS_PHASE_A_BE_TEST_FAILURE_TRIAGE.md`
