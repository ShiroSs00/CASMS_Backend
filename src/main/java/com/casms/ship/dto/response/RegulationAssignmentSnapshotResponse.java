package com.casms.ship.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulationAssignmentSnapshotResponse {

    private HostSummary host;
    private Instant checkedAt;
    private List<RegulationAssignmentItemResponse> direct;
    private List<RegulationAssignmentItemResponse> inherited;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HostSummary {
        private String type;
        private UUID id;
        private String code;
        private String name;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulationAssignmentItemResponse {
        private OperationalRegulationResponse regulation;
        private String source;
        private UUID sourceHostId;
        private String sourceLabel;
        private Boolean canRemove;
        private String removeBlockedReason;
    }
}
