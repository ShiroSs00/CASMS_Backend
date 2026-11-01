package com.casms.ship.dto;
import java.time.Instant;
import java.util.UUID;
import com.casms.ship.entity.Port;
/** Immutable display/time-basis facts. Null on legacy rows means not historically captured. */
public record PortFacts(UUID portId,String portCode,String portName,String countryCode,String timeZoneId,String domesticRegion,Instant capturedAt) {
 public static PortFacts capture(Port p){return p==null?null:new PortFacts(p.getPortId(),p.getPortCode(),p.getPortName(),p.getCountryCode(),p.getTimeZoneId(),p.getDomesticRegion(),Instant.now());}
}
