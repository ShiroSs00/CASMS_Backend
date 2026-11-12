# Sơ Đồ Lớp Thiết Kế Chi Tiết: Phân Hệ Tài Khoản Phòng & POS (Cashless Folio & POS Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho tài khoản tiêu dùng không tiền mặt trên tàu, thiết bị máy POS quầy bar/nhà hàng, định mức chi tiêu trẻ em và cơ chế đồng bộ ngoại tuyến (Offline Edge Sync).

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class PosController {
        -PosService posService
        +login(credentials) ApiResponse~PosSession~
        +sell(saleRequest) ApiResponse~PosSale~
        +statement(accountId) ApiResponse~PosStatement~
        +allowance(bookingId) ApiResponse
        +logout(sessionId) void
    }

    class OnboardOperationController {
        -OnboardOperationService operationService
        +syncOfflineTransactions(offlineBatch) ApiResponse
        +registerActivity(passengerId, request) ApiResponse
    }

    %% 2. Service Layer
    class PosService {
        -OnboardAccountRepository accountRepo
        -OnboardTransactionRepository transactionRepo
        -PosTerminalRepository terminalRepo
        +processCharge(terminalId, memberId, amount, itemDesc) OnboardTransaction
        +verifySpendingLimit(memberId, amount) boolean
        +closeAccount(accountId) FinalSettlement
    }

    class OnboardOperationService {
        +syncOfflineTransactions(offlineList) List~OnboardTransaction~
        +registerActivity(passengerId, activityId, notes) Registration
    }

    class OnboardOperationServiceImpl {
        -OnboardTransactionRepository transactionRepo
        -OnboardActivityRepository activityRepo
        +syncOfflineTransactions(offlineList) List~OnboardTransaction~
        +registerActivity(passengerId, activityId, notes) Registration
    }

    %% 3. Repository Layer
    class OnboardAccountRepository {
        +findById(id) Optional~OnboardAccount~
        +findByBookingId(bookingId) Optional~OnboardAccount~
        +save(account) OnboardAccount
    }

    class OnboardTransactionRepository {
        +findBySyncStatus(status) List~OnboardTransaction~
        +findByOnboardAccountId(accountId) List~OnboardTransaction~
        +saveAll(transactions) List~OnboardTransaction~
        +save(transaction) OnboardTransaction
    }

    class SpendingLimitRepository {
        +findByAccountMemberId(memberId) List~SpendingLimit~
        +save(limit) SpendingLimit
    }

    class PosTerminalRepository {
        +findByTerminalCode(code) Optional~PosTerminal~
        +save(terminal) PosTerminal
    }

    %% 4. Entity Layer
    class OnboardAccount {
        +UUID onboardAccountId
        +BigDecimal creditLimit
        +OnboardAccountStatus status
        +Instant activatedAt
        +Instant closedAt
    }

    class OnboardAccountMember {
        +UUID accountMemberId
        +MemberRole memberRole
        +ChargePermission chargePermission
        +AccountMemberStatus status
    }

    class OnboardTransaction {
        +UUID transactionId
        +BigDecimal amount
        +String currencyCode
        +TransactionType transactionType
        +TransactionSyncStatus syncStatus
        +Instant transactionTime
        +String description
    }

    class SpendingLimit {
        +UUID limitId
        +BigDecimal limitAmount
        +String currencyCode
        +LimitStatus status
    }

    class PosTerminal {
        +UUID terminalId
        +String terminalCode
        +String terminalName
        +Boolean enabled
    }

    %% Inter-Layer and Domain Relationships
    PosController --> PosService : delegatesTo
    OnboardOperationController --> OnboardOperationService : delegatesTo
    OnboardOperationService <|.. OnboardOperationServiceImpl : implements

    PosService --> OnboardAccountRepository : accesses
    PosService --> OnboardTransactionRepository : accesses
    PosService --> PosTerminalRepository : accesses
    OnboardOperationServiceImpl --> OnboardTransactionRepository : accesses

    OnboardAccountRepository ..> OnboardAccount : manages
    OnboardTransactionRepository ..> OnboardTransaction : manages
    SpendingLimitRepository ..> SpendingLimit : manages
    PosTerminalRepository ..> PosTerminal : manages

    OnboardAccount "1" *-- "1..*" OnboardAccountMember : authorizedGuests
    OnboardAccount "1" *-- "0..*" OnboardTransaction : ledgerEntries
    OnboardAccountMember "1" *-- "0..*" SpendingLimit : enforcedLimits
```
