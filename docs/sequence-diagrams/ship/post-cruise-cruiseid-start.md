# POST /cruise/{cruiseId}/start

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/start — CruiseController.start
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/start + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke start(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.start()


        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```

Start locks the Cruise row before evaluating lifecycle and departure readiness so it serializes with delay confirmation. See [departure and delay flows](../CRUISE-DEPARTURE-DELAY.md).

Start also locks the assigned ship row before changing the cruise to IN_PROGRESS. It checks operational status and, when an expiry is recorded, checks it against the later of today's date and planned return date in Vietnam. Expiry before that date returns 422. The scheduled [classification expiry check](../SHIP-TYPE-FLOWS.md#automatic-maintenance-for-classification-expiry) takes the same ship lock before checking whether a voyage is IN_PROGRESS.

### Kiểm tra tàu và bố trí nhân sự

Khi nhánh xử lý gọi kiểm tra chuẩn bị vận hành, yêu cầu nhân sự mới phải được vận hành duyệt và vẫn đáp ứng điều kiện về tàu, POS, định mức dịch vụ, khu vực và hoạt động. Kết quả còn thiếu được phản ánh qua kiểm tra staffing của bước hiện tại. [Luồng kiểm tra và bàn giao](../SHIP-STAFFING-HANDOFF.md).
