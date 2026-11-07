# Kiểm chứng

Đường dẫn lệnh tính từ CASMS_Frontend/CASMS_WebAdmin.

## Điều kiện

Backend tương thích, tài khoản ACTIVITY_SERVICE_MANAGER và một tài khoản đọc có quyền tương ứng, dữ liệu thử nghiệm cho catalog/operational activity, service và package ở các trạng thái cho phép thao tác. Dùng môi trường local cô lập cho mutation.

## Chạy

1. npm run typecheck, npm run build. Chạy npx --no-install eslint với danh sách tệp sửa. Ghi baseline nếu có lỗi ngoài scope.
2. npm run dev. Dùng cổng từ vite.config.ts hiện tại. Đăng nhập qua giao diện.
3. Mở toàn bộ route trong contracts/ui.md. Với list, kiểm tra tìm/lọc/reset, loading/error/empty và tên dài. Với detail, đối chiếu dữ liệu/nhãn trạng thái và nhóm action.
4. Với US2, kiểm tra catalog activity và operational activity, service, package. Thử lỗi ràng buộc đã có, kiểm tra focus/field error, sửa đúng, lưu và đọc lại. Kiểm tra hủy bản nhập, lỗi server và kết quả chưa rõ bằng môi trường thử nghiệm.
5. Với US3, kiểm tra modal trạng thái/xóa, quyền lợi gói và lịch dịch vụ nhúng. Kiểm tra cancel, Escape, Tab/Shift+Tab, focus return và busy. Hành động bị hạn chế vẫn theo quyền/lifecycle.
6. Lặp các màn đại diện ở 1366x900, 390x844 và zoom 200%. Control chuẩn 44px, compact 36px khi cần. Đo computed style và ảnh để xác minh, cho phép cuộn ngang trong bảng mà không tràn trang.
7. Kiểm tra hồi quy /admin/products và /admin/service-requests vì dùng chung generic catalog/CSS. Nhóm quyền chỉ đọc không thấy/gửi được thao tác ghi.
8. Ghi evidence theo contracts/ui.md, đánh dấu rõ ca chưa kiểm chứng và nguyên nhân. Hoàn tất sau khi tất cả SC-001 đến SC-004 có bằng chứng đạt.

Giai đoạn plan chưa chạy các bước nghiệm thu này.


## Baseline thực thi — 2026-11-07

- Source feature 004 sạch trước lượt này; working tree có thay đổi khác từ công việc trước và được giữ.
- Backend main 7d13ce4, Web Admin 5174 đang proxy tới Backend 8080, database thử nghiệm casms_delay trong container PostgreSQL 16 cô lập trên 55432.
- Dùng tài khoản activity_manager và shore_manager có ACTIVITY_SERVICE_MANAGER trong DB cô lập. Dữ liệu mutation chỉ tạo trong DB này.
- Tài liệu AGENTS, OceVie DESIGN/tokens đã đọc. Ignore dependency/build/env/log và ESLint flat-config ignores hiện có đáp ứng project.
- Baseline typecheck/build đạt. Lint hai feature có đúng hai lỗi react-hooks/refs ở ActivityCatalogEditor và CatalogFormPage.
- Đo browser trước sửa ở activities, activities/new, services, services/new, service-packages, service-packages/new: control đều 44px, radius 6px, border rgb(113,131,145). Shared Experience thắng các khai báo legacy 42px/8px, nên cần loại các rule dư.
- Ma trận dự kiến gồm catalog/operational activity, service active/inactive, package draft/active. Các phép ghi dùng fixture riêng; trạng thái thực tế và ca mô phỏng được phân biệt trong bảng dưới. Read-only/lost-permission chỉ giảm quyền trong phiên trình duyệt, không thay permission lưu trong DB.

## Kết quả implementation — 2026-11-07

### Thay đổi đã thực hiện

