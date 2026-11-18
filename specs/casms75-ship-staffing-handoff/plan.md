# Kế hoạch triển khai

- Mở rộng API yêu cầu nhân sự từ main và tái sử dụng dữ liệu tàu, cabin, cấu hình POS và phân công hiện hành.
- Đặt kiểm tra đầy đủ ở bước xác nhận tương ứng, trong giao dịch chuyển trạng thái. Dùng revision và khóa hiện hành để kiểm tra yêu cầu cũ và chuyển trạng thái đồng thời.
- Cập nhật quyền truy cập theo vai trò và phạm vi chuyến. Bổ sung thông báo ở bước tự động chuyển sang HR.
- Duy trì bước duyệt của vận hành và tương thích với yêu cầu cũ. Kiểm thử chuyển trạng thái, quyền và các trường hợp thiếu dữ liệu.

- Tái sử dụng hồ sơ ASM để giao việc, tiếp nhận và hoàn tất chương trình. Điều phối tự động sang bước tàu trong cùng giao dịch chuyển trạng thái.
- Tái sử dụng cấu hình khu vực và liên kết POS hiện tại. Kiểm tra phạm vi yêu cầu và độ phủ dịch vụ trước khi chuyển sang HR.
