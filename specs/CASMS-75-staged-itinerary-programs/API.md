# CASMS-75 — Tích hợp API lịch trình

Prefix runtime `/api`. Response dùng `ApiResponse` hiện có. Thời gian ISO-8601 có timezone, ví dụ `2030-02-01T09:00:00+07:00`. Dùng UUID occurrence trả từ bước gán khi bổ sung lịch, không dùng UUID nguồn.

## Quy trình

1. Tạo lịch trình/bố trí cảng bằng API hiện có; chưa cần đầy đủ hoạt động.
2. `GET /ships/{shipId}/areas/usages`: đọc `area_usage` và `has_services`. Ship Manager phân loại bằng `PUT /ships/{shipId}/areas/{areaId}/usage` với `{"usage":"ACTIVITY_SPACE"}`. Enum khác: OTHER, SERVICE. Khu vực đã gán dịch vụ phải bỏ dịch vụ trước khi phân loại hoạt động.
3. `GET /itineraries/{itineraryId}/programs`: nhận `cruiseId`, `revision` (của Cruise), `activities`, `excursions`, `issues` gồm code/kind/id/message.
4. `POST /itineraries/{itineraryId}/programs/assignments`, header `If-Match: <revision>`, body:

```json
{
  "activityIds": ["<UUID định nghĩa onboard>"],
  "excursions": [{
    "sourceExcursionId": "<UUID định nghĩa shore>",
    "cruiseStopId": "<UUID lần ghé trong lịch trình>",
    "destinationId": "<UUID điểm đã lên kế hoạch tại lần ghé đó>"
  }]
}
```

Trả 201, data gồm `revision`, `activityIds`, `excursionIds` của các occurrence mới. Có thể gửi một mảng rỗng, tổng phải có ít nhất một phần tử; mỗi mảng tối đa 100. Gán additive, nguồn có thể lặp trong nhiều occurrence. Không tự đặt thời gian/địa điểm/sức chứa/giá chưa biết.

5. `PUT /itineraries/{itineraryId}/programs/activities/{activityId}/schedule`, header revision mới:

```json
{"startsAt":"2030-02-01T09:00:00+07:00","endsAt":"2030-02-01T10:00:00+07:00","areaId":"<UUID ACTIVITY_SPACE>","location":"Sân khấu tầng 1","capacity":20,"price":0}
```

6. `PUT /itineraries/{itineraryId}/programs/excursions/{excursionId}/schedule`:

```json
{"startsAt":"2030-02-01T11:00:00+07:00","returnsAt":"2030-02-01T14:00:00+07:00","meetingPoint":"Cầu cảng","location":"Điểm tham quan đã chọn","capacity":20,"price":0}
```

Giá dùng USD theo quy tắc hiện có. Hai API trả revision mới trong data. Đổi giờ/địa điểm phải gửi đủ bộ trường. Nhiều hoạt động có thể cùng giờ và cùng area. Tham quan phải về **trước** all-aboard, không bằng giờ đó.

7. `DELETE /itineraries/{itineraryId}/programs/{activities|excursions}/{programId}` với If-Match: bỏ occurrence PLANNED chưa có đăng ký hiệu lực; giữ bản ghi CANCELLED và nguồn.
8. Gửi từng hồ sơ bộ phận như trước. Khi gửi tổng thể qua `/cruise-plans/{cruiseId}/submit` hoặc `/itineraries/{itineraryId}/submit`, mọi occurrence đã chọn phải đủ thông tin. Approve/publish/ready cũng kiểm lại; vẫn giữ các điều kiện readiness khác.

## Quyền và lỗi

Đọc programs: ITINERARY_READ hoặc CRUISE_READ. Sửa programs: CRUISE_MANAGER và CRUISE_UPDATE_DRAFT; itinerary DRAFT, plan được sửa. Đọc usage: SHIP_CONFIGURATION_READ hoặc CRM có CRUISE_UPDATE_DRAFT. Sửa usage: SHIP_MANAGER và SHIP_MODEL_OBJECT_UPDATE.

409 STALE_REVISION/PLAN_FROZEN/ITINERARY_NOT_EDITABLE/PROGRAM_NOT_EDITABLE; tải lại dữ liệu trước khi sửa tiếp. Sai scope không tạo dữ liệu sang chuyến khác. 422 cho window/area/destination không hợp lệ; `ITINERARY_PROGRAMS_INCOMPLETE` kèm issues khi gửi trực tiếp itinerary. Mainflow vẫn dùng cấu trúc blockers và mã lỗi PLAN_INCOMPLETE hiện có.

Migration V95 cần được triển khai trước code. Các occurrence cũ chưa có area phải được bổ sung qua quy trình phù hợp trước lần gửi/duyệt tiếp theo. Không tự suy luận area theo chuỗi location cũ. Không bắt gán toàn bộ danh mục.
