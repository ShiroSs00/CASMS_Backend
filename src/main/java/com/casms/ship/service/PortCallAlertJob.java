package com.casms.ship.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class PortCallAlertJob {
    private final JdbcTemplate jdbc;

    @Scheduled(fixedDelayString = "${casms.port-call-alert.poll-millis:60000}")
    public void notifyOperations() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                WITH pending AS (
                    SELECT s.cruise_stop_id, c.cruise_code,
                        CASE WHEN s.actual_arrival IS NULL THEN 'ARRIVAL_PENDING'
                             ELSE 'DEPARTURE_PENDING' END AS phase
                    FROM "CruiseStop" s
                    JOIN "Itinerary" i ON i.itinerary_id = s.itinerary_id
                    JOIN "Cruise" c ON c.cruise_id = i.cruise_id
                    WHERE c.status = 'IN_PROGRESS' AND s.status <> 'OMITTED'
                      AND s.actual_departure IS NULL
                      AND ((s.actual_arrival IS NULL AND s.planned_arrival < ?)
                           OR (s.actual_arrival IS NOT NULL AND s.planned_departure < ?))
                      AND EXISTS (SELECT 1 FROM "CruiseStop" other
                                  WHERE other.itinerary_id = i.itinerary_id
                                    AND other.status <> 'OMITTED' AND other.stop_order < s.stop_order)
                      AND EXISTS (SELECT 1 FROM "CruiseStop" other
                                  WHERE other.itinerary_id = i.itinerary_id
                                    AND other.status <> 'OMITTED' AND other.stop_order > s.stop_order)
                )
                INSERT INTO "Notification"
                    (notification_id, recipient_user_id, notification_type, title, content, created_at, port_call_alert_key)
                SELECT gen_random_uuid(), u.user_id, 'PORT_CALL_CONFIRMATION_PENDING',
                       'Chưa xác nhận giờ cập hoặc rời cảng',
                       'Chuyến ' || p.cruise_code || ': ' || p.phase || '. Vui lòng kiểm tra giờ thực tế.',
                       ?, p.cruise_stop_id::text || ':' || p.phase
                FROM pending p CROSS JOIN "UserRole" r JOIN "User" u ON u.user_id = r.user_id
                WHERE r.role = 'OPERATIONS_MANAGER' AND u.status = 'ACTIVE'
                ON CONFLICT (recipient_user_id, port_call_alert_key) DO NOTHING
                """, now, now, now);
    }
}