- Dùng lại Field/ListFilters/ButtonLink cho danh sách; giữ search/status, hai view hoạt động, thứ tự dữ liệu và cách lấy danh sách hiện có.
- Bỏ rule control legacy 42px/8px. Control dùng token canonical 44px/6px; date/datetime-local có padding token để tránh intrinsic height 45px của Edge. Reset border mặc định của fieldset hoạt động, giữ disabled semantics.
- Form catalog activity có lỗi tên/sức chứa/giá tại Field, summary và focus. Ba editor dùng busy và các section hiện có. Hai lỗi lint react-hooks/refs baseline đã hết nhờ snapshot baseline bằng state.
- Modal hoạt động/catalog/quyền lợi/đăng ký/điểm danh dùng footer hủy/xác nhận, danger và busy đúng ngữ cảnh. Không tạo primitive mới. Lưu quyền lợi chỉ refresh phần liên quan và khôi phục focus sau khi danh sách component tải lại.
- Sửa phân nhánh bằng giá trị catalogEntry === true, thay vì chỉ kiểm tra có property: Backend có trả catalogEntry: false cho hoạt động vận hành. Dữ liệu đó hiện mở đúng detail/editor và các action vận hành hiện có.
- Hai app đã import canonical tokens tại dòng đầu src/styles/tokens.css. Feature dùng mapping có sẵn; không thêm token, dependency, shared primitive hoặc sửa Booking/Mobile trong lượt này.
- Ngoại lệ người dùng duyệt: bỏ serviceCode/packageCode trong serializer service/package. Backend DTO không có hai trường này; trước sửa PUT trả 400 INVALID_REQUEST_BODY, sau sửa PUT và readback đạt. Endpoint/method/permissions/Backend không đổi. Serializer products giữ nguyên.

### Dữ liệu và loại bằng chứng

| Nhóm | Fixture / state | Bằng chứng |
|---|---|---|
| Catalog activity | 13e7b9a5-2bcf-4f2b-ac74-91298d6b3037 | Tạo/sửa qua UI, request thật và đọc lại tên đã sửa |
| Service | 0c7096e3-3230-438f-9c54-e647627389e1, ACTIVE | Tạo/sửa qua UI, readback thật; modal suspend dùng lỗi phản hồi mô phỏng |
| Package | e4d41d1a-0b92-47a4-afa7-04a721fdeb27, DRAFT | Tạo/sửa/readback thật; thêm/gỡ service và activity benefit thật; activate chỉ mô phỏng lỗi |
| Operational activity | 7e400000-0000-4000-8000-000000000001, PLANNED | Sửa qua UI trên chuyến CUSTOM test 7e410000-0000-4000-8000-000000000001; PUT 200, context/returnTo đúng |
| Product hồi quy | f8ca86de-252e-4c59-8cff-41b3ac5a48ff | Tạo qua UI, readback detail thật, mở edit kiểm tra tên |
| Service request | List thật; detail 7e420000-0000-4000-8000-000000000001 | Detail dùng response fixture rõ ràng vì chưa có record nghiệp vụ phù hợp |
| Registration / attendance | Response fixture 7e430000… / 7e440000… | Mô phỏng read và lỗi mutation; không tạo/sửa booking hoặc hành khách thật |
| Quyền đọc | Phiên activity_manager được giảm effectivePermissions ở response /auth/me | Chứng minh UI activities/services ẩn tạo/sửa/xóa; không phải kiểm chứng authorization Backend bằng account đọc riêng |

Tài khoản activity_manager và shore_manager hiện đều có ACTIVITY_SERVICE_MANAGER với quyền ghi. Chưa có account đọc riêng để chứng minh SC-001 đầy đủ. Route gói hiện cần TOUR_PACKAGE_MANAGE hoặc PACKAGE_COMPONENT_MANAGE; không tự tạo quyền đọc gói mới.

Chuyến test CUSTOM được clone trong DB cô lập từ draft có sẵn. Các chuyến FIXED giữ nguyên quy tắc Backend: thử sửa trả 409 FIXED_ITINERARY_READ_ONLY, UI giữ bản nhập. Với operational activity liên kết catalog, Backend kế thừa tên từ nguồn; không coi trường tên gửi lên là thay đổi nguồn. Không nới guard để làm phép thử đạt.

