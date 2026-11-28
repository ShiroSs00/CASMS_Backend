# Đặc tả BE-09 đến BE-20 — Lịch trình và chuẩn bị vận hành

**Ngày soạn** 2026-11-16

**Nền đối chiếu** `origin/main` tại `a37c4d5`

**Trạng thái** Chờ duyệt tiêu chí nghiệm thu

## Phạm vi trên backend hiện tại

- `TripPlanStop` đã có số thứ tự ngày và giờ địa phương dự kiến. `CruiseStop` đã có `Instant` đến/rời cảng dự kiến và thực tế, mốc bắt đầu xuống tàu và mốc tất cả khách phải lên tàu. BE-09 đến BE-12 bổ sung quy tắc chuyển mẫu thành chuyến và kiểm tra thời gian ở các luồng ghi.
- `CruiseCreateRequest` và API tìm tàu đang có `expectedPassengerCount`. Đề xuất dùng chung giá trị này trong nội bộ và nhận `expectedPassengers` theo hợp đồng mới, giữ tương thích cho client đang gửi tên cũ.
- `POST /cruise/eligible-ships` đã lọc trạng thái, sức chứa và lịch trùng. BE-14/15 cần đồng bộ quy tắc giữa tìm, gán và đổi giờ chuyến.
- Ngân sách hiện là một tổng VND theo chuyến. Handoff hiện tách hai nhóm ASM và SHIP, mỗi nhóm có một bản ghi theo chuyến. Hồ sơ chuẩn bị có bốn phần TECHNICAL_NAUTICAL, HOTEL_CABIN, ENTERTAINMENT_SHORE và SAFETY_CREW.
- `submitSection` hiện yêu cầu CRM ở controller. OPS có thao tác xác nhận hoặc trả bổ sung và kiểm tra revision. Luồng audit đã lưu snapshot nội bộ và có API lịch sử sự kiện. BE-18 đến BE-20 cần tiếp tục từ các cơ chế này.

## Tiêu chí nghiệm thu đề xuất

