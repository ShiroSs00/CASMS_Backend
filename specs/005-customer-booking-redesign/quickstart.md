# Quickstart — Validation guide

Nguồn acceptance là spec.md, contracts/ui-flow.md và contracts/api-preservation.md. Hướng dẫn ban đầu lập 2026-11-08; kết quả thực thi và giới hạn bằng chứng được ghi riêng bên dưới.

## Điều kiện và baseline

1. Kiểm tra checkout/working diff cả hai repo; giữ local changes. Mở AGENTS và design-systems/ocevie/DESIGN.md/tokens.css, components/styles mục tiêu trước sửa.
2. Chạy toolchain hiện có trong CASMS_Frontend/CASMS_WebBooking; ghi lỗi baseline riêng. Không dùng fixture booking-types.ts/VND làm contract live.
3. Backend tương thích và dữ liệu test cô lập: chuyến mở bán, future departure, ship/inventory thật, offer/revision hợp lệ, hỗ trợ guest lookup, quote/create. Không sửa production hoặc tắt guard để làm test đạt.
4. Chuẩn bị ít nhất: 1 guest; 2 người chung physical cabin; 2 nhóm; CATEGORY_HOLD; nhóm mixed-offer cùng tài nguyên và nhóm không có giao cabin; legacy non-fare-managed nếu còn trong adapter; model published và model lỗi/không có. Thiếu case nào ghi gap, không tính mock là runtime.
5. Dùng thông tin hành khách test; ẩn token, guest token, giấy tờ, email/phone và identifiers nhạy cảm trong log/ảnh được chia sẻ.

## Lệnh

Từ CASMS_WebBooking:

```powershell
npm run typecheck
npm run build
npx --no-install eslint src/features/booking src/app/router.tsx src/app/CustomerShipPages.tsx --max-warnings 0
npm run dev
```

Mở cổng Vite hiện có (mặc định 5173), dùng Backend từ cấu hình local hiện hành. Chạy test harness đã có nếu phù hợp; không bắt buộc thêm dependency. Nếu diff ngoài các đường dẫn trên, lint đúng phần mở rộng và kiểm tra consumer.

## Ma trận kiểm chứng

