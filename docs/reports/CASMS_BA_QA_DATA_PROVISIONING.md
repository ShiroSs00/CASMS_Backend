# CASMS Booking Agent — Cấp dữ liệu QA

## Chạy kiểm kê và HTTP

Từ thư mục Backend, dùng Python 3 và PostgreSQL `psql`. `qa.env.local` là tệp cấu hình riêng của môi trường với `DB_URL`, `DB_USERNAME` và `DB_PASSWORD`. Tool chấp nhận JDBC loopback `127.0.0.1:15439` với tên database nguồn QA đã quy định.

```powershell
python scripts/booking_agent_qa.py --qa-config qa/config/qa.env.local --inventory-only --evidence target/booking-qa-inventory.json
mvn '-Dmaven.test.skip=true' package
python scripts/booking_agent_qa.py --qa-config qa/config/qa.env.local --jar target/casms-0.0.1-SNAPSHOT.jar --plan qa/config/booking-agent-plan.local.json --cleanup --evidence target/booking-qa-e2e.json
```

`--inventory-only` kết thúc sau truy vấn read-only. Bỏ `--plan` để chạy migration và các probe HTTP có thể thực hiện trên dữ liệu hiện hữu. Nếu cần, `--psql` và `--java` nhận đường dẫn executable. `--template` chỉ nhận tên PostgreSQL QA theo guard. Status `PASS`, `FAIL`, `BLOCKED` tương ứng exit code `0`, `1`, `2`.

Mỗi lần chạy tạo clone riêng `casms_ba_qa_<random>` và JVM loopback riêng. JVM chỉ đọc cấu hình Spring đóng gói trong JAR cùng tùy chọn QA của tool. Việc attach package, mở bán và phân công có readback trước khi ghi. Mở bán dùng revision, fingerprint và event identity của lần chạy. Việc tạo booking dùng idempotency key rồi recovery trước khi replay. `--cleanup` drop duy nhất clone vừa tạo sau khi JVM dừng, với PostgreSQL từ chối drop nếu còn kết nối. JSON ghi HTTP status và code, log JVM nằm cạnh JSON để chẩn đoán cục bộ.

## Chuẩn bị hai chuyến

