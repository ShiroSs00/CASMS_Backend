package com.casms.cruisepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="\"FarePackageBenefit\"") @Getter @Setter
public class CruisePackageBenefit {
    @Id @Column(name="benefit_id") private UUID id;
    @Column(name="template_version_id",nullable=false) private UUID templateVersionId;
    @Column(nullable=false,length=25) private String inclusion;
    @Column(name="onboard_activity_id") private UUID onboardActivityId;
    @Column(name="shore_excursion_id") private UUID shoreExcursionId;
    @Column(name="service_id") private UUID serviceId;
    @Column(name="quantity_limit") private Integer quantityLimit;
    @Column(nullable=false,columnDefinition="text") private String eligibility;
    @Column(name="usage_unit",length=30) private String usageUnit;
    @Column(name="usage_policy",length=20) private String usagePolicy;
    @Column(name="service_unit_code",length=30) private String serviceUnitCode;
    @Column(name="requires_reservation") private Boolean requiresReservation;
    @Column(name="usage_starts_at") private java.time.Instant usageStartsAt;
    @Column(name="usage_ends_at") private java.time.Instant usageEndsAt;
}
