# Kế hoạch triển khai V2

- Đối chiếu `StaffingWorkflowService.transitionV2`, ASM handoff và `CruisePreparationService` với spec. Bảo toàn các quyền, luồng legacy và quyết định hiện hành của Crew, Cruise Plan, Ready và Open Sale.
- Dùng `StaffingShipModel` cho cả chương trình và phương án tàu, dùng `StaffingPlannedServicePlacement` cho danh sách cặp thiết bị đủ điều kiện và kiểm tra khi lưu. Giữ kiểm soát độ cũ ở các điểm xác nhận.
- Mở rộng response hiện có cho tác động reset, trạng thái preparation và lý do thiếu cấu hình. Giữ schema và checksum Flyway.
- Dùng bản sao Booking Agent QA cô lập, revision và quyền CRM thực tế để lưu ngân sách dự kiến khi có fixture chỉ định.
- Kiểm tra quyền, revision, dữ liệu thật trên PostgreSQL cô lập và đường đi của Frontend. Ghi bằng chứng và kết quả chạy thực tế trong bộ báo cáo Phase A.

## LEGACY — kế hoạch lưu lịch sử

- Mở rộng API yêu cầu nhân sự từ main và tái sử dụng dữ liệu tàu, cabin, cấu hình POS và phân công hiện hành.
- Đặt kiểm tra đầy đủ ở bước xác nhận tương ứng, trong giao dịch chuyển trạng thái. Dùng revision và khóa hiện hành để kiểm tra yêu cầu cũ và chuyển trạng thái đồng thời.
- Cập nhật quyền truy cập theo vai trò và phạm vi chuyến. Bổ sung thông báo ở bước tự động chuyển sang HR.
- Duy trì bước duyệt của vận hành và tương thích với yêu cầu cũ. Kiểm thử chuyển trạng thái, quyền và các trường hợp thiếu dữ liệu.

- Tái sử dụng hồ sơ ASM để giao việc, tiếp nhận và hoàn tất chương trình. Điều phối tự động sang bước tàu trong cùng giao dịch chuyển trạng thái.
- Tái sử dụng cấu hình khu vực và liên kết POS hiện tại. Kiểm tra phạm vi yêu cầu và độ phủ dịch vụ trước khi chuyển sang HR.
