package com.casms.booking.controller;

import com.casms.booking.service.BookingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Booking & Contracts", description = "Quản lý đặt chỗ chuyến đi và hợp đồng hành khách")
@RestController
@RequestMapping("/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService service;

    // TODO: implement endpoints
}