| Mã | Given / When / Then | Bằng chứng QA |
| --- | --- | --- |
| BE-09 · AC-09 | Khi soạn mẫu, giờ cảng và hoạt động là dự kiến, biểu diễn theo ngày tương đối và giờ địa phương. Người tạo nhập mốc khởi hành sớm nhất và muộn nhất. Khi tạo chuyến từ mẫu hoặc điều chỉnh giờ khởi hành, backend kiểm tra giờ được chọn trong khung và tính lại các mốc phụ thuộc. Các chuyến đã tạo giữ dữ liệu của phiên bản được sử dụng. | Tạo hai chuyến từ cùng mẫu. Thử hai biên, ngoài khung, thay đổi giờ và kiểm tra phiên bản mẫu cùng chuyến còn lại. |
| BE-10 · AC-10 | Backend tính thời lượng thực trôi qua giữa khởi hành và kết thúc theo timestamp. Nhãn ngày/đêm tính riêng theo lịch địa phương của chuyến. Số ngày đếm các ngày lịch từ ngày khởi hành đến ngày kết thúc, tính cả hai đầu. Số đêm đếm các mốc nửa đêm chuyến đi qua. Backend tính từng chỉ số theo quy tắc riêng để kiểm tra nhãn lịch. Các cảng phải đúng thứ tự và nằm trong lịch. Nếu có dữ liệu thời gian di chuyển tối thiểu giữa hai cảng, backend dùng dữ liệu đó để kiểm tra. | Thử các ví dụ ở mục nhãn thời lượng, ranh giới nửa đêm, đổi múi giờ, cảng chồng lấn và chặng thiếu thời gian di chuyển. |
| BE-11 · AC-11 | Khi có ngày khởi hành cụ thể, backend chuyển mốc đến/rời từng cảng thành timestamp dự kiến theo múi giờ IANA của cảng. API trả timestamp và múi giờ. Giờ thực tế được ghi riêng khi vận hành. So sánh thứ tự theo timestamp, kể cả khi có đổi múi giờ. | Chuyến qua hai múi giờ, tới cảng ngày hôm sau, giờ địa phương không hợp lệ hoặc mơ hồ khi đổi DST. |
| BE-12 · AC-12a | Khi tạo hoặc dời tour bờ, toàn bộ tour phải nằm trong khoảng khách được rời tàu đến hạn quay lại. Đổi giờ cảng hoặc mốc lên/xuống tàu phải kiểm tra lại tour hiện có. | Thử đúng biên, vượt biên, tour qua nửa đêm và đổi giờ cảng làm tour sai. |
| BE-12 · AC-12b | Ở cảng cuối, dùng thời điểm kết thúc chuyến làm mốc kiểm tra khi tạo hoạt động hoặc tour. Với hoạt động dưới cảng ở điểm dừng giữa chuyến, áp dụng kiểm tra tương tự theo cửa sổ ghé cảng đó và đồng thời bảo đảm hạn khách quay lại tàu. Chặn nếu thời gian còn lại dưới 2 giờ. Dành 30 phút checkout và 30 phút xếp hành lý, chuẩn bị xuống tàu trong cửa sổ áp dụng. Hoạt động phải dài hơn 60 phút và nằm trọn trong thời gian còn lại sau các khoảng đệm. | Thử riêng cảng cuối và cảng giữa chuyến, 119, 120 và 121 phút còn lại, hoạt động dài 60 và 61 phút, và tour kết thúc vượt hạn lên tàu. |
| BE-12 · AC-12e | Khi tạo hoặc đổi lịch tour bờ tại cảng trả khách đã có giờ bắt đầu trả khách hợp lệ, khoảng từ giờ cập cảng dự kiến đến giờ bắt đầu trả khách phải ít nhất 120 phút. | 119 phút trả 422; 120 phút qua kiểm tra này; cảng giữa chuyến dùng các kiểm tra hiện hành. |
| BE-12 · AC-12c | Tại cảng giữa chuyến, giờ dự kiến kích hoạt khóa dự phòng và cảnh báo. Giờ thực tế xác nhận cập/rời cảng. Đến giờ cập dự kiến mà chưa có giờ thực tế thì chuyển sang ARRIVAL_PENDING, khóa đặt mới và cảnh báo OPS khi quá giờ. Đến giờ rời dự kiến mà chưa có giờ thực tế thì chuyển sang DEPARTURE_PENDING, giữ khóa và cảnh báo OPS khi quá giờ. Sau khi ghi nhận rời cảng thực tế, mở quyền đặt mới theo giờ hoạt động, trạng thái dịch vụ và chỗ trống. Booking đã xác nhận được giữ nguyên. Sau mốc kết thúc xuống tàu ở cảng cuối, khóa đặt mới cho chuyến đã kết thúc. | Kiểm tra các chuyển trạng thái bên dưới, cập hoặc rời sớm/muộn, quá giờ thiếu xác nhận, nhận đặt ngay sau xác nhận rời cảng, booking cũ và gọi API trực tiếp. |
| BE-12 · AC-12d | Cấu hình một hoặc nhiều khoảng giờ địa phương khi tạo hoặc sửa dịch vụ. Backend chặn yêu cầu sử dụng đi qua thời gian đóng cửa. Cho phép các khoảng giờ tiếp giáp và qua nửa đêm. Áp dụng múi giờ tàu hoặc cảng theo lịch trình. Các nhóm giờ cũ là cấu hình tương thích. | Thử khung qua nửa đêm, hai khung liền nhau, khoảng gián đoạn, chính xác giờ đóng và đổi múi giờ. |
| BE-13 · AC-13 | Khi tạo chuyến thực tế, yêu cầu nhập số khách dự kiến `expectedPassengers` và lưu theo chuyến. Giá trị phải là số nguyên dương và được dùng cho tìm, gán tàu. Mẫu lịch trình có thể tạo độc lập với số khách. | Tạo mẫu thiếu số khách, tạo chuyến thiếu số khách, tạo và đọc lại chuyến hợp lệ, kiểm tra payload cũ/mới và giá trị sai. |
| BE-14 · AC-14 | API tìm tàu kiểm tra khoảng hoạt động tính từ lịch và thời điểm khởi hành được chọn. Kết quả chỉ gồm tàu có trạng thái hợp lệ, đủ sức chứa, không trùng lịch và có ngày hết hạn đăng kiểm bằng hoặc sau ngày chuyến kết thúc theo ngày địa phương Việt Nam. | Kiểm tra sức chứa, trạng thái, lịch cùng giờ hoặc kề giờ, tàu thiếu hạn, hết hạn giữa chuyến, hết hạn đúng ngày về và sau ngày về. |
| BE-15 · AC-15 | Khi tạo chuyến có tàu, gán hoặc đổi tàu, hoặc đổi giờ qua luồng sửa kế hoạch, backend kiểm tra lại sức chứa, trạng thái, xung đột lịch và hạn đăng kiểm trong giao dịch ghi. Hai yêu cầu đồng thời gán cùng tàu vào lịch trùng chỉ có một yêu cầu thành công. | Thử đổi hạn đăng kiểm, trạng thái hoặc sức chứa kể từ lúc tìm tàu, gán đồng thời và xác minh dữ liệu sau khi bị từ chối. |
| BE-16 · AC-16 | Người phụ trách tự nhập ngân sách vận hành cho chuyến thực tế. Khi ngân sách bị từ chối, người có quyền có thể sửa và nộp lại. Lần nộp mới có revision mới và giữ lịch sử quyết định trước. | Kiểm tra quyền, nhập thủ công, từ chối, sửa và nộp lại, đối chiếu lịch sử và revision. |
| BE-17 · AC-17 | Mỗi bộ phận có yêu cầu công việc riêng, gắn với chuyến và đầu mối được giao. Cập nhật hoặc yêu cầu bổ sung một hồ sơ giữ nguyên trạng thái các bộ phận khác. | Tạo hai work request cho hai bộ phận, xử lý một yêu cầu rồi đối chiếu yêu cầu còn lại. |
| BE-18 · AC-18 | `submitSection` xác minh quyền, đúng bộ phận, đúng người được giao và đúng chuyến. Người được giao nộp được hồ sơ của mình dù hồ sơ bộ phận khác còn thiếu. | Kiểm tra người đúng, sai người trong cùng bộ phận, khác bộ phận và ngoài phạm vi chuyến. |
| BE-19 · AC-19 | Operations Manager xem hồ sơ đã nộp và có thể xác nhận, từ chối hoặc yêu cầu bổ sung. Quyết định gắn với đúng phiên bản hồ sơ. Hồ sơ được yêu cầu bổ sung có thể được sửa và nộp lại theo luồng đã duyệt. | Kiểm thử cả ba quyết định, phân quyền, nộp lại và hai người cùng xử lý một phiên bản. |
| BE-20 · AC-20 | Mỗi lần thay đổi lưu revision, người thực hiện, thời điểm, trạng thái trước/sau và lịch sử phiên bản. Yêu cầu cập nhật hoặc duyệt dựa trên revision cũ bị từ chối. Khi thay đổi đầu vào làm hồ sơ đã nộp lỗi thời, các hồ sơ phụ thuộc được đánh dấu để rà lại. | Sửa đồng thời, duyệt revision cũ, đọc lịch sử và kiểm tra phạm vi hồ sơ bị ảnh hưởng. |

