package com.casms.checkin.entity;

import com.casms.checkin.enums.GuidanceSessionStatus;
import com.casms.identity.entity.User;
import com.casms.ship.entity.Cruise;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"GuidanceSession\"")
public class GuidanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "guidance_session_id")
    private UUID guidanceSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @Column(name = "session_name", nullable = false, length = 255)
    private String sessionName;

    @Column(name = "location", nullable = false, length = 255)
    private String location;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_user_id", nullable = false)
    private User instructorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GuidanceSessionStatus status;
}
