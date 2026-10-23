package com.stockflow.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import com.stockflow.common.exception.ApiError;

/** API metadata shown at the top of Swagger UI (/swagger-ui.html) and in /v3/api-docs. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final String API_ERROR = "ApiError";
    private static final String JSON = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
    private static final String DEFAULT_RESPONSE = "default";
    private static final String NULL_TYPE = "null";

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

    /**
     * Makes the spec say exactly what the API guarantees, so clients generated from it are strictly typed:
     * <ul>
     *   <li>every field is required unless marked {@code @Schema(nullable = true)} (springdoc would otherwise
     *       leave every field optional, and a generated TypeScript client would treat all of them as maybe-missing);</li>
     *   <li>every operation documents {@link ApiError} as its error response.</li>
     * </ul>
     */
    @Bean
    public OpenApiCustomizer strictContractCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            ModelConverters.getInstance().readAll(ApiError.class).forEach(components::addSchemas);
            components.getSchemas().values().forEach(OpenApiConfig::requireNonNullableProperties);

            ApiResponse errorResponse = new ApiResponse()
                    .description("Error, in the ApiError format with a stable code")
                    .content(new Content().addMediaType(JSON,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + API_ERROR))));
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                        operation.getResponses().addApiResponse(DEFAULT_RESPONSE, errorResponse)));
            }
        };
    }

    @SuppressWarnings("rawtypes")
    private static void requireNonNullableProperties(Schema schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null || properties.isEmpty()) {
            return;
        }
        List<String> required = new ArrayList<>();
        properties.forEach((name, property) -> {
            if (!isNullable(property)) {
                required.add(name);
            }
        });
        schema.setRequired(required);
    }

    /** OpenAPI 3.1 (springdoc's default) writes nullable as {@code type: [X, "null"]}; 3.0 used a flag. */
    @SuppressWarnings("rawtypes")
    private static boolean isNullable(Schema property) {
        return Boolean.TRUE.equals(property.getNullable())
                || (property.getTypes() != null && property.getTypes().contains(NULL_TYPE));
    }
}
