# Kế hoạch BE-09 đến BE-20

Nền `origin/main` tại `a37c4d5`. Thực hiện theo [đặc tả](spec.md) và [đối chiếu mã nguồn](code-review.md). Dùng Java 21, Spring Boot 3.3.4, Maven, JPA/JDBC và Flyway PostgreSQL hiện có.

## Hợp đồng và tương thích

- Mẫu có thể cấu hình một cặp mốc khởi hành dự kiến bao gồm hai biên. Cho phép dữ liệu mẫu cũ thiếu cả hai mốc. Thời lượng trôi qua và số ngày/đêm là các giá trị riêng. V123 bổ sung cặp mốc nguồn.
- Tìm tàu và các thao tác ghi lịch dùng dữ liệu sức chứa, cấu hình, trạng thái và xung đột lịch. Chuyến mới yêu cầu số khách dương, nhận tên trường cũ cùng `expectedPassengers`. Có thể lưu chuyến trước khi gán tàu.
- Kiểm tra cửa sổ hoạt động ở đường lập lịch thực tế, áp dụng hai khoảng đệm ở cảng cuối và giữa chuyến. Mẫu có khung mới kiểm tra khi chiếu lịch; mẫu cũ giữ khả năng đọc và sử dụng. V124 lưu các nhóm giờ dịch vụ và định danh cảnh báo OPS. Cảnh báo là thông báo trong ứng dụng, kiểm tra mặc định mỗi 60 giây và chống gửi trùng theo cảng, pha, người nhận.
- Dùng lại bốn phần hồ sơ, hai nhóm handoff ASM/SHIP và tổng ngân sách VND nhập thủ công. V125 bổ sung vòng duyệt ngân sách, quyền nộp theo phần và thông tin trạng thái/revision trước đó.

## Giới hạn của đợt này

Khung khởi hành được áp dụng với giờ dự kiến. Chưa có dữ liệu thời gian hành trình tối thiểu có thẩm quyền giữa các cảng. Bộ quy tắc đang kiểm tra thứ tự và trùng khoảng thời gian. Phân vai dựa trên các bộ phận và phân công hiện có.