## Nhãn thời lượng chuyến

| Khởi hành | Kết thúc | Giờ trôi qua | Nhãn hiển thị |
| --- | --- | ---: | --- |
| 08:00 ngày 10/11 | 18:00 ngày 10/11 | 10 | 1 ngày |
| 08:00 ngày 10/11 | 08:00 ngày 11/11 | 24 | 2 ngày 1 đêm |
| 20:00 ngày 10/11 | 06:00 ngày 11/11 | 10 | 2 ngày 1 đêm |
| 08:00 ngày 10/11 | 18:00 ngày 12/11 | 58 | 3 ngày 2 đêm |

Các ví dụ dùng cùng múi giờ và không có đổi offset. Tính số ngày và các mốc nửa đêm theo múi giờ IANA của cảng khởi hành, kể cả khi tàu đi qua nhiều múi giờ. Giờ hoạt động và mốc ghé cảng tiếp tục dùng múi giờ tàu hoặc cảng tương ứng. Nếu sau này cần tính đêm lưu trú của khách, cần đặc tả riêng mốc check-in và check-out.

## Giờ vận hành và quyền đặt dịch vụ

Khi tạo hoặc sửa dịch vụ, quản lý cấu hình các khoảng giờ cụ thể hằng ngày bằng `operatingWindows`. Mỗi khoảng gồm giờ bắt đầu và kết thúc, có thể qua nửa đêm. Kiểm tra giờ khi tạo yêu cầu dịch vụ hoặc ghi nhận bán tại POS. Dữ liệu nhóm giờ hiện có tiếp tục áp dụng cho dịch vụ chưa được cấu hình bằng trường mới.