1. Cung cấp hai tài khoản QA hoạt động và có vai trò `BOOKING_AGENT` độc lập. Có thể thêm `--create-agent-b` cùng credentials `CASMS_BA_QA_HR_USERNAME` và `CASMS_BA_QA_HR_PASSWORD` của Admin/HR được cấp `USER_CREATE`. Tool tạo nhân sự qua `POST /employees`, nhận thư kích hoạt ở SMTP loopback chỉ chấp nhận địa chỉ ngẫu nhiên `@example.test`, rồi gọi API kích hoạt. Token và mật khẩu chỉ nằm trong bộ nhớ của lần chạy. Chọn hai chuyến riêng có cabin phù hợp. Cần ít nhất một chuyến legacy để nghiệm thu đổi cabin legacy.
2. CRM chuẩn bị lịch trình, trạng thái plan, phạm vi bán, cabin, legacy package hoặc fare offer và revision giá theo nghiệp vụ. `advancePreparedPlan: true` cho phép tool yêu cầu sửa hồ sơ stale và chạy `submit`, `approve`, `ready` qua API hiện hành với quyền tương ứng. Tool đọc revision từ dashboard chuẩn bị bằng CRM. Nếu phân hệ cần nộp bằng chứng, cung cấp `preparationEvidence` do điều phối viên QA xác nhận. `attachLegacyPackages: true` gắn package hiện có qua CRM nếu còn thiếu.
3. Cấu hình cặp biến `CASMS_BA_QA_<ACTOR>_USERNAME` và `CASMS_BA_QA_<ACTOR>_PASSWORD`. `AGENT_A`, `AGENT_B` chạy booking. `SALES` cần `CRUISE_READ`, `CRUISE_OPEN_SALE` và quyền Operations tương ứng khi chạy plan. `STAFF` phải có vai trò `HR_MANAGER` cùng `CRUISE_STAFF_READ`, `CRUISE_STAFF_ASSIGN`. Có thể thêm `--create-staff-hr` để tạo HR thử qua Admin QA và mailbox loopback tương tự. `CRM` là Cruise Manager có quyền sửa draft khi dùng tùy chọn CRM. `FINANCE` là Finance Officer có `PAYMENT_PROCESS` và quyền refund nếu kiểm thử hoàn tiền.
4. File plan có `cruises` chứa đúng hai đối tượng. Mỗi đối tượng có `cruiseId`, `scope`, danh sách UUID `legacyPackageIds` và `fareOfferIds`. Với `SELECTED_PACKAGES`, điền `tourPackageId` thuộc danh sách legacy. Với fare, dùng `WHOLE_CRUISE` và `fare.offerId`. Tool đọc giá hiện hành và cabin tương thích từ catalogue. Có thể chỉ định `fare.priceRevisionId` và `fare.cabinId` để khóa đầu vào. Có thể cung cấp `expiredQuoteId` là quote QA thực sự hết hạn của chuyến và cùng agent. Tạo quote từ API và chờ TTL hiện hành nếu chưa có quote hết hạn. Bật `verifyFinanceCancellation` để kiểm thử quyết định phê duyệt và booking bị hủy bằng Finance QA sau ca thu hồi của BA.
5. Cấp `CASMS_BA_QA_SIGNED_CONTRACT_REFERENCE_AGENT_A` / `_AGENT_B` và `CASMS_BA_QA_COLLECTION_RECEIPT_AGENT_A` / `_AGENT_B` qua kênh QA. `paymentEvidence` chứa `order` (`payment-first` hoặc `contract-first`) và `collectedAmount`. Ca `contract-first` có thể cung cấp thêm `partialAmount` và `CASMS_BA_QA_PARTIAL_RECEIPT_<ACTOR>`. Tổng các khoản thu phải bằng cọc yêu cầu. Kiểm tra hoàn tiền cần `paymentEvidence.refundAmount` và `CASMS_BA_QA_REFUND_RECEIPT_<ACTOR>`. Dùng chứng từ do người phụ trách finance QA xác nhận.

## Phụ thuộc còn thiếu

| Hạng mục | Phụ thuộc để tiếp tục |
| --- | --- |
| Ready và mở bán | CRM hoàn tất plan và commercial scope. Operations phê duyệt và xác nhận Ready. Hai fare đã mở bán qua API trên clone. Chuyến legacy vẫn cần cấu hình thương mại hợp lệ. |
| Phân công | Hai BA có thể kích hoạt qua API QA. CRM phụ trách chuyến lưu ngân sách VND đã xác nhận qua `PUT /cruise/{id}/preparation/operating-budget` với `expectedRevision` và `amountVnd`. Hồ sơ staffing phải đi qua bên phụ trách và đạt `SENT_TO_HR` trước khi HR phân công. `submitStaffingRequest: true` cho phép tool tạo hồ sơ qua API khi ngân sách đã có. |
| Booking và cabin | Cần fixture chuyến legacy đã phát hành với `CruisePricingPolicy` được duyệt, cabin đích còn chỗ và gói legacy hợp lệ. `usesUnifiedPackages` đưa DRAFT mới vào luồng unified nên template cần có chuyến legacy đã phát hành từ quy trình hợp lệ. |
| Hợp đồng và thanh toán | Hai bộ tham chiếu hợp đồng/chứng từ thu theo hai thứ tự nghiệp vụ, tài khoản Finance QA và chứng từ hoàn khi kiểm thử refund. |

Các clone đã kiểm thử dùng Flyway mặc định. Nếu gặp database legacy thực sự cần nâng cấp, owner chạy thử trên clone và truyền `casms.flyway.legacy-upgrade-jdbc-url` bằng chính chuỗi URL JDBC của kết nối được duyệt. Guard dừng khi URL, schema hoặc history table khác điều kiện legacy.
