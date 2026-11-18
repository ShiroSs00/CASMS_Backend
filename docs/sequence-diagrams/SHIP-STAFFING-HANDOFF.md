# Bàn giao chương trình, kiểm tra tàu và bố trí nhân sự

Các URL dưới đây tính từ context path của API. Dùng [xác thực Bearer](BEARER-AUTHENTICATION.md) và principal hiện hành để xác định vai trò và phạm vi chuyến. Gửi `expectedRevision` từ yêu cầu vừa đọc khi xác nhận hoặc trả lại.

```mermaid
sequenceDiagram
    actor Planner as Quản lý lịch trình
    actor ASM as Quản lý dịch vụ và hoạt động
    actor Ship as Quản lý tàu
    actor HR as Nhân sự
    actor Ops as Vận hành
    participant API as StaffingWorkflowController
    participant WF as StaffingWorkflowService
    participant Handoff as CruiseWorkHandoffService
    participant DB as PostgreSQL
    Planner->>API: Gửi yêu cầu và revision lịch trình
    API->>WF: submit
    WF->>DB: Khóa chuyến và tạo yêu cầu
    alt Chương trình cần bổ sung
        WF->>Handoff: Giao việc cho ASM
        WF-->>Planner: PENDING_ASM và revision
        ASM->>Handoff: Nhận việc và bổ sung chương trình
        ASM->>Handoff: Hoàn tất công việc
        Handoff->>DB: Kiểm tra chương trình trong giao dịch
        alt Chương trình còn thiếu
            Handoff-->>ASM: Phản hồi lỗi và giữ yêu cầu
        else Chương trình đạt
            Handoff->>WF: Tự chuyển sang quản lý tàu
            WF->>DB: Ghi PENDING_SHIP_REVIEW và lịch sử
        end
    else Chương trình đã hoàn tất và còn hợp lệ
        WF->>DB: Ghi PENDING_SHIP_REVIEW
        WF-->>Planner: Yêu cầu chờ quản lý tàu
    end
    Ship->>API: Đọc inbox và review-details
    API->>WF: inbox và reviewDetails
    WF->>DB: Đọc chuyến, cabin, khu vực và terminal
    WF-->>Ship: Dữ liệu rà soát và dịch vụ trên tàu
    Ship->>API: PUT service-placements theo dịch vụ
    API->>WF: Kiểm tra phạm vi yêu cầu và trạng thái
    WF->>DB: Lưu liên kết mới, giữ các cấu hình đã bị tắt
    Ship->>API: SHIP_CHECKED
    API->>WF: transition
    WF->>DB: Khóa chuyến và yêu cầu, kiểm tra POS
    alt Còn khu vực hoặc dịch vụ trên tàu thiếu bố trí POS
        WF-->>Ship: 409
    else Đủ POS
        WF->>DB: Ghi SENT_TO_HR, lịch sử và thông báo quản lý chuyến
        WF-->>Ship: SENT_TO_HR và revision mới
    end
    HR->>API: HR_SUBMITTED sau khi bố trí
    API->>WF: transition
    WF->>DB: Kiểm tra số lượng, khu vực và hoạt động
    alt Thiếu nhân sự
        WF-->>HR: 409
    else Đủ nhân sự
        WF->>DB: Ghi HR_SUBMITTED và lịch sử
        WF-->>HR: Revision mới
    end
    Ship->>API: Đọc roster theo nhân viên và SHIP_CONFIRMED
    API->>WF: reviewDetails và transition
    WF->>DB: Đọc roster, kiểm tra và ghi SHIP_CONFIRMED
    HR->>API: HR_APPROVED hoặc trả lại một phần
    API->>WF: transition
    alt Duyệt HR
        WF->>DB: Ghi HR_APPROVED, phát hành vào inbox vận hành
        Ops->>API: Đọc inbox và OPS_APPROVED
        API->>WF: transition
        WF->>DB: Kiểm tra lại và ghi OPS_APPROVED
    else HR_RETURN_SHIP kèm lý do
        WF->>DB: Ghi PENDING_SHIP_REVIEW
    else HR_RETURN_STAFF kèm lý do
        WF->>DB: Ghi SENT_TO_HR
    end
```

Khi bị trả lại, đọc revision mới và thực hiện tiếp từ phần được chọn. Các thao tác đang xử lý cùng lúc hoặc dùng revision đã cũ bị kiểm tra trước khi ghi. Lịch sử lưu người thao tác và lý do.

## Điều kiện kiểm tra và trường hợp phải làm lại

- Quản lý lịch trình phải là quản lý chuyến phụ trách. Quản lý tàu phải là người quản lý tàu theo hồ sơ tàu hoặc được phân công quản lý tàu cho chuyến. Dùng API cabin theo quyền hiện hành. Đọc thông báo bằng API thông báo cá nhân.
- Kiểm tra tàu yêu cầu tàu có trạng thái READY hoặc IN_SERVICE, có cabin AVAILABLE hoặc OCCUPIED và có mô hình READY hiện hành. Mỗi khu vực phục vụ trong mô hình cần ít nhất một POS đang bật và liên kết đang bật. Lần xác nhận lưu cấu hình POS đã kiểm tra. Nếu mô hình, khu vực hoặc liên kết thay đổi, các bước duyệt tiếp theo yêu cầu quản lý tàu kiểm tra lại. HR có thể trả về phần tàu từ hàng chờ HR hoặc sau khi rà soát và có thể mở lại kết quả đã duyệt nếu cấu hình tàu đã thay đổi.
- HR dùng API phân công theo khu vực và ca hoạt động. Khi gửi, hệ thống kiểm tra định mức dịch vụ, ít nhất một bố trí khu vực còn hiệu lực cho mỗi khu vực phục vụ và ít nhất một người đang hoạt động cho mỗi hoạt động đã lên lịch. Nhân viên có thể có nhiều nhiệm vụ hoặc chưa được gắn khu vực.
- Bố trí khu vực thay đổi sau lần gửi HR có thể đưa yêu cầu về SENT_TO_HR và vô hiệu hóa kết quả kiểm tra SAFETY_CREW. Các API chuẩn bị, mở bán và khởi hành sử dụng lại điều kiện nhân sự đã được vận hành duyệt.
- Yêu cầu đã tồn tại trước migration tiếp tục theo nhánh có PLANNER_CONFIRMED. Yêu cầu mới có shipReviewWorkflow và sử dụng bước HR_APPROVED trước vận hành.

## Điểm tích hợp giao diện

1. Quản lý lịch trình dùng POST /cruises/{cruiseId}/staffing-requests. Trong giai đoạn PENDING_ASM, mở hàng chờ của ASM bằng GET /cruise-work/inbox.
2. ASM dùng PUT /cruise-work/{cruiseId}/ASM với ACCEPT và phiên bản công việc. Bổ sung ca phục vụ, hoạt động trên tàu và tour bờ trong nhóm API /cruise-plans/{cruiseId}. Gửi COMPLETE kèm phiên bản hiện hành và nội dung hoàn tất để tự bàn giao.
3. Quản lý tàu dùng GET /cruises/staffing-requests/inbox và mở review-details. Gửi PUT area-pos với terminalId và expectedRevision hoặc PUT service-placements với areaId, terminalId và expectedRevision. Dùng revision trả về từ lần thay đổi gần nhất khi xác nhận SHIP_CHECKED.
4. Sau khi kiểm tra độ phủ POS theo khu vực và dịch vụ, hệ thống ghi SENT_TO_HR và thông báo cá nhân cho quản lý chuyến. Điều kiện bố trí dịch vụ được kiểm tra lại trong các bước duyệt nhân sự.
