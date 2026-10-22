package com.stockflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** API metadata shown at the top of Swagger UI (/swagger-ui.html) and in /v3/api-docs. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI stockFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("StockFlow API")
                        .version("v1")
                        .description("""
                                Order and inventory management. Products have unique SKUs; each product has one \
                                stock row (on hand, reserved, available). Orders reserve stock on creation, deduct \
                                it on confirmation, and release or restock it on cancellation. Every error uses \
                                the same ApiError body with a stable `code`.

                                **To try it:** call `POST /api/v1/auth/login`, copy `accessToken`, click \
                                **Authorize** and paste it."""))
                // Adds the "Authorize" button and sends the token with every request made from Swagger UI.
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