### Ma trận nghiệm thu đã chạy

| Ca | Route/state/role | Kết quả |
|---|---|---|
| Tạo form và lỗi trường | Ba route /new, ASM | Blank submit có lỗi; nhập hợp lệ tạo thành công và đọc lại Backend |
| Chỉnh sửa / hủy nhầm | Ba route /:id/edit, ASM | Prompt rời trang, ở lại giữ draft; lưu và đọc lại đạt |
| Operational editor | /activities/:id/edit?cruiseId=…&returnTo=… | Lỗi capacity, focus trường đầu sai; sửa hợp lệ PUT 200, trở về cruise context |
| Search/filter/reset | Ba list, catalog và operational view | Search không khớp, reset, status PLANNED và aria-pressed đạt |
| Load failure / empty | Ba list, response fixture 500 và data [] | Notice/Thử lại/khôi phục dữ liệu và empty state đạt; lỗi mô phỏng không ghi DB |
| Unknown mutation | Ba catalog editor, phản hồi 503 | Draft giữ nguyên, submit khóa; không gửi lại tự động |
| Mất quyền giữa phiên | Service edit, bỏ SERVICE_UPDATE trong session store | Editor bị route guard thu hồi; không có PUT mới |
| Quyền lợi gói | Thêm/gỡ thật, lỗi 422 mô phỏng, giữ request để đo busy | Footer, dirty cancel/Escape, Tab, error, busy, focus return và readback đạt |
| Trạng thái | Operational open, service suspend, package activate; POST 422 mô phỏng | Busy chặn Escape/hủy/click lặp, một request, error rồi hủy đạt |
| Xóa | Ba detail, chỉ mở/hủy | Danger footer, Escape và focus return đạt; không xóa record |
| Đăng ký / điểm danh nhúng | Operational detail; response fixture | Mobile footer, lỗi server, hủy/Escape đạt; không mutation dữ liệu hành khách |
| Lịch dịch vụ nhúng | Service detail, ASM | Read-only theo policy; không có form Thêm khung giờ. Writer CRUISE_MANAGER chưa được kiểm tra mutation trong lượt này |
| Responsive | 16 route gồm 12 route chính, operational edit, products list/new, requests list | 1366x900 và 390px không tràn ngang trang, input/select 44px radius 6px; bảng cuộn trong vùng riêng |
| Zoom / keyboard | 12 route chính ở viewport CSS 683x450, reduced-motion; modal ở 1366/390/683 | Reflow tương đương 200% ở cửa sổ 1366px đạt; Tab/Shift+Tab và Escape đạt. Chưa kiểm tra zoom trình duyệt thực tế 200% |
| Modal quyền lợi | 1366/390/683 | Edit/discard, chiều rộng trong viewport đạt |
| Hồi quy generic | Products list/new/detail/edit; requests list/detail | Product dữ liệu thật và request detail fixture đạt; không sửa serializer product |

Ảnh desktop/mobile hoạt động đã xem trực tiếp, sửa border fieldset mặc định và chụp lại với reduced motion. Tên fixture dài cùng mã được hiển thị và cuộn bảng trong viewport. Các phép thử mock chỉ chứng minh hành vi UI, không được tính là xác nhận quy tắc hoặc authorization Backend.

### Kiểm tra kỹ thuật và cổng còn mở ở lượt đầu

- npm run typecheck: đạt sau các sửa TypeScript.
- npm run build: đạt, 576 modules; còn cảnh báo chunk lớn và ineffective dynamic import của ship-model-viewer ngoài feature này.
- npx --no-install eslint src/features/activities src/features/service-packages --max-warnings 0: đạt.
- git diff --check cho hai feature: đạt. Backend src không có diff; không sửa endpoint, permission, route hoặc lifecycle. Thay đổi payload service/package có approval và được cập nhật vào spec/contract/plan.
- SC-001: phần ASM đạt; account đọc thật còn thiếu. T002/T008 giữ mở cho bằng chứng này.
- SC-002: validation, dirty guard, save thật, lỗi server và unknown-outcome/lost-permission UI đạt theo ma trận; backend authorization khi đổi quyền chưa được chứng minh bằng account riêng.
- SC-003: các modal chính và registration/attendance fixture đạt; zoom native 200% còn thiếu. T017 giữ mở.
- SC-004: build/lint và hồi quy đạt; final gate T019 giữ mở do SC-001/SC-003 chưa đầy đủ.
- Không có .specify/extensions.yml nên không có before/after implement hook cần chạy. Không commit/push/merge.

