# Hợp đồng giao diện

| Điểm vào | Hành vi và đích |
|---|---|
| Card tại `/cruises` | Tiêu đề và “Xem chi tiết” mở `/cruises/:cruiseId?from=…`. “Xem lịch trình” dùng cùng đích với `#cruise-extras`. Hai link có nhãn rõ và truy cập bàn phím. |
| Tổng quan detail | Một H1, ảnh OceVie phù hợp nếu có, tàu, cảng đi/về, giờ Việt Nam. Khối trạng thái và “Bắt đầu đặt chỗ” khi đủ điều kiện. “Xem lịch trình” đi đến mục inline. |
| Điều hướng mục | Giữ `#cruise-facts`, `#cruise-packages`, `#cruise-extras`. Đích cuộn có heading ổn định. Link từ card, vào URL trực tiếp, reload và back/forward được xử lý. |
| Timeline | Danh sách ngày theo dữ liệu. Mỗi ngày chứa các cảng và giờ đến/rời địa phương theo ngày đầy đủ, kèm điểm tham quan. Ngày rỗng hiển thị “Không có lần ghé cảng trong ngày này”. |
| Tàu của chuyến | Tên tàu xác nhận từ detail, link đội tàu khi có tên và ID. Khi thiếu dữ liệu, dùng thông báo ngắn và bỏ link. Không nạp viewer 3D ngay trong detail. |
| Hạng cabin & gói | Nhóm card theo loại cabin dùng nhãn từ `cabinTypeName`. Từng offer giữ giá USD/người, hạng thương mại, quyền lợi, giới hạn và điều kiện đặt riêng. Một CTA đặt ở khối tổng quan theo gate, bước đặt hiện tại vẫn nhận lựa chọn gói/cabin. |
| Đi đặt | `/booking/party?cruiseId=…&from=…` từ gate hiện hành. Back về danh mục an toàn qua `catalogReturn`. |

Hash được xử lý một lần theo lần điều hướng hợp lệ và khi target đã có DOM. Focus tiêu đề sau khi đưa target vào viewport, tránh double scroll. Retry dữ liệu trong section không tạo lần điều hướng hash mới. Giữ back/forward và ScrollRestoration của PublicLayout. Tôn trọng reduced motion. Desktop hai cột khi đủ chỗ. Ở mobile giữ thứ tự DOM và nút đầy đủ, không che nội dung hay focus.

Dùng ButtonLink/ActionLink cho điều hướng, Button cho retry, Panel/Badge cho thông tin và trạng thái có nhãn, Loading/Empty/Notice cho tình huống đọc. Dùng `ol`, heading và `time` cho timeline. Tái dùng CSS token runtime và ảnh đã được ghi nguồn.
