package com.plataformaempleo.api.shared.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI plataformaEmpleoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Worksy API")
                .version("v1")
                .description("Worksy: plataforma de empleo con preselección de CV y revisión humana. "
                        + "API REST bajo /api/v1. Errores en formato ProblemDetail (RFC 7807)."));
    }
}
