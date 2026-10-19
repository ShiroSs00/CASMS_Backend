# TỔNG HỢP CÁC BIỂU ĐỒ TRÌNH TỰ NGHIỆP VỤ (SEQUENCE DIAGRAMS) - HỆ THỐNG CASMS

Tài liệu này mô hình hóa toàn diện các luồng nghiệp vụ cốt lõi trong hệ thống quản lý du thuyền **CASMS** (Cruise Activity Scheduling and Management System), từ khâu thiết lập đội tàu, khởi tạo chuyến đi, quy trình đặt tour tự chọn phòng, bảo mật hợp đồng, cho đến vận hành và xử lý sự cố.

---

## 1. Thiết lập Đội tàu: Phân rã theo CRUD (Fleet Management CRUD)

Khâu quản trị đội tàu được phân quyền cho `SHIP_MANAGER` (hoặc `ADMIN`), bao gồm 4 nhóm thao tác tác động lên chuỗi thực thể phân cấp: **Ship (Tàu) ➔ Deck (Tầng/Boong) ➔ Cabin (Phòng)**.

---

### 1.1. Fleet CREATE: Khởi tạo Tàu, Tầng và Buồng phòng mới

Quy trình tuần tự đăng ký một con tàu mới vào hệ thống, sau đó chia boong tầng và thiết lập từng buồng phòng theo chuẩn thiết kế.

```mermaid
sequenceDiagram
    autonumber
    actor SM as Ship Manager
    participant SC as ShipController
    participant SS as ShipService
    participant DC as DeckController
    participant DS as DeckService
    participant CC as CabinController
    participant CS as CabinService
    participant DB as PostgreSQL Database

    Note over SM,DB: 1. KHỞI TẠO TÀU (SHIP)
    SM->>SC: POST /ships (shipCode, shipName, totalDecks, totalCapacity, commissionDate)
    SC->>SS: create(ShipCreateRequest)
    SS->>DB: shipRepository.existsByShipCode(shipCode)
    alt Mã tàu đã tồn tại
        SS-->>SC: Throw BusinessException("DUPLICATE_SHIP_CODE")
        SC-->>SM: 400 Bad Request
    else Mã tàu hợp lệ
        SS->>DB: shipRepository.save(ship) (Status: ACTIVE)
        SS-->>SC: ShipResponse
        SC-->>SM: 201 Created (Tạo tàu thành công)
    end

    Note over SM,DB: 2. CẤU HÌNH TẦNG / BOONG (DECK)
    SM->>DC: POST /decks (shipId, deckNumber, deckName)
    DC->>DS: create(DeckCreateRequest)
    DS->>DB: shipRepository.findById(shipId)
    DS->>DS: Kiểm tra deckNumber <= ship.totalDecks
    DS->>DB: deckRepository.existsByShipShipIdAndDeckNumber(shipId, deckNumber)
    alt Số tầng vượt giới hạn hoặc bị trùng lặp
        DS-->>DC: Throw BusinessException("INVALID_DECK_NUMBER" / "DUPLICATE_DECK")
        DC-->>SM: 400 Bad Request
    else Tầng hợp lệ
        DS->>DB: deckRepository.save(deck)
        DS-->>DC: DeckResponse
        DC-->>SM: 201 Created (Tạo tầng thành công)
    end

    Note over SM,DB: 3. CẤU HÌNH BUỒNG PHÒNG (CABIN)
    SM->>CC: POST /cabins (shipId, deckId, cabinNumber, cabinType, capacity, isAccessible)
    CC->>CS: create(CabinCreateRequest)
    CS->>DB: Lấy Ship & Deck
    CS->>CS: Kiểm tra deck.getShip() == ship (DECK_SHIP_MISMATCH)
    CS->>DB: cabinRepository.existsByShipShipIdAndCabinNumber(shipId, cabinNumber)
    alt Trùng số phòng hoặc tầng không thuộc tàu
        CS-->>CC: Throw BusinessException
        CC-->>SM: 400 Bad Request
    else Phòng hợp lệ
        CS->>DB: cabinRepository.save(cabin) (Status: AVAILABLE)
        CS-->>CC: CabinSummaryResponse
        CC-->>SM: 201 Created (Tạo phòng thành công)
    end
```

---

