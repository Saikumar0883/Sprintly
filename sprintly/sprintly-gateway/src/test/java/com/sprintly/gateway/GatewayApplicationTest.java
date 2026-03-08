package com.sprintly.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GatewayApplicationTest {

    @Test
    void contextLoads() {
        // Verifies the entire Spring context starts without errors
        // This is the most valuable integration test — catches
        // misconfigured beans, missing properties, circular deps, etc.
    }
}
