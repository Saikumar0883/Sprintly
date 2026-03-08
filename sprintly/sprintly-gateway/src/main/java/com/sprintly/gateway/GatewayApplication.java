package com.sprintly.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║           Sprintly — Gateway / Orchestrator              ║
 * ╠══════════════════════════════════════════════════════════╣
 * ║  This is the ONLY @SpringBootApplication in the project. ║
 * ║  It imports all other modules as Maven dependencies and  ║
 * ║  lets Spring Boot scan their beans automatically.        ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * scanBasePackages covers all sub-modules:
 *   com.sprintly.auth        → AuthController, JwtService, SecurityConfig …
 *   com.sprintly.user        → UserController, UserService …
 *   com.sprintly.task        → TaskController, TaskService …
 *   com.sprintly.notification→ NotificationController, WebSocketConfig …
 *   com.sprintly.common      → GlobalExceptionHandler, AppConfigManager …
 *   com.sprintly.gateway     → SwaggerConfig, CorsConfig, RequestLoggingFilter …
 */
@SpringBootApplication(scanBasePackages = "com.sprintly")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