### 1.2. Fleet READ: Truy vấn Danh sách, Chi tiết & Tìm kiếm Phòng trống

Quy trình tra cứu thông tin đội tàu, cấu trúc các tầng và tìm kiếm buồng phòng phục vụ vận hành và đặt vé.

```mermaid
sequenceDiagram
    autonumber
    actor User as Ship Manager / Khách hàng
    participant SC as ShipController
    participant SS as ShipService
    participant DC as DeckController
    participant DS as DeckService
    participant CC as CabinController
    participant CS as CabinService
    participant DB as PostgreSQL Database

    Note over User,DB: 1. TRA CỨU THÔNG TIN TÀU
    User->>SC: GET /ships?page=0&size=10
    SC->>SS: list(Pageable)
    SS->>DB: shipRepository.findAll(Pageable)
    SS-->>SC: Page<ShipResponse>
    SC-->>User: 200 OK (Danh sách tàu)

    User->>SC: GET /ships/{shipId}/capacity-summary
    SC->>SS: getCapacitySummary(shipId)
    SS->>DB: Đếm tổng tầng thực tế & tổng cabin thực tế
    SS-->>SC: ShipCapacitySummaryResponse (totalCapacity, actualDecks, actualCabins, availableCabins)
    SC-->>User: 200 OK (Báo cáo tổng hợp sức chứa tàu)

    User->>SC: GET /ships/{shipId}/availability?start=...&end=...
    SC->>SS: checkScheduleAvailability(shipId, start, end)
    SS->>DB: cruiseRepository.existsOverlappingCruises(...)
    SS-->>SC: Boolean (true = Tàu rảnh, false = Đang có chuyến)
    SC-->>User: 200 OK

    Note over User,DB: 2. TRA CỨU TẦNG VÀ BUỒNG PHÒNG THEO TẦNG
    User->>DC: GET /decks/{deckId}/cabins
    DC->>DS: listCabinsByDeck(deckId)
    DS->>DB: cabinRepository.findByDeckDeckId(deckId)
    DS-->>DC: List<CabinSummaryResponse>
    DC-->>User: 200 OK (Danh sách phòng trên tầng)

    User->>DC: GET /decks/{deckId}/capacity
    DC->>DS: getDeckCapacity(deckId)
    DS->>DB: Đếm tổng cabin và tổng sức chứa khách trên tầng
    DS-->>DC: DeckCapacityResponse
    DC-->>User: 200 OK (Sức chứa của tầng)

    Note over User,DB: 3. TÌM KIẾM BUỒNG PHÒNG KHẢ DỤNG THEO BỘ LỌC
    User->>CC: POST /cabins/search (shipId, deckId, cabinType: BALCONY, minCapacity: 2, isAccessible: true)
    CC->>CS: searchAvailable(CabinSearchRequest)
    CS->>DB: cabinRepository.searchAvailable(...) (Status: AVAILABLE)
    CS-->>CC: List<CabinSummaryResponse>
    CC-->>User: 200 OK (Danh sách phòng trống thỏa mãn bộ lọc)
```

---

### 1.3. Fleet UPDATE: Cập nhật Thông số & Chuyển đổi Trạng thái Vận hành

Quy trình điều chỉnh thông số kỹ thuật của Tàu, Tầng, Cabin và cơ chế kiểm tra an toàn khi đưa Tàu/Phòng vào trạng thái bảo trì (`MAINTENANCE`).

