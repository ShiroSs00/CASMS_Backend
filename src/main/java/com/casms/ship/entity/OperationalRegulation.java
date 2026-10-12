package com.casms.ship.entity;

import com.casms.ship.enums.RegulationScope;
import com.casms.ship.enums.RegulationStatus;
import com.casms.ship.enums.RegulationType;
import com.casms.ship.enums.RegulationUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"OperationalRegulation\"")
public class OperationalRegulation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "regulation_id")
    private UUID regulationId;

    @Column(name = "regulation_code", nullable = false, unique = true, length = 30)
    private String regulationCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "regulation_type", nullable = false)
    private RegulationType regulationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private RegulationScope scope;

    @Column(name = "value", nullable = false, length = 100)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit")
    private RegulationUnit unit;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RegulationStatus status;

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