| Ca | Thao tác và kết quả cần chứng minh | FR / SC |
|---|---|---|
| Discovery | Type/page đúng server; detail đọc sale/offer; không giả filter hoặc lead fare; CTA đúng trạng thái | FR-004/005/009, SC-001/005 |
| Four stages | Từ detail vào party → allocation → travellers → review; một heading/progress /4, back/edit không mất dữ liệu còn hợp lệ | FR-003/006, SC-001 |
| Deep links | Mở ba route cũ với cruiseId/from; guard về bước thiếu đầu tiên, không mất query đã sanitize, không bypass eligibility | FR-012, SC-001/002 |
| Membership | Thêm/xóa/chuyển nhóm sau nhập tên/giấy tờ và offer; người còn lại giữ đúng identity/lựa chọn, không chuyển giá sang người khác | FR-005/006, SC-001/005 |
| Mixed offers | Các người cùng cabin có thể giữ offer riêng đủ điều kiện; tập cabin giao rỗng thì báo lỗi, không tự đổi offer | FR-005/007, SC-002/005 |
| Category hold | Không bắt chọn 3D/cabinId; payload cabinId null, group key rõ; không gọi đã phân cabin hoặc giữ chỗ trước server | FR-005/009, SC-005 |
| Physical allocation | Cabin thuộc mapping offer và đủ capacity theo nhánh hiện tại; không nới điều kiện cho cabin đã có phân bổ | FR-005/007, SC-002/005 |
| List ↔ 3D | Chọn list rồi mở model, chọn model rồi trở lại list; cùng cabin và một handler; 3D lỗi/thiếu manifest vẫn chọn được list nếu inventory hợp lệ | FR-007, SC-006 |
| Model lazy | Theo dõi request: không GLB/manifest do flow hoặc mount renderer trước Mở 3D; đóng modal trả focus | FR-007, SC-004/006 |
| Personal validation | Tên, email/phone, guest representative email, DOB/document rules đúng hiện tại; lỗi cạnh trường và focus đầu | FR-008/010/013, SC-004 |
| Quote | Kiểm tra giá tạo quote/readback đúng signature/amount/policy/lines; giữ dữ liệu cá nhân ngoài quote request; không tự thêm cabin price | FR-005/009/010, SC-005 |
| Quote invalidation | Đổi nhóm/order/offer/cabin/payment plan hoặc expiry thì không dùng quote cũ; sửa PII không bịa signature giá mới nhưng vẫn validate cuối | FR-010/011, SC-002 |
| Create | Final readback + một POST với key ổn định; double click chỉ một intent; receipt/reference/amount/cruise đúng đọc lại Backend | FR-009/010/011, SC-002/005 |
| Known error | Mô phỏng 409/422 và ca Backend thật phù hợp: giữ draft, báo lỗi đúng; không success giả | FR-008/011, SC-002 |
| Quote unknown | Mất phản hồi trước có quote identity: trạng thái riêng chặn quote mới, không tự retry; route đổi không xóa guard | FR-011, SC-002 |
| Booking unknown | Mất phản hồi create: giữ intent/lock trong session, không tạo key mới để gửi lại; có đường recovery cũ | FR-011, SC-002 |
| Reload | Không tự POST; không hứa khôi phục in-memory PII/identity; route quay về bước hợp lệ và lookup vẫn hoạt động | FR-012/013, SC-002 |
| Receipt | Hiện Đã tiếp nhận đặt chỗ + reference/status actual; chưa thanh toán/Draft không được biến thành paid/Confirmed | FR-004/009, SC-005 |
| Accessible layout | 1366x900, 390x844, native 200% zoom, keyboard và reduced motion; không tràn trang/che CTA, modal focus và lỗi đọc được | FR-002/008/014, SC-003/004 |
| Regression | /booking/lookup và recovery redirects giữ nguyên; home/public header/customer ship explorer không hỏng; Admin/Mobile/Backend source không đổi | FR-001/004/012 |

## Cách ghi evidence và đóng cổng

- Mỗi case ghi route, loại fixture/role, viewport, thao tác, expected/actual và request/readback đã che thông tin cần bảo vệ. Phân biệt unit/projection test, mock UI, real Backend và visual inspection.
- Chạy focused behavioral tests trước các ca runtime mutation; chỉ ghi dữ liệu trong môi trường test. Không tạo booking thật bằng thông tin người dùng để đo UX.
- Record-count/request identity/payload kiểm trong harness, không xuất raw guestToken/authorization hoặc hồ sơ người thật. Success-only readback và quota/availability không được suy từ screenshot.
- Native zoom dùng browser zoom thật và metrics; viewport nhỏ tương đương chỉ là bổ sung. Kiểm tra desktop/mobile theo lượt giới hạn, không polish vô hạn.
- Tất cả SC-001–SC-006 phải có evidence đạt. Nếu Backend/test data thiếu hoặc lệch contract, giữ task/ca phụ thuộc mở và ghi nguyên nhân. Build/lint không thay thế runtime, mock không thay thế readback.
- Trong giai đoạn plan này chưa chạy build/test UI mới và chưa đo conversion/latency; không báo redesign đã hoàn thành.


## Kết quả implementation — 2026-11-08

### Phạm vi và baseline

