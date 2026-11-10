# Implementation Plan: CASMS-75

**Spec**: [spec.md](spec.md) | **Branch**: `feat/CASMS-75-itinerary-management`

## Technical Context

Java 21, Spring Boot 3.3.4 MVC; Maven 3.9.16, Surefire 3.2.5, JUnit/Mockito từ starter-test. Không có Maven Wrapper. JPA và JdbcTemplate cùng transaction manager; Flyway migration V95; kiểm thử disposable PostgreSQL 16, phiên bản PostgreSQL triển khai chưa xác định.

## Design

1. Bổ sung `area_usage`, FK area occurrence và nguồn excursion; chỉ PLANNED/CANCELLED được phép thiếu dữ liệu lịch. Backfill theo assignment có sẵn, ưu tiên SERVICE khi có dịch vụ. Unknown giữ OTHER.
2. `ItineraryProgramService`: khóa Cruise theo quy ước mainflow → Itinerary → occurrence/stop; kiểm If-Match, trạng thái DRAFT/PLANNED, kiểm scope; gán additive trong một transaction. Không có uniqueness source vì cùng định nghĩa có thể lặp nhiều lần trong chuyến.
3. `ItineraryProgramPolicy`: kiểm tra Cruise, stop, occurrence, area, destination và supplier. Dùng chung tại submit/approve/publish itinerary và mainflow blockers/dossier. Gửi phân hệ độc lập giữ nguyên.
4. Khóa area khi gán; trigger serialize assignment dịch vụ/hoạt động và đổi phân loại. Cho phép chồng thời gian hoạt động tại cùng khu vực; giữ capacity và lifecycle khác.
5. Cung cấp API đọc lỗi theo occurrence, gán, bổ sung lịch, bỏ occurrence; API đọc/sửa usage với quyền tàu hiện có. Không thêm dependency.

## Validation

Hồi quy service hiện có; test quy tắc thời gian; PostgreSQL thật với toàn bộ Flyway, scope/revision/rollback/ràng buộc area và quyền HTTP. Không dùng H2 chứng minh SQL. Cập nhật [sequence flow](../../docs/sequence-diagrams/ITINERARY-PROGRAM-FLOWS.md), inventory và route diagrams; chạy checker và rà soát delta.