Evidence local ở Backend/target/ (đã ignore): feature004-fixtures.json, feature004-ui.cjs, feature004-acceptance.log, feature004-remaining.log, feature004-responsive.log, feature004-states.log, feature004-operational.log, feature004-status.log, feature004-registration.log, feature004-benefit-width.log, feature004-guards-regression.log và feature004-activity-{desktop,mobile}.png. Một số log đầu chứa lỗi baseline hoặc lỗi fixture đã được sửa; kết quả cuối theo ma trận trên, không coi mọi lượt log đều PASS.

## Nghiệm thu bổ sung và đóng T002/T008/T017/T019 — 2026-11-07

Phần này thay thế trạng thái cổng còn mở của lượt đầu. Không có thay đổi source ứng dụng trong lượt nghiệm thu bổ sung.

### T002 / T008 — Account đọc và ma trận dữ liệu

- Sao chép casms_delay sang casms_feature004_readonly trong cùng container test cô lập; chạy Backend phụ trên 8082. Backend chính 8080 và DB casms_delay không bị chỉnh quyền.
- Dùng account thực shore_manager (userId a0000000-0000-0000-0000-000000000016), role ACTIVITY_SERVICE_MANAGER. Fixture chỉ giữ 17 permission hiện có kết thúc bằng _READ trong DB bản sao; không tạo role/permission mới, không sửa policy source. /auth/me thực trả về tập quyền này.
- Trình duyệt đăng nhập thực, chuyển tiếp request tới Backend phụ bằng route.fetch/route.fulfill với nguyên response Backend. Không giả lập effectivePermissions hoặc kết quả ghi.
- Activities/services: list, detail, tìm không khớp, reset và xem lại dữ liệu đạt; UI không có tạo/sửa/xóa, direct edit bị chặn. Gửi PUT hợp lệ trực tiếp cho catalog activity, service và package đều nhận 403 từ Backend thật.
- Package: giữ nguyên policy hiện hành. GET catalog công khai trả 200 với ACTIVE-only, không có gói DRAFT fixture; GET detail gói DRAFT trả 403. Route Web Admin /admin/service-packages cần quyền quản lý nên tài khoản chỉ đọc thấy trang 403. Đây là kết quả đúng quyền hiện có, không bổ sung quyền đọc gói mới.
- Account activity_manager trên Backend chính vẫn có đủ 60 permission, bao gồm ACTIVITY_UPDATE, SERVICE_UPDATE và TOUR_PACKAGE_MANAGE. Ba list hiển thị đúng thứ tự ID trả về API; GET không thêm query/paging. Search/reset chạy phía client, không phát sinh request danh sách mới. API clients và route/work policy không có diff so với HEAD.
- Ghi nhận số phần tử tại thời điểm thử: catalog activities 1, services 29, packages 13. Kết quả đếm nằm trong feature004-final-read.log; số có thể thay đổi khi dữ liệu local được sửa về sau.
- Kiểm tra loading với request chậm rồi nhận dữ liệu thật cho cả ba list. Các ca error/empty/retry đã đạt từ lượt đầu. Tên detail 200 ký tự được kiểm tra ở 390x844 bằng response fixture rõ ràng, không tràn ngang trang.
- Quyền lợi kế thừa: response fixture có packageId của gói cha; UI hiện nhãn kế thừa, không có nút sửa/gỡ trên dòng đó. Đây là kiểm chứng UI owner guard, không phải phép ghi lên dữ liệu kế thừa thật. Lịch dịch vụ ASM vẫn chỉ đọc theo role policy; mutation của CRUISE_MANAGER ngoài phạm vi vai trò này.
- Sau thử nghiệm đã dừng Backend phụ, phục hồi đủ 60 mapping ASM trong DB bản sao. Hash tập permission của bản sao và DB chính đều 8605e62c128174baee4e9522a1566cea. Không giữ một thay đổi permission lâu dài để phục vụ nghiệm thu.