- Source chỉ đổi CASMS_WebBooking và tài liệu feature này. API adapters/serializers/decoders, domain, shared primitives/Overlay, package/dependency, Admin, Mobile và Backend source không đổi. Local untracked files ban đầu được giữ nguyên; không commit/push.
- Frontend checkout ban đầu: feat/cruise-schedule-package-updates-20261106 tại 80b4cbf. Backend feature branch: codex/customer-booking-redesign-20261108. Không đổi checkout.
- Baseline trước sửa: typecheck/build/lint PASS. Build đã có cảnh báo chunk >500kB. Sau sửa, typecheck/build/lint PASS, cảnh báo chunk vẫn có; không dùng việc tăng warning limit để làm sạch báo cáo.
- OceVie DESIGN.md/tokens.css, root/app AGENTS, Primitives/Overlay và consumer đã đọc. Canonical stylesheet vẫn được import trong styles/tokens.css. Component mới là composition theo domain; không thêm Button/Field/Modal system hoặc dependency.
- Impeccable launcher đã thử một lần, không chạy được vì engine chưa được cài/cache không ghi được. Đã dùng fallback đọc hướng dẫn Operate/craft-floor và đối chiếu source/ảnh thực tế; không thay canonical OceVie bằng visual world khác.
- Shared consumer: BookingProvider bao toàn bộ public session; Primitives/Overlay không sửa. Booking.module.css thêm selector catalogRedesign chỉ cho live list/detail. BookingParty.module.css theo phạm vi flow. PassengerDetailsFields chỉ còn live consumer đang dùng; legacy exports giữ tương thích. CustomerShipPages và renderer engine không sửa.

### Lệnh và công cụ thực tế

Tại CASMS_Frontend/CASMS_WebBooking:

```powershell
node --import ./tests/helpers/booking-loader.mjs --test tests/booking-redesign/*.test.mjs
npm run typecheck
npm run build
npm run lint
npm run dev -- --port 5174
```

Node v24.22.0, TypeScript hiện có; harness dùng registerHooks/transpileModule và node:test/assert, không thêm package. Test-only HTML ở tests/helpers/cabin-harness.html dùng BookingProvider/handler thật và renderer fixture, không được import từ production app.

Browser dùng Edge headless riêng qua CDP 9335, Vite 127.0.0.1:5174. Các script chỉ fixture API hoặc renderer, không đụng DB:

```powershell
node tests/booking-redesign/browser-smoke.mjs
node tests/booking-redesign/browser-states.mjs
node tests/booking-redesign/browser-sync.mjs
node tests/booking-redesign/browser-regression.mjs
```

Native 200% dùng Edge profile kiểm thử riêng, default_zoom_level=log(2)/log(1.2), CDP 9336. Không dùng profile người dùng. Chạy smoke với BOOKING_CDP_PORT=9336 và BOOKING_NATIVE_ZOOM=1. Metrics: devicePixelRatio=2, innerWidth=670 (cửa sổ vật lý khoảng 1366), visualViewport.scale=1, CSS zoom=1. Không lấy viewport thu nhỏ hoặc pinch làm bằng chứng native zoom.

### Bằng chứng đã đạt

| Loại | Kết quả | Giới hạn |
|---|---|---|
| Node / response fixtures | 17/17 PASS: session ownership, stage guards, catalog paging/type, stable group/person, mixed-offer intersection/capacity, expiry/invalidation/classifier, quote/create/readbacks/key, serializer privacy, legacy ACTIVE links | Dùng response/draft fixture, không chứng minh DB/inventory thật |
| Browser flow fixture | List → detail → party → mixed offers cùng cabin → travellers → quote → receipt; một quote POST, một create POST khi bấm liên tiếp; receipt Draft, không paid/Confirmed giả | API mô phỏng, total 220 USD là fixture |
| Browser error fixture | Empty/error/sold-out CTA, known quote rejection, expiry thu hồi xác nhận, quote-unknown và booking-unknown giữ lock qua unmount/remount, reload không tự POST | Không kiểm chứng network/server fault thật |
| Renderer fixture | Chọn từ model cập nhật radio và cả nhóm; chọn list rồi mở model nhận đúng ID; ID ngoài eligibility bị chặn; giảm người có confirmation, hủy giữ dữ liệu | Renderer nút mô phỏng, không phải GLB/WebGL published |
| UI / keyboard | ArrowLeft/Right trên radio; focus lỗi đầu; Escape và return focus modal; guard/query/back; không tràn trang 1366x900, 390x844 và native 200%; reduced motion | Model thật chưa có fixture, chỉ kiểm loading/error/fallback dialog |
| Token runtime | Select đo 48px trong public shell, border-radius 6px, font Be Vietnam Pro; canonical default-control token đọc được 44px | Không thay đổi shared token mapping |
| Public regression | Home, ships shell, lookup, my-bookings và login recovery; query/reference/bookingId/hash được giữ; không uncaught JS exception trong các smoke fixture | Fleet/model data dùng response fixture lỗi khi chưa được định nghĩa; không nghiệm thu model published thật |
| Static / build | Typecheck/build/lint và git diff --check PASS. API/domain/shared/dependency/Admin/Mobile/Backend source diff không có | Build warning chunk >500kB tồn tại từ baseline |

