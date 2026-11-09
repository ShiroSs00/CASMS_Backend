# Kiểm chứng

- Chạy test Maven chọn lọc cho MVC, ShipService và luồng đăng ký. Kiểm tra GET /api/booking/lookup không có reference qua backend local sau khi cập nhật.
- Chạy npm run build tại CASMS_WebAdmin và CASMS_WebBooking. Chạy suite booking hiện có.
- Web Admin với fixture ASM: tìm mã/tên dịch vụ và tìm lại khi danh mục lỗi, chọn đăng ký có ghi chú, đổi số chỗ rồi mở lại. Kiểm tra giữ ghi chú và xóa có chủ đích. Đổi chế độ hoạt động bằng bàn phím và kiểm tra vùng cuộn tablet.
- Với fixture quản lý lịch trình: đổi chuyến trong form lịch dịch vụ, thử giờ hợp lệ và ngoài khoảng chuyến. Mở picker khi trống, tìm sai và thử lại khi API lỗi.
- Web Booking: danh mục trống/bộ lọc rỗng, tra cứu thiếu trường và thông tin không khớp, xem gói có và không cần đặt trước trên desktop và mobile.
