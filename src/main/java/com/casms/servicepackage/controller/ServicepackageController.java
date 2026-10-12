package com.casms.servicepackage.controller;

import com.casms.servicepackage.service.ServicepackageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Services & Packages", description = "Quản lý danh mục dịch vụ trên tàu và các gói tour")
@RestController
@RequestMapping("/servicepackage")
@RequiredArgsConstructor
public class ServicepackageController {

    private final ServicepackageService service;

    // TODO: implement endpoints
}
