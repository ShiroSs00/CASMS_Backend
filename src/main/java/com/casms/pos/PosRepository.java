package com.casms.pos;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository @RequiredArgsConstructor
public class PosRepository {
    private final JdbcTemplate jdbc;
    record Area(UUID id, UUID shipId, UUID deckId, String modelStatus) {}
    record Device(PosDtos.Terminal terminal, String secretHash) {}
    record SessionRow(UUID terminalId, UUID userId, Instant expiresAt, Instant revokedAt, UUID staffAssignmentId) {}
    record StoredSale(PosDtos.Sale sale, String fingerprint) {}

    Optional<Area> area(UUID ship, UUID id, boolean lock) {
        return one(jdbc.query("SELECT o.ship_model_object_id,m.ship_id,o.deck_id,m.status FROM \"ShipModelObject\" o "
                + "JOIN \"ShipModel\" m ON m.ship_model_id=o.ship_model_id WHERE o.ship_model_object_id=? "
                + "AND m.ship_id=? AND o.object_type='FACILITY' AND o.cabin_id IS NULL AND o.deck_id IS NOT NULL "
                + "AND EXISTS(SELECT 1 FROM \"Deck\" d WHERE d.deck_id=o.deck_id AND d.ship_id=m.ship_id)"
                + (lock ? " FOR UPDATE OF o" : ""), (r,n)->new Area(id, uuid(r,"ship_id"),uuid(r,"deck_id"),r.getString("status")),id,ship));
    }
    private static final RowMapper<Device> DEVICE = (r,n)->new Device(new PosDtos.Terminal(uuid(r,"terminal_id"),
            uuid(r,"ship_id"),uuid(r,"area_object_id"),r.getString("terminal_code"),r.getString("terminal_name"),
            permissions(r),r.getBoolean("enabled")),r.getString("secret_hash"));
    Optional<Device> device(UUID id, boolean lock) {
        return one(jdbc.query("SELECT * FROM \"PosTerminal\" WHERE terminal_id=?"+(lock?" FOR UPDATE":""),DEVICE,id));
    }
    List<PosDtos.Terminal> devices(UUID ship) {
        return jdbc.query("SELECT * FROM \"PosTerminal\" WHERE ship_id=? ORDER BY terminal_code",DEVICE,ship).stream().map(Device::terminal).toList();
    }
    void createDevice(PosDtos.Terminal t,String hash) {
        jdbc.update("INSERT INTO \"PosTerminal\" (terminal_id,ship_id,area_object_id,terminal_code,terminal_name,secret_hash,permissions,enabled) "
                +"VALUES (?,?,?,?,?,?,string_to_array(?,','),?)",t.terminalId(),t.shipId(),t.areaId(),t.code(),t.name(),hash,csv(t.permissions()),t.enabled());
        jdbc.update("INSERT INTO \"PosTerminalArea\" (terminal_id,area_object_id) VALUES (?,?)",t.terminalId(),t.areaId());
    }
    void updateDevice(UUID id,PosDtos.TerminalUpdate input) {
        jdbc.update("UPDATE \"PosTerminal\" SET terminal_name=?, permissions=string_to_array(?,','),enabled=? WHERE terminal_id=?",
                input.name().trim(),csv(input.permissions()),input.enabled(),id);
        revokeDevice(id);
    }
    void rotate(UUID id,String hash) { jdbc.update("UPDATE \"PosTerminal\" SET secret_hash=? WHERE terminal_id=?",hash,id);revokeDevice(id); }
    private void revokeDevice(UUID id) {jdbc.update("UPDATE \"PosSession\" SET revoked_at=now() WHERE terminal_id=? AND revoked_at IS NULL",id);}
    List<PosDtos.AreaService> assignments(UUID area) {
        return jdbc.query("SELECT a.*,COALESCE(s.permissions,'{}') AS source_permissions FROM \"AreaServiceAssignment\" a "
                +"LEFT JOIN \"ServiceOperationPolicy\" s ON s.service_id=a.service_id WHERE area_object_id=? ORDER BY a.service_id",
                (r,n)->{var effective=permissions(r,"source_permissions");if(!r.getBoolean("inherit_permissions"))effective.retainAll(permissions(r));
                    return new PosDtos.AreaService(area,uuid(r,"service_id"),Set.copyOf(effective),r.getBoolean("enabled"),r.getBoolean("inherit_permissions"));},area);
    }
    void assign(UUID area,UUID service,PosDtos.AssignmentInput input) {
        jdbc.update("INSERT INTO \"AreaServiceAssignment\" (assignment_id,area_object_id,service_id,permissions,enabled,inherit_permissions) "
                +"VALUES (?,?,?,string_to_array(?,','),?,?) ON CONFLICT (area_object_id,service_id) DO UPDATE SET permissions=excluded.permissions,enabled=excluded.enabled,inherit_permissions=excluded.inherit_permissions",
                UUID.randomUUID(),area,service,csv(input.permissions()),input.enabled(),input.permissions()==null);
    }
    void unassign(UUID area,UUID service) {jdbc.update("DELETE FROM \"AreaServiceAssignment\" WHERE area_object_id=? AND service_id=?",area,service);}
    void session(String hash,UUID device,UUID user,Instant expires,UUID staffAssignment) {
        jdbc.update("INSERT INTO \"PosSession\" (token_hash,terminal_id,user_id,created_at,expires_at,area_staff_assignment_id) VALUES (?,?,?,now(),?,?)",hash,device,user,Timestamp.from(expires),staffAssignment);
    }
    Optional<SessionRow> session(String hash) {
        return one(jdbc.query("SELECT * FROM \"PosSession\" WHERE token_hash=?",(r,n)->new SessionRow(uuid(r,"terminal_id"),
                uuid(r,"user_id"),instant(r,"expires_at"),instant(r,"revoked_at"),uuid(r,"area_staff_assignment_id")),hash));
    }
    void logout(String hash) {jdbc.update("UPDATE \"PosSession\" SET revoked_at=now() WHERE token_hash=?",hash);}
    List<PosDtos.AvailableService> available(UUID terminal,UUID area,Set<PosPermission> devicePermissions) {
        return jdbc.query("SELECT s.service_id,s.service_code,s.service_name,s.price,s.currency_code,a.permissions AS area_permissions,p.permissions AS pos_permissions, "
                +"sp.permissions AS source_permissions,a.inherit_permissions AS area_inherit,(p.service_id IS NULL OR p.inherit_permissions) AS pos_inherit FROM \"AreaServiceAssignment\" a "
                +"JOIN \"Service\" s ON s.service_id=a.service_id JOIN \"ShipModelObject\" o ON o.ship_model_object_id=a.area_object_id "
                +"JOIN \"DeckServiceAssignment\" d ON d.deck_id=o.deck_id AND d.service_id=s.service_id "
                +"JOIN \"ServiceOperationPolicy\" sp ON sp.service_id=s.service_id "
                +"LEFT JOIN \"PosAreaServicePermission\" p ON p.area_object_id=a.area_object_id AND p.service_id=a.service_id AND p.terminal_id=? "
                +"WHERE a.area_object_id=? AND a.enabled AND (p.service_id IS NULL OR p.enabled) AND s.status='ACTIVE' ORDER BY s.service_code",(r,n)->{
                    var effective=permissions(r,"source_permissions");effective.retainAll(devicePermissions);
                    if(!r.getBoolean("area_inherit"))effective.retainAll(permissions(r,"area_permissions"));
                    if(!r.getBoolean("pos_inherit"))effective.retainAll(permissions(r,"pos_permissions"));
                    return new PosDtos.AvailableService(uuid(r,"service_id"),r.getString("service_code"),r.getString("service_name"),
                            r.getBigDecimal("price"),r.getString("currency_code"),Set.copyOf(effective));
                },terminal,area).stream().filter(s->!s.permissions().isEmpty()).toList();
    }
    List<UUID> requestIds(UUID terminal,UUID area,UUID ship,UUID cruise) {
        return jdbc.query("SELECT r.service_request_id FROM \"ServiceRequest\" r JOIN \"Booking\" b ON b.booking_id=r.booking_id "
                +"JOIN \"Cruise\" c ON c.cruise_id=b.cruise_id JOIN \"AreaServiceAssignment\" a ON a.service_id=r.service_id "
                +"LEFT JOIN \"PosAreaServicePermission\" p ON p.area_object_id=a.area_object_id AND p.service_id=a.service_id AND p.terminal_id=? "
                +"JOIN \"ServiceOperationPolicy\" sp ON sp.service_id=a.service_id "
                +"JOIN \"Service\" s ON s.service_id=r.service_id JOIN \"ShipModelObject\" o ON o.ship_model_object_id=a.area_object_id "
                +"JOIN \"DeckServiceAssignment\" d ON d.deck_id=o.deck_id AND d.service_id=r.service_id "
                +"WHERE a.area_object_id=? AND a.enabled AND (a.inherit_permissions OR 'SERVICE_REQUEST_READ'=ANY(a.permissions)) AND c.ship_id=? AND c.cruise_id=? "
                +"AND 'SERVICE_REQUEST_READ'=ANY(sp.permissions) AND (p.service_id IS NULL OR (p.enabled AND (p.inherit_permissions OR 'SERVICE_REQUEST_READ'=ANY(p.permissions)))) AND s.status='ACTIVE' "
                +"ORDER BY r.request_time DESC,r.service_request_id LIMIT 200",(r,n)->uuid(r,"service_request_id"),terminal,area,ship,cruise);
    }
    private static final RowMapper<StoredSale> SALE=(r,n)->new StoredSale(new PosDtos.Sale(uuid(r,"sale_id"),uuid(r,"terminal_id"),
            uuid(r,"area_object_id"),uuid(r,"service_id"),uuid(r,"service_request_id"),uuid(r,"onboard_account_id"),uuid(r,"booking_passenger_id"),
            r.getInt("quantity"),r.getBigDecimal("unit_price"),r.getBigDecimal("amount"),r.getString("currency"),uuid(r,"created_by_user_id"),
            uuid(r,"charge_id"),instant(r,"created_at"),instant(r,"reversed_at")),r.getString("request_fingerprint"));
    Optional<StoredSale> sale(UUID id) {return one(jdbc.query("SELECT * FROM \"PosSale\" WHERE sale_id=?",SALE,id));}
    Optional<StoredSale> sale(UUID terminal,String key) {return one(jdbc.query("SELECT * FROM \"PosSale\" WHERE terminal_id=? AND idempotency_key=?",SALE,terminal,key));}
    void sale(PosDtos.Sale s,String key,String fingerprint) {
        jdbc.update("INSERT INTO \"PosSale\" (sale_id,terminal_id,area_object_id,service_id,service_request_id,onboard_account_id,booking_passenger_id,"
                +"quantity,unit_price,amount,currency,created_by_user_id,charge_id,created_at,idempotency_key,request_fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                s.saleId(),s.terminalId(),s.areaId(),s.serviceId(),s.serviceRequestId(),s.onboardAccountId(),s.bookingPassengerId(),s.quantity(),
                s.unitPrice(),s.amount(),s.currency(),s.employeeId(),s.chargeId(),Timestamp.from(s.createdAt()),key,fingerprint);
    }
    static String csv(Set<PosPermission> p) {return p==null?"":p.stream().map(Enum::name).sorted().collect(Collectors.joining(","));}
    private static Set<PosPermission> permissions(ResultSet r) throws SQLException {
        return permissions(r,"permissions");
    }
    static Set<PosPermission> permissions(ResultSet r,String column) throws SQLException {
        Set<PosPermission> p=EnumSet.noneOf(PosPermission.class);
        for(String value:(String[])r.getArray(column).getArray())p.add(PosPermission.valueOf(value));return p;
    }
    static UUID uuid(ResultSet r,String key) throws SQLException {return r.getObject(key,UUID.class);}
    static Instant instant(ResultSet r,String key) throws SQLException {var t=r.getTimestamp(key);return t==null?null:t.toInstant();}
    private static <T> Optional<T> one(List<T> rows) {return rows.stream().findFirst();}
}
