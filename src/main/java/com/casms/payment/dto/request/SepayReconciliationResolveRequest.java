package com.casms.payment.dto.request;

import com.casms.payment.enums.SepayReconciliationAction;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SepayReconciliationResolveRequest {

    @NotNull(message = "Hành động đối soát là bắt buộc")
    @Schema(
            description =
                    "Hành động đối soát của Finance: APPLY_TO_BOOKING, MARK_FOR_REFUND,"
                        + " MANUAL_RESOLVED, DISMISSED")
    private SepayReconciliationAction action;

    @NotBlank(message = "Lý do đối soát là bắt buộc")
    @Size(max = 1000, message = "Lý do đối soát tối đa 1000 ký tự")
    @Schema(description = "Lý do xử lý đối soát")
    private String reason;

    @NotBlank(message = "Mã chứng từ đối chiếu là bắt buộc")
    @Size(max = 255, message = "Mã chứng từ tối đa 255 ký tự")
    @Schema(description = "Mã chứng từ / biên bản / sao kê ngân hàng dùng đối chiếu")
    private String evidenceReference;

    @Schema(
            description =
                    "ID đặt chỗ cần gắn khi chọn APPLY_TO_BOOKING (nếu giao dịch gốc chưa gắn đặt"
                        + " chỗ)")
    private UUID targetBookingId;
}
