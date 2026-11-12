# CASMS Domain & Architecture Class Diagrams (UML Mermaid)

Thư mục này chứa toàn bộ các sơ đồ lớp (Class Diagrams) của hệ thống **CASMS (Cruise Activity and Ship Management System)**, bao phủ đầy đủ **101 JPA Entities**, các Bounded Context nghiệp vụ và tầng thiết kế kiến trúc phần mềm Spring Boot.

## Danh Sách Sơ Đồ Lớp

| STT | Tệp tin | Phân hệ nghiệp vụ | Mô tả trọng tâm |
| :---: | :--- | :--- | :--- |
| **0** | [00-high-level-domain.md](00-high-level-domain.md) | **Tổng quan Hệ thống** | Kiến trúc miền tổng thể kết nối Tàu, Hải trình, Booking, Thanh toán, Folio, Giải trí. |
| **1** | [01-identity-iam.md](01-identity-iam.md) | **Identity & IAM** | Quản lý Người dùng (`User`), Khách (`Passenger`), Ma trận phân quyền RBAC (`Role`, `Permission`) và BLE IoT. |
| **2** | [02-ship-infrastructure.md](02-ship-infrastructure.md) | **Hạ tầng Tàu & Buồng phòng** | Thực thể vật lý Tàu (`Ship`), Boong (`Deck`), Cabin (`Cabin`), Khu vực (`Area`), Nghiệm thu an toàn. |
| **3** | [03-itinerary-port.md](03-itinerary-port.md) | **Hải trình & Cảng biển** | Cảng biển (`Port`), Điểm đến (`Destination`), Lộ trình (`Itinerary`), Chuyến đi (`Cruise`), Điểm dừng (`CruiseStop`). |
| **4** | [04-booking-contract.md](04-booking-contract.md) | **Đặt chỗ & Hợp đồng** | Đơn đặt chỗ (`Booking`), Thành viên (`BookingPassenger`), Hợp đồng vận chuyển (`BookingContract`), Gán phòng. |
| **5** | [05-package-benefits.md](05-package-benefits.md) | **Gói dịch vụ & Quyền lợi** | Gói tour (`TourPackage`), Gói tiện ích (`ServicePackage`), Quyền lợi đi kèm All-Inclusive. |
| **6** | [06-activity-excursions.md](06-activity-excursions.md) | **Giải trí & Tour bờ** | Hoạt động trên tàu (`OnboardActivity`), Đặt chỗ (`Registration`), Tham gia (`Participation`), Tour bờ (`ShoreExcursion`). |
| **7** | [07-payment-installments.md](07-payment-installments.md) | **Thanh toán & Trả góp** | Giao dịch (`Payment`), Cổng SePay/VietQR (`SepayTransaction`), Lộ trình chia đợt (`Installment`), Hóa đơn (`Invoice`). |
| **8** | [08-cashless-folio-pos.md](08-cashless-folio-pos.md) | **Tài khoản phòng & POS** | Ví chi tiêu buồng phòng (`OnboardAccount`), Giao dịch (`OnboardTransaction`), Hạn mức nợ (`SpendingLimit`), Đồng bộ ngoại tuyến. |
| **9** | [09-checkin-gangway-safety.md](09-checkin-gangway-safety.md) | **Cầu tàu & An toàn SOLAS** | Điểm danh lên xuống (`CruiseCheckin`, `GangwayEvent`), Trạm tập kết cứu sinh (`MusterStation`), Diễn tập an toàn. |
| **10** | [10-ship-3d-digital-twin.md](10-ship-3d-digital-twin.md) | **3D Digital Twin & CAD/BIM** | Mô hình 3D đa giác (`ShipModel`), Cây đối tượng không gian (`ShipModelObject`), File nhị phân (`AssetBlob`), Import Job. |
| **11** | [11-iot-ble-location.md](11-iot-ble-location.md) | **Định vị trong nhà IoT BLE** | Hạ tầng Beacon (`LocationDevice`), Tọa độ Realtime (`LocationRecord`), Định danh vòng tay (`PassengerBleIdentity`). |
| **12** | [12-service-request-feedback.md](12-service-request-feedback.md) | **Phục vụ phòng & Feedback** | Yêu cầu buồng phòng (`ServiceRequest`), Tiêu thụ quota (`ServiceUsage`), Phụ thu (`AdditionalCharge`), Khảo sát hài lòng (`Feedback`). |
| **13** | [13-impact-outbox-audit.md](13-impact-outbox-audit.md) | **Outbox, Biến động & Audit** | Sự cố trễ tàu (`CruiseImpactTask`), Giao vận bảo đảm (`ImpactNotificationOutbox`), Thông báo (`Notification`), Kiểm toán (`AuditLog`). |
| **14** | [14-pricing-package-engine.md](14-pricing-package-engine.md) | **Động cơ định giá đa tầng** | Mẫu gói tour (`CruisePackageTemplate`), Phiên bản (`TemplateVersion`), Biểu giá linh hoạt theo hạng phòng (`DepartureCruisePackagePrice`). |
| **15** | [15-layered-architecture-design.md](15-layered-architecture-design.md) | **Kiến trúc phần mềm phân tầng** | Luồng thực thi Controller Layer ➔ Service Layer ➔ Repository Layer ➔ Entity Layer trong Spring Boot. |
