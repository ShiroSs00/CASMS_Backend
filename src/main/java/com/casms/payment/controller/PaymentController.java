package com.casms.payment.controller;

import com.casms.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Finance & Payment", description = "Quản lý tài khoản chi tiêu, giao dịch, hóa đơn và thanh toán")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    // TODO: implement endpoints
}
