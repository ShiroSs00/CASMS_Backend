# Kế hoạch

1. Dùng giờ khởi hành dự kiến từ booking và giữ các điều kiện check-in hiện có.
2. Lưu mã nhận thẻ dưới dạng băm, giới hạn thời gian và dùng một lần. Tách bước POS tra cứu và xác nhận.
3. Dùng phiên POS và quyền nhân viên hiện có. Khóa bản ghi check-in/cấp thẻ trong giao dịch, ràng buộc duy nhất thẻ theo chuyến tại PostgreSQL.
4. Cho iPhone đọc mã nhận thẻ bằng camera và đọc mã thẻ bằng Core NFC. Backend chỉ lưu định danh thẻ, thông tin khách lấy từ hồ sơ.
5. Kiểm tra biên thời gian, quyền, phiên sai chuyến, mã hết hạn, phát lại, xung đột cấp thẻ, và chạy checksum migration.
