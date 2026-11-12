# CASMS Comprehensive Design & Domain Class Diagrams (UML Mermaid)

Thư mục này chứa toàn bộ danh mục **21 Sơ đồ lớp thiết kế chi tiết (Design Class Diagrams)** của hệ thống **CASMS (Cruise Activity and Ship Management System)**, bao phủ **100% tất cả các Class trong dự án**: Controllers, Services, Repositories, Entities, Security Filter Chain và Exception Architecture.

## Danh Sách 21 Sơ Đồ Lớp Toàn Diện

| STT | Tệp tin | Phân hệ nghiệp vụ / Kiến trúc | Mô tả chi tiết 4 tầng |
| :---: | :--- | :--- | :--- |
| **0** | [00-high-level-domain.md](00-high-level-domain.md) | **Tổng quan Kiến trúc Miền** | Xương sống kết nối User, Ship, Cruise, Booking, Folio, Activity. |
| **1** | [01-identity-iam.md](01-identity-iam.md) | **Identity & IAM** | `AuthController`, `IdentityService`, `UserRepository`, `User`, `Role`. |
| **2** | [02-ship-infrastructure.md](02-ship-infrastructure.md) | **Hạ tầng Tàu & Buồng phòng** | `ShipController`, `CabinService`, `CabinRepository`, `Ship`, `Deck`, `Cabin`. |
| **3** | [03-itinerary-port.md](03-itinerary-port.md) | **Hải trình & Cảng biển** | `CruiseController`, `ItineraryService`, `CruiseRepository`, `Cruise`, `Port`. |
| **4** | [04-booking-contract.md](04-booking-contract.md) | **Đặt chỗ & Hợp đồng** | `BookingController`, `BookingService`, `BookingRepository`, `Booking`, `BookingContract`. |
| **5** | [05-package-benefits.md](05-package-benefits.md) | **Gói dịch vụ & Quyền lợi** | `TourPackage`, `ServicePackage`, `BenefitSnapshot`, Hạn mức All-Inclusive. |
| **6** | [06-activity-excursions.md](06-activity-excursions.md) | **Giải trí & Tour bờ** | `OnboardOperationController`, `ActivityService`, `RegistrationRepository`, `OnboardActivity`. |
| **7** | [07-payment-installments.md](07-payment-installments.md) | **Thanh toán & Trả góp** | `PaymentController`, `SepayService`, `PaymentRepository`, `Payment`, `SepayTransaction`. |
| **8** | [08-cashless-folio-pos.md](08-cashless-folio-pos.md) | **Tài khoản phòng & POS** | `PosController`, `PosService`, `OnboardTransactionRepository`, `OnboardAccount`. |
| **9** | [09-checkin-gangway-safety.md](09-checkin-gangway-safety.md) | **Cầu tàu & An toàn SOLAS** | `CheckinController`, `GuidanceSessionService`, `CruiseCheckinRepository`, `GangwayEvent`. |
| **10** | [10-ship-3d-digital-twin.md](10-ship-3d-digital-twin.md) | **3D Digital Twin & CAD/BIM** | `ShipModelController`, `ShipModelAssetService`, `ShipModelRepository`, `ShipModel`. |
| **11** | [11-iot-ble-location.md](11-iot-ble-location.md) | **Định vị trong nhà IoT BLE** | `BleAnchorController`, `BleRuntimeService`, `LocationDeviceRepository`, `LocationRecord`. |
| **12** | [12-service-request-feedback.md](12-service-request-feedback.md) | **Phục vụ phòng & Feedback** | `ServicepackageController`, `ServiceRequestService`, `FeedbackRepository`, `ServiceRequest`. |
| **13** | [13-impact-outbox-audit.md](13-impact-outbox-audit.md) | **Outbox, Biến động & Audit** | `CruiseDelayController`, `NotificationService`, `ImpactNotificationOutboxRepository`, `AuditLog`. |
| **14** | [14-pricing-package-engine.md](14-pricing-package-engine.md) | **Động cơ định giá đa tầng** | `CruisePackageController`, `CruisePackageCabinMappingService`, `DeparturePackagePrice`. |
| **15** | [15-layered-architecture-design.md](15-layered-architecture-design.md) | **Kiến trúc phân tầng mẫu** | Controller ➔ Service ➔ Repository ➔ Entity trong Spring Boot. |
| **16** | [16-refund-dispute.md](16-refund-dispute.md) | **Hoàn tiền & Khiếu nại chi phí** | `RefundController`, `ChargeDisputeService`, `RefundRepository`, `ChargeDispute`, `Refund`. |
| **17** | [17-safety-preparation-regulations.md](17-safety-preparation-regulations.md) | **Nghiệm thu an toàn & Sự cố** | `CruisePreparationController`, `OperationalIssueService`, `PreparationSectionRepository`. |
| **18** | [18-port-window-destinations.md](18-port-window-destinations.md) | **Cửa sổ cập cảng & Điểm đến** | `PortWindowController`, `DestinationService`, `PortCallWindowRepository`, `Destination`. |
| **19** | [19-supplier-tour-groups.md](19-supplier-tour-groups.md) | **Đối tác cung ứng & Nhóm tour** | `SupplierController`, `TourGroupService`, `SupplierRepository`, `TourGroupPassenger`. |
| **20** | [20-security-jwt-exception-architecture.md](20-security-jwt-exception-architecture.md) | **Bảo mật JWT & Bắt ngoại lệ** | `SecurityConfig`, `JwtAuthenticationFilter`, `GlobalExceptionHandler`, `ApiResponse`. |
