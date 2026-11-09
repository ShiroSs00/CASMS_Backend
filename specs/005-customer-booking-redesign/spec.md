# Feature Specification: Redesign Web Booking cho khách hàng

**Feature Branch**: `codex/customer-booking-redesign-20261108`
**Created**: 2026-11-08
**Status**: Clarified — sẵn sàng lập plan
**Input**: “Luồng Booking tui cần redesign lại”; người dùng chọn A — Web Booking của khách hàng.

## Clarifications

### Session 2026-11-08

- Q: Bạn muốn redesign luồng Booking dành cho đối tượng nào? → A: Web Booking dành cho khách hàng; không redesign màn đặt chỗ của nhân viên.
- Q: Bạn muốn redesign cả cách chia bước của luồng đặt chỗ hay chỉ giao diện trong các bước hiện tại? → A: Redesign cả luồng và giao diện; được sắp xếp lại các bước, giữ nguyên quy tắc giá, giữ chỗ và thanh toán.
- Q: Bạn muốn hành trình redesign kết thúc ở lúc nhận mã đặt chỗ hay bao gồm cả việc tra cứu và xử lý sau đặt chỗ? → A: Từ tìm chuyến tới nhận mã đặt chỗ; các chức năng sau đặt chỗ giữ nguyên và được liên kết từ kết quả đặt chỗ khi phù hợp.
- Q: Bạn muốn mô hình 3D giữ vai trò nào khi khách chọn cabin? → A: Danh sách và 3D đều chọn được cabin; 3D là công cụ hỗ trợ tùy chọn, vẫn giữ lựa chọn giữ chỗ theo hạng hiện có.

## User Scenarios & Testing

### User Story 1 - Tìm và chọn chuyến phù hợp (Priority: P1)

Khách xem thông tin chuyến, điều kiện bán và các lựa chọn phòng/gói để quyết định đặt chỗ. Nội dung phân biệt rõ thông tin hành trình với lựa chọn có thể đặt tại thời điểm hiện tại.

**Why this priority**: Khách cần hiểu đúng chuyến và lựa chọn được bán trước khi nhập thông tin cá nhân.
**Independent Test**: Tìm một chuyến đang mở bán, xem chi tiết và bắt đầu đặt chỗ; thử lại với chuyến không có lựa chọn hợp lệ.

**Acceptance Scenarios**:
1. **Given** chuyến và lựa chọn đủ điều kiện bán, **When** khách mở chi tiết, **Then** thấy lịch trình, lựa chọn và hành động tiếp tục tương ứng.
2. **Given** chuyến chưa mở bán hoặc hết lựa chọn hợp lệ, **When** khách xem, **Then** không thể gửi yêu cầu đặt chỗ không hợp lệ và có thông báo theo dữ liệu hiện hành.

### User Story 2 - Chọn phòng, gói và người ở chung (Priority: P1)

Khách phân biệt các gói, hạng phòng, cách chọn cabin và nhóm hành khách ở chung; thấy ảnh hưởng của lựa chọn đối với thông tin còn phải nhập. Không yêu cầu khách hiểu mã nội bộ để thao tác.

**Why this priority**: Đây là phần liên quan trực tiếp tới chỗ ở, quyền lợi và giá mà khách đồng ý mua.
**Independent Test**: Chuẩn bị hành khách đi riêng và nhóm ở chung; chọn gói/hạng và cách nhận cabin bằng những phương thức hệ thống đang hỗ trợ.

**Acceptance Scenarios**:
1. **Given** lựa chọn đủ điều kiện và còn chỗ, **When** khách chọn gói/hạng/cabin hoặc giữ chỗ theo hạng, **Then** lựa chọn gắn đúng hành khách và nhóm theo quy tắc hiện hành.
2. **Given** lựa chọn trước đó không còn hợp lệ, **When** khách tiếp tục, **Then** được yêu cầu chọn lại phần bị ảnh hưởng, không tự đổi sang một sản phẩm khác.
3. **Given** cách chia bước đã được thiết kế lại, **When** khách quay lại bước trước rồi tiếp tục, **Then** thông tin vẫn hợp lệ được giữ và kiểm tra lại ở đúng thời điểm cần thiết, không buộc theo ba bước cũ.
4. **Given** có cabin hợp lệ để chọn, **When** khách chọn bằng danh sách hoặc mô hình 3D, **Then** hai cách dùng chung lựa chọn cabin, không tạo hai bản nhập khác nhau.
5. **Given** mô hình 3D tải chậm, lỗi hoặc khách không muốn dùng, **When** khách tiếp tục chọn cabin bằng danh sách, **Then** vẫn hoàn tất được bước chọn cabin với cùng điều kiện khả dụng; trường hợp không đọc được khả dụng không được giả định còn chỗ.

### User Story 3 - Điền thông tin và xác nhận đặt chỗ (Priority: P1)

Khách nhập thông tin hành khách/liên hệ, chọn kế hoạch thanh toán đang được hỗ trợ, kiểm tra nội dung và giá do hệ thống xác nhận trước khi gửi đặt chỗ.

