package com.casms.servicepackage.entity;

import com.casms.identity.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "\"ServiceUsage\"")
public class ServiceUsage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "service_usage_id")
    private UUID serviceUsageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private User recordedBy;

    @Column(name = "note", length = 500)
    private String note;
}
