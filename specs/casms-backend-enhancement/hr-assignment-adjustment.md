# Điều chỉnh HR và phân công theo chuyến

Nguồn tiêu chí nghiệm thu là prompt HR-AS-01 đến HR-AS-14 của người dùng.

| Hiện tại | Yêu cầu | Khoảng cách và thay đổi dự kiến |
| --- | --- | --- |
| `CruiseStaffService` chỉ kiểm tra giao nhau giữa các khoảng giờ. V18 giới hạn một ACTIVE trên từng chuyến. | Một ACTIVE trên toàn hệ thống cho mỗi nhân viên. | Dùng khóa chuyến và tài khoản, kiểm tra trạng thái ACTIVE, thêm unique index toàn hệ thống trong V100. Giữ nguyên truy vấn kiểm tra trùng lịch cho các luồng cần nó. |
| `CruiseStaff` có ACTIVE/CANCELLED và thời điểm, người kết thúc. | Phân biệt gỡ trước chuyến với hoàn thành hoặc hủy chuyến. | Giữ ACTIVE và CANCELLED, bổ sung REMOVED, COMPLETED và lý do gỡ. Gỡ chỉ trên DRAFT/PUBLISHED chưa có giờ khởi hành thực tế. Lifecycle terminal tự đóng ACTIVE. |
| Xóa draft có thể xóa lịch sử CruiseStaff. | Giữ lịch sử phân công. | Chặn xóa chuyến có lịch sử bằng service và FK. |
| Luồng gỡ lưu người và thời điểm, thiếu notification và audit. | HR thấy thông báo trực tiếp, giữ lịch sử, retry an toàn. | Dùng Notification/AuditLog trong cùng transaction. Cho phép truyền assignmentId để retry đúng lần gỡ kể cả sau phân công lại. |
| HR có quyền phân công nhưng một số service nhận ID actor từ controller. | Kiểm tra quyền ở service và bảo vệ tài khoản Admin. | Kiểm tra principal, quyền thao tác và role tài khoản đích, bao gồm các đường phân công công việc, email và hồ sơ. |
| Dữ liệu cũ có thể có nhiều ACTIVE trên các chuyến chưa kết thúc. | Migration không xóa hoặc tự chọn bản ghi giữ lại. | V100 đóng các bản ghi ACTIVE của chuyến terminal theo trạng thái đã biết, giữ các bản ghi khác. Nếu còn xung đột, migration rollback và yêu cầu đối soát. |

Luồng inactivation của Employee dùng lại thao tác gỡ và audit từng assignment. Trạng thái ACTIVE là điều kiện dùng chung của Work, POS, email và các API vận hành hiện có.

## File và API

- `CruiseStaffService`, `CruiseStaffRepository`, `CruiseStaff`, `CruiseStaffResponse` và `CruiseController` áp dụng giới hạn toàn hệ thống và mở rộng thông tin lịch sử. Các route `GET/POST /cruise/{cruiseId}/staff`, `GET .../staff/options` và `DELETE .../staff/{userId}` giữ mapping. DELETE nhận thêm query `assignmentId` và `reason`.
- `EmployeeAccountService` dùng chung luồng gỡ và khóa theo thứ tự chuyến, nhân viên. `CruiseService` từ chối xóa draft có lịch sử phân công. `CruiseStaffWorkService` và `StaffAssignmentEmailService` kiểm tra phạm vi tài khoản Admin.
- `V100__global_cruise_staff_assignment_lifecycle.sql` thêm lý do gỡ, ràng buộc một ACTIVE mỗi user, bảo vệ FK lịch sử và tự kết thúc phân công khi chuyến COMPLETED hoặc CANCELLED. Với bản ghi cũ thiếu giờ kết thúc, migration dùng giờ hoàn thành thực tế nếu có. Nếu thiếu cả giờ thực tế, ghi thời điểm chạy migration.
- `GlobalExceptionHandler` trả lỗi nghiệp vụ cho vi phạm unique và lifecycle. Audit và thông báo nội bộ dùng repository hiện có. Notification hiển thị qua `GET /notification` theo người nhận.

## Bằng chứng kiểm thử

Java 21, Maven offline. Surefire báo **51 ca, 47 qua, 0 failure, 0 error, 4 skip**. Log `artifacts/hr-assignment-focused-tests.log`.

