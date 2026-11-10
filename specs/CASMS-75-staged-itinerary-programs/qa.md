# QA evidence — CASMS-75

**Ngày chạy**: 2026-11-10, hoàn tất 14:23:07 GMT+7.
**Kết quả cuối**: PASS 70 tests; 0 failures, 0 errors, 0 skipped. Đối chiếu cả Maven summary và 9 Surefire XML, không chỉ BUILD SUCCESS.

## Environment and command

Java 21.0.11, Maven 3.9.16, Boot 3.3.4. PostgreSQL **16.15** trong container disposable `casms75-postgres-validation`, DB synthetic `casms75_test` bind localhost. Test mới bắt buộc opt-in `CASMS_TEST_POSTGRES_URL` và từ chối URL ngoài đúng DB cô lập. Không dùng datasource mặc định, không kiểm thử/ghi vào DB dùng chung.

```powershell
$env:CASMS_TEST_POSTGRES_URL='jdbc:postgresql://127.0.0.1:56559/casms75_test?stringtype=unspecified'
$env:SPRING_DATASOURCE_URL=$env:CASMS_TEST_POSTGRES_URL
$env:SPRING_DATASOURCE_USERNAME='postgres'
$env:SPRING_DATASOURCE_PASSWORD=''
mvn -B '-Dtest=ItineraryProgramPolicyTest,ItineraryProgramsPostgresIntegrationTest,ItineraryServiceTest,ItineraryCalendarTest,PreparationServiceAuthorizationTest,ActivityCatalogScheduleTest,ShoreExcursionServiceTest,ProgramCapacityGuardTest,OperationalProgramUnknownInputTest' test
python scripts/sequence_docs.py --check
git diff --check
```

Flyway đã chạy thành công **93 migration đến V95** từ DB sạch trong lần kiểm tra migration; lần chạy cuối validate đủ 93 và schema ở V95. Container thử nghiệm được dừng/xóa sau khi xác nhận. Một test cố ý commit fixture synthetic để kiểm chứng rollback thực của batch trong transaction riêng; tất cả dữ liệu chỉ tồn tại trong DB disposable.

## Test counts

| Class | Run | Failure / Error / Skip |
|---|---:|---|
| ItineraryProgramPolicyTest | 3 | 0 / 0 / 0 |
| ItineraryProgramsPostgresIntegrationTest | 15 | 0 / 0 / 0 |
| ItineraryServiceTest | 17 | 0 / 0 / 0 |
| ItineraryCalendarTest | 2 | 0 / 0 / 0 |
| PreparationServiceAuthorizationTest | 7 | 0 / 0 / 0 |
| ActivityCatalogScheduleTest | 2 | 0 / 0 / 0 |
| ShoreExcursionServiceTest | 9 | 0 / 0 / 0 |
| ProgramCapacityGuardTest | 8 | 0 / 0 / 0 |
| OperationalProgramUnknownInputTest | 7 | 0 / 0 / 0 |
| **Total** | **70** | **0 / 0 / 0** |

## Requirement traceability

| AC | Evidence |
|---|---|
| AC1 | Existing create/update/calendar tests; implementation review: draft has no completeness call |
| AC2 | departmentalSubmissionDoesNotRequireCompletePrograms; actual coordinator/permission/evidence checks retained |
| AC3 | HTTP operations and direct itinerary submit reject incomplete programs without status advance; incomplete approval cannot save; shared policy wired into publish/ready/dossier |
| AC4 | PostgreSQL source cloning with null schedule, repeated source copies; failed mixed batch rolls back earlier inserts and Cruise revision |
| AC5 | Real PostgreSQL schedule completion, explicit zero price, ordered/inclusive cruise bounds and missing-detail unit tests |
| AC6 | Stale revision/foreign call rejection, JWT CRM success and operations-reader mutation 403, exact occurrence scoping review |
| AC7 | Real cancellation of both incomplete PLANNED kinds; existing registration/capacity guards preserved |
| AC8 | Service/unknown/wrong-ship area rejection, persisted area/deck binding, physical-area SQL review |
| AC9 | Two simultaneous activities in the same area successfully complete; removed overlap guards traced to shared callers |
| AC10 | Active exact-call planned destination succeeds; unplanned destination fails; source port/destination matching and visit-list EXISTS branch reviewed |
| AC11 | Tender, strict all-aboard return and cruise bounds unit tests; real NORMAL call outside cruise produces blocker |
| AC12 | JWT CRM read succeeds; CRM and default Ship Manager usage writes forbidden; default Admin configuration permission succeeds; actual DB rejects incompatible legacy assignments and reclassification |

Draft shore availability with unknown capacity now returns its existing unavailable business error, with regression coverage preventing null unboxing/500. Fixtures in older tests were aligned with current main requirements (Cruise dates/stop source mapping and exclusion of unscheduled definitions); no production rule was relaxed to make fixtures pass.

## Documentation and review

`python scripts/sequence_docs.py --check`: PASS **614 routes**, diagram coverage and Mermaid structure. The checker was repaired to report a missing diagram path rather than crash with KeyError. Two inherited contract-print mappings were registered as static baseline, **semantic review pending**; booking behavior was not changed.

Reviewed CASMS-75 controller/service/repository/DB, authorization, transaction, response and error paths are in [ITINERARY-PROGRAM-FLOWS.md](../../docs/sequence-diagrams/ITINERARY-PROGRAM-FLOWS.md) and affected route addenda. Old generated branches are not certified by this review. `git diff --check`: PASS.

## Deployment / scope boundary

Apply V95 through the normal deployment process before running the new code. Existing unknown facilities remain OTHER; configure ACTIVITY_SPACE with the existing model-update permission (default Admin), then supply missing area/schedule data before resubmission. No shared/production migration or application deployment was performed. No frontend or whole-repository test run is claimed.
