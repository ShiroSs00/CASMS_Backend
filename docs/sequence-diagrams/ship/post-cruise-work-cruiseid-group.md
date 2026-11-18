# POST /cruise-work/{cruiseId}/{group}

## Operations preparation

See the [reviewed flow](../CRUISE-PREPARATION-REVIEW-FLOWS.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

### Bàn giao chương trình trước khi kiểm tra tàu

Đối với yêu cầu đã đi qua luồng staffing, việc ASM được tạo hoặc tái sử dụng từ bước khởi tạo. Không thể gửi yêu cầu SHIP độc lập để bỏ qua bước hoàn tất ASM.

[Xem luồng đầy đủ](../SHIP-STAFFING-HANDOFF.md).
