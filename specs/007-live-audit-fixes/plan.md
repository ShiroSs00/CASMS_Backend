# Kế hoạch 007 · Khắc phục lỗi kiểm thử live

## Technical context
Backend Spring MVC/JPA và PostgreSQL. Frontend React/TypeScript, sử dụng AdminUI, Primitives, Overlay và token OceVie đã nối runtime.

## Constitution check
Phạm vi có hai ứng dụng web và hai lỗi hợp đồng Backend trong báo cáo. API tra cứu giữ kiểm tra sở hữu/xác thực. Hợp đồng tàu tương thích khi FE/BE triển khai lệch phiên bản. Giữ chính sách quyền, luồng đặt chỗ, dữ liệu local hiện có và các consumer ngoài web. Xác minh riêng các ca hồi quy có rủi ro dữ liệu.

## Thứ tự
1. Khắc phục ghi chú đăng ký và tìm yêu cầu.
2. Sửa phản hồi API và cải thiện trạng thái nguồn, danh mục và tra cứu.
3. Rà copy, focus và responsive. Kiểm thử hồi quy.

## Artifacts
Xem [research](research.md), [data model](data-model.md), [contracts](contracts/web-api.md), [quickstart](quickstart.md) và [tasks](tasks.md).
