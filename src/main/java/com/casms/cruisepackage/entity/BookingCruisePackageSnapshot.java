package com.casms.cruisepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Prepared storage for real confirmation only. No mutable template lookup substitutes for this history. */
@Entity @Table(name="\"BookingFareSnapshot\"") @Immutable @Getter
public class BookingCruisePackageSnapshot {
    protected BookingCruisePackageSnapshot() {}
    public static BookingCruisePackageSnapshot capture(UUID passengerId,UUID bookingId,
            com.casms.cruisepackage.dto.CruisePackagePurchaseSelection selection,Instant purchasedAt) {
        var snapshot=new BookingCruisePackageSnapshot(); snapshot.bookingPassengerId=passengerId; snapshot.bookingId=bookingId;
        snapshot.offerId=selection.offerId(); snapshot.templateVersionId=selection.templateVersionId();
        snapshot.priceRevisionId=selection.priceRevisionId(); snapshot.shipId=selection.shipId();
        snapshot.logicalCategory=selection.logicalCategory(); snapshot.cabinType=selection.cabinType().name();
        snapshot.logicalCategoryId=selection.logicalCategoryId();snapshot.cabinMappingId=selection.cabinMappingId();snapshot.physicalCabinIds=selection.physicalCabinIds();
        snapshot.amount=selection.amount(); snapshot.currencyCode="USD"; snapshot.priceUnit="PERSON";
        snapshot.templateName=selection.templateName(); snapshot.templateDescription=selection.templateDescription();
        snapshot.purchasedAt=purchasedAt; return snapshot;
    }
    @Id @Column(name="booking_passenger_id") private UUID bookingPassengerId;
    @Column(name="booking_id",nullable=false) private UUID bookingId;
    @Column(name="offer_id",nullable=false) private UUID offerId;
    @Column(name="template_version_id",nullable=false) private UUID templateVersionId;
    @Column(name="price_revision_id",nullable=false) private UUID priceRevisionId;
    @Column(name="ship_id",nullable=false) private UUID shipId;
    @Column(name="logical_category",nullable=false,length=100) private String logicalCategory;
    @Column(name="logical_category_id") private UUID logicalCategoryId;
    @Column(name="cabin_mapping_id") private UUID cabinMappingId;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name="physical_cabin_ids",columnDefinition="jsonb") private java.util.List<UUID> physicalCabinIds;
    @Column(name="cabin_type",nullable=false,length=30) private String cabinType;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
    @Column(name="price_unit",nullable=false,length=10) private String priceUnit;
    @Column(name="template_name",nullable=false,length=200) private String templateName;
    @Column(name="template_description",columnDefinition="text") private String templateDescription;
    @Column(name="purchased_at",nullable=false) private Instant purchasedAt;
}
