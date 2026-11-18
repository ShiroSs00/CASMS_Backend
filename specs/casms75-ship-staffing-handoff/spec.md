# CASMS-75 — Bàn giao kiểm tra tàu và bố trí nhân sự

Quản lý lịch trình gửi chuyến đã lập để quản lý tàu kiểm tra cabin và POS trước khi nhân sự bố trí người. HR quyết định bàn giao vận hành sau khi quản lý tàu rà soát nhân sự.

| ID | Tiêu chí chấp nhận |
| --- | --- |
| AC1 | Quản lý phụ trách chuyến gửi yêu cầu. Quản lý tàu được phân công thấy yêu cầu trong danh sách và xem thông tin tàu, cabin và POS theo khu vực. Dùng quyền cabin hiện có cho thao tác sửa được cấp. |
| AC2 | Chặn xác nhận khi khu vực đang phục vụ thiếu POS hoạt động. Khi xác nhận thành công, hệ thống tự chuyển sang chờ HR và thông báo quản lý chuyến. Phương án tự chuyển đã được người dùng xác nhận. |
| AC3 | HR bố trí một hoặc nhiều người đủ điều kiện cho mỗi khu vực phục vụ. Có thể giữ nhân viên trong danh sách chuyến mà chưa gắn khu vực. Mỗi hoạt động cần có người thực hiện. Chặn gửi khi thiếu người ở khu vực hoặc hoạt động. |
| AC4 | Quản lý tàu xem nhân sự theo từng người, kèm dịch vụ, khu vực và hoạt động, rồi xác nhận đã rà soát. |
| AC5 | HR duyệt để đưa vào hàng chờ vận hành hoặc trả lại đúng phần tàu hay nhân sự kèm lý do. Kiểm tra quyền, phạm vi chuyến, thứ tự chuyển trạng thái và phiên bản yêu cầu, đồng thời ghi lịch sử. |

## Bổ sung đã duyệt — bàn giao chương trình trước khi kiểm tra tàu

| ID | Tiêu chí chấp nhận |
| --- | --- |
| AC6 | Quản lý lịch trình gửi yêu cầu. Quản lý dịch vụ và hoạt động thấy yêu cầu, tiếp nhận và bổ sung dịch vụ, hoạt động trên tàu và chương trình tại cảng trong phạm vi chuyến. Quản lý tàu chỉ nhận việc khi giai đoạn này hoàn tất. |
| AC7 | Bấm hoàn tất chương trình sẽ tự chuyển yêu cầu sang quản lý tàu. Nếu dữ liệu chương trình chưa đạt các kiểm tra hiện hành, trả lỗi và giữ nguyên bước hiện tại. Tái sử dụng hồ sơ đã hoàn tất khi phù hợp với chuyến. |
| AC8 | Quản lý tàu bố trí dịch vụ của chuyến vào khu vực phục vụ trên tàu và gắn POS tại đó. Quyền ghi giới hạn theo yêu cầu và tàu đang được kiểm tra. Chặn xác nhận nếu dịch vụ hoặc khu vực phục vụ thiếu liên kết POS phù hợp. Chương trình tại cảng thuộc phần rà soát chương trình. |
| AC9 | Sau khi tàu xác nhận, hệ thống tự chuyển sang chờ HR và thông báo cho quản lý chuyến theo AC2. |
