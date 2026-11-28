# Bằng chứng QA BE-09 đến BE-20

Checkout từ `origin/main` tại `a37c4d5`. Java 21, Maven offline với cache hiện có.

```powershell
mvn -o "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" -q "-Dtest=PortOperationTimingTest,TripPlanningEnhancementTest,CruiseServiceTest,PreparationServiceAuthorizationTest,CruiseDurationTest,TripServiceTest,ScheduleMaterializationServiceTest,CruiseDelayServiceTest,TripScheduleVersionServiceTest,ActivityCatalogScheduleTest,ActivityRegistrationWorkflowTest,ServiceRequestServiceTest,UxScheduledRequestTest,ShoreExcursionServiceTest,ItineraryProgramPolicyTest,ProgramCapacityGuardTest,UxRegistrationCancellationTest" test
```

Surefire ghi nhận **160 test, 0 failures, 0 errors, 0 skipped**.

Sau khi bổ sung biên đúng hai giờ, chạy lại `PortOperationTimingTest,ActivityCatalogScheduleTest,ScheduleMaterializationServiceTest`. Cả 20 ca đã chạy lại thành công và nằm trong tổng trên.

| Phạm vi | Số ca | Bằng chứng |
| --- | ---: | --- |
| Mẫu, phép chiếu, duration và delay | 93 | TripPlanningEnhancementTest, TripServiceTest, TripScheduleVersionServiceTest, ScheduleMaterializationServiceTest, CruiseDurationTest, CruiseDelayServiceTest |
| Số khách, tìm và đổi lịch khi gán tàu | 9 | CruiseServiceTest |
| Cảng, lịch hoạt động và nhận đặt | 48 | PortOperationTimingTest, ActivityCatalogScheduleTest, ActivityRegistrationWorkflowTest, ProgramCapacityGuardTest, ShoreExcursionServiceTest, UxRegistrationCancellationTest, ServiceRequestServiceTest, UxScheduledRequestTest, ItineraryProgramPolicyTest |
| Ngân sách và quyền nộp, duyệt hồ sơ | 10 | PreparationServiceAuthorizationTest |

`git diff --check` trả 0. `python scripts/sequence_docs.py --check` trả 1 do 63 dòng inventory và 547 sơ đồ còn thiếu, 0 dòng lỗi thời và 0 lỗi cấu trúc Mermaid. Nền trước thay đổi báo thiếu 73 dòng và 610 sơ đồ. Các route mới về ngân sách và từ chối hồ sơ có trong inventory.

Chưa chạy migration V123–V125 hoặc kiểm thử PostgreSQL. Các kịch bản cần dữ liệu thực gồm hai đường gán/đổi lịch đồng thời, lưu/đọc nhóm giờ dịch vụ, khóa cảng đồng thời với nhận đặt, dedupe cảnh báo và thay đổi revision hồ sơ.

## Hạn đăng kiểm khi lập chuyến

- `mvn.cmd -B -ntp '-Dtest=CruiseServiceTest,TripAndCruiseP0PortValidationTest' test` chạy 28 ca với 0 lỗi kiểm thử, 0 lỗi thực thi và 0 bỏ qua. Ca mới kiểm tra biên ngày về theo giờ Việt Nam, hồ sơ thiếu hạn và tàu hết hạn trước ngày về.
- Inventory và sơ đồ đã cập nhật 6 route của luồng lập chuyến. `python scripts/sequence_docs.py --check` vẫn báo 94 route và 101 sơ đồ cũ còn thiếu, 0 lỗi cấu trúc Mermaid.
- Các bộ test PostgreSQL tùy chọn đã có dữ liệu hạn đăng kiểm cho ca gán tàu hợp lệ. Chưa chạy các bộ đó.
