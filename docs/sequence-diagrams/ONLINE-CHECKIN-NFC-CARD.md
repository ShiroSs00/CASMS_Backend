# Check-in online và cấp thẻ NFC

Khách dùng phiên đăng nhập hiện có. Check-in online mở từ đúng 3 giờ trước giờ khởi hành dự kiến và đóng khi đến giờ khởi hành. Luồng booking tiếp tục tra cứu theo mã booking cùng email hoặc số điện thoại. Dịch vụ email xác nhận vẫn xử lý gửi chi tiết thanh toán.

```mermaid
sequenceDiagram
    actor Guest as Hành khách
    actor POS as Nhân viên / iPhone
    participant Security as JWT và phiên POS
    participant Controller as Check-in / POS controllers
    participant Service as CheckinService / PassengerCardService
    participant Repo as Repository / JDBC
    participant DB as PostgreSQL
    Guest->>Security: Bắt đầu và hoàn tất check-in
    Security->>Controller: Kiểm tra quyền và principal
    Controller->>Service: Kiểm tra chủ hồ sơ và cửa sổ giờ
    Service->>Repo: Khóa hồ sơ hành khách và đọc booking
    Repo->>DB: Đọc giờ khởi hành và trạng thái
    alt Ngoài cửa sổ hoặc thiếu điều kiện hoàn tất
        Service-->>Guest: Trả lỗi, giữ nguyên hồ sơ
    else Đủ điều kiện
        Service->>Repo: Lưu trạng thái BoardingReady
        Repo->>DB: UPDATE trong giao dịch
        Service-->>Guest: Trạng thái check-in
    end
    Guest->>Controller: Tạo mã nhận thẻ sau check-in
    Controller->>Service: Kiểm tra khách sở hữu hồ sơ và chưa được cấp thẻ
    Service->>Repo: Lưu băm mã và hạn sử dụng
    Repo->>DB: Ghi mã nhận thẻ trong giao dịch
    Controller-->>Guest: Mã nhận thẻ và hạn
    POS->>Security: Quét mã và gửi preview với JWT và X-POS-Session
    Security->>Controller: Kiểm tra nhân viên và phiên đúng chuyến
    Controller->>Service: Tra cứu quyền nhận thẻ
    Service->>Repo: Kiểm tra mã, hạn và phạm vi chuyến
    Controller-->>POS: Hồ sơ tối thiểu để đối chiếu
    Note over POS: Đọc mã thẻ NFC bằng Core NFC rồi nhân viên xác nhận
    POS->>Controller: Gửi mã nhận thẻ và định danh NFC
    Controller->>Service: Kiểm tra lại phiên POS và khóa mã nhận thẻ
    Service->>Repo: Kiểm tra trùng và ghi người cấp, thời điểm
    Repo->>DB: INSERT thẻ và tiêu thụ mã trong cùng giao dịch
    alt Mã hết hạn, mã dùng lại, sai chuyến hoặc trùng thẻ
        Service-->>POS: Từ chối và không cấp thẻ
    else Thành công
        Controller-->>POS: Kết quả cấp thẻ
    end
    POS->>Controller: Chạm thẻ và tra cứu bằng mã NFC
    Controller->>Service: Giới hạn tra cứu theo chuyến của phiên POS
    Controller-->>POS: Hồ sơ tối thiểu của thẻ đã cấp
```
