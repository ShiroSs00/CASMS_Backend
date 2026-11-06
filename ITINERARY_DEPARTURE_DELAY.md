# Khởi hành theo lịch mẫu và delay trước giờ xuất phát

## A. Kiến trúc hiện tại

Nhánh làm việc là `feat/CASMS-75-itinerary-management`, dựa trên `origin/main` tại `3a3771b`. Trong CASMS, `Trip` và `TripScheduleVersion` là mẫu. `TripPlanDay`, `TripPlanStop` và `TripPlanEntry` lưu số ngày, `LocalTime`, thứ tự, cảng, loại hoạt động và liên kết danh mục. Giờ về lấy từ lần ghé cuối. Thời lượng hoạt động và di chuyển suy ra từ khoảng thời gian tương ứng. Chuyến cụ thể là `Cruise`, lưu thời điểm khởi hành và về dự kiến dưới dạng `Instant`, thời điểm thực tế và tàu được gán. Bản vận hành gồm `Itinerary`, `ItineraryDay`, `CruiseStop`, `OnboardActivity` và `ShoreExcursion`.

Trước thay đổi, luồng preview và luồng tạo chương trình chiếu giờ địa phương lên ngày chạy nhưng chưa áp dụng độ lệch khi người tạo chuyến chọn giờ khác giờ mẫu. Đã có departure gate và luồng apply-change với quy tắc riêng. Luồng delay chuyên biệt cần kiểm tra sẵn sàng, có preview, có idempotency và giữ lịch trước khi thay đổi.

## B. Luồng triển khai

- Preview và tạo chuyến dùng cùng độ lệch giữa giờ mốc mẫu và giờ khởi hành thực tế. Hoạt động ở cảng tiếp tục tính theo múi giờ cảng và lịch trên tàu dùng giờ cơ sở Asia/Ho_Chi_Minh. Mẫu legacy thiếu giờ mốc dùng cách chiếu cũ. Quy trình publish mẫu hiện có kiểm tra giờ khởi hành.
- Điều kiện trước delay dùng `PUBLISHED` kết hợp Departure Gate hiện tại, kiểm tra chuyến còn trước giờ xuất phát và chưa có actual departure. Gate sử dụng dữ liệu tàu, lịch, cảng, nhân sự, quy định và các phân hệ chuẩn bị đã xác nhận. Delay thêm kiểm tra vấn đề vận hành chưa xử lý, trùng lịch tàu/nhân sự, cửa sổ cảng, guidance đã lên lịch và khung dịch vụ cố định.
- Preview trả lịch trước/sau, countdown, revision và blockers. Confirm lấy khóa, kiểm tra lại rồi dịch departure, return, giờ cảng, all-aboard, hoạt động trên tàu và tour bờ. Mọi giờ trong các mục dịch chuyển dùng cùng số phút. Các lịch có khung cố định hoặc cần phân bổ lại ngày phải xử lý qua luồng thay đổi lịch phù hợp. Một delay hợp lệ giữ vòng đời hiện tại để vẫn có thể đánh giá Departure Gate. Việc trả lại phân hệ chuẩn bị vẫn đi qua workflow hiện có.
- Mỗi lần thành công ghi snapshot trước/sau, lý do, category, actor, thời gian và revision. Các lần tiếp theo tính từ lịch mới. Nhân sự đang được gán nhận thông báo nội bộ trong cùng transaction. Dữ liệu mẫu, booking, chính sách hoàn tiền và giờ thực tế tiếp tục do các luồng hiện có quản lý.

## C. Dữ liệu

Tái sử dụng `CruiseAuditEvent`, `Cruise.revision` và `Notification`. Khóa ghi trên Cruise bảo vệ các lần confirm và start. Khóa stop bảo vệ lúc ghi nhận actual departure. Idempotency identity có phạm vi theo Cruise và request UUID. Các ghi dữ liệu lịch, audit và thông báo dùng cùng transaction. Không cần migration hay entity mới.

## D. API

| Route dưới /api | Quyền | Kết quả |
|---|---|---|
| GET /cruises/{cruiseId}/departure-readiness | CRUISE_READ hoặc CRUISE_VALIDATE | Departure Gate hiện tại |
| GET /cruises/{cruiseId}/schedule | CRUISE_READ, TRIP_READ hoặc ITINERARY_READ | Lịch vận hành hiện tại |
| GET /cruises/{cruiseId}/delays | CRUISE_READ | Mốc ban đầu, dự kiến, thực tế, tổng phút và lịch sử |
| POST /cruises/{cruiseId}/delays/preview | OPERATIONS_MANAGER và CRUISE_START | Lịch trước/sau, revision, thời gian còn lại và blockers |
| POST /cruises/{cruiseId}/delays | OPERATIONS_MANAGER và CRUISE_START | Sự kiện đã xác nhận hoặc kết quả cũ nếu retry đúng |

Ví dụ request và sequence nằm trong [luồng khởi hành và delay](docs/sequence-diagrams/CRUISE-DEPARTURE-DELAY.md). Confirm trả 409 nếu revision cũ hoặc request UUID được tái sử dụng với nội dung khác. Delay bị chặn trả 422 cùng danh sách lý do, payload sai trả 400.

## E. Tích hợp frontend

Repo hiện tại chỉ chứa backend. UI có thể đọc trạng thái và lịch, gọi preview, cho phép xác nhận khi blockers rỗng, và sử dụng cùng request UUID khi retry confirm. Sau thành công, tải lại lịch và lịch sử để hiển thị giờ mới. Category dùng mã mở rộng dạng chữ hoa và dấu gạch dưới, ví dụ `WEATHER_CONDITION`, `TECHNICAL_ISSUE`, `PORT_OPERATION`, `SAFETY_REQUIREMENT`, `OPERATIONAL_REASON`, `OTHER`.

## F. Kiểm thử

- Kiểm tra inventory và cấu trúc sequence đạt 604 route.
- Maven `verify` tập trung đạt 50 ca, gồm delay unit, materialization, hợp đồng lập lịch mẫu, phân quyền baseline và checksum migration. JAR đóng gói thành công.
- PostgreSQL 16 tạm đã áp dụng 91 migration đến V93. Booking PostgreSQL đạt 6/6. Test delay PostgreSQL đạt kịch bản retry đồng thời, hai lần delay nối tiếp, từ chối revision cũ và rollback khi audit lỗi. Kịch bản này dùng cổng readiness và cửa sổ cảng giả lập để cô lập transaction. Dữ liệu lịch, audit và khóa ghi dùng PostgreSQL thật.
- Lượt Maven `verify` với PostgreSQL đạt 52/52, gồm 27/27 ca Import 3D và các nhóm delay, materialization và Booking. Hai lượt có một số bộ test trùng nhau. Chưa chạy lại toàn bộ backend suite hoặc xác nhận end-to-end với cấu hình chuẩn bị và cửa sổ cảng thật.

## G. Phạm vi còn lại

Chưa có UI trong repo này. Thông báo được lưu cho nhân sự được gán chuyến. Thông báo cho khách cần luồng riêng sử dụng mốc đã xác nhận. Việc tự phân bổ lại vai trò ngày khi delay qua nửa đêm và tự dịch khung lịch cố định cần chính sách nghiệp vụ riêng.