Với dịch vụ vẫn dùng nhóm giờ cũ, CASMS áp dụng các mốc mặc định dưới đây.

| Nhóm | Giờ địa phương mặc định | Mục đích |
| --- | --- | --- |
| Ban ngày (Daytime) | 06:00–17:59 | Dịch vụ ban ngày |
| Buổi tối (Evening) | 18:00–21:59 | Dịch vụ buổi tối |
| Ban đêm (Nighttime) | 22:00–05:59 ngày hôm sau | Dịch vụ ban đêm |

Giờ hoạt động xác định thời điểm dịch vụ có thể được sử dụng. Trạng thái hành trình, trạng thái vận hành và chỗ trống xác định khả năng tiếp nhận đặt mới. Yêu cầu đặt phải thỏa cả hai nhóm điều kiện. Các ranh giới mặc định chuyển nhóm đúng 06:00, 18:00 và 22:00 theo múi giờ áp dụng.

| Điều kiện tại cảng giữa chuyến | Trạng thái vận hành | Nhận đặt mới trên tàu |
| --- | --- | --- |
| Trước giờ đến dự kiến và chưa ghi nhận cập cảng | EN_ROUTE | Mở theo cấu hình và chỗ trống |
| Tới giờ đến dự kiến, thiếu giờ cập thực tế | ARRIVAL_PENDING | Khóa và cảnh báo OPS khi quá giờ |
| Đã ghi nhận cập cảng, chưa tới giờ rời dự kiến, chưa ghi nhận rời | IN_PORT | Khóa |
| Đã tới giờ rời dự kiến sau khi ghi nhận cập cảng, thiếu giờ rời thực tế | DEPARTURE_PENDING | Giữ khóa và cảnh báo OPS khi quá giờ |
| Đã ghi nhận rời cảng thực tế | DEPARTED | Mở theo cấu hình và chỗ trống |

Khi cả giờ đến và giờ rời dự kiến đều đã qua mà giờ cập thực tế còn thiếu, dừng ở ARRIVAL_PENDING và giữ khóa. Tới giờ rời dự kiến chỉ kích hoạt trạng thái chờ. Xác nhận rời cảng thực tế mới kết thúc khóa dự phòng.

## Điểm cần duyệt

1. **Khởi hành và thời gian di chuyển.** Cần chốt thời điểm kiểm tra khung khởi hành dự kiến hoặc thực tế. Cần xác định nguồn thời gian di chuyển tối thiểu nếu dùng để kiểm tra từng chặng.
2. **Khoảng đệm và cảnh báo.** Cần chốt cách áp dụng 30 + 30 phút với hoạt động dưới cảng giữa chuyến, cùng độ trễ và cơ chế gửi cảnh báo OPS khi sự kiện thực tế chưa được ghi. Với hai khoảng đệm trong cửa sổ và hoạt động dài hơn 60 phút, đúng 120 phút vẫn không đủ.
3. **Ngân sách và hồ sơ vận hành.** Đề xuất giữ một tổng VND nhập thủ công theo chuyến theo backend hiện tại. Cần chốt nếu phải tách khoản/bộ phận, bộ phận nhận work request và người nộp mỗi phần. Việc sửa và nộp lại sau khi bị từ chối đã xác nhận cho ngân sách. Cần chốt quy tắc này cho các hồ sơ bộ phận khác và cách OPS phân biệt từ chối với yêu cầu bổ sung.
