package com.casms.trip.dto.response;
import java.util.UUID;
public record DestinationVisitResponse(UUID visitId,String clientKey,UUID destinationId,String destinationName,Integer sequence) {}
