# Data Model — Booking view model và state

Feature UI-only; không migration hoặc đổi cấu trúc Backend. Nguồn chuẩn: features/booking/api/real-booking-api.ts, booking-quote-api.ts và domain/cruise-package/purchase-contract.ts trong Web Booking.

## Dữ liệu hiện có

| Entity | Trường/quan hệ cần giữ | Ràng buộc |
|---|---|---|
| Cruise | cruiseId, shipId, giờ đi/về, status, sellability | Chỉ tiếp tục khi adapter xác nhận được bán; không dùng PUBLISHED như đủ điều kiện duy nhất |
| Offer/price revision | offerId, priceRevisionId, logical category, physicalCabinIds, amount/currency | Theo từng passenger; lựa chọn phải nằm trong catalog hiện hành của chuyến |
| AvailableCabin | cabinId/number, deckId/name/number, cabinType, capacity, remainingCapacity, isAccessible | Không đoán thuộc tính thiếu; dùng đúng filter của offer và capacity hiện hành |
| BookingDraft | cruiseId, currency, paymentPlan, notes, passengers[] | USD; FULL/DEPOSIT/STAGES; notes tối đa 500; giữ serializer hiện có |
| BookingPersonDraft | fullName, DOB, nationality, documentType/number, email, phoneNumber, cabinId, tourPackageId, farePackageOfferId/revision, mode, cabinGroupKey | Giữ validation tên 2–100, email/phone, ngày sinh, giấy tờ trùng và các ràng buộc hiện hành; guest cần email đại diện theo guard hiện có |
| QuoteIntent | quote, selection fingerprint, guestToken?, idempotencyKey | Một identity cho cùng ý định gửi; readback/expiry/policy/total và từng line phải khớp |
| SavedBooking | id/reference, cruise, currency, agreedFareAmount, trạng thái, lịch thanh toán/contract | Reference chỉ chứng minh đã tiếp nhận; không đồng nhất payment/contract/booking status |

## Projection UI mới

- **Party/group view**: khóa người đi ổn định trong UI, nhãn Hành khách 1…; khóa nhóm ổn định và membership; chỉ là view của passengers[], không là bản nhập song song. Có thể tạo slot trống trước khi nhập họ tên. Việc xóa thành viên phải giữ đúng định danh của người còn lại, không chuyển offer/giấy tờ sang người khác do đổi index.
- **Group allocation**: tên dễ hiểu Phòng 1/2 thay free-text group key. cabinGroupKey vẫn dùng serializer hiện hành. Mỗi nhóm cùng tài nguyên mode + cabinId hoặc mode + loại cabin; không áp điều kiện “mọi người cùng offer” nếu contract không yêu cầu.
- **Selection presentation**: active group/person, selected cabin, bộ lọc danh sách trên tập ứng viên đã hợp lệ, modelOpen/loading/error. Không đưa các field UI này vào payload.
- **Progress**: party → allocation → travellers → review. Complete/invalidated là trạng thái UI, không phải booking lifecycle. Đổi số người/nhóm cập nhật guard và quote fingerprint tương ứng.

Không tăng hạn mức số người tùy ý, không tự tách nhóm để lách capacity. Empty group key hiện mặc định BOOKING_GROUP ở CATEGORY_HOLD hoặc CABIN:<id> ở physical; UI mới phải tạo nhóm rõ ràng và giữ nghĩa mapping, không bỏ key gây gộp nhóm ngoài ý muốn.

## Cabin mode và hợp lệ

- PHYSICAL_CABIN: cabin thuộc physicalCabinIds của offer nếu fare-managed, đủ nguyên capacity theo contract và đủ chỗ cho nhóm sau khi tính các thành viên khác trong draft.
- CATEGORY_HOLD: cabinId gửi null, chưa có cabin vật lý; chỉ server quyết định khả năng giữ hạng cho nguyên nhóm.
- Nhóm trộn các offer hợp lệ chỉ chọn cabin trong giao các tập ứng viên của thành viên; nếu không có giao thì báo cần điều chỉnh lựa chọn hoặc nhóm, không tự đổi gói.
- Nhánh legacy non-fare-managed tiếp tục có cabin/tourPackageId theo adapter; không buộc nó sang fare-managed hoặc trộn legacy package vào fare selection.

## State transitions

| State | Hành động / kết quả | State tiếp theo |
|---|---|---|
| incomplete | Hoàn tất các bước trước hợp lệ | review-unquoted |
| review-unquoted | Khách bấm Kiểm tra giá, một request có lock | quoting |
| quoting | Quote/readback hợp lệ | quote-ready |
| quoting | Lỗi đã xác định | review-unquoted, giữ draft/lỗi |
| quoting | Kết quả chưa xác định, không có identity để đọc lại | quote-unknown, chặn quote mới, dùng phục hồi hiện có |
| quote-ready | Đổi economic selection/nhóm/thứ tự người/payment plan hoặc hết hạn | review-unquoted, quote cũ không dùng được |
| quote-ready | Validate cuối, khóa gửi, readback quote/create | submitting |
| submitting | Response được decoder/identity/amount guard xác nhận | received, giữ saved result |
| submitting | Rejection đã xác định | Giữ lỗi/draft và chỉ dùng quote tiếp khi còn hợp lệ |
| submitting | Không xác định kết quả ghi | booking-unknown, giữ intent/lock recovery, không POST lại |
| received | Xem reference, trạng thái và link lookup | Kết thúc phạm vi redesign |

Các trạng thái unknown/lock không bị xóa chỉ vì chuyển route. Lookup sau đặt chỗ nằm ngoài redesign. In-memory draft/quote không được hứa sống qua reload; không tự ghi PII/secret vào browser storage. Kiểm tra reload chỉ đảm bảo không tự gửi, không hiển thị success giả, quay về ngữ cảnh hợp lệ và giữ đường đối chiếu hiện có.
