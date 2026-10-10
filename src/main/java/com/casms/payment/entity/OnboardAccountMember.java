package com.casms.payment.entity;

import com.casms.identity.entity.Passenger;
import com.casms.payment.enums.AccountMemberStatus;
import com.casms.payment.enums.ChargePermission;
import com.casms.payment.enums.MemberRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"OnboardAccountMember\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"onboard_account_id", "passenger_id"}))
public class OnboardAccountMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_member_id")
    private UUID accountMemberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboard_account_id", nullable = false)
    private OnboardAccount onboardAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false)
    private MemberRole memberRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_permission", nullable = false)
    private ChargePermission chargePermission;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountMemberStatus status;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
