package com.casms.servicepackage.entity;

import com.casms.activity.entity.Supplier;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStop;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "\"ServiceSchedule\"")
public class ServiceSchedule {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "service_schedule_id")
    private UUID serviceScheduleId;

    @Column(name = "schedule_code", nullable = false, unique = true, length = 60)
    private String scheduleCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id")
    private Cruise cruise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_stop_id")
    private CruiseStop cruiseStop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "location", nullable = false, length = 250)
    private String location;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
