# Công việc V2

- [x] F01, F11 — Đối chiếu tạo yêu cầu, handoff ASM, kiểm tàu, CRM dispatch, HR nộp, CRM xác nhận và OPS duyệt với `StaffingWorkflowService` và `CruiseWorkHandoffService`. Ghi các đường trả và điều kiện trong spec.
- [x] F05, F06, F13, F25, F26, E13 — Sửa dữ liệu lựa chọn ở `CruiseScopedProgramsController`, `ItineraryProgramPolicy`, `CruiseServiceLocationService` và `StaffingWorkflowService`.
- [x] E08, E09, F24, F27 — Trả phạm vi reset và hiệu lực phương án ở API staffing. Giữ kiểm tra ca và thực thi quyền POS riêng.
- [x] F02, F09, F10 — Tách các trường trạng thái API preparation. Giữ ownership và maker/checker.
- [x] A6 — Nối seeder ngân sách CRM vào chạy thử Booking Agent QA với fixture số tiền tường minh.
- [ ] F15 — Nghiệm thu E2E qua API/UI đúng vai trò trên môi trường có đủ dữ liệu. Xem kết quả test và các ca còn mở trong `docs/reports/CASMS_PHASE_A_BE_REGRESSION.md`.

## LEGACY — công việc lịch sử

- [x] AC1–AC2 — Bổ sung yêu cầu kiểm tra tàu, kiểm tra cabin và POS, thông báo khi tự động chuyển HR.
- [x] AC3 — Bổ sung phân công khu vực và điều kiện đầy đủ theo khu vực và hoạt động.
- [x] AC4–AC5 — Bổ sung danh sách theo nhân viên, rà soát của quản lý tàu, duyệt HR và trả lại đúng phần.
- [x] AC1–AC5 — Chạy unit test, đối chiếu checksum và cập nhật tài liệu trình tự API. Kết quả trong qa.md.
- [ ] AC1–AC5 — Kiểm tra migration và luồng đầu cuối trên PostgreSQL cô lập.

- [x] AC6–AC7 — Nối tiếp nhận, bổ sung và hoàn tất chương trình ASM với hàng chờ quản lý tàu.
- [x] AC8–AC9 — Bổ sung bố trí dịch vụ tại khu vực/POS theo phạm vi chuyến và kiểm tra trước bàn giao HR.
