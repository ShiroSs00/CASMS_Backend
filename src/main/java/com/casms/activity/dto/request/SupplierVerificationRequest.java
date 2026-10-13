package com.casms.activity.dto.request;

import com.casms.activity.enums.SupplierVerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupplierVerificationRequest(

        @NotNull
        SupplierVerificationStatus verificationStatus,

        @Size(max = 1000)
        String verificationNotes
) {
}
