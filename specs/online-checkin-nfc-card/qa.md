# Bằng chứng QA

## Maven

```powershell
mvn -o -B -ntp "-Dtest=CheckinServiceTest,PassengerCardServiceTest,PosAccessServicePlacementTest,RolePermissionInitializerTest,MigrationChecksumLockTest,BookingLookupContractTest" test
```

Java 21, datasource trỏ loopback và Flyway tắt. **30/30 đạt**, 0 lỗi assertion, 0 lỗi thực thi, 0 bỏ qua.

| Điều kiện | Kết quả |
| --- | --- |
| AC1 · Start/complete ở T−3h−1s, T−3h, T−3h+1s, T | 4 ca dùng đồng hồ cố định đạt. Khách đã hủy bị từ chối trước khi ghi hoặc trả lại trạng thái hoàn tất. |
| AC2 · Mã nhận thẻ | Test chủ hồ sơ, yêu cầu check-in hoàn tất và chỉ lưu băm đạt. |
| AC3–AC4 · POS và cấp thẻ | Test chuẩn hóa ID, lưu người cấp, từ chối sai chuyến và phát lại đạt bằng mock. Khóa checksum V138 đạt. Chưa chạy thử giao dịch đồng thời trên PostgreSQL. |
| AC5 · Booking | Contract test tra cứu booking đạt. Chưa gửi email thật trong lượt kiểm thử. |

## iPhone

Đã kiểm tra tham chiếu của 5 tệp Swift trong project, quyền NFC và mô tả quyền camera. Chưa build Xcode hoặc đọc thẻ trên iPhone vì môi trường hiện tại là Windows. Nghiệm thu trên macOS và iPhone thật cần kiểm tra QR, khả năng đọc ổn định của chip, xác nhận cấp thẻ và tra cứu thẻ từ phiên POS.

## Tài liệu API

`git diff --check` đạt. Sequence checker còn 73 route và 533 sơ đồ thiếu ở phạm vi toàn repo, giảm 2 sơ đồ so với baseline 535. Bốn endpoint mới và hai endpoint check-in thay đổi đều có tài liệu. Không có lỗi cấu trúc Mermaid.
