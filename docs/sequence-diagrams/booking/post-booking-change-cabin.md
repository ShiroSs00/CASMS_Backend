# POST /booking/change-cabin

```mermaid
sequenceDiagram
    title POST /booking/change-cabin — BookingController.changeCabin
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BookingController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /booking/change-cabin + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke changeCabin(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: bookingService.changeCabin()

    Note over Controller,Service: Source branch: for BookingPassenger bp : bookingPassengers
    Note over Controller,Service: Source branch: for Installment inst : installments
    Note over Controller,Service: Source branch: if !isStaff && !booking.getBookedByUser().getUserId().equals(principal.getUserId())
    Note over Controller,Service: Source branch: if !newCabin.getShip().getShipId().equals(cruise.getShip().getShipId())
    Note over Controller,Service: Source branch: if auth != null && auth.getPrincipal() instanceof CasmsPrincipal principal
    Note over Controller,Service: Source branch: if booking.getBookingStatus() == BookingStatus.Cancelled
    Note over Controller,Service: Source branch: if bpp != null && bpp.getTourPackage() != null
    Note over Controller,Service: Source branch: if c.getDeck() != null
    Note over Controller,Service: Source branch: if currentOccupied >= newCabin.getCapacity()
    Note over Controller,Service: Source branch: if inst.getSequenceNo() == 1) iRes.setDescription("Đợt 1: Đặt cọc giữ chỗ 30% khi ký hợp đồng"
    Note over Controller,Service: Source branch: if inst.getSequenceNo() == 2) iRes.setDescription("Đợt 2: Thanh toán giữa kỳ 40%"
    Note over Controller,Service: Source branch: if installments.size() == 1
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.findById()
            Service->>Repository: BookingPassengerRepository.lockById()
            Service->>Repository: BookingPassengerRepository.save()
            Service->>Repository: BookingRepository.findByIdForUpdate()
            Service->>Repository: CabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise()
            Service->>Repository: CabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull()
            Service->>Repository: CabinAssignmentRepository.save()
            Service->>Repository: CabinRepository.findByIdForUpdate()
            Service->>Repository: CruiseRepository.findByIdForUpdate()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