```mermaid
sequenceDiagram
    autonumber
    actor SM as Ship Manager
    participant SC as ShipController
    participant SS as ShipService
    participant DC as DeckController
    participant DS as DeckService
    participant CC as CabinController
    participant CS as CabinService
    participant DB as PostgreSQL Database

    Note over SM,DB: 1. CẬP NHẬT THÔNG TIN TÀU & GIỚI HẠN BOONG
    SM->>SC: PATCH /ships/{shipId} (shipName, totalCapacity, totalDecks)
    SC->>SS: update(shipId, ShipUpdateRequest)
    SS->>DB: deckRepository.countByShipShipId(shipId) (Lấy số tầng hiện có)
    alt newTotalDecks < số tầng thực tế đã tạo
        SS-->>SC: Throw BusinessException("INVALID_TOTAL_DECKS")
        SC-->>SM: 400 Bad Request
    else Hợp lệ
        SS->>DB: Cập nhật thông số tàu và lưu DB
        SS-->>SC: ShipResponse
        SC-->>SM: 200 OK
    end

    Note over SM,DB: 2. CHUYỂN TRẠNG THÁI TÀU (ACTIVE / MAINTENANCE / INACTIVE)
    SM->>SC: POST /ships/{shipId}/status?status=MAINTENANCE
    SC->>SS: changeStatus(shipId, MAINTENANCE)
    SS->>DB: cruiseRepository.findCurrentActiveCruise(shipId, now())
    alt Tàu đang trong một chuyến đi đang diễn ra
        SS-->>SC: Throw BusinessException("SHIP_ACTIVE_CRUISE", "Không thể đưa tàu vào bảo trì khi đang trên biển")
        SC-->>SM: 400 Bad Request
    else Tàu đang ở cảng
        SS->>DB: ship.setStatus(MAINTENANCE)
        SS-->>SC: ShipResponse
        SC-->>SM: 200 OK (Tàu đã chuyển sang MAINTENANCE)
    end

    Note over SM,DB: 3. CẬP NHẬT TẦNG VÀ PHÒNG
    SM->>DC: PATCH /decks/{deckId} (deckNumber, deckName)
    DC->>DS: update(deckId, DeckUpdateRequest)
    DS->>DS: Kiểm tra deckNumber <= ship.totalDecks & không trùng
    DS->>DB: deckRepository.save(deck)
    DS-->>DC: DeckResponse
    DC-->>SM: 200 OK

    SM->>CC: PATCH /cabins/{cabinId} (cabinType, capacity, isAccessible)
    CC->>CS: update(cabinId, CabinUpdateRequest)
    CS->>DB: cabinRepository.save(cabin)
    CS-->>CC: CabinSummaryResponse
    CC-->>SM: 200 OK

    Note over SM,DB: 4. CHUYỂN TRẠNG THÁI PHÒNG (BẢO TRÌ / SẴN SÀNG)
    SM->>CC: POST /cabins/{cabinId}/status?status=MAINTENANCE
    CC->>CS: changeOperationalStatus(cabinId, MAINTENANCE)
    alt Cabin đang có khách ở (status == OCCUPIED)
        CS-->>CC: Throw BusinessException("CABIN_OCCUPIED", "Phòng đang có khách ở, không thể bảo trì")
        CC-->>SM: 400 Bad Request
    else Phòng trống
        CS->>DB: cabin.setStatus(MAINTENANCE)
        CS-->>CC: CabinSummaryResponse
        CC-->>SM: 200 OK (Phòng tạm ngưng phục vụ để sửa chữa)
    end
```

---

### 1.4. Fleet DELETE: Xóa An toàn Tàu, Tầng, Cabin Chưa Sử dụng

Quy trình bảo toàn toàn vẹn dữ liệu: Hệ thống nghiêm cấm xóa các thực thể đã phát sinh dữ liệu liên quan (chuyến đi hoặc booking của khách). Việc xóa chỉ cho phép theo thứ tự ngược: **Cabin ➔ Deck ➔ Ship**.

