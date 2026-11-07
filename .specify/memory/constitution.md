<!--
Sync Impact Report
Version change: unversioned scaffold -> 1.0.0 (initial adoption)
Principles replaced:
- Principle slot 1 -> I. Phạm vi rõ ràng và bảo toàn công việc hiện có
- Principle slot 2 -> II. Hợp đồng và nghiệp vụ là nguồn sự thật
- Principle slot 3 -> III. OceVie là chuẩn bắt buộc cho giao diện web
- Principle slot 4 -> IV. Tái sử dụng và thay đổi có giới hạn
- Principle slot 5 -> V. Nghiệm thu bằng bằng chứng
Sections added:
- Phạm vi áp dụng và giới hạn kỹ thuật
- Quy trình phát triển và cổng nghiệm thu
- Governance with amendment, versioning and review rules
Sections removed: unresolved scaffold placeholders and illustrative comments
Deferred placeholders: none
Follow-up: existing feature plans must re-evaluate Constitution Check against v1.0.0
before implementation. Templates and feature artifacts are unchanged by this command.
Remove this review-only comment before committing the constitution.
-->

# CASMS / OceVie Constitution

## Core Principles

### I. Phạm vi rõ ràng và bảo toàn công việc hiện có

Mỗi feature BẮT BUỘC xác định ứng dụng, route, vai trò và hành vi được thay đổi trước khi triển khai.
Công việc BẮT BUỘC dựa trên mã nguồn và trạng thái checkout thực tế.
Thay đổi local hiện có BẮT BUỘC được bảo toàn và phân biệt với thay đổi của feature.
Thao tác Git và xuất bản BẮT BUỘC tuân theo phạm vi người dùng đã cho phép.

Công việc UI web mặc định áp dụng cho CASMS_WebAdmin và CASMS_WebBooking.
Thay đổi Backend, Mobile App hoặc khu vực khác BẮT BUỘC có trong phạm vi feature được chấp thuận.
Khi gặp yêu cầu nghiệp vụ chưa rõ ảnh hưởng tới kết quả, BẮT BUỘC làm rõ trước khi quyết định.

### II. Hợp đồng và nghiệp vụ là nguồn sự thật

API, validation nghiệp vụ, permissions, trạng thái và lifecycle hiện hành BẮT BUỘC được bảo toàn
trừ khi feature được chấp thuận nêu rõ thay đổi tương ứng.
FE BẮT BUỘC hiển thị và gửi thao tác theo hợp đồng đã kiểm chứng,
đồng thời Backend tiếp tục kiểm tra quyền và điều kiện nghiệp vụ tại điểm thực thi.

Thay đổi giao diện BẮT BUỘC giữ route/query/returnTo, giá, tiền tệ, quyền lợi,
booking và quy trình xét duyệt đang áp dụng.
Khi sửa hợp đồng hoặc dữ liệu theo feature riêng, BẮT BUỘC chỉ rõ consumer,
tương thích dữ liệu cũ và kịch bản hồi quy cần thiết.
Mọi trạng thái thành công BẮT BUỘC dựa trên kết quả đã được hệ thống xác nhận.

### III. OceVie là chuẩn bắt buộc cho giao diện web

Trước mọi công việc UI trong CASMS_WebAdmin hoặc CASMS_WebBooking,
BẮT BUỘC đọc AGENTS áp dụng, DESIGN.md, tokens.css của design-systems/ocevie
trong repo CASMS_Frontend và component dùng chung của màn đích.

Giao diện BẮT BUỘC dùng token chuẩn đã nối vào stylesheet runtime.
Semantic token mới BẮT BUỘC được định nghĩa ở nguồn chuẩn và ánh xạ vào ứng dụng liên quan.
Mọi biến thể component BẮT BUỘC tuân theo anatomy, hành vi và nhận diện OceVie.
Admin và Booking BẮT BUỘC giữ bố cục, mật độ và trải nghiệm phù hợp với đối tượng của mình.

Label, lỗi tại trường, focus, thao tác keyboard và responsive BẮT BUỘC được kiểm chứng.
Modal và Drawer BẮT BUỘC có header, body, footer phù hợp,
quản lý focus và bảo vệ bản nhập chưa lưu theo ngữ cảnh.
Carbon và HTML Component Lab được dùng làm tài liệu tham chiếu.
Khi yêu cầu giao diện xung đột với OceVie, BẮT BUỘC giải thích và được người dùng chấp thuận
trước khi thiết lập một pattern mới.

### IV. Tái sử dụng và thay đổi có giới hạn

Trước khi thêm component, BẮT BUỘC kiểm tra implementation hiện có và ưu tiên tái sử dụng
hoặc mở rộng biến thể thích hợp.
Web Admin sử dụng các primitive từ AdminUI và Overlay hiện có.
Web Booking sử dụng Primitives và Overlay hiện có.
Mỗi ứng dụng BẮT BUỘC giữ một hệ primitive thống nhất.

Thay đổi BẮT BUỘC được giới hạn ở phần cần để đáp ứng yêu cầu.
Khi sửa component hoặc CSS dùng chung, BẮT BUỘC xác định consumer và kiểm tra hồi quy
những bề mặt chịu ảnh hưởng.
Thư viện UI, dependency mới hoặc cấu trúc dùng chung mới BẮT BUỘC có căn cứ từ phạm vi
đã được người dùng chấp thuận trước khi thêm.