**Why this priority**: Cần tránh sai người, sai lựa chọn hoặc tạo đặt chỗ trùng do lỗi gửi yêu cầu.
**Independent Test**: Đi từ bản nhập hợp lệ tới kết quả được hệ thống xác nhận; thử lỗi trường, giá hết hiệu lực và mất phản hồi khi gửi.

**Acceptance Scenarios**:
1. **Given** thông tin chưa hợp lệ, **When** khách tiếp tục, **Then** lỗi xuất hiện cạnh trường, focus tới lỗi đầu và giữ các giá trị đã nhập.
2. **Given** nội dung hợp lệ và báo giá còn hiệu lực, **When** khách xác nhận gửi, **Then** chỉ hiện thành công khi hệ thống trả về đặt chỗ tương ứng.
3. **Given** chưa xác định kết quả gửi, **When** mất phản hồi, **Then** không khuyến khích gửi lại tạo trùng, giữ thông tin phù hợp và cung cấp đường đối chiếu kết quả hiện có.
4. **Given** hệ thống đã xác nhận đặt chỗ thành công, **When** khách xem kết quả, **Then** thấy mã đặt chỗ, trạng thái do hệ thống trả về và đường dẫn phù hợp tới các chức năng sau đặt chỗ hiện có; không mô tả đặt chỗ như đã thanh toán.

### Edge Cases

- Chuyến, gói, hạng hoặc cabin hết chỗ hay thay đổi khả dụng trong khi khách đang nhập.
- Báo giá hết hạn, thay đổi lựa chọn sau khi nhận báo giá, thông tin giá/điều kiện không đọc được.
- Nhóm nhiều hành khách, sức chứa không phù hợp hoặc không còn cabin đáp ứng nguyên nhóm.
- Trở lại bước trước, mở trực tiếp một bước chưa đủ dữ liệu, tải lại trang hoặc rời luồng khi có bản nhập.
- Mô hình tàu không có, đang tải hoặc lỗi; không giả vờ đã chọn/giữ cabin.
- Phiên truy cập thay đổi, trường thông tin cá nhân không hợp lệ, yêu cầu gửi trùng hoặc kết quả gửi chưa rõ.
- Màn hình hẹp, tên chuyến/gói dài, thao tác chỉ bằng bàn phím và phóng to 200%.

## Requirements

### Functional Requirements

- **FR-001**: Phạm vi là trải nghiệm khách hàng trong Web Booking. Web Admin, Mobile và thay đổi nghiệp vụ/phân quyền/giá không thuộc feature này.
- **FR-002**: Các màn trong phạm vi phải dùng nhận diện và tương tác OceVie, với mật độ phù hợp khách hàng; tái sử dụng nền tảng giao diện hiện có.
- **FR-003**: Redesign cả trình tự/chia bước và giao diện. Được sắp xếp lại quá trình chọn chuyến, gói, phòng và nhập hành khách để khách hiểu lựa chọn trước khi xác nhận; không bị ràng buộc bởi ba bước hiện tại. Việc sắp xếp lại không thay quy tắc giá, giữ chỗ hoặc thanh toán tại FR-005.
- **FR-004**: Phạm vi từ tìm chuyến, xem chi tiết, chọn phòng/gói, nhập hành khách, kiểm tra và xác nhận tới nhận mã đặt chỗ. Kết quả thành công phải có mã đặt chỗ và bước tiếp theo theo khả năng hiện hành. Không redesign tra cứu, thanh toán thực tế, theo dõi hoặc các thao tác sau đặt chỗ; giữ đường dẫn sang các chức năng này và đường đối chiếu kết quả gửi chưa rõ.
- **FR-005**: Phải bảo toàn điều kiện mở bán, lựa chọn hợp lệ, giá/tiền tệ, quyền lợi, sức chứa, nhóm ở chung, kế hoạch thanh toán và quy tắc báo giá/đặt chỗ hiện hành. Không cộng tiền cabin lần hai nếu gói đã bao gồm quyền cabin.
- **FR-006**: Mỗi bước phải có mục tiêu, hành động chính và thông tin cần thiết để khách biết đang chọn gì, còn thiếu gì và có thể quay lại sửa gì. Lựa chọn trước đó phải được giữ khi còn hợp lệ.
- **FR-007**: Khách chọn được cabin cụ thể bằng danh sách và có thể mở 3D để xem vị trí hoặc chọn trực quan. Hai cách phải đồng bộ cùng lựa chọn và cùng kiểm tra khả dụng. 3D là tùy chọn, không chặn tiếp tục nếu danh sách và dữ liệu khả dụng hợp lệ; khi dữ liệu khả dụng chưa xác nhận phải chặn lựa chọn tương ứng. Giữ cách giữ chỗ theo hạng hiện có và không tải mô hình trước khi khách yêu cầu xem.
- **FR-008**: Lỗi nhập liệu phải gắn với trường, thông báo lỗi truy cập được bằng công cụ hỗ trợ và giữ bản nhập. Loading, rỗng, không còn chỗ và lỗi tải phải phân biệt được.
- **FR-009**: Giá, khả dụng và kết quả giữ/đặt chỗ phải lấy từ kết quả hiện hành của hệ thống; không báo thành công từ thao tác chọn trên giao diện đơn thuần.
- **FR-010**: Nội dung kiểm tra trước gửi phải xác định đúng chuyến, phòng/gói, hành khách, liên hệ, kế hoạch thanh toán và số tiền đã được xác nhận. Việc chọn kế hoạch thanh toán không được mô tả như đã thu tiền.
- **FR-011**: Trong lúc gửi, phải chặn gửi lặp; khi báo giá hết hạn hoặc kết quả chưa rõ phải có hướng xử lý theo cơ chế hiện có, không tự tạo yêu cầu mới.
- **FR-012**: Bảo toàn đường dẫn vào/ra và dữ liệu bản nhập theo cam kết hiện có. Nếu việc đổi thứ tự bước cần đổi đường dẫn, thiết kế tương thích phải được nêu rõ trong plan trước triển khai.
- **FR-013**: Không ghi hoặc xuất thông tin cá nhân, token hay chứng từ vào ảnh/log nghiệm thu. Dữ liệu dùng kiểm thử phải phù hợp môi trường được phép.
- **FR-014**: Các thông tin nghiệp vụ cần cho quyết định phải còn đọc được; lược bỏ hướng dẫn kỹ thuật/mã nội bộ và subtext lặp lại. Nhãn trường tiền là “Giá”; đơn vị và giá trị tiền thực tế vẫn theo dữ liệu được xác nhận.