```mermaid
sequenceDiagram
    autonumber
    actor SM as Ship Manager
    participant CC as CabinController
    participant CS as CabinService
    participant DC as DeckController
    participant DS as DeckService
    participant SC as ShipController
    participant SS as ShipService
    participant DB as PostgreSQL Database

    Note over SM,DB: 1. XÓA CABIN CHƯA SỬ DỤNG
    SM->>CC: DELETE /cabins/{cabinId}/unused
    CC->>CS: deleteUnused(cabinId)
    CS->>DB: cabinAssignmentRepository.existsByCabinCabinId(cabinId)
    alt Cabin đã từng được gán cho khách trong bất kỳ booking nào
        CS-->>CC: Throw BusinessException("CABIN_IN_USE", "Phòng đã có lịch sử booking, không thể xóa")
        CC-->>SM: 400 Bad Request
    else Cabin chưa từng có khách đặt
        CS->>DB: cabinRepository.delete(cabin)
        CS-->>CC: 204 No Content
        CC-->>SM: 204 No Content (Xóa cabin thành công)
    end

    Note over SM,DB: 2. XÓA TẦNG / BOONG CHƯA SỬ DỤNG
    SM->>DC: DELETE /decks/{deckId}/unused
    DC->>DS: deleteUnused(deckId)
    DS->>DB: cabinRepository.existsByDeckDeckId(deckId)
    alt Tầng còn chứa buồng phòng
        DS-->>DC: Throw BusinessException("DECK_IN_USE", "Không thể xóa tầng vì vẫn còn phòng bên trong")
        DC-->>SM: 400 Bad Request
    else Tầng rỗng (không còn phòng nào)
        DS->>DB: deckRepository.delete(deck)
        DS-->>DC: 204 No Content
        DC-->>SM: 204 No Content (Xóa tầng thành công)
    end

    Note over SM,DB: 3. XÓA TÀU CHƯA SỬ DỤNG
    SM->>SC: DELETE /ships/{shipId}/unused
    SC->>SS: deleteUnused(shipId)
    SS->>DB: cruiseRepository.existsByShip(ship)
    alt Tàu đã được gán cho một hoặc nhiều chuyến đi (Cruise)
        SS-->>SC: Throw BusinessException("SHIP_IN_USE", "Không thể xóa tàu vì đã được gán cho chuyến đi")
        SC-->>SM: 400 Bad Request
    else Tàu chưa từng được gán cho Cruise nào
        SS->>DB: shipRepository.delete(ship)
        SS-->>SC: 204 No Content
        SC-->>SM: 204 No Content (Xóa tàu thành công)
    end
```

---

## 2. Biểu đồ: Lập Kế hoạch Chuyến đi, Thẩm định & Phát hành (Cruise Planning & Publishing)

Quy trình người quản lý chuyến đi (`CRUISE_MANAGER`) khởi tạo chuyến đi dạng nháp (`DRAFT`), gán tàu rảnh lịch, thiết lập hải trình (`Itinerary`), kiểm tra 6 tiêu chuẩn sẵn sàng vận hành (`Operational Readiness`) và phát hành (`PUBLISHED`) để mở bán vé.

```mermaid
sequenceDiagram
    autonumber
    actor CM as Cruise Manager
    participant CC as CruiseController
    participant CS as CruiseService
    participant IC as ItineraryController
    participant IS as ItineraryService
    participant DB as PostgreSQL Database

    Note over CM,DB: GIAI ĐOẠN 1: TẠO BẢN NHÁP CHUYẾN ĐI (DRAFT)
    CM->>CC: POST /cruise (cruiseCode, cruiseName, departureDatetime, returnDatetime, ports)
    CC->>CS: create(CruiseCreateRequest)
    CS->>CS: Validate returnDatetime > departureDatetime
    CS->>DB: cruiseRepository.existsByCruiseCode(cruiseCode)
    CS->>DB: cruiseRepository.save(cruise) (Status: DRAFT)
    CS-->>CC: CruiseResponse
    CC-->>CM: 201 Created (Cruise ở trạng thái DRAFT)

    Note over CM,DB: GIAI ĐOẠN 2: GÁN TÀU CHO CHUYẾN ĐI
    CM->>CC: POST /cruise/{id}/assign-ship?shipId=...
    CC->>CS: assignShip(cruiseId, shipId)
    CS->>DB: Lấy Ship & Cruise
    CS->>CS: Kiểm tra ship.status == ACTIVE
    CS->>DB: cruiseRepository.existsOverlappingCruises(shipId, departure, return)
    alt Tàu không ACTIVE hoặc bị trùng lịch với chuyến khác
        CS-->>CC: Throw BusinessException("SHIP_NOT_ACTIVE" / "SHIP_SCHEDULE_CONFLICT")
        CC-->>CM: 400 Bad Request
    else Tàu sẵn sàng
        CS->>DB: Gán tàu vào cruise & lưu DB
        CS-->>CC: CruiseResponse
        CC-->>CM: 200 OK
    end

    Note over CM,DB: GIAI ĐOẠN 3: CẤU HÌNH & PHÊ DUYỆT HẢI TRÌNH (ITINERARY)
    CM->>IC: POST /itinerary (cruiseId, startDate, endDate, itineraryType)
    IC->>IS: create(ItineraryCreateRequest)
    IS->>DB: Lưu Itinerary (DRAFT)
    CM->>IC: POST /itinerary/{id}/submit
    IC->>IS: submit(itineraryId) ➔ Kiểm tra ngày hợp lệ ➔ Chuyển PENDING_APPROVAL
    CM->>IC: POST /itinerary/{id}/approve
    IC->>IS: approve(itineraryId) ➔ Lưu trạng thái APPROVED
    IC-->>CM: 200 OK (Hải trình đã được phê duyệt)

    Note over CM,DB: GIAI ĐOẠN 4: KIỂM DUYỆT SẴN SÀNG & PHÁT HÀNH (PUBLISH)
    CM->>CC: GET /cruise/{id}/readiness
    CC->>CS: validateOperationalReadiness(cruiseId)
    CS->>CS: Quét 6 tiêu chuẩn:
    CS->>CS: 1. Tàu đã gán & ACTIVE
    CS->>CS: 2. Tàu có Decks > 0 & Cabins > 0
    CS->>CS: 3. Lịch trình return > departure & ở tương lai
    CS->>CS: 4. Cảng đi và cảng đến ACTIVE
    CS->>CS: 5. Hải trình đã liên kết & APPROVED
    CS->>CS: 6. Không có xung đột quy định an toàn
    CS-->>CC: CruiseReadinessResponse (isReady: true/false, issues: [...])
    CC-->>CM: 200 OK (Kết quả kiểm duyệt)

    CM->>CC: POST /cruise/{id}/publish
    CC->>CS: publish(cruiseId)
    alt isReady == false
        CS-->>CC: Throw BusinessException("CRUISE_NOT_READY", issues)
        CC-->>CM: 422 Unprocessable Entity
    else isReady == true
        CS->>DB: cruise.setStatus(PUBLISHED)
        CS-->>CC: CruiseResponse
        CC-->>CM: 200 OK (Chuyến đi chính thức mở bán vé!)
    end
```

