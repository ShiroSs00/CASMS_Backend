package com.casms.activity.controller;

import com.casms.activity.service.ActivityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Activities & Excursions", description = "Quản lý hoạt động trên tàu và các tour tham quan trên bờ")
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService service;

    // TODO: implement endpoints
}
