package com.casms.servicepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.enums.ServiceUsagePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.ZoneId;
import java.sql.Timestamp;

/** Caller holds the Service row lock, including completion, so reservations cannot race usage. */
@Service
@RequiredArgsConstructor
public class ServiceUsageQuotaService {
    private final JdbcTemplate jdbc;

    /** Preserve explicit INCLUDED service purchases from the older package model. */
    public void requireLegacyPrepaid(ServiceRequest request) {
        var links = jdbc.queryForList("""
                WITH RECURSIVE chain AS (
                  SELECT p.tour_package_id,p.parent_package_id,0 AS depth,ARRAY[p.tour_package_id] AS path
                    FROM "BookingPassengerPackage" b JOIN "TourPackage" p USING(tour_package_id)
                    WHERE b.booking_passenger_id=?
                  UNION ALL
                  SELECT p.tour_package_id,p.parent_package_id,c.depth+1,c.path||p.tour_package_id
                    FROM "TourPackage" p JOIN chain c ON p.tour_package_id=c.parent_package_id
                    WHERE NOT p.tour_package_id=ANY(c.path)
                ) SELECT s.benefit_type::text AS benefit_type,s.quantity_limit,s.unit_code,s.owner_scope
                  FROM chain c JOIN "TourPackageService" s USING(tour_package_id)
                  WHERE s.service_id=? ORDER BY c.depth LIMIT 1
                """, request.getBookingPassenger().getBookingPassengerId(), request.getService().getServiceId());
        if (links.isEmpty() || !"INCLUDED".equals(links.getFirst().get("benefit_type")))
            throw new BusinessException("PREPAID_ENTITLEMENT_REQUIRED", "Paid service requires a purchased service benefit; onboard retail must be recorded through POS");
        var grant = links.getFirst();
        if (!"PASSENGER".equals(grant.get("owner_scope")))
            throw new BusinessException("BENEFIT_USAGE_UNIT_UNSUPPORTED", "Legacy service entitlement must be scoped to the passenger");
        if (grant.get("unit_code") != null && !grant.get("unit_code").equals(request.getService().getUnitCode()))
            throw new BusinessException("BENEFIT_USAGE_UNIT_UNSUPPORTED", "Legacy service unit differs from the configured service unit");
        if (grant.get("quantity_limit") instanceof Number limit) {
            Long allocated = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(q.quantity),0) FROM (
                      SELECT u.quantity FROM "ServiceUsage" u JOIN "ServiceRequest" r USING(service_request_id)
                        WHERE r.booking_passenger_id=? AND r.service_id=? AND r.usage_mode<>'ONBOARD'
                        AND NOT EXISTS(SELECT 1 FROM "PosSale" sale WHERE sale.service_request_id=r.service_request_id)
                      UNION ALL
                      SELECT r.quantity FROM "ServiceRequest" r WHERE r.booking_passenger_id=? AND r.service_id=?
                        AND r.usage_mode<>'ONBOARD'
                        AND NOT EXISTS(SELECT 1 FROM "PosSale" sale WHERE sale.service_request_id=r.service_request_id) AND r.status IN ('REQUESTED','ACCEPTED','IN_PROGRESS')
                        AND (?::uuid IS NULL OR r.service_request_id<>?::uuid)
                    ) q
                    """, Long.class, request.getBookingPassenger().getBookingPassengerId(), request.getService().getServiceId(),
                    request.getBookingPassenger().getBookingPassengerId(), request.getService().getServiceId(),
                    request.getServiceRequestId(), request.getServiceRequestId());
            if (allocated == null || allocated + request.getQuantity() > limit.longValue())
                throw new BusinessException("ENTITLEMENT_EXHAUSTED", "Legacy purchased service quota includes used and pending quantities");
        }
    }

    public void requireAvailable(ServiceRequest request, Instant at) {
        if (!"PRE_CRUISE".equals(request.getUsageMode())
                || request.getUsagePolicySnapshot() == ServiceUsagePolicy.UNLIMITED) return;
        if (request.getBookingPassenger() == null)
            throw new BusinessException("SERVICE_BOOKING_REQUIRED", "A booking passenger is required for prepaid usage");
        Instant start = Instant.parse("0001-01-01T00:00:00Z"), end = Instant.parse("9999-12-31T00:00:00Z");
        if (request.getUsagePolicySnapshot() == ServiceUsagePolicy.PER_DAY) {
            var zone = ZoneId.of(request.getUsageTimeZoneSnapshot());
            var day = at.atZone(zone).toLocalDate();
            start = day.atStartOfDay(zone).toInstant();
            end = day.plusDays(1).atStartOfDay(zone).toInstant();
        }
        Long allocated = jdbc.queryForObject("""
                SELECT COALESCE(SUM(q.quantity),0) FROM (
                  SELECT u.quantity FROM "ServiceUsage" u JOIN "ServiceRequest" r USING(service_request_id)
                    WHERE r.booking_passenger_id=? AND r.service_id=? AND r.usage_mode='PRE_CRUISE'
                      AND u.used_at>=? AND u.used_at<?
                  UNION ALL
                  SELECT r.quantity FROM "ServiceRequest" r LEFT JOIN "ServiceSchedule" s USING(service_schedule_id)
                    WHERE r.booking_passenger_id=? AND r.service_id=? AND r.usage_mode='PRE_CRUISE'
                      AND r.status IN ('REQUESTED','ACCEPTED','IN_PROGRESS')
                      AND (?::uuid IS NULL OR r.service_request_id<>?::uuid)
                      AND COALESCE(s.starts_at,r.request_time)>=? AND COALESCE(s.starts_at,r.request_time)<?
                ) q
                """, Long.class,
                request.getBookingPassenger().getBookingPassengerId(), request.getService().getServiceId(), Timestamp.from(start), Timestamp.from(end),
                request.getBookingPassenger().getBookingPassengerId(), request.getService().getServiceId(),
                request.getServiceRequestId(), request.getServiceRequestId(), Timestamp.from(start), Timestamp.from(end));
        if (request.getUsageLimitSnapshot() == null || allocated == null
                || allocated + request.getQuantity() > request.getUsageLimitSnapshot())
            throw new BusinessException("SERVICE_USAGE_LIMIT_EXCEEDED", "Service usage quota for this passenger and period is exhausted");
    }
}
