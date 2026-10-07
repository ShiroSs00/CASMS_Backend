package com.casms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CASMSApplication {

    public static void main(String[] args) {
        SpringApplication.run(CASMSApplication.class, args);
    }
}
