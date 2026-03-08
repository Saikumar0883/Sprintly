package com.sprintly.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI 3 configuration.
 *
 * Access Swagger UI at: http://localhost:8080/swagger-ui.html
 *
 * springdoc-openapi auto-discovers ALL @RestController beans from every module
 * imported by sprintly-gateway, and groups them all in one unified API doc.
 *
 * The JWT security scheme adds an "Authorize" button to Swagger UI so testers
 * can paste a Bearer token and call protected endpoints directly from the browser.
 */
@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI taskFlowOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local Dev Server")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, jwtSecurityScheme())
                );
    }

    private Info apiInfo() {
        return new Info()
                .title("TaskFlow API")
                .description("""
                        Real-Time Collaborative Task Management System.
                        
                        **Authentication:** Use POST /api/auth/login to get a JWT token.
                        Click the **Authorize** button and paste: `<your-token>` (without Bearer prefix).
                        
                        **Modules:**
                        - 🔐 Auth — Register, Login, Refresh, Logout
                        - 👤 Users — Profile management, role assignment
                        - ✅ Tasks — CRUD, assignment, status transitions, comments
                        - 🔔 Notifications — WebSocket real-time alerts
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("Sprintly Team")
                        .email("dev@sprintly.com"))
                .license(new License().name("MIT"));
    }

    private SecurityScheme jwtSecurityScheme() {
        return new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste your JWT access token here (obtained from POST /api/auth/login)");
    }
}
