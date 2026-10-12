package com.casms.checkin.entity;

import com.casms.checkin.enums.CheckinStepCode;
import com.casms.checkin.enums.CheckinStepStatus;
import com.casms.identity.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CheckinStep\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cruise_checkin_id", "step_code"}))
public class CheckinStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "checkin_step_id")
    private UUID checkinStepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_checkin_id", nullable = false)
    private CruiseCheckin cruiseCheckin;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_code", nullable = false)
    private CheckinStepCode stepCode;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CheckinStepStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by_user_id")
    private User checkedByUser;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;
}
