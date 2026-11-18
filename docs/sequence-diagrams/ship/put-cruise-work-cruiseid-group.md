# PUT /cruise-work/{cruiseId}/{group}

## Operations preparation

See the [reviewed flow](../CRUISE-PREPARATION-REVIEW-FLOWS.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

### Bàn giao chương trình trước khi kiểm tra tàu

Với yêu cầu staffing chờ ASM, COMPLETE kiểm tra chương trình trước khi chuyển việc cho quản lý tàu. Lỗi dữ liệu hoặc revision giữ nguyên trạng thái của cả hai yêu cầu. Khóa chuyến và cập nhật cả hai trong cùng giao dịch.

[Xem luồng đầy đủ](../SHIP-STAFFING-HANDOFF.md).
