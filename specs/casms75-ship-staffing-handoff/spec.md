# CASMS-75 — Bàn giao chương trình, phương án tàu và nhân sự V2

## Hợp đồng hiện hành

Mỗi thao tác dùng revision hiện tại của yêu cầu. Các chuyển trạng thái tăng revision, ghi action, actor và lý do vào `StaffingDecision`. Hoàn tất ASM ghi bằng chứng ở handoff và ở lịch sử staffing. Chuyến và yêu cầu được khóa trong cùng giao dịch.

| Thao tác | Vai trò và phạm vi | Đầu vào và điều kiện | Kết quả và người tiếp nhận |
| --- | --- | --- | --- |
| Tạo yêu cầu | CRM phụ trách chuyến, `CRUISE_PREPARATION_SUBMIT` | Revision chuyến khớp, có ngân sách VND dự kiến, không có yêu cầu đang mở | `PENDING_ASM` → ASM. Nếu chương trình ASM đã hoàn tất và vẫn hợp lệ, tái sử dụng và chuyển thẳng `PENDING_SHIP_REVIEW` → Ship |
| Nhận và hoàn tất chương trình | ASM có `TOUR_PACKAGE_MANAGE`, người đã tiếp nhận hoàn tất với revision của handoff | `PENDING_ASM`, chương trình và lịch hợp lệ, có nội dung phản hồi | `PENDING_SHIP_REVIEW` → Ship được phân công |
| `SHIP_CHECKED` | Ship Manager là thành viên ACTIVE của chuyến, `CRUISE_PREPARATION_READ`, khác CRM gửi | `PENDING_SHIP_REVIEW`, tàu và model hiệu lực, chương trình đã hoàn tất, khu vực SERVICE đã mở có người cần bố trí và POS hợp lệ, dịch vụ onboard có placement | `SHIP_REVIEWED` → CRM. Lưu fingerprint và người kiểm tra |
| `SEND_TO_HR` | CRM phụ trách chuyến | `SHIP_REVIEWED`, phương án tàu còn hiệu lực | `SENT_TO_HR` → HR |
| `HR_SUBMITTED` | HR, `CRUISE_STAFF_ASSIGN`, độc lập với người gửi và người kiểm tàu | `SENT_TO_HR`, đủ phân công hợp lệ theo nhu cầu và ca hoạt động | `HR_SUBMITTED` → CRM |
| `PLANNER_CONFIRMED` | CRM phụ trách chuyến, khác Ship và HR đã xử lý | `HR_SUBMITTED`, phương án và độ phủ vẫn hợp lệ | `PLANNER_CONFIRMED` → OPS |
| `OPS_APPROVED` | OPS, `CRUISE_VALIDATE`, độc lập với người gửi, Ship và HR | `PLANNER_CONFIRMED`, phương án và độ phủ vẫn hợp lệ | `OPS_APPROVED`, revision duyệt khớp revision yêu cầu |

## Trả lại và hiệu lực dữ liệu

- Ship dùng `SHIP_RETURN_ASM` từ `PENDING_SHIP_REVIEW` để mở lại handoff ASM. HR dùng `HR_RETURN_SHIP` từ `SENT_TO_HR`. CRM dùng `CRM_RETURN_SHIP` từ `SHIP_REVIEWED`, `HR_SUBMITTED`, `OPS_APPROVED`, hoặc `PLANNER_CONFIRMED` khi phương án đã cũ.
- Các đường về ASM/Ship xóa phân công theo nhu cầu của yêu cầu và bỏ fingerprint kiểm tàu. Nhu cầu còn cho tới khi Ship lưu lại phương án. Lưu phương án thay toàn bộ nhu cầu và cascade phân công thuộc các nhu cầu đó. Membership và duty thuộc cơ chế riêng.
- CRM dùng `CRM_RETURN_HR` từ `HR_SUBMITTED` về `SENT_TO_HR`. OPS dùng `OPS_RETURN_CRM` từ `PLANNER_CONFIRMED` về `HR_SUBMITTED`. Hai đường giữ phương án và phân công. Mọi đường trả cần lý do. Trả khỏi `OPS_APPROVED` làm mất hiệu lực bằng chứng chuẩn bị liên quan staffing.
- Chuyến có thể dùng phương án rỗng nếu chương trình không có dịch vụ onboard cần bố trí ở khu vực SERVICE. Nếu mở khu vực SERVICE, phải có nhu cầu và POS tương ứng. Ca hoạt động vẫn được kiểm tra khi HR nộp phương án và ở các bước duyệt sau.
- ASM và staffing dùng model READY của đúng tàu theo `StaffingShipModel`. Ưu tiên model có asset key, rồi mới nhất theo created_at và ID. Đề nghị boong của ASM và cấu hình đủ điều kiện cho placement là hai dữ liệu riêng. Thay đổi cấu hình khiến phương án mất hiệu lực khi không còn đáp ứng gate hoặc fingerprint đã đổi.

## Bốn quyết định của chuyến

Duyệt staffing, nộp/duyệt hồ sơ chuẩn bị, duyệt Cruise Plan và mở bán có quyền, bằng chứng và trạng thái riêng. OPS cũng có thao tác Ready riêng của Cruise Plan. Ship kiểm tra phương án với `SHIP_CHECKED`, và nộp `TECHNICAL_NAUTICAL` qua API preparation. CRM nộp `HOTEL_CABIN` và `ENTERTAINMENT_SHORE`. OPS đủ phạm vi nộp `SAFETY_CREW`. Người duyệt hồ sơ là OPS khác người nộp.

## LEGACY — tiêu chí lịch sử

Các AC dưới đây ghi lại thiết kế cũ. Chúng không mô tả quyền hay chuyển trạng thái V2 và không dùng để nghiệm thu yêu cầu V2.

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
