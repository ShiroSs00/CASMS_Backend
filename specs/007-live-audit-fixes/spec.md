# 007 · Khắc phục lỗi từ hai báo cáo kiểm thử CASMS

## US1 · Quản lý hoạt động và dịch vụ
- R01. Tìm yêu cầu theo ghi chú, mã định danh yêu cầu/dịch vụ/đặt chỗ/hành khách và tên/mã dịch vụ khi quyền hiện tại cho phép đọc danh mục. Giữ bộ lọc trạng thái và khả năng tìm bằng mã nếu danh mục lỗi.
- R02. Khi sửa đăng ký PENDING, tải đúng ghi chú và số chỗ từ chi tiết. Sửa số chỗ giữ nội dung cũ, xóa chủ động vẫn được. Tách khỏi số chỗ khi đăng ký mới.
- R03. Giữ ngày giờ đã nhập khi đổi chuyến hoặc cảng, báo ngoài khoảng chuyến theo múi giờ hiện hành trước khi gửi. Giữ xác thực server và khóa gửi trùng.
- R04. Làm rõ khả năng đáp ứng, lý do đặt chỗ đã xác nhận chỉ xem được gói, và cách kiểm tra thao tác chưa rõ kết quả. Thống nhất nhãn tour trong màn đăng ký.
- R05. Đổi chế độ danh sách hoạt động đưa focus tới vùng danh sách. Giữ bảng cuộn riêng trên tablet và thao tác bằng bàn phím.

## US2 · Quản lý lịch trình và tàu
- R06. Phân biệt nguồn lịch trình trống, tìm kiếm trống, trang rỗng và lỗi tải. Tận dụng link phân quyền tới Chuyến đi. Giữ nguồn đang chọn khi truy vấn thay đổi.
- R07. API tàu trả kết quả thành công rỗng khi tàu tồn tại và không có chuyến đang chạy. Tàu không tồn tại vẫn 404. Web Admin đọc được phản hồi của cả hai phiên bản backend.

## US3 · Khách Web Booking
- R08. API thiếu tham số query bắt buộc trả 400 với mã ổn định, giữ các kiểm tra tham chiếu và xác thực hiện tại.
- R09. Danh mục trống có hành động phù hợp với bộ lọc, phân trang hoặc hỗ trợ. Tra cứu gắn lỗi vào mã và/hoặc nhóm liên hệ tùy nguyên nhân, giữ dữ liệu đã nhập và dùng cùng thông báo cho sai mã hoặc sai thông tin liên hệ.
- R10. Quyền lợi yêu cầu đặt lịch được gắn badge cảnh báo dựa trên cờ API.

## Giới hạn
V17 đã hợp nhất ASM. Giữ nguyên phân quyền Admin/ASM, yêu cầu dịch vụ do hành khách tạo và các trạng thái Draft/Confirmed. Tìm tên hành khách/cabin của ASM cần xác nhận phạm vi đọc dữ liệu cá nhân riêng. Giữ Mobile App, migration đã áp dụng và dữ liệu môi trường ngoài local. Dùng thuật ngữ trong các màn thuộc phạm vi này.

## Nghiệm thu
Kiểm tra build hai web, test Backend và ca dữ liệu hồi quy. Kiểm tra thiếu tham số qua HTTP, các trạng thái 404/rỗng, bộ lọc và ghi chú trên browser desktop/tablet nếu có fixture phù hợp.
