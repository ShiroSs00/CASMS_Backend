# Đặc tả BE-01 đến BE-08 — Loại tàu và danh mục địa lý chuyến đi

**Ngày soạn** 2026-11-16
**Trạng thái** Đã được yêu cầu triển khai
**Nguồn** Yêu cầu BE-01 đến BE-08 và xác nhận bổ sung về phân bổ dữ liệu giữa tàu thực tế và loại tàu. Các hạng mục BE-02 đến BE-08 có mức P0.

## Kịch bản người dùng và tiêu chí nghiệm thu

| Hạng mục | Điều kiện, thao tác và kết quả mong đợi | Phạm vi backend và bằng chứng QA |
| --- | --- | --- |
| BE-01 · AC-01 | Admin tạo loại tàu bằng cách tải lên mô hình 3D chứa đầy đủ thông tin bắt buộc của loại. Hệ thống đọc và kiểm tra dữ liệu trong gói model ngay lần tạo đầu tiên. Nếu thiếu hoặc sai dữ liệu bắt buộc, API trả lỗi chỉ rõ trường và không lưu bản ghi loại tàu. Lần nhập hợp lệ tạo loại tàu với hồ sơ đầy đủ và giữ nguyên số bản ghi `Ship`. Các trường riêng theo chiếc được bổ sung trong luồng quản lý tàu. | Thử tải gói hợp lệ, thiếu trường và sai dữ liệu. Đọc lại loại tàu, đối chiếu dữ liệu với gói nguồn và kiểm tra số bản ghi `ShipType` và `Ship` trước/sau. |
| BE-02 · AC-02 | Khi Quản lý tàu tạo hai tàu từ cùng một loại, cả hai liên kết tới cùng loại. Sửa tên, đăng kiểm hoặc hồ sơ của một tàu giữ nguyên dữ liệu của tàu còn lại. | Quan hệ một-nhiều, khóa ngoại và kiểm tra độc lập dữ liệu trên PostgreSQL. |
| BE-03 · AC-03 | Hai tàu cùng loại dùng được revision 3D đã cấu hình. Cả hai tham chiếu cùng asset, mỗi tàu có mapping và dữ liệu Cabin/Deck/Area riêng. | Kiểm tra số bản ghi asset, cấu hình 3D và mapping theo từng tàu. Kiểm tra hồi quy luồng đọc 3D hiện có. |
| BE-04 · AC-04 | Với loại ACTIVE có model hợp lệ, Quản lý tàu chọn loại, tự nhập tên từng chiếc và có thể gắn hoặc để trống từng nhóm hồ sơ chứng nhận, bảo hành, bảo trì và sửa chữa khi tạo tàu ở trạng thái DRAFT. API lưu hồ sơ theo từng tàu và cho phép bổ sung sau. Loại không tồn tại, INACTIVE hoặc lỗi dựng cấu trúc tàu bị từ chối và giao dịch không để lại tàu dở dang. | Tạo tàu với hồ sơ đầy đủ và với từng nhóm để trống. Bổ sung hồ sơ, đọc lại liên kết theo từng tàu, kiểm tra quyền và rollback. |
| BE-04 · AC-04b | Khi yêu cầu đổi trạng thái tàu, hệ thống kiểm tra đủ ba nhóm hồ sơ chứng nhận, bảo hành và bảo trì, sửa chữa. Nếu thiếu, API liệt kê nhóm thiếu và giữ trạng thái hiện tại. Khi đủ hồ sơ, yêu cầu tiếp tục được đánh giá theo các quy tắc chuyển trạng thái hiện có. | Kiểm tra từng nhóm thiếu, nhiều nhóm thiếu, trạng thái giữ nguyên khi lỗi và chuyển trạng thái sau khi bổ sung đủ hồ sơ. |
| BE-05 · AC-05 | Admin có API tạo qua luồng tải lên mô hình 3D, xem danh sách, xem chi tiết, cập nhật, xóa và đổi trạng thái loại tàu. API tạo yêu cầu gói model hợp lệ với đầy đủ thông tin loại. Danh sách dùng khi tạo tàu chỉ hiển thị loại ACTIVE. Đề xuất cho phép xóa loại chưa được sử dụng. Với loại đã gắn tàu, Admin có thể vô hiệu hóa và tàu hiện có giữ liên kết. | Kiểm tra quyền Admin và Quản lý tàu, từ chối yêu cầu tạo thiếu model, lọc trạng thái và từ chối xóa loại đang sử dụng. |
| BE-06 · AC-06 | Hồ sơ tàu lưu riêng mã do nhà sản xuất cấp và mã định danh nội bộ. Hệ thống tự sinh mã nội bộ duy nhất theo định dạng quy định. Đề xuất dùng định dạng `SH` hiện có, kiểm tra IMO duy nhất toàn hệ thống và số đăng ký duy nhất theo quốc gia đăng ký. Hai yêu cầu ghi đồng thời cùng một định danh phải duy nhất chỉ có một yêu cầu thành công. | Đọc lại cả hai mã qua API, kiểm tra định dạng, trùng lặp, dữ liệu cũ và ràng buộc PostgreSQL. |
| BE-07 · AC-07 | Sau migration, các nước và ba miền trong danh mục hiện tại tồn tại trong database với mã, nhãn, thứ tự và mô tả hiện có. Danh mục lập kế hoạch đọc các mục ACTIVE và giữ cấu trúc phản hồi hiện tại. | Đối chiếu toàn bộ seed với danh sách hiện tại, chạy migration và kiểm tra API hồi quy. |
| BE-08 · AC-08 | Admin xem được cả mục ACTIVE và INACTIVE và bật/tắt từng mục. Tạo chuyến chỉ được chọn mục ACTIVE, kể cả qua API trực tiếp. Tắt Việt Nam đồng thời tắt Bắc Bộ, Trung Bộ và Nam Bộ. Lịch nháp đã chọn mục bị tắt báo lỗi và yêu cầu tạo lại. Chuyến và lịch trình đã phát hành giữ nguyên dữ liệu và tiếp tục xem được. | Kiểm thử RBAC, tạo chuyến với mục bị tắt, lỗi trên lịch nháp, dữ liệu đã phát hành và việc tắt cả ba miền khi tắt Việt Nam. |
| BE-09 · AC-09 | Admin thêm quốc gia bằng mã ISO 3166-1 alpha-2 và tên hiển thị. Backend chuẩn hóa mã, từ chối mã sai hay trùng, lưu quốc gia mới ở trạng thái ACTIVE và trả mục đó trong danh mục lập chuyến. Admin có thể tắt quốc gia qua API hiện có. Chuyến nội địa vẫn mặc định VN. | Thêm quốc gia mới, đọc danh mục đang hoạt động, thử mã sai, mã trùng và hai yêu cầu trùng. Sau đó tắt mục vừa tạo và kiểm tra danh mục. Kiểm tra quyền Admin và hồi quy phạm vi nội địa. |

