# Đặc tả check-in online và cấp thẻ NFC

**Ngày:** 2026-11-19
**Trạng thái:** Luồng được duyệt qua trao đổi với người dùng

## Kịch bản nghiệm thu

1. **AC1 · Check-in online.** Khách đăng nhập bằng tài khoản hiện có. Hệ thống dùng giờ khởi hành dự kiến hiện hành. Với booking xác nhận và đủ điều kiện, khách có thể bắt đầu và hoàn tất từ đúng T−3 giờ, trước giờ khởi hành. Yêu cầu quá sớm bị từ chối. Hoàn tất giữ nguyên điều kiện hợp đồng, liên hệ khẩn cấp và quyền sở hữu.
2. **AC2 · Mã nhận thẻ.** Khách đã hoàn tất check-in có thể tạo mã nhận thẻ có hạn dùng. Mã thuộc một hành khách của một chuyến. App hiển thị mã cho nhân viên; không dùng mã đặt chỗ cho thao tác cấp thẻ.
3. **AC3 · Cấp thẻ.** Nhân viên đăng nhập POS gắn với đúng chuyến, tra cứu mã nhận thẻ, đọc thẻ NFC thật bằng iPhone, đối chiếu tên và chủ động xác nhận. Backend liên kết mã thẻ với hành khách và chuyến và ghi người thực hiện, thời điểm.
4. **AC4 · Bảo toàn cấp thẻ.** POS khác chuyến, mã hết hạn, mã đã dùng và thẻ đã cấp cho khách khác trong chuyến bị từ chối. Cấp thẻ đồng thời chỉ tạo một liên kết. POS đúng chuyến có thể dùng thẻ đã cấp để tra cứu hồ sơ tối thiểu.
5. **AC5 · Booking.** Mã booking cùng email hoặc điện thoại tiếp tục tra cứu thông tin đặt chỗ và thanh toán. Email xác nhận dùng luồng gửi chi tiết thanh toán hiện có. Check-in sử dụng quyền truy cập của chuyến trên tài khoản đăng nhập hiện có.

## Phụ thuộc nghiệm thu

- App POS iOS có quyền Core NFC. Loại chip của thẻ mẫu chưa được cung cấp; nghiệm thu cần iPhone và thẻ tương thích trên thiết bị thật.
- Giờ khởi hành là mốc đóng cửa sổ online. Cấp thẻ sau check-in hoạt động theo phiên POS của chuyến.
