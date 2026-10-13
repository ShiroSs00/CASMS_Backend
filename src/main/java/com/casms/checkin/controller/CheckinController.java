package com.casms.checkin.controller;

import com.casms.checkin.service.CheckinService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Check-in & Boarding", description = "Quản lý thủ tục check-in, định danh hành khách và check-out")
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService service;

    // TODO: implement endpoints
}
