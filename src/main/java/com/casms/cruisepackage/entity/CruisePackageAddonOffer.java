package com.casms.cruisepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Departure Add-on offer configuration; not a purchase, grant, reservation or payment. */
@Entity @Table(name="\"CruisePackageAddonOffer\"") @Getter @Setter
public class CruisePackageAddonOffer {
    @Id @Column(name="addon_offer_id") private UUID id;
    @Column(name="cruise_id",nullable=false) private UUID cruiseId;
    @Column(name="ship_id",nullable=false) private UUID shipId;
    @Column(name="onboard_activity_id") private UUID onboardActivityId;
    @Column(name="shore_excursion_id") private UUID shoreExcursionId;
    @Column(name="service_id") private UUID serviceId;
    @Column(name="resource_name",nullable=false) private String resourceName;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="currency_code",nullable=false) private String currencyCode;
    @Column(name="pricing_unit",nullable=false) private String pricingUnit;
    @Column(name="service_unit_code") private String serviceUnitCode;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="sales_windows",columnDefinition="jsonb",nullable=false)
    private List<Map<String,String>> salesWindows;
    @Column(nullable=false,columnDefinition="text") private String eligibility;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="published_by") private UUID publishedBy;
    @Column(name="published_at") private Instant publishedAt;
    @Version private long revision;
}