## Phân bổ dữ liệu đã xác nhận

- **Loại tàu** giữ các thông tin còn lại, gồm nhà sản xuất, thông số thiết kế dùng chung, model 3D, mã và tên loại, trạng thái loại. Tất cả thông tin nguồn của loại phải có trong gói mô hình 3D ngay lần tải lên để tạo loại. Trạng thái là dữ liệu quản trị của hệ thống, được thao tác qua API quản lý loại.
- **Tàu thực tế** có các thông tin được bổ sung trong luồng quản lý tàu:
  - Mã tàu do nhà sản xuất cấp, mã nội bộ do hệ thống sinh, tên tàu do Quản lý tàu nhập và số IMO.
  - Nhà máy đóng tàu, quốc gia đóng tàu, năm đóng tàu.
  - Ngày bàn giao.
  - Quốc gia đăng ký và số đăng ký tàu.
  - Đăng kiểm và chứng nhận, gồm đơn vị đăng kiểm, ngày đăng kiểm gần nhất, ngày hết hạn và hồ sơ chứng nhận. Ngày đăng kiểm gần nhất có thể để trống, dùng định dạng ngày ISO và không được ở tương lai. Khi có cả hai ngày, ngày hết hạn không được trước ngày đăng kiểm gần nhất. Quản lý tàu gắn hồ sơ chứng nhận khi tạo tàu.
  - Bảo hành, gồm thông tin hợp đồng bảo hành do Quản lý tàu gắn khi tạo tàu.
  - Lịch sử bảo trì và sửa chữa do Quản lý tàu gắn khi tạo tàu.

## Đề xuất về phiên bản 3D và dữ liệu hiện có
- **Model 3D** đề xuất dùng chung asset/revision theo loại và giữ liên kết revision của mỗi tàu. Khi Admin cập nhật loại, các tàu đã tạo giữ revision và mapping đang dùng cho đến khi được nâng cấp có chủ đích.
- **Tàu hiện có** cần giữ nguyên ID, mã, dữ liệu vận hành và API đọc. Cần đối chiếu model nguồn trước khi tự động gắn loại cho tàu cũ. Trường hợp nguồn thiếu hoặc mơ hồ cần cách gán do Admin xác nhận.

## Phạm vi hồ sơ của đợt này

- API lưu danh sách tham chiếu theo từng nhóm hồ sơ. Nghiệp vụ lưu tệp và quản lý vòng đời tài liệu có thể được đặc tả riêng.
- Tàu lịch sử chưa gắn loại giữ hành vi hiện có. Hai đường dẫn tạo tàu trực tiếp cũ trả 410 và chỉ đến luồng tạo theo loại.

## Điều kiện hoàn thành

- Tất cả AC có bằng chứng theo API, quyền và dữ liệu. Các ca rollback, khóa ngoại và trùng định danh có bằng chứng PostgreSQL.
- Các thay đổi API được cập nhật trong inventory và sequence diagrams, đối chiếu với triển khai và chạy `python scripts/sequence_docs.py --check`.
