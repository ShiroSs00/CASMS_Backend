# Phân Hệ Tài Khoản Phòng Không Tiền Mặt & Điểm Bán Hàng (Cashless Folio & POS)

Mô hình tài khoản tiêu dùng buồng phòng (`OnboardAccount`), ủy quyền thành viên phòng (`OnboardAccountMember`), nhật ký chi tiêu (`OnboardTransaction`), định mức chi tiêu cá nhân (`SpendingLimit`) và thiết bị máy POS quầy bar/nhà hàng (`PosTerminal`).

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

    class OnboardAccount {
        +UUID onboardAccountId
        +BigDecimal creditLimit
        +OnboardAccountStatus status
        +Instant activatedAt
        +Instant closedAt
        +Instant createdAt
    }

    class OnboardAccountMember {
        +UUID accountMemberId
        +MemberRole memberRole
        +ChargePermission chargePermission
        +AccountMemberStatus status
        +Instant effectiveFrom
        +Instant effectiveTo
    }

    class OnboardTransaction {
        +UUID transactionId
        +BigDecimal amount
        +String currencyCode
        +TransactionType transactionType
        +TransactionStatus status
        +String sourceType
        +UUID sourceReferenceId
        +TransactionSyncStatus syncStatus
        +Instant syncedAt
        +Instant transactionTime
        +String description
    }

    class SpendingLimit {
        +UUID limitId
        +BigDecimal limitAmount
        +String currencyCode
        +LimitStatus status
        +Instant effectiveFrom
        +Instant effectiveTo
    }

    class PosTerminal {
        +UUID terminalId
        +String terminalCode
        +String terminalName
        +UUID areaId
        +Boolean enabled
    }

    class PosSession {
        +UUID sessionId
        +String sessionTokenHash
        +Instant openedAt
        +Instant revokedAt
    }

    class FinalSettlement {
        +UUID settlementId
        +BigDecimal totalCharges
        +BigDecimal totalCredits
        +BigDecimal finalPayable
        +SettlementStatus status
        +Instant settledAt
    }

    class TransactionSyncStatus {
        <<enumeration>>
        PENDING
        SYNCED
        FAILED
    }

    class TransactionType {
        <<enumeration>>
        CHARGE
        VOID
        REFUND
        ADJUSTMENT
    }

    %% Relationships
    OnboardAccount "1" *-- "1..*" OnboardAccountMember : authorizedGuests
    OnboardAccount "1" *-- "0..*" OnboardTransaction : ledgerEntries
    OnboardAccountMember "1" *-- "0..*" SpendingLimit : enforcedLimits
    PosTerminal "1" *-- "0..*" PosSession : operationalSessions
    PosSession "1" ..> OnboardTransaction : recordsTransactions
    OnboardAccount "1" *-- "0..1" FinalSettlement : closesAtDisembarkation
    OnboardTransaction --> TransactionSyncStatus : edgeSyncState
    OnboardTransaction --> TransactionType : type
```

## Giải Thích Quan Hệ & Ràng Buộc
- **Cashless Folio (Tài khoản phòng)**: Hành khách trên tàu không cần mang tiền mặt. Mọi chi phí ăn uống, giải trí tại quầy bar hay spa được quẹt thẻ và hạch toán trực tiếp vào `OnboardAccount` của buồng phòng.
- **Hybrid Edge-to-Cloud Sync**: Cột `syncStatus` (`PENDING`, `SYNCED`, `FAILED`) trên `OnboardTransaction` cho phép máy POS hoạt động ngoại tuyến khi mất sóng biển, sau đó tự động đồng bộ lên máy chủ đám mây khi tàu kết nối lại vệ tinh hoặc cập cảng mà không gây gián đoạn phục vụ.
- **SpendingLimit**: Giới hạn chi tiêu theo ngày hoặc theo chuyến dành cho trẻ em/người phụ thuộc trong phòng nhằm tránh phát sinh nợ ngoài dự kiến.
- **FinalSettlement**: Quyết toán tổng thể số dư chi tiêu tại quầy lễ tân trước khi hành khách rời tàu (Disembarkation).

