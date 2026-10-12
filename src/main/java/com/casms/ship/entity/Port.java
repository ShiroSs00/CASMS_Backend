package com.casms.ship.entity;

import com.casms.ship.enums.PortStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Port\"")
public class Port {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "port_id")
    private UUID portId;

    @Column(name = "port_code", unique = true, length = 50)
    private String portCode;

    @Column(name = "port_name", nullable = false, length = 200)
    private String portName;

    @Column(name = "country_code", nullable = false, columnDefinition = "char(2)")
    private String countryCode;

    @Column(name = "time_zone_id", nullable = false, length = 100)
    private String timeZoneId;

    @Column(name = "latitude", precision = 9, scale = 6)
    private java.math.BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private java.math.BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PortStatus status;

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
