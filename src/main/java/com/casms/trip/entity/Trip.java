package com.casms.trip.entity;

import com.casms.identity.entity.User;
import com.casms.ship.enums.ItineraryType;
import com.casms.trip.enums.TripStatus;
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
@Table(name = "\"Trip\"")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "trip_code", nullable = false, unique = true, length = 20)
    private String tripCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "duration_nights", nullable = false)
    private Integer durationNights;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_group", length = 20)
    private com.casms.trip.enums.TripDurationGroup durationGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", length = 50)
    private ItineraryType tripType;

    @Column(name = "planned_scope", length = 50)
    private String plannedScope;

    @Column(name = "domestic_regions", columnDefinition = "TEXT")
    private String domesticRegionsJson;

    @Column(name = "country_codes", columnDefinition = "TEXT")
    private String countryCodesJson;

    @Column(name = "ship_requirements", columnDefinition = "TEXT")
    private String shipRequirements;

    @Column(name = "candidate_destinations", columnDefinition = "TEXT")
    private String candidateDestinations;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TripStatus status = TripStatus.DRAFT;

    @Column(name = "structural_locked_at")
    private Instant structuralLockedAt;

    @Version
    @Column(name = "revision", nullable = false)
    private Long revision = 1L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "trip", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNumber ASC")
    private List<TripScheduleVersion> versions = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (revision == null) {
            revision = 1L;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public List<String> getDomesticRegions() {
        if (domesticRegionsJson == null || domesticRegionsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    domesticRegionsJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return java.util.Arrays.stream(domesticRegionsJson.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    public void setDomesticRegions(List<String> regions) {
        if (regions == null || regions.isEmpty()) {
            this.domesticRegionsJson = null;
        } else {
            try {
                this.domesticRegionsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(regions);
            } catch (Exception e) {
                this.domesticRegionsJson = String.join(",", regions);
            }
        }
    }

    public List<String> getCountryCodes() {
        if (countryCodesJson == null || countryCodesJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    countryCodesJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return java.util.Arrays.stream(countryCodesJson.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    public void setCountryCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            this.countryCodesJson = null;
        } else {
            try {
                this.countryCodesJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(codes);
            } catch (Exception e) {
                this.countryCodesJson = String.join(",", codes);
            }
        }
    }
}
