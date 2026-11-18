# Bằng chứng kiểm thử

Nhánh `feat/CASMS-75-itinerary-management` đồng bộ `origin/main` tại `c615e94`. Kiểm thử unit dùng Java 21, Maven 3.9.16 và Surefire 3.2.5. Worktree không có Maven Wrapper.

```powershell
mvn.cmd -B -ntp "-Dtest=StaffingWorkflowServiceTest,CruiseStaffWorkServiceTest,PreparationServiceAuthorizationTest,ShipReadinessAssessmentTest,RolePermissionInitializerTest,MigrationChecksumLockTest" test
python scripts/sequence_docs.py --check
git diff HEAD --check
```

| Phạm vi | Thực thi | Đạt | Lỗi assertion | Lỗi thực thi | Bỏ qua |
| --- | ---: | ---: | ---: | ---: | ---: |
| StaffingWorkflowServiceTest | 11 | 11 | 0 | 0 | 0 |
| CruiseStaffWorkServiceTest | 4 | 4 | 0 | 0 | 0 |
| PreparationServiceAuthorizationTest | 10 | 10 | 0 | 0 | 0 |
| ShipReadinessAssessmentTest | 2 | 2 | 0 | 0 | 0 |
| MigrationChecksumLockTest | 3 | 3 | 0 | 0 | 0 |
| RolePermissionInitializerTest | 12 | 11 | 1 | 0 | 0 |
| Tổng | 42 | 41 | 1 | 0 | 0 |

Ca RBAC `mergeRetainsOpsWindowGrantsAndManualOpsGrantsWithoutGrantingCrmWrites` lỗi tại chỗ kiểm tra giữ quyền `USER_READ` của vận hành. Chạy lại đúng ca này với lớp phân quyền nguyên bản từ origin/main cho cùng lỗi assertion. Thay đổi phân quyền của luồng mới chỉ thêm `NOTIFICATION_READ` cho quản lý chuyến.

Unit test bao phủ từ chối khi thiếu POS hoặc mô hình READY, tự chuyển HR và ghi thông báo, cấu hình POS thay đổi, revision cũ, duyệt sớm và thiếu lý do trả lại. Cần PostgreSQL cô lập để xác minh migration V134 và các trường hợp đầu cuối về hàng chờ, phân công khu vực, phân công hoạt động và đồng thời. Chưa chạy các ca đó hoặc áp migration vào cơ sở dữ liệu.

Kiểm tra tài liệu toàn repo vẫn báo 74 route và 546 sơ đồ còn thiếu từ trước. Đối chiếu tập lỗi với tài liệu trên `origin/main` cho thấy 0 lỗi bao phủ phát sinh và 0 lỗi cấu trúc Mermaid. `git diff HEAD --check` đạt.

## Ca hồi quy bổ sung

| Ca | Tiêu chí | Bằng chứng cần có |
| --- | --- | --- |
| Gửi và nhận yêu cầu ASM | AC6 | Trước khi ASM hoàn tất, yêu cầu hiện trong hàng chờ ASM và chưa xuất hiện trong hàng chờ quản lý tàu. |
| Hoàn tất chương trình | AC7 | Chương trình hợp lệ chuyển bước trong cùng giao dịch. Lỗi dữ liệu hoặc revision cũ giữ nguyên trạng thái và quyền chỉnh sửa. |
| Phạm vi bố trí POS | AC8 | Kiểm tra dịch vụ chưa có trên chuyến, tàu hoặc POS sai phạm vi, khu vực sai mô hình và phiên bản yêu cầu cũ. |
| Kiểm tra độ phủ | AC8–AC9 | POS đang tắt, liên kết bị tắt hoặc dịch vụ trên tàu chưa được gắn đều chặn xác nhận. Dịch vụ tại cảng không cần POS tàu. Thành công chuyển HR và ghi thông báo. |

## Kết quả cuối cho AC6–AC9

```powershell
mvn.cmd -B -ntp "-Dtest=StaffingWorkflowServiceTest,CruiseWorkHandoffServiceTest,ServiceScheduleEditingTest,MigrationChecksumLockTest,CruiseStaffWorkServiceTest,PreparationServiceAuthorizationTest,PosRequestValidationTest,PosAccessServicePlacementTest" test
```

**42/42 đạt**. Lỗi assertion 0, lỗi thực thi 0, bỏ qua 0. Bao gồm 15 ca staffing, 3 ca chuyển giao ASM, 2 ca bảo toàn liên kết POS bị tắt (kể cả xung đột ghi), 3 ca ca phục vụ, 3 ca checksum và 16 ca hồi quy liên quan.

`python scripts/sequence_docs.py --check` còn báo 71 route và 542 sơ đồ thiếu từ trước. Đối chiếu tập lỗi với tài liệu origin/main cho thấy 0 lỗi mới và 0 lỗi cấu trúc Mermaid. `git diff HEAD --check` đạt. Chưa áp migration V135 hoặc chạy các ca đầu cuối trên PostgreSQL cô lập.
