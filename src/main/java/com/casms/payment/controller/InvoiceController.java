package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.payment.dto.response.InvoiceResponse;
import com.casms.payment.enums.InvoiceDocumentType;
import com.casms.payment.service.InvoiceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Invoicing & Folio",
        description =
                "Quản lý hóa đơn vé tour, sao kê chi tiêu trên tàu (Folio) và hóa đơn điều chỉnh")
@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Operation(summary = "Sinh hóa đơn cho đơn đặt tour (Booking Invoice)")
    @PreAuthorize("hasAnyAuthority('INVOICE_GENERATE', 'PAYMENT_PROCESS')")
    @PostMapping("/booking/{bookingId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvoiceResponse> generateBookingInvoice(
            @Parameter(description = "ID đơn đặt tour", required = true) @PathVariable
                    UUID bookingId) {
        return ApiResponse.success(
                "Booking invoice generated", invoiceService.generateBookingInvoice(bookingId));
    }

    @Operation(
            summary =
                    "Sinh bảng kê chi tiêu / hóa đơn quyết toán trên tàu (Preliminary Folio hoặc"
                        + " Final Statement)")
    @PreAuthorize("hasAnyAuthority('INVOICE_GENERATE', 'PAYMENT_PROCESS')")
    @PostMapping("/onboard/{onboardAccountId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvoiceResponse> generateOnboardInvoice(
            @Parameter(description = "ID tài khoản phòng trên tàu", required = true) @PathVariable
                    UUID onboardAccountId,
            @Parameter(
                            description = "Loại hóa đơn: PreliminaryFolio hoặc FinalStatement",
                            required = true)
                    @RequestParam(defaultValue = "FinalStatement")
                    InvoiceDocumentType documentType) {
        return ApiResponse.success(
                "Onboard invoice generated",
                invoiceService.generateOnboardInvoice(onboardAccountId, documentType));
    }

    @Operation(summary = "Xem chi tiết hóa đơn kèm danh sách dòng chi tiết")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ', 'INVOICE_GENERATE', 'PAYMENT_PROCESS')")
    @GetMapping("/{invoiceId}")
    public ApiResponse<InvoiceResponse> getInvoiceDetail(
            @Parameter(description = "ID hóa đơn", required = true) @PathVariable UUID invoiceId) {
        return ApiResponse.success(invoiceService.getInvoiceDetail(invoiceId));
    }

    @Operation(summary = "Lấy danh sách hóa đơn theo đơn đặt tour")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ', 'INVOICE_GENERATE')")
    @GetMapping("/booking/{bookingId}")
    public ApiResponse<List<InvoiceResponse>> getInvoicesForBooking(
            @Parameter(description = "ID đơn đặt tour", required = true) @PathVariable
                    UUID bookingId) {
        return ApiResponse.success(invoiceService.getInvoicesForBooking(bookingId));
    }

    @Operation(summary = "Lấy danh sách hóa đơn theo tài khoản phòng trên tàu")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ', 'INVOICE_GENERATE')")
    @GetMapping("/onboard/{onboardAccountId}")
    public ApiResponse<List<InvoiceResponse>> getInvoicesForOnboardAccount(
            @Parameter(description = "ID tài khoản phòng trên tàu", required = true) @PathVariable
                    UUID onboardAccountId) {
        return ApiResponse.success(invoiceService.getInvoicesForOnboardAccount(onboardAccountId));
    }
}
