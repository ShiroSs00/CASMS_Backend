package com.casms.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BusinessCodeGeneratorPostgresIntegrationTest {

    @Autowired
    private BusinessCodeGenerator generator;

    @Test
    void concurrentRequestsReceiveUniqueFormattedCodes() {
        try (var executor = Executors.newFixedThreadPool(12)) {
            Set<String> codes = IntStream.range(0, 100)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> generator.next("SH"), executor))
                    .map(CompletableFuture::join)
                    .collect(Collectors.toSet());

            assertEquals(100, codes.size());
            assertTrue(codes.stream().allMatch(code -> code.matches("SH-[0-9]{4}")));
        }
    }
}
