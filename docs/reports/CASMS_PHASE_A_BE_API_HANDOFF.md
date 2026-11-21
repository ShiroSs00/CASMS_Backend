# Phase A Backend — Frontend handoff

Các route dưới đây dùng tiền tố `/api`. Revision ở mutation luôn lấy từ response mới nhất của chính đối tượng đang sửa. Trạng thái workflow và mã lỗi hiện có được giữ. Các DTO được mở rộng; có thay đổi hành vi có chủ đích ở danh sách lựa chọn được chấp nhận.

| Endpoint và dữ liệu | Trước | Phase A và việc Web Admin cần cập nhật |
| --- | --- | --- |
| `GET /cruises/staffing-requests/{requestId}/review-details` → `plannedServices[]` | `eligibleAreaIds` liệt kê cả khu vực chưa cấu hình cho dịch vụ. | V2 trả `configuredPlacements[]` gồm cặp `{areaId, terminalId}` đủ cấu hình nền. `eligiblePlacements[]` là tập con có khu vực đang mở và đã chọn đúng máy, lưu được với cấu hình hiện tại. `eligibleAreaIds` của V2 lấy từ tập con này. Chọn cặp cụ thể để gọi `PUT …/service-placements/{serviceId}`. `proposal` vẫn mô tả đề nghị boong riêng. |
| Cùng endpoint → `areas[].eligibleTerminals`, `shipPlanReady` | Các máy liên kết đang bật có thể thiếu quyền đọc service; client tự suy độ đầy đủ. | V2 chỉ trả máy đúng khu vực và có `SERVICE_READ`. Dùng `shipPlanReady` cho gate `SHIP_CHECKED` cùng revision và quyền hiện có. Khi danh sách rỗng và không có dịch vụ cần placement, hiển thị “Không áp dụng” nếu server trả `true`. Bỏ điều kiện bắt `activeAreas.length > 0` trong `StaffingV2.tsx`. |
| `GET /cruises/staffing-requests/{requestId}` và snapshot trong các response staffing | Trả nhu cầu và nhân sự, thiếu số hàng thực tế sẽ mất khi reset. | Đọc `planResetImpact.needsToReplaceOnSave` và `assignmentsToClear`. Trả về ASM/Ship xóa assignment, giữ danh sách nhu cầu tới lần lưu phương án. Lưu phương án tạo lại nhu cầu và xóa assignment cũ. Trả OPS→CRM hoặc CRM→HR giữ phân công. Nếu `shipPlanStale` là true, tải lại và hướng dẫn trả Ship; đừng tính `CruiseStaff` ACTIVE vào người đã gán theo nhu cầu. |
| `GET /cruise-plans/{cruiseId}/program-options` | Khu vực hoạt động có thể đến từ DRAFT và nhiều model. Không phân biệt service đề xuất. | `ship.modelId` là model READY hiệu lực hoặc null. `activityAreas` giới hạn đúng model và đúng boong/tàu. `services[].configuredOnShip` cho biết đã gắn danh mục trên boong tàu hay mới là đề xuất khi ASM đang chuẩn bị. Cờ này chưa biểu thị đủ Area/POS. |
| `GET /cruise/{cruiseId}/service-locations` | Có `decks` và `shipAssigned`, khó diễn giải lý do rỗng. | Dùng `shipId` và `deckBlocker` là `SHIP_REQUIRED`, `SHIP_DECK_REQUIRED` hoặc null. `shipAssigned` hiện hành nói về người phụ trách tàu. Giữ đường đề nghị/xác nhận boong riêng. |
| `GET /cruise/{cruiseId}/preparation` và mutation section | `status` chung, revision khóa hiện tại, check items và người xử lý. | Giữ trường cũ. Adapter `preparation-api.ts` nhận thêm `dataReadiness`, `submissionStatus`, `approvalStatus`, `submittedRevision`, `missingRequiredCheckCodes`. Revision khóa vẫn là `revision`. `submittedRevision` có thể null với hồ sơ lịch sử thiếu event nộp. `allowedActions` quyết định CTA được phép. |

## Lý do chưa đủ điều kiện

- `placementBlocker` null nếu có cặp đang dùng được. `ACTIVE_AREA_POS_SELECTION_REQUIRED` nghĩa là có cặp cấu hình hợp lệ, cần mở khu vực hoặc chọn đúng máy trước.
- `SERVICE_INACTIVE`, `SHIP_MODEL_REQUIRED`, `DECK_SERVICE_REQUIRED`, `AREA_SERVICE_REQUIRED`, `SERVICE_AREA_POS_REQUIRED` và `SERVICE_POS_PERMISSION_REQUIRED` là mã lý do trong DTO. Hiển thị tiếng Việt và đường xử lý đúng quyền.
- Lỗi mutation giữ mã cũ. Tải lại khi gặp `STAFFING_REVISION_CONFLICT` hoặc `STAFFING_SHIP_REVIEW_STALE`. Cặp cấu hình không còn đủ quyền trả `STAFFING_SERVICE_POS_PERMISSION_REQUIRED`; máy ngoài phạm vi hoặc không đủ quyền khu vực trả `STAFFING_POS_PLACEMENT_OUT_OF_SCOPE`. `ACTIVITY_AREA_REQUIRED` cũng áp dụng khi khu vực thuộc model đã thay.
- Nếu CRM gặp HTTP 422 `PREPARATION_BUDGET_REQUIRED`, tải `GET /cruise/{id}/preparation/operating-budget`. Khi `canEdit` cho phép, gửi `PUT` cùng `expectedRevision` của chuyến và `amountVnd` là chuỗi số VND nguyên dương. Lấy lại `scheduleRevision` trước khi gửi staffing.
- FE cần hiển thị `TECHNICAL_NAUTICAL` đang soạn, đã nộp hay đã duyệt độc lập với kết quả kiểm tàu. Bên duyệt dùng revision mới nhất và action từ API. POS/khu vực/ca hoạt động dùng dữ liệu cấp quyền tương ứng, tách khỏi trạng thái đủ người trong staffing.


## Payload cho các thao tác chính

- Tạo yêu cầu bằng `POST /cruises/{cruiseId}/staffing-requests` với `scheduleRevision` từ GET cùng route và `requirements: []`. Chuyển bước bằng `POST /cruises/staffing-requests/{requestId}/{action}` với `expectedRevision` và `comment` bắt buộc khi trả.
- Lưu phương án bằng `PUT …/ship-plan` với `expectedRevision`, `activeAreaIds` và `needs`. Khi không áp dụng, gửi hai danh sách rỗng. Chọn máy bằng `PUT …/area-pos/{areaId}` với `terminalId` và `expectedRevision`. Lưu placement bằng `PUT …/service-placements/{serviceId}` với `areaId`, `terminalId` và `expectedRevision`.
- Gán hoặc gỡ nhu cầu bằng `PUT …/needs/{needId}/assignments/{userId}` với `assigned` và `expectedRevision`. Ca dùng API công việc hiện hành. Membership và duty còn hiệu lực cần hiển thị từ nguồn riêng.
- `dataReadiness` là `READY` hoặc `INCOMPLETE` theo hạng mục bắt buộc hiện tại. `submissionStatus` cho biết có người nộp được ghi nhận. `approvalStatus` là `NOT_REVIEWED`, `PENDING`, `CONFIRMED`, `RETURNED`, `REJECTED` hoặc `STALE`. Nhóm đã trả có thể còn thông tin lần nộp trước.