Ảnh trong evidence/ chỉ chứa dữ liệu thử nghiệm tổng hợp; không có thông tin khách thật hoặc token. Suffix -zoom200 là native zoom, các ảnh desktop/mobile còn lại là viewport tương ứng. Không dùng ảnh hoặc fixture response làm bằng chứng Backend E2E.

### Backend local và các cổng chưa đạt

- Kiểm tra ban đầu trong sandbox không thấy Docker; kiểm tra host sau đó xác nhận container casms-postgres và PostgreSQL cổng 5432 đang chạy. Không có Backend cổng 8080 đang chạy.
- DB ocv_mf02_unified_e2e tồn tại nhưng chưa có schema ứng dụng. DB ocv_mf02_e2e_test có 133 bảng public và được thử cho startup, với migration tắt, Hibernate ddl-auto=validate, bind 127.0.0.1:8080 và SePay tắt. Credentials chỉ lấy trong memory, không ghi vào artifact.
- Backend startup FAIL: Schema-validation: missing column [catalog_entry] in table [OnboardActivity]. Kiểm tra metadata các DB ocv_latest/casms_local_20261102/casms cũng chưa có cột này. Không sửa DB, không bật ddl-auto update, không chạy migration hoặc trỏ DB ngoài local để vượt gate. Backend thử nghiệm đã thoát.
- Vì vậy T012/T024/T033 giữ mở: chưa có real sale/availability/offer readback, valid GLB published, quote/create/readback guest/authenticated. Cần môi trường test schema tương thích trước khi nghiệm thu tiếp.
- T007 đã bổ sung PASS trên checkpoint dựng lại riêng trong target/feature005-foundation-checkpoint: router/màn ba bước từ HEAD 80b4cbf, implementation Provider/context ownership hiện tại cùng helper transaction, controller cũ chỉ áp phần hoist quote/unknown. Chạy browser-foundation-checkpoint.mjs với Vite checkpoint ở 5175: cabin-experience → travellers → review → quote modal; 1 quote/0 create, category hold/USD/from query giữ đúng. Đây là test hồi quy snapshot sau implementation, không backdate thành bằng chứng đã chạy trước khi wire router worktree. Snapshot và server test tách khỏi source sản phẩm.

### Giới hạn A1 và trạng thái cuối

Khóa/identity chỉ được giữ trong cùng BookingProvider còn sống. Đổi route không làm mất lock; đổi economic selection thu hồi quote, sửa PII vẫn validate cuối. Sau reload không hứa khôi phục draft/PII/intent; không tự POST, không báo thành công giả, quay về party/lookup hiện có. Không thêm localStorage/sessionStorage chứa PII/guest token. Không tuyên bố chống lặp xuyên reload khi chưa có recovery contract bền vững.

35/38 task đã đánh dấu đạt; còn T012/T024/T033 như trên. Feature chưa được nghiệm thu hoàn tất với Backend thật. requirements.md giữ nguyên 16/16 markers. after_implement hooks: .specify/extensions.yml không tồn tại.


### Setup / ignore verification

Git ignore đã che node_modules/dist/coverage/log/env, bảo toàn .env.example. ESLint flat config có ignores dist/node_modules/coverage. Package private nên không cần .npmignore xuất bản. Không có Dockerfile/Prettier/Terraform/Helm trong app để tạo ignore không liên quan; không sửa ignore hiện hữu. Profile native zoom và checkpoint nằm trong Backend target/ đã được git check-ignore xác nhận.
