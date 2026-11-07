# Kế hoạch FE — Lịch trình và dời giờ khởi hành

**Feature Spec Kit** 003-itinerary-departure-delay-fe. **Checkout Backend** main. **Checkout FE** feat/cruise-schedule-package-updates-20261106.

## Technical Context

React, TypeScript, React Router, Vite và CSS Modules của CASMS_WebAdmin. Dùng các primitive OceVie hiện có và session access từ /auth/me. Chỉ lập kế hoạch cho Web Admin. API là nguồn sự thật về lịch, revision và điều kiện vận hành.

## Constitution Check

Trước và sau thiết kế, constitution của workspace vẫn là template chưa có nguyên tắc được điền. Không thể xác nhận tuân thủ nguyên tắc dự án từ template. Các giới hạn theo yêu cầu được giữ trong đặc tả và hợp đồng FE.

## Phạm vi theo vai trò

| Bề mặt | Điều phối lịch trình (CRUISE_MANAGER) | Quản lý vận hành (OPERATIONS_MANAGER) |
|---|---|---|
| /admin/trips và trình chỉnh mẫu | Giữ quyền tạo/sửa, bản nháp, kiểm tra và gửi hiện hành | Xem hoặc xét duyệt theo quyền hiện có |
| Form chuyến khởi hành | Chọn mẫu, giờ chuyến và xem preview, theo quyền hiện tại | Giữ quyền hiện hành |
| Chi tiết chuyến | Xem lịch/lịch sử theo quyền API | Cùng quyền đọc tương ứng. Preview và xác nhận delay cần thêm CRUISE_START |
| Bắt đầu vận hành | Giữ chính sách hiện hành | Giữ chính sách hiện hành |

## Phase 0 — Đối chiếu

Xem research.md. Đã xác minh ba endpoint delay và schema lỗi từ source Backend main tại 7d13ce4. FE dùng /cruises/{id}/delays với số nhiều. Gate còn lại là kiểm thử tích hợp trên bản backend chạy thực tế.

## Phase 1 — Thiết kế

1. Giữ trình chỉnh mẫu trong features/trip-planning. Tái sử dụng CruiseFormPage, DepartureSchedulePreview và departure-api ở form chuyến.
2. Đặt thao tác delay trong khu Vận hành của CruiseDetailPage, cạnh Departure Gate. Tái sử dụng Field, Button, Modal, Notice, Loading và bộ hiển thị lịch hiện có. Lịch sử delay nằm trên chi tiết chuyến theo quyền đọc tương ứng.
3. Tách draft, preview và confirm intent theo data-model.md. Sau confirm, vô hiệu dữ liệu cũ và đọc lại chi tiết, lịch, lịch sử và gate. Cơ chế đổi lịch hiện hữu tiếp tục xử lý lịch cần phân bổ lại.
4. Xác minh theo quickstart.md. Hợp đồng source đủ để lập tasks. Đưa kiểm thử bản backend chạy thực tế vào tiêu chí nghiệm thu.
