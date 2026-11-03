package com.casms.cruisepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="\"DepartureFarePackage\"") @Getter @Setter
public class DepartureCruisePackage {
    @Id @Column(name="offer_id") private UUID id;
    @Column(name="cruise_id",nullable=false) private UUID cruiseId;
    @Column(name="template_version_id",nullable=false) private UUID templateVersionId;
    @Column(name="ship_id",nullable=false) private UUID shipId;
    @Column(name="logical_category",nullable=false,length=100) private String logicalCategory;
    @Column(name="logical_category_id") private UUID logicalCategoryId;
    @Column(name="proposed_cabin_type",nullable=false,length=30) private String proposedCabinType;
    @Column(name="commercial_revision",nullable=false) private long commercialRevision;
    @Column(name="sale_enabled",nullable=false) private boolean saleEnabled;
    @Column(name="sale_requested",nullable=false) private boolean saleRequested;
    @Column(name="sales_start",nullable=false) private Instant salesStart;
    @Column(name="sales_end",nullable=false) private Instant salesEnd;
    @Column(nullable=false,length=20) private String status;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="published_by") private UUID publishedBy;
    @Version private long revision;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="created_by",nullable=false) private UUID createdBy;
}
