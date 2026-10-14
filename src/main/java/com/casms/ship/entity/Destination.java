package com.casms.ship.entity;

import com.casms.ship.enums.DestinationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Destination\"")
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "destination_id")
    private UUID destinationId;

    @Column(name = "destination_name", nullable = false, length = 200)
    private String destinationName;

    @Column(name = "country_code", nullable = false, columnDefinition = "char(2)")
    private String countryCode;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DestinationStatus status;

    @OneToMany(mappedBy = "destination", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Port> ports = new ArrayList<>();

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

    public void addPort(Port port) {
        ports.add(port);
        port.setDestination(this);
    }

    public void removePort(Port port) {
        ports.remove(port);
        port.setDestination(null);
    }
}