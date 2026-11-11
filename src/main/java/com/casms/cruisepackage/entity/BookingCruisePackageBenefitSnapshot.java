package com.casms.cruisepackage.entity;

import com.casms.cruisepackage.dto.CruisePackagePurchaseSelection;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name="\"BookingFareBenefitSnapshot\"") @Immutable @Getter
public class BookingCruisePackageBenefitSnapshot {
    protected BookingCruisePackageBenefitSnapshot() {}
    public static BookingCruisePackageBenefitSnapshot capture(UUID passengerId,CruisePackagePurchaseSelection.Benefit source,Instant purchasedAt) {
        var snapshot=new BookingCruisePackageBenefitSnapshot(); snapshot.id=UUID.randomUUID(); snapshot.bookingPassengerId=passengerId;
        snapshot.sourceBenefitId=source.sourceBenefitId(); snapshot.inclusion=source.inclusion();
        snapshot.onboardActivityId=source.onboardActivityId(); snapshot.shoreExcursionId=source.shoreExcursionId();
        snapshot.serviceId=source.serviceId(); snapshot.quantityLimit=source.quantityLimit();
        snapshot.eligibility=source.eligibility(); snapshot.resourceName=source.resourceName();
        snapshot.usageUnit=source.usageUnit();snapshot.requiresReservation=source.requiresReservation();
        snapshot.usagePolicy=source.usagePolicy();snapshot.serviceUnitCode=source.serviceUnitCode();
        snapshot.usageStartsAt=source.usageStartsAt();snapshot.usageEndsAt=source.usageEndsAt();
        if(List.of("INCLUDED","LIMITED_INCLUDED").contains(source.inclusion()))snapshot.entitlementGrantedAt=purchasedAt;
        snapshot.occurrenceBindings=source.occurrences().stream().map(o->Map.<String,Object>of(
                "resourceType",o.resourceType(),"resourceId",o.resourceId().toString(),"name",o.name())).toList();
        return snapshot;
    }
    @Id @Column(name="snapshot_benefit_id") private UUID id;
    @Column(name="booking_passenger_id",nullable=false) private UUID bookingPassengerId;
    @Column(name="source_benefit_id",nullable=false) private UUID sourceBenefitId;
    @Column(nullable=false,length=25) private String inclusion;
    @Column(name="onboard_activity_id") private UUID onboardActivityId;
    @Column(name="shore_excursion_id") private UUID shoreExcursionId;
    @Column(name="service_id") private UUID serviceId;
    @Column(name="quantity_limit") private Integer quantityLimit;
    @Column(nullable=false,columnDefinition="text") private String eligibility;
    @Column(name="resource_name",nullable=false,length=250) private String resourceName;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="occurrence_bindings",nullable=false,columnDefinition="jsonb")
    private List<Map<String,Object>> occurrenceBindings;
    @Column(name="usage_unit",length=30) private String usageUnit;
    @Column(name="usage_policy",length=20) private String usagePolicy;
    @Column(name="service_unit_code",length=30) private String serviceUnitCode;
    @Column(name="requires_reservation") private Boolean requiresReservation;
    @Column(name="usage_starts_at") private java.time.Instant usageStartsAt;
    @Column(name="usage_ends_at") private java.time.Instant usageEndsAt;
    @Column(name="entitlement_granted_at") private java.time.Instant entitlementGrantedAt;
}