---

## 3. Biểu đồ: Đặt Tour & Khách Tự Chọn Phòng (Booking & Self-Cabin Selection)

Quy trình hành khách (kể cả khách vãng lai chưa đăng nhập) xem sơ đồ buồng phòng trống, tự chọn cabin cụ thể, chọn gói dịch vụ (Basic/Plus/Premium), lựa chọn phương thức thanh toán (Full/Cọc 30%/3 giai đoạn) và nhận mã đối chiếu ký hợp đồng bản cứng bảo mật.

```mermaid
sequenceDiagram
    autonumber
    actor Cust as Khách Hàng (Vãng lai / Đã Login)
    participant BC as BookingController
    participant BS as BookingService
    participant CabinRepo as CabinRepository
    participant AssignRepo as CabinAssignmentRepository
    participant PackRepo as TourPackageRepository
    participant UserRepo as UserRepository
    participant DB as PostgreSQL Database
    participant NotifRepo as NotificationRepository

    Note over Cust,NotifRepo: BƯỚC 1: TRA CỨU BUỒNG PHÒNG CÒN TRỐNG ĐỂ TỰ CHỌN
    Cust->>BC: GET /booking/available-cabins?cruiseId=...&deckId=...&cabinType=BALCONY
    BC->>BS: getAvailableCabins(cruiseId, deckId, cabinType, minCapacity)
    BS->>CabinRepo: searchAvailable(...)
    BS->>AssignRepo: countActiveAssignmentsByCabinAndCruise(...)
    BS-->>BC: Danh sách phòng còn trống (cabinId, cabinNumber, deckName, remainingSlots)
    BC-->>Cust: 200 OK (Hiển thị sơ đồ phòng cho khách chọn)

    Note over Cust,NotifRepo: BƯỚC 2: GỬI YÊU CẦU ĐẶT TOUR
    Cust->>BC: POST /booking (cruiseId, paymentPlan, passengers: [tên, DOB, email, SĐT, cabinId, tourPackageId])
    BC->>BC: Kiểm tra Bean Validation (@NotBlank, @Email, @Past, @Pattern SĐT)
    BC->>BS: createBooking(BookingCreateRequest, principal)

    Note over BS,DB: BƯỚC 3: THẨM ĐỊNH NGHIỆP VỤ & SỨC CHỨA
    BS->>DB: getCruise(cruiseId) ➔ Kiểm tra Cruise == PUBLISHED & chưa khởi hành
    alt Khách vãng lai (principal == null)
        BS->>BS: Kiểm tra email người đại diện (PRIMARY_EMAIL_REQUIRED)
        BS->>UserRepo: findByEmail ➔ Nếu chưa có thì tự động tạo User (Role: PASSENGER)
    end
    BS->>BS: Kiểm tra trùng lặp giấy tờ tùy thân (DUPLICATE_PASSENGER_DOCUMENT)
    BS->>DB: Kiểm tra tổng tải trọng tàu (EXCEEDS_SHIP_CAPACITY)

    loop Với từng Cabin khách đã tự chọn
        BS->>CabinRepo: findById(cabinId)
        BS->>BS: Kiểm tra phòng thuộc đúng tàu (CABIN_SHIP_MISMATCH)
        BS->>BS: Kiểm tra trạng thái phòng == AVAILABLE
        BS->>AssignRepo: Lấy số chỗ đã gán trên chuyến này
        BS->>BS: Kiểm tra (khách gán vào phòng <= remainingSlots) (CABIN_OVERCAPACITY)
    end

    Note over BS,DB: BƯỚC 4: TÍNH TIỀN, LẬP HỢP ĐỒNG & LỊCH THANH TOÁN
    BS->>BS: Tính tổng tiền = Tổng giá phòng theo hạng + Tổng giá gói TourPackage
    BS->>BS: Tính cọc: FULL (100%) hoặc DEPOSIT/STAGES (30% cọc)
    BS->>DB: bookingRepository.save(booking) (Reference: BK-YYYYMMDD-XXXX, Status: Draft)

    loop Với từng hành khách
        BS->>DB: passengerRepository.save(passenger) (Trim & chuẩn hóa dữ liệu)
        BS->>DB: bookingPassengerRepository.save(bookingPassenger)
        BS->>DB: cabinAssignmentRepository.save(cabinAssignment) (Gán phòng tự chọn, validTo: null)
        opt Có chọn TourPackage
            BS->>DB: bookingPassengerPackageRepository.save(bpp)
        end
    end

    Note over BS,NotifRepo: BƯỚC 5: TẠO HỢP ĐỒNG BẢO MẬT & THƯ MỜI KÝ BẢN CỨNG
    BS->>DB: bookingContractRepository.save(contract) (Lưu CTR-BK-..., DocumentRef - KHÔNG LƯU FILE THÔ)
    BS->>DB: installmentRepository.saveAll(Lập 1, 2 hoặc 3 đợt thanh toán theo kế hoạch)
    BS->>NotifRepo: Lưu Notification (CONTRACT_SIGNING_INVITATION: Mời mang CCCD/Hộ chiếu đến VP ký bản cứng)
    BS-->>BC: BookingDetailResponse
    BC-->>Cust: 201 Created (Chi tiết đơn, lộ trình thanh toán & hướng dẫn ký hợp đồng)
```

