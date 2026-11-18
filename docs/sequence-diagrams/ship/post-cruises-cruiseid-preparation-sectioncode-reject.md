# POST /cruises/{cruiseId}/preparation/{sectionCode}/reject

## Operations preparation

See the [reviewed flow](../CRUISE-PREPARATION-REVIEW-FLOWS.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

### Kiểm tra tàu và bố trí nhân sự

Khi tính checklist SAFETY_CREW, hệ thống kiểm tra nhân sự onboard và trạng thái duyệt yêu cầu nhân sự. Yêu cầu mới cần được vận hành duyệt và đáp ứng lại điều kiện về tàu, POS, định mức dịch vụ, khu vực và hoạt động. Kết quả ảnh hưởng trạng thái checklist và khả năng xác nhận phần này. [Luồng kiểm tra và bàn giao](../SHIP-STAFFING-HANDOFF.md).
