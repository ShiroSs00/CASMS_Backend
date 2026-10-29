# POST /booking/{bookingId}/contract/accept

```mermaid
sequenceDiagram
    title POST /booking/{bookingId}/contract/accept — BookingController.acceptContract
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BookingController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /booking/{bookingId}/contract/accept + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke acceptContract(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: bookingService.acceptContract()

    Note over Controller,Service: Source branch: for BookingPassenger bp : bookingPassengers
    Note over Controller,Service: Source branch: for Installment inst : installments
    Note over Controller,Service: Source branch: if !belongs
    Note over Controller,Service: Source branch: if !passengers.isEmpty()
    Note over Controller,Service: Source branch: if auth != null && auth.getPrincipal() instanceof CasmsPrincipal principal
    Note over Controller,Service: Source branch: if booking.getBookingStatus() != BookingStatus.Draft
    Note over Controller,Service: Source branch: if booking.getBookingStatus() == BookingStatus.Cancelled
    Note over Controller,Service: Source branch: if bpp != null && bpp.getTourPackage() != null
    Note over Controller,Service: Source branch: if c.getDeck() != null
    Note over Controller,Service: Source branch: if contract == null || contract.getStatus() != BookingContractStatus.Accepted
    Note over Controller,Service: Source branch: if contract.getStatus() == BookingContractStatus.Accepted
    Note over Controller,Service: Source branch: if inst.getSequenceNo() == 1) iRes.setDescription("Đợt 1: Đặt cọc giữ chỗ 30% khi ký hợp đồng"
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingContractRepository.findByBookingBookingId()
            Service->>Repository: BookingContractRepository.save()
            Service->>Repository: BookingPassengerRepository.findByBookingBookingId()
            Service->>Repository: BookingRepository.findByIdForUpdate()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