### T017 — Zoom trình duyệt thực tế

- Edge headless dùng profile kiểm thử riêng với native default zoom level log(2)/log(1.2). Không dùng CSS zoom, pinch scale hoặc chỉ thu nhỏ viewport để tính là native zoom.
- Bằng chứng runtime: outerWidth 1366, outerHeight 900, innerWidth 670, innerHeight 403, devicePixelRatio 2, visualViewport.scale 1; CDP visualViewport.zoom xác nhận 2. Chrome có scrollbar/window chrome nên CSS viewport khác 683px của mô phỏng trước đó.
- Kiểm tra đủ 12 route list/detail/new/edit của ba nhóm, thêm operational detail/edit. Mọi route không tràn ngang trang; input/select vẫn 44 CSS px, radius 6 CSS px.
- Modal xóa của cả ba nhóm nằm trong viewport; Tab/Shift+Tab, Escape và focus trả về trigger đạt. Modal quyền lợi với nội dung dài có vùng body cuộn; footer hủy/lưu vẫn sử dụng được ở native 200%.
- Desktop 1366x900, mobile 390x844, dirty cancel, reduced motion và modal quyền lợi ở các chiều rộng đã đạt ở lượt đầu. Không cần sửa thêm UI.
- Ảnh native zoom được chụp bằng CDP layout coordinates và đã xem trực tiếp. Playwright screenshot mặc định ở native zoom có thể cắt ảnh theo CSS width; ảnh cuối không dùng bản chụp bị cắt đó.

### T019 — Cổng cuối

| Tiêu chí | Kết quả cuối |
|---|---|
| SC-001 | Đạt: ASM đủ ba nhóm màn; account chỉ đọc thật được xem activities/services và bị chặn package admin/direct edit đúng quyền hiện có |
| SC-002 | Đạt theo ma trận lượt đầu: validation, draft/unknown outcome, lưu/readback thật; bổ sung Backend 403 thực tế cho account đọc |
| SC-003 | Đạt: các modal chính, fixture registration/attendance, desktop/mobile và native zoom 200% có bằng chứng; phân biệt rõ UI response fixture với mutation Backend thật |
| SC-004 | Đạt: typecheck/build/lint hai feature và hồi quy generic consumer; diff API client/work policy/navigation/Backend src không đổi |

Đã chạy lại npm run typecheck, npm run build và npx --no-install eslint src/features/activities src/features/service-packages --max-warnings 0; đều đạt. Build còn các cảnh báo ship-model-viewer chunk/dynamic import đã có và nằm ngoài scope. git diff --check cho hai feature đạt. Không có checklists/ hoặc .specify/extensions.yml cần gate/hook bổ sung. Không commit/push/merge.

Evidence bổ sung ở target/: feature004-readonly.cjs/.log, feature004-readonly-package-denied.png, feature004-native-zoom.cjs/.log, feature004-native-zoom-form.png, feature004-native-zoom-modal.png, feature004-final-read.cjs/.log và feature004-permission-restore.log. Các log cuối đều PASS. Profile trình duyệt và DB bản sao là artifact local kiểm thử; account đọc chỉ là fixture tạm thời và đã phục hồi quyền sau nghiệm thu.

## Điều chỉnh theo phản hồi UI sau nghiệm thu — 2026-11-07