---

## 4. Biểu đồ: Tra Cứu Đơn Đặt Chỗ Cho Khách Vãng Lai (Guest Booking Lookup)

Quy trình khách hàng chưa đăng nhập tra cứu lại thông tin đơn đặt chỗ, lộ trình thanh toán và mã hợp đồng đối chiếu.

```mermaid
sequenceDiagram
    autonumber
    actor Guest as Khách Hàng Vãng Lai
    participant BC as BookingController
    participant BS as BookingService
    participant BR as BookingRepository
    participant BPR as BookingPassengerRepository

    Guest->>BC: GET /booking/lookup?reference=BK-20261019-XXXX&email=guest@example.com
    BC->>BS: lookupBooking(reference, email, phoneNumber)
    BS->>BR: findByBookingReference(reference)
    alt Không tìm thấy mã đơn
        BR-->>BS: Optional.empty()
        BS-->>BC: Throw ResourceNotFoundException
        BC-->>Guest: 404 Not Found
    else Tìm thấy đơn
        BR-->>BS: Booking Entity
        BS->>BS: Đối chiếu email với User đặt hoặc email hành khách
        alt Email / SĐT không khớp
            BS-->>BC: Throw BusinessException("LOOKUP_MISMATCH", 403 Forbidden)
            BC-->>Guest: 403 Forbidden (Không có quyền tra cứu đơn này)
        else Xác thực trùng khớp
            BS->>BPR: Lấy danh sách hành khách, phòng đã chọn, gói dịch vụ
            BS->>BS: Lấy lịch thanh toán & hướng dẫn ký hợp đồng bản cứng
            BS-->>BC: BookingDetailResponse
            BC-->>Guest: 200 OK (Hiển thị chi tiết đặt chỗ và mã hợp đồng đối chiếu)
        end
    end
```

