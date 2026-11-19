# POS cấp thẻ trên iPhone

Mở PassengerCardPOS.xcodeproj bằng Xcode trên macOS, chọn Team trong Signing & Capabilities và đặt bundle identifier phù hợp hồ sơ ký. Hồ sơ provisioning cần quyền Near Field Communication Tag Reading. Chạy trên iPhone thật từ iOS 16 với camera và NFC.

Đăng nhập bằng tài khoản nhân viên đã có quyền POS_ACCESS và CHECKIN_BOARDING. Nhập URL HTTPS có hậu tố /api, terminal UUID, cruise UUID và thông tin xác thực thiết bị được cấp cho terminal. Không dùng dữ liệu khách thật để nghiệm thu.

App khách gọi POST /api/checkin/passenger/{passengerId}/card-claim sau check-in và hiển thị nguyên chuỗi data.code trong QR. Khi camera khả dụng, nhân viên quét QR trên iPhone. Nhân viên cũng có thể nhập mã bằng tay. Tra cứu tên, chạm thẻ NFC, đối chiếu khách rồi xác nhận. Sau đó thử chạm thẻ để tra cứu từ đúng phiên POS.

Bản iOS này gửi mã định danh thẻ ở dạng hex, không ghi dữ liệu lên thẻ. Dùng thẻ mẫu có UID ổn định theo chuẩn mà iPhone đọc được. Khả năng đọc mẫu thẻ phải được xác nhận trên thiết bị thật.

Để nghiệm thu cả hai phía, dùng tab Hành khách trên một thiết bị và tab POS trên iPhone thứ hai. Tab Hành khách dùng UUID của bản ghi hành khách trong booking và hiển thị QR sau khi check-in thành công. Tích hợp app khách hiện hữu bằng API cấp mã ở trên. Trang booking và gửi email xác nhận tiếp tục dùng luồng booking hiện tại.