```powershell
mvn.cmd -o -B '-Dtest=CruiseStaffLifecyclePostgresIntegrationTest,CruiseStaffServiceTest,BaselineCruiseScopeTest,CruiseStaffWorkServiceTest,CruiseControllerTest,CruiseServiceTest,EmployeeAccountServiceTest,EmployeeWorkbookTest,EmployeeActivationTokenTest,RolePermissionInitializerTest,StaffAssignmentEmailRendererTest,StaffAssignmentEmailDispatcherTest' test
python scripts/sequence_docs.py --check
git diff --check
```

| ID | Ca kiểm tra và kết quả |
| --- | --- |
| HR-AS-01 | **PARTIAL** `assignStaff_Success` qua mức service, lưu assignment và enqueue email. Ca PostgreSQL `concurrentAssignmentsToNonOverlappingCruisesHaveOneWinnerAndDatabaseRejectsBypass` bị skip. |
| HR-AS-02 | **PARTIAL** `nonOverlappingOpenCruiseStillBlocksSecondAssignment` qua, báo `STAFF_ALREADY_ASSIGNED_TO_OPEN_CRUISE`. Chưa kiểm chứng dữ liệu thật. |
| HR-AS-03 | **BLOCKED** `runningCruiseBlocksRemovalAndCompletionReleasesEmployee` bị skip. |
| HR-AS-04 | **BLOCKED** Cùng ca PostgreSQL kiểm tra trigger COMPLETED và phân công tiếp theo. |
| HR-AS-05 | **PARTIAL** `removalPreservesAuditAndNotifiesHrOnceOnRetry` qua, trả REMOVED kèm người, giờ và lý do. Chưa đọc lại lịch sử trên PostgreSQL. |
| HR-AS-06 | **PARTIAL** Cùng ca unit xác minh đúng người nhận, mã chuyến và lý do. Chưa đọc Notification đã commit trên PostgreSQL. |
| HR-AS-07 | **PARTIAL** `activeCruiseRemovalIsRejectedWithoutMutation` qua với `CRUISE_STAFF_REMOVAL_NOT_ALLOWED`. Ca PostgreSQL bị skip. |
| HR-AS-08 | **BLOCKED** `removingBeforeDepartureKeepsHistoryAndNotificationIsIdempotent` bị skip, bao gồm phân công sang chuyến khác sau khi gỡ. |
| HR-AS-09 | **PASS** `inactiveAccountCannotBeAssigned` qua ở mức service. |
| HR-AS-10 | **BLOCKED** Ca hai HR cạnh tranh và INSERT trực tiếp vi phạm unique bị skip. |
| HR-AS-11 | **BLOCKED** `notificationConstraintFailureRollsBackRemovalAndAudit` bị skip. Ca sử dụng CHECK constraint thật trong transaction kiểm thử để ép lỗi ghi Notification. |
| HR-AS-12 | **PARTIAL** Ca unit gọi lại cùng assignment và chỉ ghi một audit và một Notification. Ca PostgreSQL bị skip. |
| HR-AS-13 | **PASS** `adminAccountCannotBeRemovedThroughHrAssignment` và `hrCannotProvisionAdminEvenThroughService` qua ở mức service. |
| HR-AS-14 | **PASS** `serviceRejectsActorWithoutAssignmentPermission`, `assignStaffRejectsShipboardRoleThatTheAccountDoesNotHave` và `assignmentInCruiseACannotBeRemovedThroughCruiseB` qua ở mức service. |

`sequence_docs.py --check` qua 659 route. Chưa có `CASMS_TEST_POSTGRES_URL` trong phiên chạy. V100, unique, trigger lifecycle và rollback thật chưa được xác minh. Nếu migration phát hiện dữ liệu xung đột, HR cần đối soát trước khi chạy lại. Notification gỡ dùng hộp thư nội bộ, nên không phụ thuộc SMTP. Không có thao tác Git xuất bản hoặc triển khai trong lượt này.

## Kiểm tra trên nhánh tích hợp

Migration công việc nhân viên dùng V101 vì main đã có V96. Maven trên nền f357919 ghi nhận 52 ca, 47 qua, 0 lỗi và 5 ca PostgreSQL bị skip. Các sơ đồ nền bị ignore trong repo được tái tạo cục bộ để chạy kiểm tra độ phủ; commit chỉ chứa sơ đồ bị ảnh hưởng.
