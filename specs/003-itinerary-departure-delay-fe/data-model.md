# Mô hình trạng thái FE

| Trạng thái | Dữ liệu logic | Quy tắc |
|---|---|---|
| Chi tiết chuyến | cruiseId, trạng thái, giờ dự kiến/thực tế, lịch và gate | Đọc từ backend. Lịch mẫu và lịch vận hành hiển thị riêng |
| Bản nhập delay | Đề xuất dời giờ, lý do, category | Ràng buộc trường và danh mục theo API đã xác minh |
| Preview | Lịch trước/sau, countdown, revision, blockers | Gắn với đầu vào hiện tại. Thay đầu vào, revision mới hoặc countdown về 0 làm preview mất hiệu lực |
| Confirm intent | cruiseId, UUID, payload và revision đã preview | Lưu trước khi gửi theo tài khoản và chuyến trong sessionStorage. Giữ nguyên toàn bộ payload qua reload cùng tab khi chưa rõ kết quả. Chỉ mở intent mới sau khi xác định kết quả |
| Lịch sử | Mốc ban đầu/dự kiến/thực tế, tổng phút, sự kiện | Đọc lại từ API sau confirm. Không lưu dự đoán như lịch thật |

Luồng: nhập → preview → bị chặn hoặc có thể xác nhận → đang gửi → thành công, lỗi xác định hoặc chưa rõ kết quả. Countdown khóa xác nhận mới khi về 0. Backend kiểm tra lại lúc confirm. Kiểm tra lại intent cũ không phụ thuộc countdown hoặc revision hiện tại.