### Key Entities

- **Chuyến khởi hành**: hành trình, thời gian, tàu và điều kiện được bán cho khách.
- **Lựa chọn gói/hạng**: quyền lợi, hạng cabin, phiên bản giá và trạng thái đủ điều kiện chọn trên chuyến.
- **Cabin / nhóm ở chung**: cabin vật lý hoặc giữ chỗ theo hạng, nhóm hành khách và sức chứa theo cơ chế hiện hành.
- **Bản nhập đặt chỗ**: chuyến, lựa chọn của từng hành khách/nhóm, thông tin cá nhân/liên hệ, kế hoạch thanh toán và ghi chú.
- **Báo giá**: nội dung và số tiền đã được hệ thống xác nhận, điều kiện/thời hạn hiệu lực.
- **Đặt chỗ và kết quả gửi**: định danh xác nhận, nội dung đã ghi nhận và trạng thái xử lý; không đồng nhất với đã thanh toán.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Với mỗi kịch bản chuẩn gồm khách đi riêng và nhóm ở chung, khách hoàn tất hành trình đã chốt mà không phải nhập lại thông tin vẫn còn hợp lệ khi quay lại bước trước.
- **SC-002**: 100% kịch bản lỗi bắt buộc gồm hết chỗ, báo giá hết hạn, lỗi trường và kết quả gửi chưa rõ không hiển thị thành công giả hoặc tạo thêm đặt chỗ do thao tác lặp.
- **SC-003**: Mọi màn trong phạm vi sử dụng được ở chiều rộng 390px và 1366px, phóng to 200%, bằng bàn phím; không tràn ngang toàn trang hoặc bị che hành động chính.
- **SC-004**: Mọi lỗi trường có label/liên kết lỗi rõ ràng; focus đến lỗi đầu; đóng hộp thoại trả focus phù hợp và bảo vệ thay đổi chưa lưu theo ngữ cảnh.
- **SC-005**: Dữ liệu chuyến, gói, phòng/nhóm, báo giá và đặt chỗ sau gửi đối chiếu đúng nội dung khách đã xác nhận trong các ca nghiệm thu có dữ liệu thật. Bằng chứng mô phỏng phải được phân biệt.
- **SC-006**: Một kịch bản chọn cabin cụ thể hoàn tất được bằng danh sách khi không mở 3D hoặc mô hình lỗi; khi mở 3D, cabin chọn tại một cách hiển thị đúng ở cách còn lại và không vượt điều kiện khả dụng hiện hành.

## Assumptions

- Giữ ngôn ngữ tiếng Việt và nền tảng thương hiệu OceVie hiện có; không tự thay font/màu/primitive hay thêm thư viện giao diện.
- Đơn vị tiền của luồng tạo đặt chỗ hiện tại là USD; redesign không đổi giá hoặc quy tắc tính tiền.
- Giữ cơ chế phiên truy cập/xác thực hiện tại; không thêm bắt buộc đăng nhập hoặc đăng ký tài khoản mới chỉ vì redesign.
- Luồng đang có chọn cabin vật lý và giữ chỗ theo hạng; quyền giữ chỗ và cách phân nhóm tiếp tục do hệ thống hiện hành quyết định.
- Chưa triển khai code, chưa tạo task triển khai và chưa commit/push trong giai đoạn specify/clarify/plan này.
- Đã chốt quyền thiết kế lại cả luồng và giao diện, từ tìm chuyến đến nhận mã đặt chỗ; danh sách và 3D dùng chung lựa chọn, 3D là tùy chọn. Cách chia bước cụ thể là đầu ra của plan trong các giới hạn đã chốt, không thay đổi nghiệp vụ.
