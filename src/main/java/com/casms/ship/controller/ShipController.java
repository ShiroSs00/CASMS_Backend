package com.casms.ship.controller;

import com.casms.ship.service.ShipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ship")
@RequiredArgsConstructor
public class ShipController {

    private final ShipService service;
    
    // TODO: implement endpoints
}
