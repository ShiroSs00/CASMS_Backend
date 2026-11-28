# Kế hoạch triển khai BE-01 đến BE-08

Nguồn nghiệp vụ là [đặc tả](spec.md). Nền triển khai là `origin/main` commit `43b19e2`. Backend sử dụng Java 21, Spring Boot 3.3.4, Spring MVC, Spring Data JPA, Flyway và PostgreSQL. Repository tại commit này dùng một module Maven và không kèm Maven Wrapper.

## Loại tàu và tàu thực tế

- Thêm quan hệ tùy chọn `Ship.shipType` để giữ dữ liệu tàu lịch sử. Loại tàu đang hoạt động có gói model đầy đủ. Migration V121 thêm khóa ngoại từ loại đến manifest, cùng ràng buộc duy nhất cho IMO và số đăng ký theo quốc gia.
- Admin tạo hoặc thay phiên bản model bằng gói ZIP. Các tàu đã tạo giữ phiên bản đang dùng. Quản lý tàu tạo tàu từ loại đang hoạt động, dùng chung dữ liệu asset và dựng cấu trúc, ánh xạ riêng theo tàu trong cùng giao dịch.
- Quản lý tàu nhập hồ sơ trước khi xác nhận tạo tàu theo loại. Dùng chung kiểm tra hồ sơ và hạn đăng kiểm tại bước xác nhận để tạo tàu ở READY. Hồ sơ tàu cũ vẫn đọc và sửa theo phạm vi tàu. Hai đường dẫn tạo tàu trực tiếp cũ trả 410.
- Kiểm tra hết hạn định kỳ theo ngày Việt Nam. Gửi thông báo trong ứng dụng có định danh chống trùng. Chuyển tàu đủ điều kiện sang MAINTENANCE khi không còn chuyến IN_PROGRESS.

## Danh mục chuyến đi

- V122 khởi tạo năm quốc gia và ba miền. Giữ cấu trúc API danh mục lập kế hoạch, thêm thao tác Admin bật và tắt.
- BE-09 bổ sung thao tác tạo quốc gia trong cùng danh mục và dùng khóa chính hiện tại để bảo vệ mã trùng. Luồng lập chuyến tiếp tục đọc các mục ACTIVE.
- Chặn lựa chọn địa lý không khả dụng khi tạo chuyến. Lịch nháp không còn khả dụng báo lỗi tạo lại. Tắt Việt Nam và ba miền trong cùng giao dịch.

## Xác minh

Chạy test tập trung theo [bằng chứng QA](qa.md), kiểm tra migration và luồng nhập qua PostgreSQL cô lập khi có môi trường, cập nhật inventory và sequence diagrams.
