package com.casms.servicepackage.controller;

import com.casms.servicepackage.service.ServicepackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/servicepackage")
@RequiredArgsConstructor
public class ServicepackageController {

    private final ServicepackageService service;
    
    // TODO: implement endpoints
}
