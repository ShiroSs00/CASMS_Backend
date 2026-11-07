# Đối chiếu nguồn

- **Phân vai.** work-policy.ts định nghĩa CRUISE_MANAGER là Itinerary Coordinator. CRUISE_START đã xuất hiện trong chính sách công việc của cả hai vai trò, nên quyền này riêng lẻ không đại diện cho quyền delay. Dùng điều kiện đồng thời theo tài liệu đính kèm. Giữ nguyên phân quyền lifecycle hiện tại.
- **Vị trí.** CruiseDetailPage đã có khu Vận hành và nút bắt đầu vận hành. Đặt delay tại đây vì thao tác sửa lịch của Cruise cụ thể. Trình chỉnh mẫu và bước xét duyệt kế hoạch bán có vòng đời riêng.
- **Dữ liệu.** departure-api.ts đã gọi schedule và departure-readiness. Lịch dự kiến ở form chuyến giữ kiến trúc preview hiện có. Tính offset trong trình duyệt sẽ dễ lệch backend tại ranh giới ngày, mẫu thiếu mốc giờ và cảng khác múi giờ.
- **Bằng chứng hợp đồng.** Backend main tại 7d13ce4 chứa CruiseDelayController, CruiseDelayRequest và CruiseDelayService. Route delay chỉ khai báo dạng số nhiều /cruises, trong khi các route CruiseController cũ chấp nhận cả hai dạng. Category là chuỗi theo regex, không phải enum. Hợp đồng bên dưới được xác minh từ source, chưa kiểm thử trên môi trường chạy.
- **Phục hồi.** Khi kết quả confirm chưa rõ, giữ bản preview và intent gốc cho retry cùng payload hoặc đối soát lịch sử. Lấy preview mới sau khi biết revision đã cũ. Tránh tự động gửi lại mutation.
