# Đặc tả 006 — Chi tiết chuyến và xem lịch trình

**Ngày** 2026-11-09

**Phạm vi** CASMS_WebBooking, khách xem chuyến công khai trước khi đặt.

Ảnh Royal Caribbean là tham chiếu về cách đặt hành động xem lịch trình cạnh thông tin chuyến. Trong feature này, “booking detail” là trang `/cruises/:cruiseId`. Hồ sơ đặt chỗ sau khi gửi, tra cứu, bốn bước giao dịch, Admin và Mobile nằm ngoài phạm vi. Giữ hành vi mở bán, gói, quyền truy cập và hợp đồng dữ liệu hiện có.

## User stories

### US1 — Hiểu chuyến và tìm được lịch trình (P1)

Khách cần biết mình đang xem chuyến nào, đi tàu gì, khởi hành và trở về khi nào. Từ thẻ chuyến, khách chọn “Xem chi tiết” để vào đầu trang hoặc “Xem lịch trình” để đến đúng mục trong cùng trang chi tiết.

**Nghiệm thu** Với danh mục đã lọc và phân trang, cả hai liên kết mở đúng chuyến. Mở mới hoặc tải lại URL có hash đưa đến đúng mục sau khi dữ liệu chi tiết tải xong. Đường về danh mục giữ query đã được kiểm tra.

### US2 — Đọc lịch trình theo ngày và cảng (P1)

Khách xem ngày theo giờ tàu và giờ đến/rời theo địa phương từng cảng. Một ngày có thể có nhiều cảng, nhiều điểm tham quan hoặc thiếu dữ liệu cảng.

**Nghiệm thu** Với dữ liệu có nhiều múi giờ và ngày địa phương khác ngày của tàu, ngày và thời điểm hiển thị theo đúng nguồn. Ngày rỗng ghi rõ chưa có lần ghé cảng. Lỗi đọc, lịch chưa sẵn sàng và lịch rỗng có thông báo riêng. Retry chỉ tải lại lịch trình.

### US3 — Xem gói và chuyển sang đặt chỗ (P1)

Khách cần xem tàu của chuyến, so sánh gói theo hạng cabin và biết khi nào có thể bắt đầu đặt. Trạng thái bán, dữ liệu gói và lịch trình có thể tải độc lập.

**Nghiệm thu** CTA chỉ xuất hiện khi kết quả từ adapter hiện hành xác nhận `sellable && transactionReady`. Nếu CTA xuất hiện, nó dẫn đến `/booking/party` với đúng `cruiseId` và `from` an toàn. Giá gói và quyền lợi lấy từ catalog công khai của đúng chuyến. Lỗi lịch trình không tự đổi trạng thái mua.

## Yêu cầu chức năng

- **FR-001** Tái bố cục chi tiết với một H1, ảnh OceVie hiện có nếu phù hợp, thông tin tàu, cảng đi/về và giờ chuyến. Thẻ kết quả bổ sung liên kết “Xem lịch trình”, giữ lọc, phân trang và đường xem chi tiết.
- **FR-002** Giữ `/cruises/:cruiseId`, `from`, các anchor `#cruise-facts`, `#cruise-packages`, `#cruise-extras` và đích đặt chỗ hiện hành. Đích “Xem lịch trình” là `#cruise-extras`. Hỗ trợ tải trực tiếp, quay lại và chuyển focus đến tiêu đề mục.
- **FR-003** Trình bày theo ngày, cảng và điểm tham quan bằng dữ liệu public itinerary đã decode. Hiển thị ngày giờ tàu và ngày giờ địa phương cảng, kể cả khi lệch ngày. Hiển thị thời lượng khi có giá trị. Ngày không có lần ghé cảng dùng mô tả trung tính.
- **FR-004** Tách loading, rỗng, lỗi đọc và lịch trình chưa sẵn sàng. Khi chuyến không còn được công khai, dừng hiển thị dữ liệu của chuyến và đưa khách về danh mục theo luồng hiện tại. Retry không điều hướng, không tự cuộn lại và không gửi mutation.
- **FR-005** Giữ cổng công khai và cổng giao dịch hiện hành. Trạng thái mở bán hiển thị từ dữ liệu công khai. CTA dùng kết quả adapter giao dịch và chỉ hiện khi cả hai cờ cho phép. Giữ riêng các trạng thái chưa cho phép, lỗi và đang kiểm tra.
- **FR-006** Tái dùng catalog công khai để trình bày “Hạng cabin & gói”. Nhóm offer theo `cabinType`, giữ tên hạng thương mại và giá USD/người của từng offer, quyền lợi, giới hạn và điều kiện đặt riêng. Không gộp hai offer khác nhau, suy số phòng trống hoặc tự chọn cabin từ trang xem.
- **FR-007** Dùng token OceVie và UI hiện có. Bố cục hai cột ở desktop khi đủ chỗ, một cột ở mobile. Thứ tự bàn phím theo nội dung, focus thấy được, tiêu đề anchor không bị header che và chuyển động tuân reduced motion.
- **FR-008** Tải theo chuyến, một lượt đọc itinerary khi trang chi tiết mở, ngoại trừ retry theo yêu cầu và cơ chế query hiện có. Danh mục giữ số lượt đọc hiện tại, kể cả khi có nhiều thẻ.
- **FR-009** Có khối “Tàu của chuyến” từ dữ liệu chi tiết. Khi biết tên và ID tàu, cung cấp link “Khám phá đội tàu” đến `/ships`. Quyền lợi ăn uống/giải trí/hoạt động đã công bố được trình bày theo từng gói. Chỉ hiển thị ảnh có nguồn phù hợp, kèm nhãn minh họa khi cần.

## Tiêu chí thành công

- **SC-001** Từ danh mục có query, đi chi tiết, xem lịch trình, tải lại và quay lại danh mục thành công. Kiểm chứng ba anchor cũ, đúng chuyến và focus sau tải.
- **SC-002** Ma trận lịch trình nhiều cảng, chênh ngày, thiếu trường tùy chọn, ngày rỗng, lịch rỗng, lỗi mạng và chuyến mất quyền công khai cho kết quả đúng theo hợp đồng.
- **SC-003** Ma trận `sellable/transactionReady`, lỗi gói và đọc lịch trình giữ đúng CTA, giá và điều kiện. Hai offer cùng hạng vẫn là hai lựa chọn so sánh. Ca có/thiếu tàu dùng đúng khối thông tin. Kiểm chứng 0 mutation và giữ query khi đi đến bước đặt.
- **SC-004** Kiểm tra UI ở 1366×900, 390×844, native zoom 200% và bàn phím. Nội dung chính, hai hành động và đích anchor vẫn đọc và thao tác được.
- **SC-005** Kiểm chứng trên ít nhất một chuyến công khai có lịch trình đã materialize ở Backend tương thích. Ghi rõ kết quả fixture riêng với dữ liệu thật và để mở ca thật nếu thiếu môi trường.

Kiểm thử hồi quy yêu cầu tập trung vào hash sau tải bất đồng bộ, múi giờ, cổng CTA và số lượt đọc. Dùng harness hiện có khi phù hợp.