---

## 5. Biểu đồ: Ký Hợp Đồng Trực Tiếp & Xác Nhận Đơn (In-person Signing & Confirmation)

Quy trình giải quyết bảo mật thông tin định danh: khách hàng mang giấy tờ tùy thân gốc tới văn phòng CASMS để đối chiếu, ký hợp đồng bản cứng và thanh toán.

```mermaid
sequenceDiagram
    autonumber
    actor Cust as Khách Hàng
    actor Staff as Nhân Viên Giao Dịch CASMS
    participant BC as BookingController
    participant BS as BookingService
    participant DB as PostgreSQL Database

    Cust->>Staff: Đến văn phòng CASMS, xuất trình CCCD/Hộ chiếu gốc + Mã đơn (BK-...) / Mã HĐ (CTR-...)
    Staff->>BC: GET /booking/{bookingId} (hoặc tra cứu theo reference)
    BC->>BS: detail(bookingId, staffPrincipal)
    BS->>DB: Lấy thông tin Booking & BookingContract
    BS-->>Staff: Hiển thị thông tin đối chiếu hành khách & giá trị cọc

    Staff->>Staff: 1. Đối chiếu tính hợp lệ của CCCD/Hộ chiếu gốc với thông tin đã đăng ký
    Staff->>Staff: 2. In văn bản hợp đồng pháp lý từ hệ thống nội bộ bảo mật
    Cust->>Cust: Đọc điều khoản và ký hợp đồng bản cứng tại quầy
    Staff->>Staff: Đóng dấu văn bản, lưu trữ bản cứng an toàn vào kho hồ sơ bảo mật

    Cust->>Staff: Thanh toán tiền cọc (Đợt 1) hoặc thanh toán toàn bộ (Tiền mặt/Quẹt thẻ/Chuyển khoản)
    Staff->>BC: POST /booking/{bookingId}/confirm
    BC->>BS: confirm(bookingId, staffPrincipal)
    BS->>DB: Cập nhật booking.setStatus(Confirmed)
    BS->>DB: Cập nhật installment đợt 1 ➔ Status: Paid
    BS->>DB: Cập nhật contract ➔ Status: Active
    BS-->>BC: BookingDetailResponse (Status: Confirmed)
    BC-->>Staff: 200 OK
    Staff-->>Cust: Bàn giao phiếu thu, biên nhận cọc và 01 bản hợp đồng gốc có dấu mộc
```

---

## 6. Biểu đồ: Đổi Phòng & Hủy Đặt Chỗ (Cabin Change & Booking Cancellation)

Quy trình xử lý biến động buồng phòng: chuyển đổi phòng cho khách hoặc hủy tour để giải phóng chỗ trống cho hành khách khác.

