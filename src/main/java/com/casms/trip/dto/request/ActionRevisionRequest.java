package com.casms.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu thực hiện thao tác trên phiên bản kèm đối chiếu revision")
public class ActionRevisionRequest {

    @Schema(description = "Revision hiện tại của phiên bản để đối chiếu lạc quan")
    private Long expectedRevision;
}
