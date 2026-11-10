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
                .title("Plataforma de empleo - API")
                .version("v1")
                .description("API REST bajo /api/v1. Errores en formato ProblemDetail (RFC 7807)."));
    }
}
