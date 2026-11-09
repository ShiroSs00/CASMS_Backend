# Đối chiếu hai báo cáo

- V17 chuyển các vai trò cũ thành ASM. Không sửa V3 hay thêm seed mật khẩu. Việc Admin thuần bị chặn là ranh giới vai trò hiện hành.
- Tạo yêu cầu cho khách cần nghiệp vụ và quyền riêng. Endpoint hiện tại gắn hành khách với người đang đăng nhập. POS là luồng riêng.
- Danh sách yêu cầu có các ID và ghi chú. ASM có SERVICE_READ nhưng thiếu BOOKING_READ. Dùng metadata danh mục với quyền hiện tại, không truy xuất thêm thông tin hành khách/cabin.
- RegistrationView đã trả notes và API cập nhật nhận notes. FE bỏ qua dữ liệu khi mở bản ghi. Điền từ chi tiết và tách trạng thái biểu mẫu mới/sửa.
- Thiếu query reference bị Spring MVC chặn trước nghiệp vụ; bắt exception 400 với mã và tên tham số. Trường hợp reference rỗng tiếp tục xử lý theo nghiệp vụ hiện hành.
- Với tàu rảnh, trả success và dữ liệu rỗng theo envelope hiện tại. Giữ fallback lỗi 404 cũ của Web Admin cho triển khai FE trước BE.
- Danh mục booking và picker Admin đã có các trạng thái trống cơ bản. Nâng cấp hành động phục hồi, giữ tham số và lựa chọn. Thất bại tra cứu vẫn dùng thông báo thống nhất vì liên quan xác thực khách.
- Shared DataTable đã có vùng cuộn ngang và focus. Giữ cấu trúc bảng và giới hạn overflow trong màn thao tác thay vì nhân đôi dữ liệu thành thẻ.
- Sửa copy trong các bề mặt được xác minh. Giữ các từ đã phân biệt theo ngữ cảnh Chuyến đi, Chuyến khởi hành và Lịch trình.