```mermaid
sequenceDiagram
    autonumber
    actor User as Hành Khách / Nhân Viên
    participant BC as BookingController
    participant BS as BookingService
    participant AssignRepo as CabinAssignmentRepository
    participant DB as PostgreSQL Database

    alt TRƯỜNG HỢP A: ĐỔI PHÒNG (CHANGE CABIN)
        User->>BC: POST /booking/change-cabin (bookingPassengerId, newCabinId, reason)
        BC->>BS: changeCabin(CabinChangeRequest, principal)
        BS->>DB: Lấy BookingPassenger & Cabin mới
        BS->>BS: Kiểm tra Cabin mới thuộc cùng tàu & Status == AVAILABLE
        BS->>AssignRepo: countActiveAssignmentsByCabinAndCruise(newCabinId)
        alt Phòng mới đã kín chỗ
            BS-->>BC: Throw BusinessException("CABIN_FULL")
            BC-->>User: 400 Bad Request
        else Phòng mới còn chỗ
            BS->>AssignRepo: Đóng hiệu lực gán phòng cũ (validTo = now(), changeReason)
            BS->>AssignRepo: Tạo bản ghi gán phòng mới (validFrom = now(), validTo = null)
            BS->>DB: Cập nhật bp.setRequestedCabinCategory(newCabin.type)
            BS-->>BC: BookingDetailResponse
            BC-->>User: 200 OK (Đổi phòng thành công)
        end

    else TRƯỜNG HỢP B: HỦY ĐẶT CHỖ (CANCEL BOOKING)
        User->>BC: POST /booking/{bookingId}/cancel (reason)
        BC->>BS: cancel(bookingId, BookingCancelRequest, principal)
        BS->>DB: booking.setStatus(Cancelled) & lưu cancellationReason
        loop Với tất cả hành khách trong đơn
            BS->>DB: bp.setParticipationStatus(Cancelled)
            BS->>AssignRepo: Đóng phân phòng hiện hành (validTo = now(), giải phóng slot)
        end
        BS->>DB: Hủy hợp đồng liên quan (contract.setStatus(Cancelled))
        BS-->>BC: BookingDetailResponse (Status: Cancelled)
        BC-->>User: 200 OK (Đã hủy đặt chỗ, buồng phòng tự động được hoàn về kho trống)
    end
```

---

## 7. Biểu đồ: Quản Lý Sự Cố Vận Hành & An Toàn Tàu (Operational Issue & Maintenance)

Quy trình tiếp nhận báo cáo kỹ thuật, cách ly tàu hư hỏng khỏi lịch trình xuất bến và hoàn tất bảo trì kỹ thuật an toàn.

```mermaid
sequenceDiagram
    autonumber
    actor Crew as Thủy Thủ Đoàn / Kỹ Sư
    actor SM as Ship Manager
    participant OIC as OperationalIssueController
    participant OIS as OperationalIssueService
    participant SC as ShipController
    participant SS as ShipService
    participant DB as PostgreSQL Database

    Crew->>OIC: POST /operational-issues (shipId, issueType: SAFETY/TECHNICAL, severity: CRITICAL, description)
    OIC->>OIS: report(OperationalIssueRequest)
    OIS->>DB: operationalIssueRepository.save(issue) (Status: OPEN)
    OIS-->>OIC: OperationalIssueResponse
    OIC-->>Crew: 201 Created (Sự cố đã được ghi nhận vào nhật ký kỹ thuật)

    alt Sự cố mức độ CRITICAL hoặc HIGH ảnh hưởng an toàn
        SM->>SC: POST /ships/{shipId}/status?status=MAINTENANCE
        SC->>SS: changeStatus(shipId, MAINTENANCE)
        SS->>DB: cruiseRepository.findCurrentActiveCruise(...) ➔ Kiểm tra tàu không ở giữa biển
        SS->>DB: ship.setStatus(MAINTENANCE)
        SS-->>SC: ShipResponse (Tàu đã bị cách ly bảo trì, hệ thống khóa không cho gán Cruise mới)
        SC-->>SM: 200 OK
    end

    Note over SM,DB: TIẾN HÀNH BẢO DƯỠNG & SỬA CHỮA
    SM->>OIC: POST /operational-issues/{id}/assign (assigneeId: Kỹ sư trưởng)
    OIC->>OIS: assignIssue ➔ Status: IN_PROGRESS
    Crew->>OIC: POST /operational-issues/{id}/resolve (resolutionNotes)
    OIC->>OIS: resolveIssue ➔ Ghi nhận resolvedBy, resolvedAt ➔ Status: RESOLVED
    SM->>OIC: POST /operational-issues/{id}/close
    OIC->>OIS: closeIssue ➔ Status: CLOSED

    Note over SM,DB: KIỂM ĐỊNH KỸ THUẬT LẠI TRƯỚC KHI TÁI KHAI THÁC
    SM->>SC: GET /ships/{shipId}/readiness
    SC->>SS: validateOperationalReadiness(shipId)
    SM->>SC: POST /ships/{shipId}/status?status=ACTIVE
    SC->>SS: changeStatus(shipId, ACTIVE)
    SC-->>SM: 200 OK (Tàu đạt chuẩn an toàn, quay trở lại trạng thái khai thác)
```
