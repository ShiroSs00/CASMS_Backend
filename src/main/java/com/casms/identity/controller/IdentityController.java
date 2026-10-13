package com.casms.identity.controller;

import com.casms.identity.service.IdentityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Identity & Users", description = "Quản lý tài khoản, người dùng và hành khách")
@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService service;

    // TODO: implement endpoints
}
