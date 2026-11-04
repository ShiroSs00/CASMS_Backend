package com.casms.cruisepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="\"DepartureFarePackagePrice\"") @Getter @Setter
public class DepartureCruisePackagePrice {
    @Id @Column(name="price_revision_id") private UUID id;
    @Column(name="offer_id",nullable=false) private UUID offerId;
    @Column(name="template_version_id",nullable=false) private UUID templateVersionId;
    @Column(name="revision_number",nullable=false) private int revisionNumber;
    @Column(name="offer_commercial_revision",nullable=false) private long offerCommercialRevision;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
    @Column(name="price_unit",nullable=false,length=10) private String priceUnit;
    @Column(nullable=false,length=20) private String status;
    @Version private long revision;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="submitted_by") private UUID submittedBy;
    @Column(name="submitted_at") private Instant submittedAt;
    @Column(name="reviewed_by") private UUID reviewedBy;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="review_reason",columnDefinition="text") private String reviewReason;
}
