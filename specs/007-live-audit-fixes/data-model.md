# Dữ liệu và trạng thái

- ServiceRequest: ID, serviceId, bookingId, bookingPassengerId, passengerId, note và status. Tên/mã dịch vụ đến từ danh mục được phép đọc.
- Registration: quantity, notes nullable, status và target. Chỉ cập nhật PENDING theo hợp đồng hiện tại. Ghi chú rỗng chủ động là thao tác hợp lệ.
- Cruise: departureDatetime, returnDatetime và trạng thái. Phạm vi thời gian kiểm tra theo instant sau khi đổi giờ tàu hoặc giờ cảng.
- EligibleSource: content, totalElements và totalPages. Phân biệt kết quả trống và trang không còn kết quả.
- BookingLookup: reference và email hoặc phone. Cùng một phản hồi UI cho các trường hợp thông tin tra cứu không khớp.
