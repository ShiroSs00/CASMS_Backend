package com.casms.activity.dto.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(

        @NotBlank
        @Size(max = 250)
        String supplierName,

        @Size(max = 100)
        String supplierType,

        @NotBlank
        @Size(max = 200)
        String contactName,

        @Size(max = 50)
        String contactPhone,

        @Email
        @Size(max = 255)
        String contactEmail,

        @Size(max = 1000)
        String notes
) {
}