### V. Nghiệm thu bằng bằng chứng

Mỗi yêu cầu và tiêu chí thành công có thể triển khai BẮT BUỘC có task hoặc bằng chứng
cho thấy implementation hiện tại đã đáp ứng.
Task chỉ được đánh dấu hoàn tất khi công việc và kiểm chứng được yêu cầu của task đã đạt.
Báo cáo BẮT BUỘC phân biệt kiểm tra tĩnh, test mô phỏng, integration test và nghiệm thu runtime.

Kiểm tra phù hợp với phạm vi BẮT BUỘC gồm typecheck/build/lint khi đổi code,
test cần thiết cho regression cụ thể và kịch bản UI/API liên quan.
Test dùng mock BẮT BUỘC công bố phần giả lập khi dùng làm bằng chứng.
Ca thiếu môi trường, quyền hoặc dữ liệu BẮT BUỘC được giữ mở cùng nguyên nhân.
Lỗi baseline BẮT BUỘC được ghi rõ để người đọc đánh giá kết quả nghiệm thu.

## Phạm vi áp dụng và giới hạn kỹ thuật

Constitution này áp dụng cho các feature CASMS được quản lý bằng Spec Kit trong workspace này.
Quy tắc OceVie áp dụng cho hai ứng dụng web.
Backend, Mobile App và các thay đổi hợp đồng sử dụng những nguyên tắc có liên quan
trong phạm vi feature được chấp thuận.

Implementation BẮT BUỘC theo stack, entry point, adapter và quy ước hiện có trong repo đích.
Bản sửa UI BẮT BUỘC dùng CSS theo phạm vi để giữ hành vi consumer.
Mutation trong kiểm thử BẮT BUỘC chạy trên dữ liệu thử nghiệm phù hợp với phạm vi được phép.
Thay đổi dữ liệu hoặc migration BẮT BUỘC được kiểm tra tác động trước khi thực thi.

## Quy trình phát triển và cổng nghiệm thu

1. BẮT BUỘC đọc chỉ dẫn repo, xác định scope và kiểm tra baseline mã nguồn, dữ liệu và công cụ.
2. Đặc tả BẮT BUỘC nêu user stories, yêu cầu, edge cases và tiêu chí có thể kiểm chứng.
3. Plan BẮT BUỘC đối chiếu constitution, quyết định tái sử dụng, hợp đồng và giới hạn kỹ thuật.
4. Tasks BẮT BUỘC ánh xạ yêu cầu, ghi đường dẫn tệp, phụ thuộc và cách nghiệm thu.
5. Phân tích BẮT BUỘC có đủ spec, plan và tasks, và thực hiện theo chế độ read-only.
6. Trước triển khai, BẮT BUỘC xử lý phát hiện CRITICAL và làm rõ các quyết định
   nghiệp vụ hoặc hợp đồng còn chặn implementation.
7. Triển khai BẮT BUỘC giữ task status theo bằng chứng và kiểm tra lại consumer bị ảnh hưởng.
8. Báo cáo BẮT BUỘC nêu phần đã làm, kết quả kiểm chứng, hạn chế và quyết định còn cần người dùng.

Với UI web, nghiệm thu BẮT BUỘC đối chiếu primitive/token, label/validation, action hierarchy,
focus/keyboard và viewport quy định trong feature.
Feature dùng component chung BẮT BUỘC có tiêu chí hồi quy cho các consumer liên quan.

## Governance

Constitution là nguồn nguyên tắc bắt buộc khi đánh giá các artifact Spec Kit.
Mâu thuẫn với nguyên tắc BẮT BUỘC được giải quyết bằng việc điều chỉnh feature artifact
hoặc một lần sửa constitution được người dùng yêu cầu rõ ràng.
Mỗi lần sửa constitution BẮT BUỘC ghi nguyên nhân, tác động, phiên bản và ngày áp dụng.
Người triển khai BẮT BUỘC báo cáo các ngoại lệ cần duyệt trước khi áp dụng.

Phiên bản dùng semantic versioning.
MAJOR áp dụng khi loại bỏ hoặc thay đổi nguyên tắc làm mất tương thích.
MINOR áp dụng khi thêm nguyên tắc hoặc mở rộng hướng dẫn có tác động hành vi.
PATCH áp dụng khi làm rõ câu chữ mà giữ nguyên nghĩa.
Bản 1.0.0 là lần thông qua đầu tiên từ scaffold chưa có phiên bản.

Plan và phân tích tiếp theo BẮT BUỘC đọc phiên bản constitution hiện tại ở runtime.
Khi nguyên tắc thay đổi, feature đang làm BẮT BUỘC đánh giá lại Constitution Check
trước khi tiếp tục triển khai.
Chỉ được tuyên bố tuân thủ sau khi có đối chiếu cụ thể với các nguyên tắc áp dụng.

**Version**: 1.0.0 | **Ratified**: 2026-11-07 | **Last Amended**: 2026-11-07