- Người dùng yêu cầu Activity Manager không có submenu Chuyến khởi hành; đã loại mục này khỏi nhóm sidebar ASM và đổi landing của role sang chức năng được phép đầu tiên ngoài cruises. Giữ API permission và quyền đọc cruise phục vụ ngữ cảnh biểu mẫu.
- Xem chi tiết từ danh sách activities/services/products/service-packages mở trong Modal OceVie hiện có, giữ vị trí/bộ lọc danh sách. Nội dung detail dùng chung với direct URL; không nhúng thêm AdminShell. Hành động sửa vẫn dẫn tới editor hiện có. Modal confirmation/benefit bên trong giữ guard; Escape của modal con không đóng modal cha.
- Quyền lợi gói hiển thị DataTable, Badge, action compact; gói không được chỉnh sửa chỉ hiển thị hai cột thông tin, không có dãy nút disabled. Gộp phần thành phần trùng lặp; ưu đãi theo nhóm dịch vụ vẫn hiển thị khi có dữ liệu. Không đổi các giới hạn DRAFT/permission/inherited owner hoặc request ghi.
- Bỏ các subtext được đánh dấu: hướng dẫn khởi tạo mẫu gói, mô tả Cách phục vụ, hint đơn vị/múi giờ dịch vụ, hai dòng hướng dẫn ngữ cảnh tham quan. Giữ label, lỗi validation và thông báo lỗi nghiệp vụ.
- Kiểm chứng ở 1366x900 và 390x844: mở/đóng detail modal đủ bốn danh mục, URL vẫn ở list, không tràn ngang, chỉ một h1/shell, Escape và focus return đạt. Nested benefit modal kiểm tra Escape chỉ đóng modal con, dirty discard, filter sau đóng modal cha đạt. Các subtext không còn trong source/render.
- Build (bao gồm TypeScript) và lint các feature/tệp sửa đạt; git diff --check đạt. Không thay Backend, DB, API hoặc permission source ở lượt UI này. Navigation/landing được sửa đúng yêu cầu mới nên nhận định navigation không đổi ở cổng nghiệm thu trước chỉ áp dụng cho lượt trước.
- Evidence: target/feature004-modal-followup.cjs/.log và feature004-package-modal-{1366,390}.png. Đã xem ảnh desktop/mobile. Cảnh báo build ship-model-viewer có sẵn vẫn còn; không commit/push.
- Cancel event của Overlay đã được chặn propagation tại dialog để Escape không đóng cả modal cha. Đồng bộ đúng thay đổi một dòng này sang Overlay Web Booking theo quy tắc shared component của repo; Web Booking typecheck và lint Overlay đạt. Web Admin build/TypeScript và lint đạt sau sửa cuối. Không thay giao diện Booking hoặc Mobile.

### Bổ sung nhà cung cấp và nhãn giá theo phản hồi tiếp theo

- Nhà cung cấp: bỏ các summary card rời rạc, dùng PageHeader/Badge/Panel/RecordFacts/Button/Field/Modal chung. List mở detail modal, direct URL vẫn có trang riêng; liên hệ, metadata và xác minh giữ nguyên giá trị. Modal con xác minh có dirty guard, lỗi hiện ngay trong dialog; request, eligibility và permission hiện có được giữ. List chỉ tải lại sau khi có cập nhật/xóa, đóng detail không sửa giữ focus/bộ lọc.
- Bỏ description của header danh mục, gồm dòng “Danh mục gói, thành phần và quyền lợi đã cấu hình.” được người dùng đánh dấu.
- Người dùng xác nhận chỉ muốn nhãn “Giá”. Sửa bản dịch shared program.field.priceUsd từ “Giá mới · USD” thành “Giá”, và label catalog activity tương ứng thành “Giá”. Giữ key, giá trị tiền tệ, payload USD và validation. Không dùng “Giá (USD)” hoặc “Giá mới” trên các trường này.
- Giá trong form tham quan nằm trọn một hàng, giữ required/input/validation hiện có. Desktop/mobile đo price control 44px và không tràn ngang.
- Kiểm chứng bằng target/feature004-supplier-followup.cjs/.log: supplier modal 1366/390, Escape/focus, nested verify dirty/error/payload fixture; không mutation DB. Nhãn giá ở excursion/service/activity và subtext gói đã kiểm tra render. Ảnh supplier-modal và excursion-price desktop/mobile đã xem trực tiếp.
- Web Admin build (TypeScript) và lint supplier + các tệp sửa đạt; diff whitespace đạt. Không thay Backend/API/permission hoặc commit/push.
