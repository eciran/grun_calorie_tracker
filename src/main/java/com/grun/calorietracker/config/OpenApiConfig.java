package com.grun.calorietracker.config;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "GRun Calorie Tracker API",
                version = "v1",
                description = "Backend API for user authentication, product lookup, food logs, exercise logs, goals, and progress tracking.",
                contact = @Contact(name = "GRun"),
                license = @License(name = "Private")
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER,
        description = "JWT access token returned from the login or register endpoint."
)
public class OpenApiConfig {

    private static final String API_ERROR_SCHEMA = "ApiErrorResponseDto";
    private static final String API_ERROR_SCHEMA_REF = "#/components/schemas/" + API_ERROR_SCHEMA;

    @Bean
    public OpenApiCustomizer standardizeErrorResponses() {
        return openApi -> {
            ensureErrorSchemaExists(openApi);

            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperations().forEach(operation -> {
                        if (operation.getResponses() == null) {
                            return;
                        }

                        operation.getResponses().forEach((statusCode, response) -> {
                            if (isNoContentResponse(statusCode)) {
                                response.setContent(null);
                                return;
                            }

                            if (isErrorResponse(statusCode)) {
                                response.setContent(errorContent(statusCode, response.getDescription(), path));
                            }
                        });
                    })
            );
        };
    }

    private void ensureErrorSchemaExists(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }

        Map<String, Schema> schemas = ModelConverters.getInstance()
                .read(ApiErrorResponseDto.class);
        Schema errorSchema = schemas.get(API_ERROR_SCHEMA);
        if (errorSchema != null) {
            openApi.getComponents().addSchemas(API_ERROR_SCHEMA, errorSchema);
        }
    }

    private boolean isNoContentResponse(String statusCode) {
        return "204".equals(statusCode);
    }

    private boolean isErrorResponse(String statusCode) {
        try {
            return Integer.parseInt(statusCode) >= 400;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private Content errorContent(String statusCode, String description, String path) {
        return new Content().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType()
                        .schema(new Schema<>().$ref(API_ERROR_SCHEMA_REF))
                        .example(errorExampleValue(statusCode, description, path))
        );
    }

    private Map<String, Object> errorExampleValue(String statusCode, String description, String path) {
        int status = Integer.parseInt(statusCode);
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("timestamp", "2026-05-14T23:44:15");
        example.put("status", status);
        example.put("error", errorTitle(status));
        example.put("message", exampleMessage(status, description));
        example.put("path", path);
        return example;
    }

    private String errorTitle(int status) {
        return switch (status) {
            case 400 -> "Bad request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not found";
            case 409 -> "Conflict";
            default -> status >= 500 ? "Unexpected error" : "Error";
        };
    }

    private String exampleMessage(int status, String description) {
        if (description != null && !description.isBlank()) {
            return description;
        }

        return switch (status) {
            case 400 -> "Request validation failed.";
            case 401 -> "JWT token is missing or invalid.";
            case 403 -> "Authenticated user is not allowed to access this resource.";
            case 404 -> "Requested resource was not found.";
            case 409 -> "Request conflicts with the current resource state.";
            default -> "Request could not be completed.";
        };
    }
}
